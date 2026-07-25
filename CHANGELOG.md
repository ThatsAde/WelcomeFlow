# Changelog

All notable changes to WelcomeFlow are documented here.

This project follows [Semantic Versioning](https://semver.org/). Config file
formats are treated as public API: a breaking change to `flows.yml`,
`config.yml` or `messages.yml` requires a major version bump.

---

## [1.0.0] - unreleased

First public release.

### Onboarding

- Configurable flows made of ordered steps, each with an objective and action
  lists attached to six lifecycle triggers.
- 13 objective types: `WALK_DISTANCE`, `BREAK_BLOCK`, `PLACE_BLOCK`,
  `CRAFT_ITEM`, `PICKUP_ITEM`, `KILL_ENTITY`, `RUN_COMMAND`, `SEND_CHAT`,
  `OPEN_INVENTORY`, `INTERACT_BLOCK`, `REACH_LOCATION`, `PLAY_TIME`,
  `MANUAL`.
- 14 action types: `message`, `broadcast`, `actionbar`, `title`, `bossbar`,
  `sound`, `particle`, `potion-effect`, `teleport`, `give-item`, `open-book`,
  `command`, `delay`, `conditional`.
- Progress survives logout and server restart. A player resumes on the step
  they left, with partial progress intact.
- Deterministic flow selection by priority, with flow id as tie breaker, so
  the choice never depends on YAML key order.
- Flows can be chained with `previous-flow-completed`.
- Optional per step `hint-after` nudge.
- Running checklist bossbar.

### Retention

- Second join reward on a configurable Nth login, with an anti farm guard
  requiring a minimum first session length.
- Return bonus for players coming back after a configurable time away, with a
  cooldown between claims and an optional lifetime cap.
- Double claim guards at two levels, persistent and in flight, so a reward
  cannot be paid twice.
- Retention rewards are counted separately from any flow, since a returning
  player has not necessarily run a tutorial.

### Statistics

- Retention funnel counting joined, started, reached each step, dropped out on
  each step, and completed.
- `/wf stats` renders the funnel with conversion percentages and a per step
  drop off count.
- Interrupted runs are reclassified as abandoned once past the flow's
  `abandon-after-hours` window, swept once at startup rather than on a timer.

### Administration

- Eight subcommands under `/welcomeflow`, `/wflow` and `/wf`, with full tab
  completion: `start`, `reset`, `skip`, `status`, `stats`, `validate`, `test`,
  `reload`.
- `/wf validate` reports every configuration problem with file, exact YAML
  path, the offending value and what was expected. It never prints a stack
  trace.
- `/wf status` explains retention eligibility in plain words, for example
  `on join 1 of 2` or `on cooldown for another 4h 20m`.
- `/wf reload` preserves active sessions and re-registers objective listeners.
- `/wf test` runs a flow exactly as a player sees it, then discards the data.
- `/wf reset` works on offline players.

### Integration

- Ten PlaceholderAPI placeholders, all reading in memory state.
- PlaceholderAPI placeholders from other plugins are resolved inside
  WelcomeFlow's own message and action strings.
- Public API with six Bukkit events and the `WelcomeFlowAPI` facade.
  `FlowStartEvent` and `RetentionRewardEvent` are cancellable.
- Vault detected as an optional soft dependency.

### Configuration

- Three commented files: `config.yml`, `flows.yml`, `messages.yml`.
- Actions accept a compact single line form and an expanded map form, parsed
  identically and mixable in the same list.
- Every user facing string lives in `messages.yml`. None are hardcoded.
- Named locations so coordinates live in one place.
- `ui.bossbar.style` accepts both Bukkit spellings (`SEGMENTED_10`) and
  Adventure spellings (`NOTCHED_10`).
- The bundled configuration is a complete working 6 step tutorial that
  requires zero edits.

### Compatibility

- One jar covers Paper 1.21.4 through the 26.x line, verified by compiling the
  same sources against both API versions and by booting the same artifact on
  both a real 1.21.4 server and a real 26.1.2 server.
- Paper forks such as Purpur are supported.

### Technical

- SQLite storage behind a `StorageProvider` interface, with versioned
  idempotent migrations. All database access is asynchronous.
- No shading. The one runtime library is declared to Paper's library loader.
- Event listeners are registered only for the objective types that loaded
  flows actually use.
- No per tick scanning. The only scheduled repeating task is the periodic
  session flush, which skips sessions with no pending changes.

### Known limitations

- Startup takes roughly 300ms, most of which is the SQLite driver extracting
  its native library on first connection. This is deliberate: the plugin opens
  the database during enable so it can fail immediately and visibly on an
  unwritable data folder, rather than at the moment a player joins.
- `conditional` supports a fixed set of five conditions rather than a general
  expression language.
- `OPEN_INVENTORY` does not fire for a player's own survival inventory,
  because the client handles that locally and no packet reaches the server.
- MySQL storage is planned for 1.1 behind the existing `StorageProvider`
  interface, so it will be a configuration change rather than a migration.
