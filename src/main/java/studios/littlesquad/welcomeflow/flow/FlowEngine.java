package studios.littlesquad.welcomeflow.flow;

import org.bukkit.entity.Player;
import studios.littlesquad.welcomeflow.action.ActionContext;
import studios.littlesquad.welcomeflow.action.ActionRunner;
import studios.littlesquad.welcomeflow.action.ActionTrigger;
import studios.littlesquad.welcomeflow.config.ConfigManager;
import studios.littlesquad.welcomeflow.objective.ObjectiveDefinition;
import studios.littlesquad.welcomeflow.objective.ObjectiveType;
import studios.littlesquad.welcomeflow.session.PlayerSession;
import studios.littlesquad.welcomeflow.session.SessionManager;
import studios.littlesquad.welcomeflow.storage.FlowProgress;
import studios.littlesquad.welcomeflow.storage.FlowState;
import studios.littlesquad.welcomeflow.storage.FunnelEvent;
import studios.littlesquad.welcomeflow.storage.StorageException;
import studios.littlesquad.welcomeflow.storage.StorageProvider;
import studios.littlesquad.welcomeflow.util.Scheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The state machine: assigns flows, credits objective progress, advances
 * steps and fires the matching action lists.
 *
 * <p>Every state transition in the plugin goes through this class. The
 * objective listeners are deliberately thin -- they translate a Bukkit event
 * into a single {@link #creditProgress} call and know nothing about steps,
 * persistence or actions. That keeps the rules in one readable place instead
 * of smeared across thirteen listeners.
 *
 * <p>Main thread only. Storage writes are delegated to
 * {@link SessionManager}, which snapshots and writes off-thread.
 */
public final class FlowEngine {

    private final FlowRegistry flowRegistry;
    private final SessionManager sessionManager;
    private final ActionRunner actionRunner;
    private final StorageProvider storage;
    private final Scheduling scheduling;
    private final ConfigManager config;
    private final Logger logger;

    /**
     * Reactors to state transitions (checklist bossbar, public API events).
     * Notified defensively -- a misbehaving observer must not derail a
     * player's flow.
     */
    private final List<FlowObserver> observers = new ArrayList<>();

    public FlowEngine(FlowRegistry flowRegistry, SessionManager sessionManager, ActionRunner actionRunner,
                      StorageProvider storage, Scheduling scheduling, ConfigManager config, Logger logger) {
        this.flowRegistry = flowRegistry;
        this.sessionManager = sessionManager;
        this.actionRunner = actionRunner;
        this.storage = storage;
        this.scheduling = scheduling;
        this.config = config;
        this.logger = logger;
    }

    // --- joining ----------------------------------------------------------

    /**
     * Decides what happens to a player who just joined: resume an interrupted
     * flow, or start a newly selected one.
     */
    public void onJoin(Player player, PlayerSession session) {
        countFunnelForSelectableFlow(player, session);

        FlowProgress existing = session.progress();
        if (existing != null) {
            Flow flow = flowRegistry.byId(existing.flowId());
            if (flow == null) {
                // The flow was renamed or deleted while the player was away.
                // Dropping the row beats leaving them stuck referencing it.
                logger.warning("Player " + player.getName() + " had progress in flow '" + existing.flowId()
                        + "' which no longer exists in flows.yml; clearing their progress.");
                session.progress(null);
                return;
            }
            long window = flow.settings().abandonAfterMillis();
            if (window > 0 && session.timeAwayMillis() >= window) {
                existing.state(FlowState.ABANDONED);
                session.markDirty();
                sessionManager.flush(session);
                incrementFunnel(flow.id(), FunnelEvent.ABANDONED.key());
                session.progress(null);
                return;
            }
            if (!flow.settings().resumeOnRejoin()) {
                restart(player, session, flow);
                return;
            }
            resume(player, session, flow, existing);
            return;
        }

        Flow selected = flowRegistry.selectFor(player, session);
        if (config.isDebug()) {
            flowRegistry.explainSelection(player, session)
                    .forEach(line -> logger.info("[flow-select] " + player.getName() + ": " + line));
        }
        if (selected == null) {
            return;
        }
        startFlow(player, session, selected);
    }

    /**
     * Begins {@code flow} for the player, firing {@code on-flow-start}.
     *
     * <p>Observers are consulted first and any one of them can veto, which is
     * what backs the cancellable {@code FlowStartEvent}. Nothing is written
     * before that vote, so a cancelled start leaves no trace.
     */
    public void startFlow(Player player, PlayerSession session, Flow flow) {
        if (!allowFlowStart(player, session, flow, false)) {
            if (config.isDebug()) {
                logger.info("[flow] start of '" + flow.id() + "' for " + player.getName()
                        + " was cancelled by another plugin.");
            }
            return;
        }
        FlowProgress progress = FlowProgress.start(session.uuid(), flow.id(), System.currentTimeMillis());
        session.pausePlayTime(System.currentTimeMillis());
        session.progress(progress);
        session.clearTransientCounters();

        if (!session.temporary()) incrementFunnel(flow.id(), FunnelEvent.STARTED.key());
        notifyObservers(observer -> observer.onFlowStart(player, session, flow));
        actionRunner.run(flow.actionsFor(ActionTrigger.ON_FLOW_START),
                ActionContext.ofFlow(player, session, flow.id()));
        beginStep(player, session, flow, progress);
    }

    private void restart(Player player, PlayerSession session, Flow flow) {
        if (config.isDebug()) {
            logger.info("[flow] " + player.getName() + " restarting flow '" + flow.id()
                    + "' (resume-on-rejoin is false).");
        }
        startFlow(player, session, flow);
    }

    private void resume(Player player, PlayerSession session, Flow flow, FlowProgress progress) {
        if (flow.isFinished(progress.stepIndex())) {
            // Config shrank while they were away; treat as complete rather than
            // leaving them parked past the last step forever.
            completeFlow(player, session, flow, progress);
            return;
        }
        // Resume is vetted too: a plugin that cancels onboarding for a player
        // must not be bypassed simply because they relogged mid-flow.
        if (!allowFlowStart(player, session, flow, true)) {
            if (config.isDebug()) {
                logger.info("[flow] resume of '" + flow.id() + "' for " + player.getName()
                        + " was cancelled by another plugin.");
            }
            return;
        }
        session.clearTransientCounters();
        if (config.isDebug()) {
            logger.info("[flow] " + player.getName() + " resuming '" + flow.id()
                    + "' at step " + progress.stepIndex() + " (" + progress.stepProgress() + " done).");
        }
        notifyObservers(observer -> observer.onFlowStart(player, session, flow));
        beginStep(player, session, flow, progress);
    }

    /** Fires {@code on-step-start} for the step the player is now on. */
    private void beginStep(Player player, PlayerSession session, Flow flow, FlowProgress progress) {
        Step step = flow.stepAt(progress.stepIndex());
        if (step == null) {
            completeFlow(player, session, flow, progress);
            return;
        }
        if (!session.temporary()) incrementFunnel(flow.id(), FunnelEvent.stepReachedKey(step.id()));
        int stepIndex = progress.stepIndex();
        int done = progress.stepProgress();
        notifyObservers(observer -> observer.onStepStart(player, session, flow, step, stepIndex, done));
        actionRunner.run(step.actionsFor(ActionTrigger.ON_STEP_START), contextFor(player, session, flow, step));
        scheduleHint(player, session, flow, step, stepIndex);
        schedulePlayTimeCompletion(player, session, flow, step, stepIndex);
    }

    /**
     * Drives a {@link ObjectiveType#PLAY_TIME} step with a single one-shot
     * task instead of a repeating timer.
     *
     * <p>The objective is "seconds online this session", so the completion
     * time is known the moment the step starts -- there is nothing to poll
     * for. Like the hint task, it re-checks on fire that the player is still
     * on the same step of the same flow, so a task left over from before a
     * relog or a {@code /wf skip} cannot credit the wrong step.
     */
    private void schedulePlayTimeCompletion(Player player, PlayerSession session, Flow flow, Step step,
                                            int stepIndex) {
        if (step.objective().type() != ObjectiveType.PLAY_TIME) {
            return;
        }
        session.startPlayTime(System.currentTimeMillis());
        long generation = session.generation();
        FlowProgress expected = session.progress();
        long delayTicks = Math.max(1L, (step.requiredAmount() - expected.stepProgress()) * 20L);
        scheduling.runSyncLater(() -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerSession current = sessionManager.get(session.uuid());
            if (current != session || current.generation() != generation || current.progress() != expected) {
                return;
            }
            FlowProgress progress = current.progress();
            if (!progress.flowId().equals(flow.id()) || progress.stepIndex() != stepIndex) {
                return;
            }
            session.pausePlayTime(System.currentTimeMillis());
            forceCompleteCurrentStep(player);
        }, delayTicks);
    }

    /**
     * Schedules the step's hint, if it has one.
     *
     * <p>One-shot task per step rather than a repeating checker, and it
     * re-verifies on fire that the player is still online and still on the
     * same step -- otherwise a hint for step 2 could arrive while the player
     * is on step 5.
     */
    private void scheduleHint(Player player, PlayerSession session, Flow flow, Step step, int stepIndex) {
        if (!step.hasHint()) {
            return;
        }
        long delayTicks = Math.max(1L, step.hintAfterMillis() / 50L);
        long generation = session.generation();
        FlowProgress expected = session.progress();
        scheduling.runSyncLater(() -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerSession current = sessionManager.get(session.uuid());
            if (current != session || current.generation() != generation || current.progress() != expected) {
                return;
            }
            FlowProgress progress = current.progress();
            if (!progress.flowId().equals(flow.id()) || progress.stepIndex() != stepIndex) {
                return; // Already moved on; the hint is stale.
            }
            actionRunner.run(step.actionsFor(ActionTrigger.ON_STEP_PROGRESS),
                    contextFor(player, current, flow, step));
        }, delayTicks);
    }

    // --- progress ---------------------------------------------------------

    /**
     * Credits objective progress if the player's current step is waiting for
     * exactly this kind of event.
     *
     * <p>This is the single entry point the objective listeners use. It
     * verifies the player has a live session and an active flow, that the
     * current step's objective type matches, and that the type-specific filter
     * passes, before touching any state.
     *
     * @param type the objective type the calling listener represents
     * @param amount units observed; ignored when not positive
     * @param filter type-specific check against the step's definition, e.g.
     *     "is this the material the step asked for"
     */
    public void creditProgress(Player player, ObjectiveType type, int amount,
                               Predicate<ObjectiveDefinition> filter) {
        if (amount <= 0) {
            return;
        }
        PlayerSession session = sessionManager.get(player.getUniqueId());
        if (session == null || session.progress() == null) {
            return;
        }
        FlowProgress progress = session.progress();
        Flow flow = flowRegistry.byId(progress.flowId());
        if (flow == null) {
            return;
        }
        Step step = flow.stepAt(progress.stepIndex());
        if (step == null || step.objective().type() != type) {
            return;
        }
        if (filter != null && !filter.test(step.objective())) {
            return;
        }

        progress.addStepProgress(amount);
        progress.updatedAt(System.currentTimeMillis());
        session.markDirty();

        if (progress.stepProgress() >= step.requiredAmount()) {
            completeStep(player, session, flow, step, progress);
        } else {
            int stepIndex = progress.stepIndex();
            int done = progress.stepProgress();
            notifyObservers(observer -> observer.onStepProgress(player, session, flow, step, stepIndex, done));
            actionRunner.run(step.actionsFor(ActionTrigger.ON_STEP_PROGRESS),
                    contextFor(player, session, flow, step));
        }
    }

    /** Convenience for listeners with no filtering to do. */
    public void creditProgress(Player player, ObjectiveType type, int amount) {
        creditProgress(player, type, amount, null);
    }

    /**
     * Force-completes the player's current step, regardless of progress
     * ({@code /wf skip} and the API's {@code completeObjective}).
     *
     * @return true if a step was completed
     */
    public boolean forceCompleteCurrentStep(Player player) {
        PlayerSession session = sessionManager.get(player.getUniqueId());
        if (session == null || session.progress() == null) {
            return false;
        }
        FlowProgress progress = session.progress();
        Flow flow = flowRegistry.byId(progress.flowId());
        if (flow == null) {
            return false;
        }
        Step step = flow.stepAt(progress.stepIndex());
        if (step == null) {
            return false;
        }
        progress.stepProgress(step.requiredAmount());
        completeStep(player, session, flow, step, progress);
        return true;
    }

    private void completeStep(Player player, PlayerSession session, Flow flow, Step step, FlowProgress progress) {
        session.pausePlayTime(System.currentTimeMillis());
        notifyObservers(observer -> observer.onStepComplete(player, session, flow, step));
        actionRunner.run(step.actionsFor(ActionTrigger.ON_STEP_COMPLETE),
                contextFor(player, session, flow, step));

        progress.advanceStep();
        progress.updatedAt(System.currentTimeMillis());
        session.clearTransientCounters();
        session.markDirty();
        // Step boundaries are the natural durability point: flush now so a
        // crash costs at most one partially-done step.
        sessionManager.flush(session);

        if (flow.isFinished(progress.stepIndex())) {
            completeFlow(player, session, flow, progress);
        } else {
            beginStep(player, session, flow, progress);
        }
    }

    private void completeFlow(Player player, PlayerSession session, Flow flow, FlowProgress progress) {
        progress.state(FlowState.COMPLETED);
        progress.updatedAt(System.currentTimeMillis());
        session.addCompletedFlow(flow.id());
        session.markDirty();
        sessionManager.flush(session);

        if (!session.temporary()) incrementFunnel(flow.id(), FunnelEvent.COMPLETED.key());
        notifyObservers(observer -> observer.onFlowComplete(player, session, flow));
        actionRunner.run(flow.actionsFor(ActionTrigger.ON_FLOW_COMPLETE),
                ActionContext.ofFlow(player, session, flow.id()),
                () -> notifyObservers(o -> o.onFlowActionsComplete(player, session, flow)));

        // Clearing the active pointer is what stops the finished flow being
        // resumed on the next login; the COMPLETED row stays for stats and the
        // previous-flow-completed condition.
        session.progress(null);
    }

    // --- quitting ---------------------------------------------------------

    /**
     * Records where a player dropped out. The row stays {@link FlowState#ACTIVE}
     * so they can resume; the startup sweep reclassifies it as abandoned only
     * once the flow's window has passed.
     */
    public void onQuit(PlayerSession session) {
        session.pausePlayTime(System.currentTimeMillis());
        FlowProgress progress = session.progress();
        if (progress == null) {
            return;
        }
        Flow flow = flowRegistry.byId(progress.flowId());
        if (flow == null) {
            return;
        }
        Step step = flow.stepAt(progress.stepIndex());
        if (step != null && !session.temporary()) {
            incrementFunnel(flow.id(), FunnelEvent.stepAbandonedKey(step.id()));
        }
        notifyObservers(observer -> observer.onFlowInterrupted(session, flow, step));
    }

    /** Executes abandon actions while the quitting player is still available. */
    public void onQuit(Player player, PlayerSession session) {
        onQuit(session);
        FlowProgress progress = session.progress();
        Flow flow = progress == null ? null : flowRegistry.byId(progress.flowId());
        if (flow != null && !session.temporary()) {
            actionRunner.run(flow.actionsFor(ActionTrigger.ON_FLOW_ABANDON),
                    ActionContext.ofFlow(player, session, flow.id()));
        }
    }

    /** Restores timers/UI after a preview or a validated reload, without replaying rewards. */
    public void refresh(Player player, PlayerSession session, Flow previousFlow) {
        session.pausePlayTime(System.currentTimeMillis());
        FlowProgress progress = session.progress();
        if (progress == null) return;
        Flow flow = flowRegistry.byId(progress.flowId());
        if (flow == null) {
            session.progress(null);
            sessionManager.flush(session);
            return;
        }
        Step oldStep = previousFlow == null ? null : previousFlow.stepAt(progress.stepIndex());
        if (oldStep != null) {
            int index = flow.indexOfStep(oldStep.id());
            if (index < 0) { index = 0; progress.stepProgress(0); }
            else if (!oldStep.objective().equals(flow.stepAt(index).objective())) progress.stepProgress(0);
            progress.stepIndex(index);
        }
        Step step = flow.stepAt(progress.stepIndex());
        if (step == null) {
            progress.stepIndex(0);
            progress.stepProgress(0);
            step = flow.stepAt(0);
        }
        session.markDirty();
        Step activeStep = step;
        notifyObservers(o -> o.onStepStart(player, session, flow, activeStep, progress.stepIndex(), progress.stepProgress()));
        scheduleHint(player, session, flow, step, progress.stepIndex());
        schedulePlayTimeCompletion(player, session, flow, step, progress.stepIndex());
        sessionManager.flush(session);
    }

    // --- observers --------------------------------------------------------

    public void addObserver(FlowObserver observer) {
        observers.add(observer);
    }

    /**
     * Asks every observer whether a flow may start.
     *
     * <p>An observer that throws is treated as abstaining rather than as a
     * veto: a broken third-party handler should not be able to silently
     * disable onboarding for the whole server.
     */
    private boolean allowFlowStart(Player player, PlayerSession session, Flow flow, boolean resumed) {
        for (FlowObserver observer : observers) {
            try {
                if (!observer.allowFlowStart(player, session, flow, resumed)) {
                    return false;
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Flow observer " + observer.getClass().getSimpleName()
                        + " threw while vetting a flow start; treating as no objection.", e);
            }
        }
        return true;
    }

    /**
     * Notifies every observer, isolating failures.
     *
     * <p>An exception from the bossbar renderer or a third-party event
     * handler must not abort the transition that triggered it -- the player's
     * progress matters more than the notification.
     */
    private void notifyObservers(Consumer<FlowObserver> notification) {
        for (FlowObserver observer : observers) {
            try {
                notification.accept(observer);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING,
                        "Flow observer " + observer.getClass().getSimpleName() + " threw; continuing.", e);
            }
        }
    }

    // --- helpers ----------------------------------------------------------

    private ActionContext contextFor(Player player, PlayerSession session, Flow flow, Step step) {
        FlowProgress progress = session.progress();
        int done = progress == null ? 0 : progress.stepProgress();
        return new ActionContext(player, session, flow.id(), step.id(), done, step.requiredAmount());
    }

    /**
     * Credits {@code joined} against whichever flow this player would be
     * offered, so the funnel's first stage is comparable with {@code started}.
     * Counting against every flow would make the numbers meaningless.
     */
    private void countFunnelForSelectableFlow(Player player, PlayerSession session) {
        Flow candidate = session.progress() != null
                ? flowRegistry.byId(session.progress().flowId())
                : flowRegistry.selectFor(player, session);
        if (candidate != null) {
            incrementFunnel(candidate.id(), FunnelEvent.JOINED.key());
        }
    }

    /** Fire-and-forget counter bump; never blocks the main thread. */
    public void incrementFunnel(String flowId, String eventKey) {
        scheduling.runAsync(() -> {
            try {
                storage.incrementFunnel(flowId, eventKey);
            } catch (StorageException e) {
                logger.log(Level.WARNING, "Could not record funnel event '" + eventKey
                        + "' for flow '" + flowId + "'.", e);
            }
        });
    }

    public FlowRegistry registry() {
        return flowRegistry;
    }

    /**
     * The player's live session, or {@code null}.
     *
     * <p>Exposed for the objective listeners that need the session itself
     * (rather than just to credit progress) -- the movement listener banks
     * fractional block distances there.
     */
    public PlayerSession sessionFor(Player player) {
        return sessionManager.get(player.getUniqueId());
    }

    /** Resolves a named location for {@link ObjectiveType#REACH_LOCATION}. */
    public org.bukkit.Location namedLocation(String name) {
        return config.namedLocation(name);
    }
}
