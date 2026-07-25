# WelcomeFlow

**Turn first joins into returning players.**

WelcomeFlow is an onboarding and first-session retention system for Paper
servers. It guides a new player through their first 5 to 15 minutes with a
configurable checklist of micro-objectives, rewards them for finishing, and
rewards them again for coming back.

It is not a tutorial plugin that shows a wall of text and hopes. It measures
where new players drop off, and it pays them to return.

```
Install the jar  →  restart  →  done.
```

The bundled configuration is a complete, working 6 step tutorial. Zero edits
required.

---

## Contents

- [Requirements](#requirements)
- [Install](#install)
- [How it works](#how-it-works)
- [Configuration](#configuration)
  - [Action syntax](#action-syntax)
  - [Action reference](#action-reference)
  - [Objective reference](#objective-reference)
  - [Triggers](#triggers)
  - [Flow conditions and settings](#flow-conditions-and-settings)
  - [Retention rewards](#retention-rewards)
- [Commands and permissions](#commands-and-permissions)
- [PlaceholderAPI](#placeholderapi)
- [Developer API](#developer-api)
- [Compatibility](#compatibility)
- [Troubleshooting](#troubleshooting)

---

## Requirements

| | |
|---|---|
| **Server** | Paper 1.21.4 or newer, including the 26.x line |
| **Java** | 21 or newer |
| **Optional** | PlaceholderAPI, Vault |

Paper forks (Purpur, Pufferfish, and similar) work. Spigot and CraftBukkit do
not: WelcomeFlow uses Paper's plugin system and Adventure APIs.

## Install

1. Drop `WelcomeFlow.jar` into `plugins/`.
2. Restart the server.
3. That is the whole procedure.

On first start the plugin writes `config.yml`, `flows.yml` and `messages.yml`
into `plugins/WelcomeFlow/`, downloads its one runtime library, and begins
onboarding the next player who joins.

To confirm everything is healthy:

```
/wf validate
```

A clean install reports `No problems found. Every config file is valid.`

---

## How it works

A player joins. WelcomeFlow picks the highest priority flow whose conditions
they match, and starts it. Each step has an **objective** (walk 25 blocks,
break 5 oak logs, run `/kit starter`) and a list of **actions** that fire at
points in its lifecycle (send a title, play a sound, give an item).

Progress is saved as the player goes. If they log out halfway through step 4,
they resume on step 4 with their partial progress intact, even across a server
restart.

Three things happen alongside the tutorial:

**The checklist bossbar** shows the current step and how far through the flow
they are. It updates on progress, and disappears when the flow completes.

**Retention rewards** fire independently of any flow. The second join reward
lands on a player's Nth login (default 2nd). The return bonus lands when a
player comes back after being away (default 12 hours).

**The funnel** counts everything: joined, started, reached each step, dropped
out on each step, completed. `/wf stats` renders it. This is the number that
tells you whether your tutorial is working, and which step is losing people.

---

## Configuration

Three files, all commented, all reloadable with `/wf reload`.

| File | Contains |
|---|---|
| `config.yml` | storage, retention settings, bossbar and title defaults, named locations |
| `flows.yml` | flow definitions: steps, objectives, actions |
| `messages.yml` | every user facing string, in MiniMessage format |

No text is hardcoded in the plugin. Anything a player sees can be changed in
`messages.yml`.

### Action syntax

Every action can be written two ways. Both are parsed identically, and you can
mix them freely in the same list.

**Compact form.** One line, fields separated by `;;`:

```yaml
- 'title: <white>Welcome;;<gray>Let''s get started;;10;;60;;20'
- 'sound: ENTITY_PLAYER_LEVELUP;;1;;1'
- 'message: <green>Nice work!'
```

**Expanded form.** A map, fields by name, for actions with many options:

```yaml
- type: title
  text: '<white>Welcome'
  subtitle: '<gray>Let''s get started'
  fade-in: 10
  stay: 60
  fade-out: 20
```

Three rules govern the compact form:

1. **A single `;;` separates every field**, text and numbers alike. There is
   no special case to remember.
2. **Only the first `:` splits the type from its arguments.** This means
   MiniMessage gradients and hex colours pass through untouched:
   `'title: <gradient:#7ec8ff:#ffffff>Welcome</gradient>'` works exactly as
   written.
3. **A literal `;;` inside a field is escaped as `\;;`.** Rarely needed.

Some actions take a bracketed modifier, for example
`'command[console]: eco give <player> 500'`.

**Placeholders available in any action text:**

| Tag | Value |
|---|---|
| `<player>` | the player's name |
| `<flow>` | current flow id |
| `<step>` | current step id |
| `<progress>` | units completed on this step |
| `<required>` | units this step needs |
| `<percent>` | step progress as 0 to 100 |

If PlaceholderAPI is installed, `%placeholders%` from other plugins are
resolved too.

### Action reference

| Action | Syntax |
|---|---|
| `message` | `message: <text>` |
| `broadcast` | `broadcast: <text>` |
| `actionbar` | `actionbar: <text>` |
| `title` | `title: <text>;;<subtitle>;;<fade-in>;;<stay>;;<fade-out>` |
| `bossbar` | `bossbar: <text>;;<seconds>;;<color>;;<overlay>;;<progress-mode>` |
| `sound` | `sound: <SOUND_NAME>;;<volume>;;<pitch>` |
| `particle` | `particle: <PARTICLE>;;<count>;;<offsetX>;;<offsetY>;;<offsetZ>;;<extra>` |
| `potion-effect` | `potion-effect: <EFFECT>;;<seconds>;;<amplifier>` |
| `teleport` | `teleport: <named-location>` or `teleport: <world>;;<x>;;<y>;;<z>;;<yaw>;;<pitch>` |
| `give-item` | `give-item: <MATERIAL>;;<amount>;;<display-name>` |
| `open-book` | `open-book: <title>;;<page1>;;<page2>;;...` |
| `command` | `command: <cmd>` or `command[console]: <cmd>` (no leading slash) |
| `delay` | `delay: <duration>` (ticks, or `20t` / `3s` / `2m`) |
| `conditional` | map form only, see below |

Timing fields on `title` are optional and fall back to the `ui.title` defaults
in `config.yml`, so you can set house style once.

`delay` does not block the server. The rest of the action list is scheduled
and runs later.

**`conditional`** runs one of two action lists:

```yaml
- type: conditional
  condition: 'permission:myserver.vip'
  then:
    - 'give-item: DIAMOND;;5'
  else:
    - 'give-item: IRON_INGOT;;5'
```

Supported conditions:

| Condition | Example |
|---|---|
| `permission` | `permission:myserver.vip` |
| `world` | `world:world_nether` |
| `flow-completed` | `flow-completed:welcome` |
| `has-item` | `has-item:DIAMOND` or `has-item:DIAMOND:3` |
| `placeholder` | `placeholder:%vault_eco_balance%=0` (requires PlaceholderAPI) |

This is a fixed set rather than a general expression language. It covers what
real configurations need, and every misspelling produces a precise error
instead of silently evaluating to false.

### Objective reference

| Type | Required keys | Optional keys | Counts |
|---|---|---|---|
| `WALK_DISTANCE` | | | blocks travelled |
| `BREAK_BLOCK` | | `material` | blocks broken |
| `PLACE_BLOCK` | | `material` | blocks placed |
| `CRAFT_ITEM` | | `material` | items crafted |
| `PICKUP_ITEM` | | `material` | items picked up |
| `KILL_ENTITY` | | `entity` | mobs killed |
| `RUN_COMMAND` | `pattern` | | matching commands run |
| `SEND_CHAT` | | | chat messages sent |
| `OPEN_INVENTORY` | | | containers opened |
| `INTERACT_BLOCK` | | `material` | right clicks on blocks |
| `REACH_LOCATION` | `location` | `radius` | arrival at a named location |
| `PLAY_TIME` | | | seconds online this session |
| `MANUAL` | | | advanced only by API or `/wf skip` |

Every objective also takes `amount` (default 1) and an optional `hint-after`
duration, after which the step's `on-step-progress` actions fire as a nudge.

```yaml
- id: chop
  display: "<white>Chop some wood"
  objective:
    type: BREAK_BLOCK
    material: OAK_LOG
    amount: 5
  hint-after: 60s
  on-step-complete:
    - 'message: <green>Nice. Now craft a crafting table.'
```

Notes worth knowing:

- **`RUN_COMMAND`** matches before the command executes, so a step still
  completes if the target command is missing or the player lacks permission.
  Matching ignores case, a leading slash, and repeated spaces, so
  `/Kit Starter` satisfies `pattern: "/kit starter"`. A trailing `*` matches
  by prefix: `pattern: "/kit *"`.
- **`OPEN_INVENTORY`** does not fire for a player's own survival inventory.
  The client handles that locally and no packet reaches the server. It fires
  for chests, crafting tables, furnaces, shop GUIs and similar.
- **`PICKUP_ITEM`** credits the whole stack. Picking up 12 logs counts as 12.
- **`KILL_ENTITY`** credits the killer only, never environmental deaths.
- **Only the objective types your flows actually use** get an event listener.
  A server whose flows never craft anything pays nothing for crafting events.

### Triggers

Action lists attach to these points. Flow level triggers go on the flow, step
level triggers go on the step.

| Trigger | Level | Fires when |
|---|---|---|
| `on-flow-start` | flow | the flow begins, and on resume |
| `on-step-start` | step | the step becomes current, and on resume |
| `on-step-progress` | step | progress is credited but the step is not done, and on `hint-after` |
| `on-step-complete` | step | the objective is satisfied |
| `on-flow-complete` | flow | the last step completes |
| `on-flow-abandon` | flow | the player drops out past the abandon window |

### Flow conditions and settings

```yaml
flows:
  welcome:
    priority: 10
    conditions:
      first-join-only: true
      permission: ""              # empty = everyone
      worlds: []                  # empty = all worlds
      previous-flow-completed: "" # chain flows by naming another flow id
    settings:
      resume-on-rejoin: true
      abandon-after-hours: 48
      show-checklist-bossbar: true
    steps:
      - ...
```

When several flows match, the highest `priority` wins, with the flow id as
tie breaker. Selection is deterministic and never depends on the order of keys
in your YAML. Set `debug: true` in `config.yml` to have the plugin log exactly
why each flow was chosen or skipped.

`previous-flow-completed` is how you chain flows: a beginner flow, then an
intermediate one that only unlocks after it.

### Retention rewards

This is the part that turns a tutorial into a retention system. Both live in
`config.yml`, and both accept any action from the table above.

```yaml
second-join:
  enabled: true
  trigger-join-number: 2
  minimum-first-session-seconds: 120
  actions:
    - 'title: <gold>Welcome back!;;<gray>Thanks for returning;;10;;60;;20'
    - 'command[console]: eco give <player> 250'

return-bonus:
  enabled: true
  minimum-hours-away: 12
  cooldown-hours: 20
  max-claims: 0        # 0 = unlimited
  actions:
    - 'title: <aqua>Good to see you;;<gray>Away for <time-away>;;10;;60;;20'
```

Two design points that matter if you attach real currency:

**It cannot be farmed by relogging.** The second join reward requires the
player's *first* session to have lasted at least
`minimum-first-session-seconds`. That duration is banked once, when their
first session ends, so joining and quitting repeatedly never satisfies it.

**Installing on an established server does not pay everyone at once.** The
join counter is WelcomeFlow's own and starts at zero the first time it sees a
player. A veteran of 500 logins is at count 1 after you install, and earns the
reward on their genuine next return.

The return bonus adds `<time-away>` to its action placeholders, rendered as
`2h 15m`.

---

## Commands and permissions

All subcommands are available under `/welcomeflow`, `/wflow` and `/wf`.

| Command | Permission | Purpose |
|---|---|---|
| `/wf` | `welcomeflow.use` | help |
| `/wf start <player> [flow]` | `welcomeflow.admin` | force start a flow; omit the flow to use normal selection |
| `/wf reset <player>` | `welcomeflow.admin` | wipe progress and reward flags, works offline |
| `/wf skip [player]` | `welcomeflow.admin` | complete the current step |
| `/wf status [player]` | `welcomeflow.admin` | flow, step, progress, retention eligibility |
| `/wf stats [flow]` | `welcomeflow.admin` | the retention funnel |
| `/wf validate` | `welcomeflow.admin` | re-check every config file |
| `/wf test <flow>` | `welcomeflow.admin` | run a flow on yourself, discarding the data afterwards |
| `/wf reload` | `welcomeflow.admin` | reload config, preserving active sessions |

`welcomeflow.use` defaults to everyone, `welcomeflow.admin` to operators.
`welcomeflow.*` grants both.

**`/wf status`** answers the question this plugin generates most support
tickets about. Alongside flow and step it explains retention eligibility in
plain words: `on join 1 of 2`, `first session was 45s, needs 120s`,
`on cooldown for another 4h 20m`.

**`/wf stats`** renders the funnel with a conversion percentage and a drop off
count per step:

```
--- Retention funnel: welcome ---
Joined: 412 (100%)
Started: 389 (94%)
  step move: 389 (94%) dropped here: 12
  step chop: 377 (92%) dropped here: 96
  step craft: 281 (68%) dropped here: 31
  ...
Completed: 203 (49%)
--- Retention rewards (server-wide) ---
Second-join rewards: 178 (43%)
Return bonuses: 94 (23%)
```

The drop off column is the actionable one. In the example above, `chop` is
losing a quarter of everyone who reaches it, and that is where to look first.

**`/wf validate`** names the file, the exact YAML path, the offending value
and what was expected:

```
ERROR [flows.yml] flows.welcome.steps[1].objective.material: Unknown material
      'OAK_LOGG'. Use a Bukkit Material name, e.g. OAK_LOG.
```

It never prints a stack trace.

---

## PlaceholderAPI

Ten placeholders, all reading in memory state, cheap enough for a scoreboard
that refreshes every tick.

| Placeholder | Value |
|---|---|
| `%welcomeflow_active%` | `true` / `false` |
| `%welcomeflow_flow%` | current flow id |
| `%welcomeflow_step%` | current step's display text |
| `%welcomeflow_step_index%` | current step number, 1 based |
| `%welcomeflow_steps_total%` | steps in the flow |
| `%welcomeflow_progress_percent%` | 0 to 100 |
| `%welcomeflow_progress_bar%` | rendered bar, for example `■■■□□□□□□□` |
| `%welcomeflow_completed%` | how many flows the player has finished |
| `%welcomeflow_joins%` | logins WelcomeFlow has seen |
| `%welcomeflow_next_return_bonus%` | `now`, `never`, or a duration like `4h 20m` |

`%welcomeflow_progress_bar%` is deliberately uncoloured so you can wrap it in
whatever formatting your scoreboard plugin uses.

---

## Developer API

WelcomeFlow ships a public API for other plugins. Add it as a soft dependency
and guard your calls with `WelcomeFlowAPI.isReady()`, since plugin load order
is not guaranteed.

```yaml
# paper-plugin.yml
dependencies:
  server:
    WelcomeFlow:
      required: false
      join-classpath: true
```

### Events

Listen to these rather than polling. All fire on the main thread.

| Event | Cancellable | Fires |
|---|---|---|
| `FlowStartEvent` | yes | before a flow starts or resumes |
| `StepStartEvent` | no | a step becomes current, including on resume |
| `StepCompleteEvent` | no | an objective is satisfied |
| `FlowCompleteEvent` | no | the last step completes |
| `FlowAbandonEvent` | no | the player quits mid flow (player is offline) |
| `RetentionRewardEvent` | yes | a retention reward is about to pay out |

```java
@EventHandler
public void onStart(FlowStartEvent event) {
    if (event.getPlayer().hasPermission("myserver.skiptutorial")) {
        event.setCancelled(true);
    }
}

@EventHandler
public void onComplete(FlowCompleteEvent event) {
    getLogger().info(event.getPlayer().getName()
            + " finished " + event.getFlowId()
            + " in " + event.getDurationMillis() + "ms");
}
```

Cancelling `FlowStartEvent` genuinely prevents the flow, on both a fresh start
and a resume. Cancelling `RetentionRewardEvent` withholds the payout, but note
the claim has already been marked by then: cancelling means the player
forfeits that reward, not that they will be offered it again next login. That
ordering is deliberate, because the claim guard has to be set before anything
can fail.

### WelcomeFlowAPI

Main thread only. All methods are static.

```java
if (!WelcomeFlowAPI.isReady()) return;

boolean inFlow   = WelcomeFlowAPI.isInFlow(player);
Optional<String> flow    = WelcomeFlowAPI.getActiveFlowId(player);
Optional<String> step    = WelcomeFlowAPI.getActiveStepId(player);
Optional<Integer> pct    = WelcomeFlowAPI.getProgressPercent(player);
boolean done     = WelcomeFlowAPI.hasCompletedFlow(player, "welcome");
List<String> ids = WelcomeFlowAPI.getFlowIds();

WelcomeFlowAPI.startFlow(player, "welcome");
WelcomeFlowAPI.completeCurrentStep(player);
WelcomeFlowAPI.resetPlayer(player);
```

### Driving a step from your own plugin

A step with `type: MANUAL` advances only when you say so. This is how you make
a tutorial step depend on something only your plugin knows about:

```yaml
- id: visit-shop
  display: "<white>Visit the server shop"
  objective:
    type: MANUAL
```

```java
// somewhere in your shop plugin
public void onShopOpened(Player player) {
    if (WelcomeFlowAPI.isReady()) {
        WelcomeFlowAPI.completeCurrentStep(player);
    }
}
```

---

## Compatibility

**One jar covers Paper 1.21.4 through the 26.x line.** That is verified, not
assumed. Two things back it up:

The build compiles the same sources twice, once against the 1.21.4 API and
once against the 26.1.2 API, and fails if any code touches an API that does
not exist in both. Cross version compatibility is a build invariant here, not
a hope.

The same jar is then booted on a real Paper 1.21.4 server and a real Paper
26.1.2 server, and the build fails if any WelcomeFlow class appears in a stack
trace on either.

You do not need a different download for a different server version, and
updating your server does not require updating this plugin.

---

## Troubleshooting

**A player did not get the second join reward.**
Run `/wf status <player>`. It states the reason directly, usually
`on join 1 of 2` or `first session was 45s, needs 120s`.

**A tutorial step never completes.**
For `RUN_COMMAND`, check the pattern with `/wf validate` and remember that
matching happens on what the player types. For `OPEN_INVENTORY`, note that a
player's own inventory does not fire a server event; use a chest or crafting
table.

**Config changes did not take effect.**
`/wf reload` re-reads all three files and reports any problems. Active
sessions are preserved, so a player mid flow keeps their progress.

**Everything loads but nothing happens for new players.**
Check that a flow's conditions can actually match. `first-join-only: true`
means returning players are skipped. Set `debug: true` in `config.yml` and the
console logs which flow was selected and why the others were not.

**The plugin did not enable.**
The most common cause is a data folder the server cannot write to. The console
message says so explicitly. WelcomeFlow disables itself rather than run
without storage, because half working onboarding is worse than none.

---

## Support

Bug reports and questions go through the resource discussion page. Please
include your server version, the plugin version, and the output of
`/wf validate`.

Made by LittleSquad Studios.
