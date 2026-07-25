# Example flows

Three complete, ready to paste flows for the server types WelcomeFlow was
built for. Each one is a full `flows.yml` you can drop in and adapt.

They assume nothing beyond vanilla Paper except where noted. Where a flow
calls another plugin's command, that line is marked and can be deleted without
breaking anything else.

- [Survival](#survival)
- [Skyblock](#skyblock)
- [Factions](#factions)
- [Chaining flows](#chaining-flows)
- [Tuning advice](#tuning-advice)

---

## Survival

Goal: get a new player from spawn to their first shelter, so they have
something to log back in for. Six steps, roughly 8 minutes.

The last step deliberately points at a kit, because a player who leaves with
gear in their inventory has a reason to return.

```yaml
flows:
  survival-start:
    priority: 10
    conditions:
      first-join-only: true
    settings:
      resume-on-rejoin: true
      abandon-after-hours: 48
      show-checklist-bossbar: true

    on-flow-start:
      - 'title: <gradient:#7ec8ff:#ffffff>Welcome</gradient>;;<gray>Let''s get you set up;;10;;70;;20'
      - 'sound: ENTITY_PLAYER_LEVELUP;;1;;1'
      - 'message: <gray>Follow the checklist above. It takes about 5 minutes.'

    steps:
      - id: move
        display: "<white>Take your first steps"
        objective:
          type: WALK_DISTANCE
          amount: 30
        hint-after: 45s
        on-step-progress:
          - 'actionbar: <gray>Walk around to get started'
        on-step-complete:
          - 'message: <green>Good. Now punch a tree for wood.'
          - 'sound: ENTITY_EXPERIENCE_ORB_PICKUP;;1;;1.2'

      - id: wood
        display: "<white>Gather 8 wood"
        objective:
          type: BREAK_BLOCK
          material: OAK_LOG
          amount: 8
        hint-after: 90s
        on-step-progress:
          - 'actionbar: <gray>Wood: <yellow><progress><gray>/<yellow><required>'
        on-step-complete:
          - 'message: <green>Now craft a crafting table.'

      - id: table
        display: "<white>Craft a crafting table"
        objective:
          type: CRAFT_ITEM
          material: CRAFTING_TABLE
          amount: 1
        hint-after: 60s
        on-step-progress:
          - 'message: <yellow>Hint:<gray> open your inventory and place 4 planks in the 2x2 grid.'
        on-step-complete:
          - 'message: <green>Now make a wooden pickaxe.'

      - id: pickaxe
        display: "<white>Craft a wooden pickaxe"
        objective:
          type: CRAFT_ITEM
          material: WOODEN_PICKAXE
          amount: 1
        on-step-complete:
          - 'message: <green>Mine some stone and you are properly started.'

      - id: stone
        display: "<white>Mine 10 stone"
        objective:
          type: BREAK_BLOCK
          material: STONE
          amount: 10
        hint-after: 2m
        on-step-progress:
          - 'actionbar: <gray>Stone: <yellow><progress><gray>/<yellow><required>'
        on-step-complete:
          - 'message: <green>Last one: grab your starter kit.'

      # Remove this step if you do not run a kits plugin.
      - id: kit
        display: "<white>Claim your starter kit"
        objective:
          type: RUN_COMMAND
          pattern: "/kit starter"
        hint-after: 30s
        on-step-progress:
          - 'message: <yellow>Hint:<gray> type <white>/kit starter</white> in chat.'

    on-flow-complete:
      - 'title: <gradient:#ffd700:#ffffff>All set!</gradient>;;<gray>Welcome to the server;;10;;80;;20'
      - 'sound: UI_TOAST_CHALLENGE_COMPLETE;;1;;1'
      - 'broadcast: <yellow><player> just finished getting started. Say hi!'
      - 'give-item: BREAD;;16'
      - 'delay: 2s'
      - 'message: <gray>Tip: log back in tomorrow for a return bonus.'
```

---

## Skyblock

Goal: island created, first tree planted, first cobblestone generated. The
cobble generator step is the one that matters, because a player who has not
built one has not really started playing Skyblock.

Uses `MANUAL` for island creation so it works with any island plugin. See
[Chaining flows](#chaining-flows) for how to drive it, or swap it for a
`RUN_COMMAND` step matching your plugin's command.

```yaml
flows:
  skyblock-start:
    priority: 10
    conditions:
      first-join-only: true
    settings:
      resume-on-rejoin: true
      abandon-after-hours: 72
      show-checklist-bossbar: true

    on-flow-start:
      - 'title: <gradient:#7ec8ff:#ffffff>Skyblock</gradient>;;<gray>Let''s build your island;;10;;70;;20'
      - 'sound: ENTITY_PLAYER_LEVELUP;;1;;1'

    steps:
      # Replace the pattern with your island plugin's command, or drive this
      # step from the API. See docs/CONFIG.md "Chaining flows".
      - id: island
        display: "<white>Create your island"
        objective:
          type: RUN_COMMAND
          pattern: "/is create*"
        hint-after: 30s
        on-step-progress:
          - 'message: <yellow>Hint:<gray> type <white>/is create</white> to begin.'
        on-step-complete:
          - 'message: <green>Your island is ready. Now chop your tree.'
          - 'sound: ENTITY_EXPERIENCE_ORB_PICKUP;;1;;1.2'

      - id: tree
        display: "<white>Chop your starting tree"
        objective:
          type: BREAK_BLOCK
          material: OAK_LOG
          amount: 4
        hint-after: 90s
        on-step-progress:
          - 'actionbar: <gray>Logs: <yellow><progress><gray>/<yellow><required>'
        on-step-complete:
          - 'message: <green>Keep at least one sapling. Now craft a crafting table.'

      - id: table
        display: "<white>Craft a crafting table"
        objective:
          type: CRAFT_ITEM
          material: CRAFTING_TABLE
          amount: 1
        on-step-complete:
          - 'message: <green>Now the important part: build a cobblestone generator.'

      - id: generator
        display: "<white>Build a cobblestone generator"
        objective:
          type: BREAK_BLOCK
          material: COBBLESTONE
          amount: 10
        hint-after: 3m
        on-step-progress:
          - 'message: <yellow>Hint:<gray> put lava next to water. Mine the cobble that forms.'
        on-step-complete:
          - 'message: <green>That is infinite stone. You are properly started now.'

      - id: chest
        display: "<white>Place a chest to store your loot"
        objective:
          type: PLACE_BLOCK
          material: CHEST
          amount: 1
        hint-after: 60s

    on-flow-complete:
      - 'title: <gradient:#ffd700:#ffffff>Island ready!</gradient>;;<gray>Good luck out there;;10;;80;;20'
      - 'sound: UI_TOAST_CHALLENGE_COMPLETE;;1;;1'
      - 'give-item: OAK_SAPLING;;4'
      - 'give-item: BREAD;;16'
      - 'broadcast: <yellow><player> just started their island!'
```

---

## Factions

Goal: understand the two things that get new Factions players killed, which
are being unclaimed and being unarmed. Shorter and blunter than the others,
because Factions players want to be playing, not reading.

```yaml
flows:
  factions-start:
    priority: 10
    conditions:
      first-join-only: true
    settings:
      resume-on-rejoin: true
      abandon-after-hours: 24
      show-checklist-bossbar: true

    on-flow-start:
      - 'title: <gradient:#ff7e7e:#ffffff>Welcome</gradient>;;<gray>Four steps and you are ready;;10;;70;;20'
      - 'sound: ENTITY_PLAYER_LEVELUP;;1;;1'
      - 'message: <gray>This server is PvP. These four steps keep you alive.'

    steps:
      - id: gear
        display: "<white>Get some armour"
        objective:
          type: CRAFT_ITEM
          material: LEATHER_CHESTPLATE
          amount: 1
        hint-after: 2m
        on-step-progress:
          - 'message: <yellow>Hint:<gray> anything is better than nothing out here.'
        on-step-complete:
          - 'message: <green>Better. Now get a weapon.'

      - id: weapon
        display: "<white>Craft a sword"
        objective:
          type: CRAFT_ITEM
          material: STONE_SWORD
          amount: 1
        hint-after: 90s
        on-step-complete:
          - 'message: <green>Now claim land so people cannot raid you.'

      # Replace the pattern with your factions plugin's command.
      - id: faction
        display: "<white>Create or join a faction"
        objective:
          type: RUN_COMMAND
          pattern: "/f *"
        hint-after: 60s
        on-step-progress:
          - 'message: <yellow>Hint:<gray> <white>/f create <name></white> or <white>/f join <name></white>.'
        on-step-complete:
          - 'message: <green>Last thing: read the rules.'

      - id: rules
        display: "<white>Read the server rules"
        objective:
          type: RUN_COMMAND
          pattern: "/rules"
        hint-after: 30s
        on-step-progress:
          - 'message: <yellow>Hint:<gray> type <white>/rules</white>. It takes ten seconds and saves bans.'

    on-flow-complete:
      - 'title: <gradient:#ffd700:#ffffff>Ready</gradient>;;<gray>Good luck;;10;;80;;20'
      - 'sound: UI_TOAST_CHALLENGE_COMPLETE;;1;;1'
      - 'give-item: GOLDEN_APPLE;;2;;<gold>Welcome gift'
      - 'give-item: COOKED_BEEF;;16'
      - 'message: <gray>Log in tomorrow for a return bonus.'
```

---

## Chaining flows

`previous-flow-completed` gates one flow behind another. Give the follow up a
lower priority so the first one is always chosen while it is still available.

```yaml
flows:
  basics:
    priority: 20
    conditions:
      first-join-only: true
    steps:
      - id: move
        display: "<white>Take your first steps"
        objective: {type: WALK_DISTANCE, amount: 20}

  advanced:
    priority: 10
    conditions:
      first-join-only: false
      previous-flow-completed: basics
    steps:
      - id: shop
        display: "<white>Visit the server shop"
        objective:
          type: MANUAL
```

The `MANUAL` step above advances only from your own code:

```java
if (WelcomeFlowAPI.isReady()) {
    WelcomeFlowAPI.completeCurrentStep(player);
}
```

`/wf validate` reports a flow whose `previous-flow-completed` names a flow
that does not exist, because that flow could never start and the failure would
otherwise be completely silent.

---

## Tuning advice

**Read the funnel before changing anything.** `/wf stats` shows a drop off
count per step. Whichever step has the largest number is where you are losing
players, and it is almost always one of two problems: the objective is unclear,
or it is too slow.

**A hint is cheaper than a shorter step.** Before cutting an objective's
`amount`, try adding `hint-after` with an `on-step-progress` message. Most
drop off is confusion, not difficulty.

**Keep the whole flow under about 10 minutes.** The point is a player's first
session, not a full tutorial. Anything you cannot fit belongs in a second flow
gated behind `previous-flow-completed`.

**Put the reward at the end, not the start.** A player who receives everything
in the first thirty seconds has no reason to reach step 6.

**Set `abandon-after-hours` to match your audience.** It controls how long an
interrupted run stays resumable. Servers with weekend players want a longer
window; the 24 hours in the Factions example above suits a faster cycle.

**Test with `/wf test <flow>`.** It runs the flow on you exactly as a real
player would see it, including the bossbar and rewards, then discards the data
when you finish or log out.
