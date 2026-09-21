# AYA ISLAND — Game Specification & Build Order

**Version 1.0 — Design Team → Implementation**
**Target engine:** Raccoon Engine V2 (Java 21 + LuaJ 3.0.2)
**Status:** Approved. Build in the order given.

---

## 0. How To Use This Document

This is not a mood board. It is a build order.

- **Sections 1–8** are the *reference spec*: what exists, what it's made of, what the numbers are. Read it once, then come back to it when you need a table.
- **Section 9** is the *assignment list*: Milestones M0 through M14. Do them in order. Each one tells you what to build, what data to add, and a **Definition of Done** — a concrete test you run to prove the milestone is finished.
- **Section 10** is appendices: tables you'll copy directly into code.

Rules of engagement:

1. **Do not skip ahead.** Each milestone assumes the previous one works. M7 (emoji dialogue) is the fun one and you will want to build it on day one. Don't. It needs the needs-vector from M3 and the NPC population from M5 to have anything to say.
2. **Every number in this document is a starting value, not a law.** They are chosen to be playable, not perfect. Tune them in a config file, not in code.
3. **Anything marked `[DEFERRED]` is out of scope for v1.** That includes all the mystery/lore content. It's good, it's staying, it's not now.

---

## 1. Product Definition

### 1.1 One-line pitch

> A first-person, DOOM-engine life simulator set on a small island, where you can do anything and nothing, nobody speaks a word of dialogue, and the whole world runs on needs and emoji.

### 1.2 The reference stack

| Reference | What we take from it |
|---|---|
| **BitLife** | Life as a sequence of states and consequences. Age, career, crime, death. Systemic, not scripted. |
| **The Sims** | Needs vectors driving autonomous behavior. NPCs have lives independent of you. |
| **GTA (first-person)** | Open world, freedom to misbehave, escalating police response, vehicles. |
| **DOOM (1993)** | The renderer, the movement feel, billboarded sprites, the technical budget. |
| **Henry Stickmin** | The visual language: flat, doodled, thick-outlined, silly. |

### 1.3 Design pillars

These three sentences settle every argument. If a feature contradicts one, the feature is wrong.

**P1 — No scripted dialogue, ever.**
Not one line of authored conversation exists in this game. Every social exchange is emoji generated from state. This is a hard constraint and it is the entire identity of the project. It also means we never write, localize, or voice a single line — which is why a solo dev can ship an open world.

**P2 — The simulation runs whether you watch or not.**
NPCs have jobs, homes, schedules, needs and relationships. They go to work at 08:00 because their schedule says so, not because you walked into the trigger volume. When you're not looking, they're simulated cheaply — but they are simulated.

**P3 — "Should" is never "must."**
The game will tell you that you're a teenager who should be in school. It will never make you go. Ignore it and truancy consequences fire. Buy a gun at 09:00 and spend the rest of the run in a jail cell. That is a valid playthrough and the game must not break.

### 1.4 Non-goals for v1

- Combat depth. Guns exist, they fire, they kill, police respond. No enemy AI beyond flee/fight/panic.
- Multiplayer of any kind.
- Modding tools beyond what the existing map editor already does.
- Story, mystery, or lore content. `[DEFERRED]`
- More than one island.

---

## 2. Technical Context & Budget

You are building on the Raccoon Engine. These are the constraints you are designing inside of:

| Constraint | Value | What it means for design |
|---|---|---|
| Renderer | CPU software raycaster, multithreaded, column-by-column | Sprite count per frame is the bottleneck, not polygon count |
| Internal resolution | 640×480, upscaled | Never design UI text below ~8px tall |
| World model | Grid sectors with independent floor/ceiling heights, portals | No overlapping geometry. No room-over-room. Bridges are fake. |
| Sprites | Billboarded, 8-angle strips, 96×128 per frame | All NPCs face-on; no 3D character models |
| Scripting | LuaJ 3.0.2, **every script is re-parsed every frame** | Do NOT put simulation logic in Lua. Lua is for triggers and glue only. |
| Palette | VGA256-style `.gpl`, 247 colors | All art authored against this palette, no exceptions |
| Audio | Java Sound | Keep to a small set of looping tracks + one-shots |

### 2.1 The single most important architectural decision

**All simulation lives in Java. Lua only fires events.**

Because Lua scripts are re-parsed every frame, anything you put there costs you the parse, every frame, forever. The needs system, the scheduler, the economy, the emoji generator — all Java, all in a fixed-timestep simulation tick. Lua's job is: "player entered sector 42" → call a Java method. That's it.

### 2.2 Performance budget (targets, 60 FPS)

| Budget item | Target |
|---|---|
| Active (fully simulated) NPCs | 24 max |
| Visible sprites on screen | 32 max |
| Statistical (off-screen) NPCs | 400 |
| Simulation tick rate | 10 Hz, fixed timestep, decoupled from render |
| Emoji generation time | < 2 ms per exchange |
| Save file write | < 200 ms |

---

## 3. Core Data Model

This is the spine of the whole game. Build these structures in M0–M3 and everything else hangs off them.

### 3.1 `WorldState` — the root object

```
WorldState
├── seed: long                  // population seed from character creation
├── clock: GameClock
├── player: Player
├── npcs: NPC[]                 // all of them, active and statistical
├── locations: Location[]       // static, loaded from world data
├── economy: EconomyState
├── wanted: WantedState
├── flags: Map<String, Object>  // misc persistent flags
└── diary: DiaryEntry[]
```

Everything serializes from `WorldState`. If it isn't reachable from here, it doesn't survive a save.

### 3.2 `GameClock`

```
GameClock
├── minute: int        // 0-59
├── hour: int          // 0-23
├── day: int           // 1-28
├── season: enum       // SPRING, SUMMER, AUTUMN, WINTER
├── year: int          // starts at 1
└── phase: enum        // derived: NIGHT, MORNING, AFTERNOON, EVENING
```

- **1 real second = 1 in-game minute.** A full in-game day is 24 real minutes.
- **A season is 28 days. A year is 4 seasons = 112 days.**
- Phase is derived, never stored:

| Phase | Hours |
|---|---|
| NIGHT | 00:00 – 05:59 |
| MORNING | 06:00 – 11:59 |
| AFTERNOON | 12:00 – 17:59 |
| EVENING | 18:00 – 23:59 |

- **Time skip:** certain actions jump the clock (sleep → next 07:00; work shift → +8h; bus ride → +20min). During a skip, run the simulation in coarse mode (see §5.3) rather than in real time.

### 3.3 `Player`

```
Player
├── name: string
├── age: int                    // starts 17 or lower per backstory
├── health: int                 // 0-100, death at 0
├── money: int
├── needs: NeedsVector
├── inventory: ItemStack[32]
├── equipped: ItemRef
├── position: {sectorId, x, y, z, angle}
├── home: LocationRef | null
├── job: JobRef | null
├── education: enum             // NONE, ELEMENTARY, HIGHSCHOOL, UNIVERSITY, GRADUATE
├── relationships: Map<npcId, Relationship>
├── skills: Map<SkillId, int>   // 0-100
└── backstory: BackstoryData    // from character creation
```

### 3.4 `NeedsVector`

Six needs, each a float 0–100. 100 = fully satisfied, 0 = critical. Used by BOTH the player and every NPC — same struct, same math. This is what makes the emoji AI cheap: one vector explains everyone's behavior.

```
NeedsVector
├── hunger: float
├── energy: float
├── hygiene: float
├── social: float
├── fun: float
└── bladder: float
```

Decay per in-game hour (see **Appendix A** for the full table and consequences).

### 3.5 `NPC`

```
NPC
├── id: int
├── name: string                // generated, never displayed as dialogue
├── age: int
├── spriteId: string            // e.g. "NPC_063_00_012_00_176" from your generator
├── simLevel: enum              // ACTIVE, STATISTICAL, DORMANT
├── needs: NeedsVector
├── personality: PersonalityVector
├── mood: float                 // -100..100, derived each tick from needs
├── job: JobRef | null
├── home: LocationRef
├── schedule: ScheduleSlot[]    // 8 slots covering 24h
├── currentAction: ActionRef
├── relationships: Map<npcId, Relationship>
├── position: {sectorId, x, y, z, angle}
├── inventory: ItemStack[4]     // NPCs carry very little
└── alive: bool
```

### 3.6 `PersonalityVector`

Four traits, 0–100, rolled at world generation from the seed. These never change. They bias the emoji generator and the scheduler.

| Trait | Low (0) | High (100) | Affects |
|---|---|---|---|
| `sociability` | Avoids people | Seeks people | Schedule slot choice, greeting willingness, social decay rate |
| `temper` | Unflappable | Volatile | Negative-emoji weighting, fight vs flee |
| `diligence` | Skips work | Never misses | Schedule adherence, job retention |
| `bravery` | Panics | Stands ground | Reaction to weapons, crime witness behavior |

### 3.7 `Relationship`

```
Relationship
├── friendship: int    // -100..100
├── romance: int       // 0..100, gated: requires friendship >= 40
├── lastInteracted: GameClock
└── knownAs: enum      // STRANGER, ACQUAINTANCE, FRIEND, CLOSE_FRIEND, PARTNER, FAMILY, ENEMY
```

`knownAs` is derived from friendship/romance thresholds, not stored independently. See **Appendix D**.

### 3.8 `Location`

```
Location
├── id: string             // "BCH", "HS", "PS" — the codes from the design docs
├── displayName: string
├── zone: enum             // A, B, C, D, E, F, G, H
├── type: enum             // RESIDENCE, WORKPLACE, SHOP, PUBLIC, TRANSIT, RESTRICTED
├── sectorIds: int[]       // which engine sectors comprise it
├── entrance: {sectorId, x, y}
├── openHours: {start, end}
├── jobs: JobDef[]
├── needsServed: NeedType[]    // what a visit can satisfy
└── travelNode: string | null  // bus/subway stop id, if any
```

---

## 4. Screen Flow Specification

Every screen in the flow diagram, specified. Build these as empty navigable shells in **M1**, fill them in later milestones.

### 4.1 Flow map

```
SPLASH ──> MAIN MENU ──┬── New Game ──> CHARACTER CREATION ──> WORLDGEN LOADING ──> GAMEPLAY
                       ├── Load Game ──> LOAD SLOTS ──────────────────────────────> GAMEPLAY
                       ├── Options ────> OPTIONS
                       ├── Extras ─────> EXTRAS (gallery / commentary / debug)
                       └── Exit ───────> QUIT

GAMEPLAY ──┬── [Tab]  ──> INVENTORY
           ├── [P]    ──> PHONE (overlay, 10 apps)
           ├── [E]    ──> INTERACTION (NPC / item / vehicle)
           ├── [Esc]  ──> PAUSE
           └── death  ──> GAME OVER ──> MAIN MENU
```

### 4.2 Screen: SPLASH

- Full-screen image, 640×480, held 3 seconds, skippable with any key.
- **Design note:** the image is a screenshot taken *from inside the game world*, Life-is-Strange style. Ship with 6 hand-picked locations; pick one at random each boot.
- Fades to MAIN MENU over 0.5s.

### 4.3 Screen: MAIN MENU

- Background: a different in-world screenshot, with a slow pan (2 px/sec) for life.
- Music: island theme, looping, starts here and continues through menus.
- Items, vertically stacked, centered-left: `NEW GAME` · `LOAD GAME` · `OPTIONS` · `EXTRAS` · `EXIT`
- Selected item is indicated by a hand-drawn arrow doodle, not a highlight box.

### 4.4 Screen: LOAD SLOTS

- Three slots. Each shows: character name, age, in-game date, money, a thumbnail screenshot captured at save time, and total playtime.
- Empty slots read `— EMPTY —` and are non-selectable.
- Holding a slot for 2 seconds offers DELETE with a confirm.

### 4.5 Screen: OPTIONS

Minimum set, per the diagram:

| Setting | Type | Default |
|---|---|---|
| Music | On/Off + volume 0-10 | On, 7 |
| Sound FX | On/Off + volume 0-10 | On, 8 |
| Mouse sensitivity | 1-10 | 5 |
| Invert Y | Toggle | Off |
| Controls | Sub-screen, rebindable | — |
| Window scale | 1x / 2x / 3x / Fullscreen | 2x |

Options persist to a config file *outside* the save, so they survive a new game.

### 4.6 Screen: CHARACTER CREATION

Four sequential panels, `<` `>` navigation, `CONFIRM` on the last.

**Panel 1 — Identity**
- Name (text entry, 16 char max)
- Sex (affects sprite base and a few NPC reaction weights only)
- Starting age: fixed at **17**

**Panel 2 — Appearance**
- Skin color index, hair style, hair color index, clothing style, clothing color index — these map **directly** onto your existing NPC sprite generator's parameters. Reuse the generator; the player is just NPC #0.
- Live preview showing the 8-angle strip cycling.

**Panel 3 — Backstory**
This is the BitLife layer. Five choices, each granting starting modifiers:

| Question | Options | Effect |
|---|---|---|
| Family wealth | Poor / Modest / Comfortable | Starting money: 50 / 400 / 2000 |
| Upbringing | Strict / Neglected / Loving | `diligence` +15 / `bravery` +15 / `sociability` +15 |
| School record | Dropout / Average / Honors | Education level and job gating |
| Childhood hobby | Sports / Books / Streets / Music | Starting skill at 25 |
| Home | Beach / City / Countryside | Starting `home` LocationRef and zone |

**Panel 4 — Population Seed**
- A number field, defaulted to a random long, editable.
- Text under it: *"The same seed always grows the same island."*
- This seeds ALL of world generation: NPC names, appearances, personalities, jobs, home assignments, and family trees.

### 4.7 Screen: WORLDGEN LOADING

Not a fake progress bar. It's doing real work, so show it:

```
Raising the island...          [====      ]
Naming 340 people...           [==        ]
Handing out jobs...            [          ]
Deciding who loves who...      [          ]
```

Below, cycle a random island fact or a tip. Minimum display time 2 seconds even if generation is instant, so it doesn't flash.

### 4.8 Screen: GAMEPLAY (the HUD)

The DOOM reference matters here: the HUD is a **bottom bar**, chunky, hand-drawn, not floating minimalist widgets.

| HUD element | Position | Content |
|---|---|---|
| Portrait | Bottom-left | Player face doodle; expression changes with mood |
| Health | Bottom-left, beside portrait | Number 0-100 |
| Money | Bottom-left | `$` + number |
| Needs cluster | Bottom-center | Six tiny icons, each with a 5-segment bar. Segments empty as the need decays. |
| Clock & date | Bottom-right | `MON · SPRING 04 · 14:32` |
| Equipped item | Bottom-right | Icon + count |
| Wanted stars | Top-center | 0-5 stars, only drawn when > 0, flashing while actively hunted |
| Interaction prompt | Center screen | `[E]` + icon, only when something is in range |
| Emoji bark | Above NPC sprite | Speech-doodle bubble, 2.5s, fades |

**Critical:** a need drops below 20 → its icon flashes. Below 5 → the portrait expression changes and the icon flashes red.

### 4.9 Screen: INVENTORY

- 32 slots in a 8×4 grid. Grid-select with arrows or mouse.
- Selected item shows: name, description, and contextual verbs (`USE`, `EQUIP`, `DROP`, `GIVE`).
- `GIVE` is only enabled when an NPC is currently targeted — it hands off to the interaction system.
- Inventory does **not** pause the world. Time keeps moving. This is deliberate and it matters for tension.

### 4.10 Screen: INTERACTION

Not a screen so much as a contextual mode entered with `[E]`. Three variants:

**(a) NPC interaction** → opens the **emoji wheel** (see §5.6). The world does not pause; the NPC can walk away mid-conversation if their needs demand it.

**(b) Item interaction** → immediate. Pick up, or use in place (sit on bench, sleep in bed, watch TV, play arcade cabinet).

**(c) Vehicle interaction** → enter/exit. Changes movement mode (see §5.10).

### 4.11 Screen: PHONE

A full overlay, drawn as a doodled phone held in the lower-right, screen occupying ~40% of the view. `[P]` to open and close. **The world does not pause.**

Ten apps in a 2×5 grid:

| App | Function |
|---|---|
| **Missions** | The "should do" list. Never mandatory. Shows soft objectives with consequences listed. |
| **Calendar** | Season, day, upcoming festivals, rent due date, work shifts, birthdays of known NPCs |
| **Relationships** | Every NPC you've met: portrait, name, relationship bar, `knownAs` label. Sortable. |
| **Music** | Toggles headphones. While active, plays a chosen track and **muffles all world audio by 70%**. Deafness has consequences: you don't hear sirens. |
| **Camera** | Freezes a frame, saves a screenshot to the Gallery in EXTRAS. Also used to capture the save-slot thumbnail. |
| **Call** | Dial a known NPC. Success depends on relationship + their current schedule slot. Summons them to your location, or they decline. |
| **Text** | Send an emoji string to a known NPC. Get an emoji string back. Same generator as face-to-face, reduced weight on physical-need responses. |
| **Diary** | Free text entry, timestamped. Purely for the player. Auto-logs major life events too. |
| **Save** | Writes to a chosen slot. |
| **Exit** | Back to main menu, with an unsaved-progress warning. |

### 4.12 Screen: PAUSE

`RESUME` · `OPTIONS` · `SAVE` · `QUIT TO MENU`. This one *does* pause the simulation.

### 4.13 Screen: EXTRAS

- **Gallery** — every photo taken with the phone camera, browsable.
- **Commentary** — your dev notes, unlockable or just always on. This is where the personality of the project lives.
- **Debug Mode** — toggled here, applies in-game:
  - Give money, give any item, set any need
  - Teleport to any location by code
  - Set wanted level, clear wanted level
  - Freeze time / set time of day
  - Toggle NPC simulation-level overlay (shows ACTIVE/STATISTICAL/DORMANT tags above heads)
  - Kill/revive player
  - Force an emoji exchange with a chosen intent, for testing the generator

### 4.14 Screen: GAME OVER

Triggered on death (health 0, old age, or executed). Shows a BitLife-style life summary:

```
[Name] — died at age [N]
Cause: [starvation / gunshot / old age / ...]

Jobs held: 3        Friends made: 12
Crimes: 7           Days in jail: 210
Money at death: $44 Photos taken: 31
```

Then `LOAD` · `NEW GAME` · `MAIN MENU`.

---

## 5. Systems Specification

### 5.1 The simulation tick

One fixed tick at **10 Hz**, independent of render framerate.

```
each tick:
  1. advance clock
  2. decay needs (player + all ACTIVE npcs)
  3. update NPC simulation levels based on player position
  4. run scheduler for NPCs whose schedule slot just changed
  5. step NPC action state machines (ACTIVE only)
  6. run statistical update for STATISTICAL npcs (every 60th tick only)
  7. update wanted level & police response
  8. process economy events (rent, wages, bills) on day boundaries
  9. check life events (birthday, death, eviction, firing)
```

Render reads a snapshot. Never let rendering mutate simulation state.

### 5.2 Needs system

Needs decay linearly per in-game hour. When a need hits 0, a consequence fires and repeats every in-game hour until relieved.

| Need | Decay/hr | At 0, consequence |
|---|---|---|
| Hunger | 4.2 | −5 health per hour |
| Energy | 3.5 | Forced collapse: player blacks out, wakes 6h later at current location, −10 health |
| Hygiene | 2.0 | NPC interactions get a −20 friendship modifier; some venues deny entry |
| Social | 2.5 | Mood floor drops; −2 health per hour (loneliness) |
| Fun | 2.2 | Mood penalty; skill gain rate halved |
| Bladder | 5.0 | Public accident: hygiene to 0, nearby NPCs lose 10 friendship, tiny wanted bump |

Restoration comes from **actions at locations**. Each `Location` declares `needsServed`. See **Appendix B** for the full action table.

**Mood** is derived every tick, never stored as an independent value:

```
mood = (average(needs) - 50) * 1.6
       + (personality.sociability - 50) * 0.1
       + situationalModifiers      // recent events, ±30 max, decays over 2 in-game hours
mood = clamp(mood, -100, 100)
```

Mood drives: NPC action choice, emoji generation weights, facial expression, and whether they'll talk to you at all.

### 5.3 NPC simulation levels (LOD) — the cheapness trick

This is what makes 400 NPCs affordable on a software raycaster. Three tiers:

| Level | Condition | What runs | Cost |
|---|---|---|---|
| **ACTIVE** | Within 40 map units of player AND in a connected sector | Full needs decay, pathfinding, action state machine, sprite render, emoji capable | Expensive; cap at 24 |
| **STATISTICAL** | Same zone as player, but out of range | Needs decay in bulk every 6 seconds (60 ticks). No pathfinding — position is *assumed* to be their current schedule slot's location. No sprite. | Cheap; ~400 |
| **DORMANT** | Different zone entirely | Nothing runs. On promotion, fast-forward their needs by elapsed time in one calculation. | Free |

**Promotion/demotion:**
- DORMANT → STATISTICAL: fast-forward needs using `elapsedHours × decayRate`, clamp, then run their scheduler to place them.
- STATISTICAL → ACTIVE: spawn their sprite at their schedule slot's location, pick a wander target, begin the action state machine.
- ACTIVE → STATISTICAL: despawn sprite, freeze position at nearest location node.

**The trick that sells it:** because an NPC's *schedule* is deterministic and their *position* is inferred from it, a STATISTICAL NPC costs one float-multiply every six seconds. The player can never tell the difference, because by the time they're close enough to observe, the NPC has been promoted.

### 5.4 Scheduling

Every NPC has 8 schedule slots covering the day, generated at worldgen from their job, age and personality.

```
ScheduleSlot { startHour, endHour, activity, locationRef }
```

Activities: `SLEEP`, `WORK`, `SCHOOL`, `EAT`, `SOCIALIZE`, `LEISURE`, `ERRAND`, `WANDER`.

Slot generation rules:
- Age < 6 → home all day with a guardian
- Age 6–11 → ELEMENTARY 08:00–15:00
- Age 12–17 → HIGHSCHOOL 08:00–16:00
- Age 18–22, if enrolled → UNIVERSITY, variable
- Age 18–64 with a job → job's shift hours
- Age 65+ → no work, more LEISURE and SOCIALIZE
- Everyone: SLEEP at home ~23:00–07:00, shifted ±2h by `diligence`

**Schedule adherence:** each slot transition, roll against `diligence`. On failure, the NPC does `WANDER` or `LEISURE` instead. Low-diligence NPCs miss work, and after 3 misses in a season, they lose their job — which changes their schedule, their money, and eventually their mood. The world stays alive because it can go wrong on its own.

### 5.5 NPC action state machine (ACTIVE only)

```
IDLE ──> PATHING ──> PERFORMING ──> IDLE
  │                       │
  └──> REACTING <─────────┘      (interrupt: player, gunshot, crime, weather)
```

- **IDLE:** pick an action satisfying the lowest need available at the current schedule slot's location.
- **PATHING:** A* over a coarse navigation graph of location nodes, not per-pixel. Nodes are placed at doorways, junctions and points of interest during map authoring.
- **PERFORMING:** play the relevant sprite pose, tick the need restoration, emit an occasional ambient emoji bark.
- **REACTING:** interrupt. Priority order: gun drawn nearby > gunshot heard > player initiates interaction > crime witnessed > need critical.

### 5.6 THE EMOJI COMMUNICATION SYSTEM

This is the heart of the game. It gets its own long section.

#### 5.6.1 Concept

There is no text dialogue. The player says things by choosing an emoji. The NPC answers with 1–4 emoji, generated from their internal state. The exchange can change the NPC's behavior.

#### 5.6.2 Player input — the emoji wheel

On `[E]` against an NPC, a radial wheel appears with **12 intents**. Number keys 1–9,0,-,= or mouse direction to pick.

| # | Emoji | Intent ID | Meaning |
|---|---|---|---|
| 1 | 👋 | `GREET` | Hello |
| 2 | 😊 | `FRIENDLY` | Be nice, chat |
| 3 | ❤️ | `FLIRT` | Romance attempt |
| 4 | 😂 | `JOKE` | Humor |
| 5 | ❓ | `ASK` | Ask about something / request info |
| 6 | 🙏 | `REQUEST` | Ask a favor |
| 7 | 💰 | `TRADE` | Offer money / propose deal |
| 8 | 🍔 | `GIFT` | Offer the equipped item |
| 9 | 👉 | `SUMMON` | "Follow me" |
| 0 | 😠 | `INSULT` | Be rude |
| - | 👊 | `THREATEN` | Intimidate |
| = | 🚪 | `LEAVE` | End conversation |

#### 5.6.3 Response generation — the algorithm

**Step 1 — Compute the response class.**

```
score = 0
score += moodContribution        // mood * 0.4
score += relationshipContribution // friendship * 0.5, romance * 0.3 if intent is FLIRT
score += intentBaseValue         // per-intent table, Appendix C
score += personalityModifier     // e.g. high temper amplifies negative response to INSULT
score += needsUrgency            // if any need < 15, large negative pull toward DISMISS
score += randomJitter            // ±12, so the same inputs don't always give the same reply

responseClass = 
  score >  55  -> ENTHUSIASTIC
  score >  20  -> POSITIVE
  score > -15  -> NEUTRAL
  score > -50  -> NEGATIVE
  else         -> HOSTILE
```

**Step 2 — Select the seed emoji.**

Each `(responseClass, intent)` pair maps to a small pool of seed emoji with weights. Pick one weighted-randomly. This is a lookup table — roughly 60 entries. **Appendix C** has the starter set.

**Step 3 — Extend via a second-order Markov chain.**

You have an emoji transition table built from a hand-authored corpus (see 5.6.5). Given the seed, walk the chain 0–3 more steps:

```
sequence = [seed]
length = weightedPick(1:40%, 2:35%, 3:18%, 4:7%)
while sequence.length < length:
    context = last two emoji (or one, if only one so far)
    candidates = transitionTable[context]
    // bias candidates by state:
    for each candidate c:
        weight[c] = baseWeight[c]
                  * needsBias(c, npc.needs)      // hungry NPC boosts 🍔😩🥺
                  * moodBias(c, npc.mood)
                  * personalityBias(c, npc.personality)
    next = weightedPick(candidates, weight)
    if next == TERMINATOR: break
    sequence.append(next)
```

**Step 4 — Apply the need-leak.**

With probability `(100 - lowestNeedValue) / 100 × 0.6`, append the emoji corresponding to their most urgent need, regardless of what was said. This is the single highest-value trick in the system: a starving NPC responding to your joke with `😂🍔🥺` reads instantly as *"ha, funny, but I'm hungry"* — and you wrote no dialogue to achieve it.

**Step 5 — Apply state effects.**

The exchange has consequences:

| Response class | Friendship Δ | Other effects |
|---|---|---|
| ENTHUSIASTIC | +8 | May gift an item; may agree to SUMMON; romance +4 if FLIRT |
| POSITIVE | +4 | May agree to SUMMON |
| NEUTRAL | +1 | — |
| NEGATIVE | −5 | Walks away; refuses further interaction for 2 in-game hours |
| HOSTILE | −12 | If `temper` > 70 → attacks player. Else flees. May call police if player is armed. |

Repeated interaction within 10 in-game minutes applies a stacking −10 score penalty. Spamming is punished.

#### 5.6.4 NPC ambient barks

Unprompted, NPCs emit a single emoji above their head when:
- A need crosses below 25 → the need emoji
- Mood crosses a threshold → 😄 / 😐 / 😞
- They witness a crime → 😱 or 😠 depending on `bravery`
- Weather / festival / time-of-day events → 🌧️ 🎆 🌙
- They pass a friend → 👋

Rate-limit: max one bark per NPC per 20 in-game minutes.

#### 5.6.5 The corpus (what you have to author)

You author this **once**, by hand, and it is the only "writing" in the game:

1. **Emoji vocabulary: 80–120 emoji.** No more. A tight set reads as a language; a huge set reads as noise.
2. **Seed table:** ~60 `(responseClass, intent) → weighted emoji pool` entries.
3. **Transition corpus:** ~300 hand-written emoji "sentences" of 2–4 emoji each, tagged by mood bucket. Build the order-2 transition table from these at load time.
4. **Need-bias table:** for each of the 6 needs, which emoji get boosted and by how much.

That's a weekend of authoring, in a plain text file, and it powers every conversation in the game forever.

#### 5.6.6 Storage format

Plain text, loaded at startup, hot-reloadable in debug mode:

```
# emoji_corpus.txt
[VOCAB]
👋 😊 ❤️ 😂 ❓ 🙏 💰 🍔 👉 😠 👊 🚪 😴 🚿 🎉 🤝 😱 ...

[SEEDS]
ENTHUSIASTIC|GREET = 👋:40, 😄:35, 🤗:25
POSITIVE|GREET     = 👋:50, 😊:30, 🙂:20
NEUTRAL|GREET      = 🙂:40, 👋:30, 😐:30
NEGATIVE|GREET     = 😒:50, 😐:30, 🙄:20
HOSTILE|GREET      = 😠:60, 🖕:20, 🚪:20
...

[CHAINS]
mood:high = 😊 😄 🎉
mood:high = 👋 😊 🤝
mood:low  = 😞 😔 💔
mood:low  = 😐 🚪
...

[NEEDBIAS]
hunger = 🍔:3.0, 😩:2.0, 🥺:1.8
energy = 😴:3.0, 🥱:2.5, 🛏️:2.0
...
```

### 5.7 Economy

| Element | Rule |
|---|---|
| Wages | Paid at the end of each work shift, directly to `player.money` |
| Rent | Due day 1 of each week (days 1, 8, 15, 22). Auto-deducted if affordable. |
| Missed rent | 1 miss → warning. 2 misses → eviction: `home` becomes null. |
| Homeless | No SLEEP location. Energy decays 1.5× and hygiene 2×. Health drains slowly. Survivable, miserable, and a legitimate way to play. |
| Shops | Fixed prices from an item table. No haggling in v1. |
| Theft | Taking an item without paying while a shopkeeper has line of sight → crime, wanted +1 |

### 5.8 Jobs

Jobs are drawn from the big role list in the design docs, grouped by location. Each `JobDef`:

```
JobDef { id, title, locationId, wagePerShift, shiftStart, shiftEnd,
         requiredEducation, requiredAge, requiredSkill, isCriminal }
```

- Apply at a location during open hours. Approval = education + age + skill check. Rejection is fine and common.
- Missing 3 shifts in a season → fired.
- **Criminal jobs** (smuggler, dealer, loan shark) pay 3× and carry a per-shift chance of generating wanted level.
- NPCs hold the same jobs from the same pool. A location's jobs are a finite pool — if the barista slot is filled by an NPC, you can't have it until they quit, get fired, or die.

### 5.9 Crime, Wanted & Police

**Wanted levels 0–5.** Each crime adds points; points decay 1 per in-game hour when not actively pursued.

| Crime | Wanted Δ |
|---|---|
| Trespassing (restricted zone) | +1 |
| Theft (witnessed) | +1 |
| Vandalism | +1 |
| Brandishing a weapon in public | +2 |
| Assault | +2 |
| Firing a weapon in public | +3 |
| Killing an NPC (witnessed) | +4 |
| Killing a police officer | +5 |

**Witness requirement:** a crime only registers if an NPC has line of sight within 25 map units, or a police officer is within 40. Unwitnessed crimes still happen — bodies persist, and a later discovery can retroactively trigger a level-2 investigation.

**Police response by level:**

| Level | Response |
|---|---|
| 1 | Nearest officer moves to investigate, walking |
| 2 | Two officers, running, will arrest on contact |
| 3 | Four officers, armed, shoot if player is armed |
| 4 | Roadblocks at zone transit nodes; officers spawn continuously |
| 5 | Island-wide; ferries and airport locked down |

**Arrest:** on contact with an arresting officer, the player is taken to Jailhouse (JH). Sentence in in-game days = `wantedLevel × 3 + crimeHistoryCount`. Time skips forward; needs are maintained at a miserable-but-survivable 40. Inventory confiscated. On release, wanted resets to 0 and `crimeHistoryCount` increments permanently (affects future sentences and job applications).

**Death:** if the player dies at wanted 4+, GAME OVER with cause `shot by police`.

### 5.10 Movement & Vehicles

| Mode | Speed | Notes |
|---|---|---|
| Walk | 1.0× | Default |
| Run | 1.8× | Drains energy at 3× rate |
| Bicycle | 2.4× | No energy drain. Can be stolen (crime). |
| Car | 5.0× | Only on road sectors. Hitting an NPC = assault or kill. |
| Bus | — | Fast travel between zone transit nodes. Costs $5, skips 20 in-game minutes. |
| Subway | — | Fast travel, city zones only. Costs $3, skips 10 minutes. |
| Ferry / Plane | — | Story/edge use only; locked at wanted 5. |

Vehicles are engine sprites with an interaction. Entering swaps the camera's movement parameters and hides the player's weapon. The engine is a raycaster — do not attempt real vehicle physics. Cars move on a constrained speed/turn model, no drifting, no suspension.

### 5.11 Aging & Life Events

Checked at each day boundary:

| Event | Trigger |
|---|---|
| Birthday | Day matches birth day → age +1, phone notification |
| Education transition | Age 18 → highschool ends; enroll in UNI if record allows |
| Adult obligations | Age 18 → rent becomes payable, truancy no longer applies |
| Health decline | Age 60+ → max health drops 1 per year |
| Natural death | Age 70+ → daily death roll, rising with age; certain by 95 |
| NPC deaths | Same rules. NPCs die. Their jobs open up. Their homes free up. Their friends' mood drops. |

**This is the BitLife layer and it's what makes a run feel like a life.** Do not cut it.

### 5.12 Minigames & Diversions

Keep these tiny. They're texture, not content.

| Diversion | Implementation |
|---|---|
| Arcade cabinets | 2–3 micro-games rendered to a flat texture on the cabinet face. Snake, a shooting gallery, a Simon-style pattern game. Costs $1 per play, restores Fun. |
| Television | A looping sequence of doodle "channels" on a wall texture. Sitting and watching restores Fun slowly and Energy slightly. |
| Headphones | See Phone → Music. Restores Fun passively while walking. Muffles world audio. |
| Camera | Screenshots to the gallery. Restores a trickle of Fun. |

---

## 6. World Specification

### 6.1 Zones

The island is 8 macro zones. Each zone is a separate map file; transitions happen at defined boundary sectors with a short load.

| Zone | Character | Contains |
|---|---|---|
| **A** | Open, airy, spiritual. Cliffs and sky. | Temple (T), Lighthouse (L) |
| **B** | The beach strip. Tourists, heat, noise. | Beach (BCH), Bungalows (BB), Resort (BH), Dock (D) |
| **C** | Agricultural countryside. | Ranch (R), Cabin in the Woods (C) |
| **D** | Mountain, D-shaped ridge. | Mountain (MT), Lover's Hill (LH) |
| **E** | North-east: the civic and commercial town. **The densest zone.** | Hospital (HOS), Elementary (ES), High School (HS), University (UNI), Park (PK), Supermarket (SM), Airport (AIR), Cinema (CN), Police Station (PS), Barber (BR), Cafe (CAF), Library (LIB), Art Gallery (ART), Arcade (ARC), Fire Station (FS), Stadium (SAM), Houses (H#) |
| **F** | Deep forest. Sparse, unsettling, good for hiding. | Forest interior, trails |
| **G** | Quiet, liminal. | Graveyard (GY) |
| **H** | Lower south town — the "hidden" side. Nightlife and crime. | Bar (BAR), Strip Club (STR), Concert Hall (CRT), Jailhouse (JH), Houses (H#) |

`[DEFERRED]` — Ruins (RU), Underground Bunker (UB), and all mystery content.

### 6.2 Industry-to-zone mapping

Used by worldgen to distribute jobs sensibly.

| Industry | Zones |
|---|---|
| Tourism | B, A, E |
| Education | E |
| Religion / Tradition | A |
| Agriculture | C |
| Forestry / Nature | F |
| Government | E, H |
| Entertainment / Nightlife | E, H |
| Transportation | E, B |
| Healthcare | E |
| Retail | E, H |
| Fishing / Marine | B, A |
| Mountain Recreation | D |
| Crime / Black Market | H, F, D |
| Infrastructure | All |

### 6.3 Transit network

Bus stops at every zone boundary; subway connecting E ↔ H only. A player can cross the island in roughly 4 bus hops. **Design rule:** walking the whole island end-to-end should take about 12 in-game hours. Transit exists so the island can be big without being tedious.

### 6.4 Worldgen algorithm

Run once at New Game, deterministic from the seed:

```
1. Seed RNG with the population seed
2. Load static location data for all 8 zones
3. Generate 340 NPCs:
     a. roll age from a realistic distribution (see Appendix E)
     b. roll personality vector
     c. generate appearance params → call sprite generator → spriteId
     d. generate name
4. Build family units:
     a. pair ~40% of adults 25-60 into households
     b. assign 0-3 children per household, ages consistent with parents
     c. set FAMILY relationships at friendship 60-90
5. Assign homes: one household per residence location, distributed by zone
6. Assign jobs: walk the job pool, match by age/education/zone proximity
     - leave ~8% of working-age adults unemployed (this is realistic and it creates drama)
7. Generate schedules from job + age + personality
8. Seed initial acquaintance relationships: NPCs sharing a workplace or
   neighborhood get friendship 10-40
9. Place the player at their chosen starting home
```

---

## 7. Art Specification

### 7.1 The look

**Henry Stickmin meets a DOOM total conversion.** Flat, hand-drawn, thick black outlines, mostly-white fills with flat color accents. Deliberately doodled — wobbly lines are correct, not a mistake. The world geometry is blocky and plainly a raycaster, and the art leans *into* that rather than hiding it.

### 7.2 Hard technical rules

| Rule | Reason |
|---|---|
| All art authored against the 247-color VGA `.gpl` palette | Engine constraint, non-negotiable |
| Minimum feature size 3–4 px | Single-pixel detail shimmers badly under raycaster scaling |
| Flat fills, no dithering | Dithering crawls when textures are perspective-scaled |
| Spend detail budget on outlines, not interior texture | Outlines survive downscaling; texture doesn't |
| Muted palette columns for environments, saturated for NPCs and items | Makes characters and pickups pop against the world without a highlight shader |
| Wall textures 64×64 | Engine convention |
| NPC sprites: 8-angle strips, 96×128 per frame, 768×128 per strip | Matches your existing generator |

### 7.3 Required sprite poses

Per NPC, generated by the existing tool:

| Pose | Priority | Notes |
|---|---|---|
| `standing` | M5 | Required |
| `walking1`–`walking4` | M6 | Required for the world to feel alive |
| `sitting` | M8 | For cafes, benches, buses |
| `panic` | M10 | Fleeing from crime |
| `dead` | M10 | Single frame, no angles needed (billboard flat on the ground) |

### 7.4 Audio

- **Music:** island-flavored, sparse, a little melancholy — the docs call for "very island, cries, almost like Japan." 6–8 looping tracks: main menu, day, night, beach, town, forest, tense/police, jail.
- **SFX:** footsteps (4 surface variants), doors, phone open/close, camera shutter, gunshot, siren, bus, bike bell, emoji pop (a single soft blip when an emoji bark appears).
- **Diegetic music:** arcade cabinets, the bar, the concert hall each have their own looping track audible near them, ducking the ambient track.

---

## 8. Save Format

Plain text, human-readable, one file per slot. Debuggable by hand, which you will need.

```
# aya_save_1.txt
[META]
version=1
saved_at=2026-09-12T14:03:00
playtime_seconds=14402
thumbnail=slot1_thumb.png

[CLOCK]
minute=32 hour=14 day=4 season=SPRING year=1

[PLAYER]
name=Rafael age=17 health=88 money=412
needs=hunger:62.1,energy:40.0,hygiene:80.5,social:22.0,fun:55.3,bladder:71.0
home=H_012 job=CAF_BARISTA education=HIGHSCHOOL
pos=sector:88,x:12.4,y:30.1,z:0.0,angle:1.57
inventory=PHONE:1,SANDWICH:2,CAMERA:1
skills=CHARISMA:34,FITNESS:12

[WANTED]
level=0 points=0 history=1

[NPC]
id=17 name=Mira age=34 sprite=NPC_063_00_012_00_176 home=H_004 job=SM_CASHIER
  needs=... personality=soc:71,tem:22,dil:84,bra:40 alive=1
  rel=0:friendship:35,romance:0
[/NPC]
... (×340)

[DIARY]
1|SPRING|3|Started working at the cafe.
```

**Save-size note:** 340 NPCs × ~200 bytes ≈ 70 KB. Fine. Don't optimize it.

---

---

# 9. THE BUILD ORDER

> **This is the assignment.** Fifteen milestones. Do them in order. Each has a Definition of Done — a test you actually run. If the test doesn't pass, you are not done, no matter how much code exists.

A rough sense of scale is given per milestone so you can plan, but treat it as relative weight, not a deadline.

---

## M0 — Foundations
**Weight: S** · *Get the skeleton standing before you hang anything on it.*

### Build
1. Create the `aya` package inside the Raccoon Engine project. Keep game code strictly separate from engine code — you should be able to update the engine without touching the game.
2. Implement a **fixed-timestep simulation loop** at 10 Hz, decoupled from the render loop. Render reads a snapshot; simulation never touches rendering.
3. Implement `GameClock` with full advance logic, phase derivation, season and year rollover.
4. Implement a `Config` loader reading a plain-text `aya.cfg`. Every tunable number in this document goes in here, not in code.
5. Implement a debug console overlay (toggle with `~`) that prints the clock and current tick rate.

### Definition of Done
Launch the game. The console shows the clock advancing at 1 minute per real second. Let it run 24 real minutes — the date rolls over to day 2 correctly, and phases changed at 06:00, 12:00, 18:00 and 00:00. Change the tick rate in `aya.cfg`, relaunch, and the change takes effect without recompiling.

---

## M1 — Screen Flow Skeleton
**Weight: M** · *Every screen exists and connects. All of them are empty. That's correct.*

### Build
1. A `ScreenManager` with a screen stack (push/pop), so overlays like Phone and Inventory can sit on top of gameplay.
2. Every screen from §4 as an empty class that draws its own name in the middle of the screen and handles its navigation inputs: SPLASH, MAIN_MENU, LOAD_SLOTS, OPTIONS, CHARACTER_CREATION, WORLDGEN_LOADING, GAMEPLAY, INVENTORY, PHONE, PAUSE, EXTRAS, GAME_OVER.
3. Wire the full transition graph from §4.1.
4. Implement the OPTIONS screen for real — it's small and you'll want working volume control immediately. Persist to `aya.cfg`.

### Definition of Done
From a cold boot you can reach every single screen in the flow diagram and get back to the main menu, without a crash and without a dead end. Mute the music in Options, quit, relaunch — it's still muted.

---

## M2 — Character Creation & Save/Load
**Weight: M** · *Persistence early. Retrofitting saves is misery.*

### Build
1. All four character creation panels per §4.6, including the live sprite preview driven by your existing NPC generator.
2. The `WorldState` root object and every struct from §3 — even the ones nothing uses yet.
3. Serializer and deserializer for the §8 save format. Write it, read it, compare the resulting object graph.
4. Three save slots with metadata display, plus slot deletion.
5. Hook up the Phone → Save app and the Pause → Save entry, even though the Phone is still a stub.

### Definition of Done
Create a character named `TESTER`, save to slot 2, quit fully, relaunch, load slot 2 — name, age, appearance parameters, backstory modifiers and starting money all match. Open the save file in a text editor and read it without decoding anything.

---

## M3 — Time, Needs, Stats & HUD
**Weight: M** · *The player becomes a simulated being.*

### Build
1. `NeedsVector` with the decay table from Appendix A, running on the sim tick for the player.
2. Consequence firing at 0 for each need, with the effects specified in §5.2.
3. Health, death at 0 health → GAME_OVER screen with the life summary.
4. `mood` derivation per §5.2.
5. The full HUD from §4.8: portrait with mood-driven expression, health, money, six need bars, clock, equipped item, interaction prompt slot.
6. Debug commands: set any need, set health, set money, set time of day, freeze time.

### Definition of Done
Start a new game and don't touch anything. Watch the need bars drain at the specified rates. Set hunger to 0 with a debug command — health starts draining 5/hr and the portrait's expression changes. Let it run to death; the GAME OVER screen appears with `Cause: starvation`.

---

## M4 — World Data & The Vertical Slice Zone
**Weight: L** · *Build ONE zone completely before building eight badly.*

### Build
1. The `Location` struct and a plain-text world data file listing every location's id, name, zone, type, hours, and needs served.
2. **Author Zone E only**, in the map editor. It's the densest zone and the best stress test — if the renderer holds up in E it holds up anywhere. Include at minimum: Supermarket, Cafe, Park, Police Station, High School, and 6 houses.
3. A **navigation graph** layer: place nodes at doorways, junctions and points of interest, and store them in the map data. NPCs will path over these, not over pixels.
4. Sector-to-location binding, so the game always knows which location the player is standing in.
5. Zone transition stubs at the boundary — for now they just print which zone you'd be entering.
6. Bus stop entities that fast-travel within Zone E (they'll go inter-zone later).

### Definition of Done
Walk from one end of Zone E to the other. The HUD or debug overlay correctly names the location you're standing in at every point, including outdoors. Frame rate stays at 60 with nothing else running. The nav graph is visible in debug overlay and fully connected — no orphan nodes.

---

## M5 — Population Generation
**Weight: L** · *340 people who have never met you but have full lives.*

### Build
1. The full worldgen algorithm from §6.4, deterministic from seed.
2. Name generation. Keep a name pool in a text file; nobody needs a Markov name generator.
3. Batch-call your existing sprite generator (the Flask batch endpoint) at worldgen to produce all NPC sprite strips at once. Cache them to disk keyed by `spriteId` — identical parameter combinations reuse the same files.
4. Family unit generation and initial relationship seeding.
5. Home assignment and job assignment from the finite job pool.
6. A debug NPC browser screen: scroll every generated NPC and inspect their full state.

### Definition of Done
Generate a world with seed `12345`, note NPC #17's name, age, personality, job and home. Delete the save, generate again with seed `12345` — identical in every field. Generate with `12346` — completely different. All 340 NPCs have sprite strips on disk, and the sprite cache reused files for duplicate appearance combinations.

---

## M6 — NPC Simulation, Scheduling & LOD
**Weight: XL** · *This is the hardest milestone. Budget accordingly.*

### Build
1. Schedule generation per §5.4, for every NPC.
2. The three-tier LOD system from §5.3, with correct promotion and demotion including needs fast-forwarding.
3. The NPC action state machine (§5.5) for ACTIVE NPCs.
4. A* pathfinding over the nav graph.
5. Sprite spawning, 8-angle billboard selection based on view angle, and walk-cycle animation.
6. NPC need decay and restoration from performing actions at locations.
7. Schedule adherence rolls against `diligence`, including the job-loss consequence after 3 missed shifts.
8. Debug overlay: LOD tag above every NPC's head, plus a counter of ACTIVE / STATISTICAL / DORMANT.

### Definition of Done
Stand in Zone E at 07:45 in-game and watch. NPCs leave their houses and walk to work. At 08:00, the supermarket has its cashier behind the counter and the school has students inside. Walk away 100 units and back — the counter is still staffed, and the cashier's needs have decayed correctly for the elapsed time. The ACTIVE counter never exceeds 24 and frame rate holds at 60.

---

## M7 — The Emoji Communication System
**Weight: L** · *The soul of the game. You've earned it.*

### Build
1. Author the corpus file per §5.6.5 — vocabulary, seed table, chain corpus, need-bias table. **Do the authoring first, before the code.** You'll design better once you've written 300 emoji sentences by hand.
2. A corpus loader that builds the order-2 transition table at startup, hot-reloadable in debug mode.
3. The emoji wheel UI (§5.6.2), 12 intents, radial, keyboard and mouse selectable.
4. The full 5-step generation algorithm from §5.6.3.
5. The speech-doodle bubble renderer, positioned above the target NPC, 2.5 second display.
6. Relationship effects per the response-class table.
7. Behavior consequences: SUMMON makes an NPC follow you; HOSTILE with high temper makes them attack; NEGATIVE makes them refuse interaction for 2 hours.
8. Ambient barks per §5.6.4, with rate limiting.
9. Debug: force a specific intent and response class, dump the generation's score breakdown to console.

### Definition of Done
Walk up to a stranger, greet them — you get a neutral, slightly cold reply. Greet the same person daily for a week of in-game time; the replies visibly warm up. Find an NPC whose hunger is below 20, tell them a joke, and watch the need-leak produce something like `😂🍔🥺`. Insult a high-temper NPC and get attacked. Insult a low-temper NPC and watch them walk away instead. **Every one of those exchanges must be generated — grep the codebase and confirm there is not one hard-coded response string.**

---

## M8 — Items, Inventory, Shops & Interaction
**Weight: M**

### Build
1. The item database in a text file: id, name, description, price, category, verbs, needs restored.
2. The 32-slot inventory screen (§4.9), which does **not** pause the sim.
3. World item pickups as engine sprites.
4. Usable world objects: beds (sleep + time skip), toilets, showers, benches, TVs, arcade cabinets.
5. Shop interaction: browse the location's stock, buy, sell.
6. The `GIVE` verb, routing into the emoji system as a `GIFT` intent with the item's value modifying the response score.
7. Theft: taking shop stock in a shopkeeper's line of sight → the crime hook (which M10 will complete).

### Definition of Done
Start with $400. Buy a sandwich at the supermarket, eat it, watch hunger rise. Sleep in your bed at 23:00 — time skips to 07:00, energy is full, and the world has moved on correctly (NPCs are where their 07:00 schedule slot says they should be). Give a gift to an NPC and see their response warm relative to the item's value.

---

## M9 — Jobs, Money, Housing & Rent
**Weight: M**

### Build
1. The `JobDef` table for every job in the design docs, tied to locations.
2. Job application flow: interact with a workplace during open hours, pass or fail the requirement check.
3. Working: be at the workplace during your shift, and the shift completes with a time skip and a wage payment. Missing shifts accumulates toward firing.
4. Rent: weekly deduction, warning, eviction on second miss.
5. The homeless state per §5.7 — fully survivable, not a game over.
6. Home ownership: buy a residence outright if you have the money.
7. Basic decoration: place a small set of furniture items in your own residence.

### Definition of Done
Get hired at the cafe. Work three shifts and get paid three times. Deliberately skip the next three and get fired. Stop paying rent for two weeks and get evicted — then survive a further three in-game days homeless without the game breaking or soft-locking.

---

## M10 — Crime, Police, Wanted & Jail
**Weight: L**

### Build
1. Weapons: at minimum a melee option and one firearm. Engine-side, this is the existing weapon rendering plus a hitscan.
2. NPC damage, death, and persistent corpses.
3. The witness system (§5.9) — line of sight, distance thresholds, and delayed discovery of unwitnessed crimes.
4. The wanted level with point accumulation and hourly decay.
5. Police NPCs with the five escalation tiers.
6. Arrest, sentencing, the jail time skip, inventory confiscation, and release.
7. NPC panic reactions: flee if low `bravery`, confront if high. Panic sprite pose.
8. Death by police → GAME OVER.

### Definition of Done
Fire a gun in the middle of Zone E at midday. Nearby NPCs panic and flee, one calls it in, wanted hits 3, armed officers arrive and engage. Get arrested. Serve the sentence. Get released with wanted at 0, no inventory, and a permanent criminal record that shows up when you next apply for a job. Then commit the same crime in an empty alley at 03:00 with nobody in sight — wanted stays at 0 until someone finds the body.

---

## M11 — The Phone
**Weight: L** · *Ten apps. It's a lot of UI, but it's all shallow.*

### Build
All ten apps from §4.11. Ordered by dependency:

1. **Save**, **Exit** — trivial, already have the backends
2. **Calendar** — reads the clock and known NPC birthdays and rent dates
3. **Relationships** — reads the relationship map, sortable list with portraits
4. **Diary** — text entry plus auto-logged life events
5. **Camera** — capture the frame buffer, write to gallery, also generate save thumbnails
6. **Missions** — soft objective list; each entry names its consequence for ignoring it
7. **Music** — track selection, world audio ducking to 30%
8. **Text** — emoji exchange at a distance, reusing the M7 generator with physical-need responses down-weighted
9. **Call** — summon an NPC, success gated on relationship and their current schedule slot

### Definition of Done
The phone opens and closes without pausing the world (verify: a need bar keeps draining while it's open). Take a photo, find it in the Extras gallery. Text an NPC across the island and get a generated emoji reply. Call a close friend at 14:00 and they come; call the same friend at 03:00 while they're asleep and they refuse.

---

## M12 — Vehicles & Transit
**Weight: M**

### Build
1. Bicycles: enter, ride, dismount, theft as a crime.
2. Cars: the constrained movement model from §5.10, road-sector restriction, collision with NPCs producing assault or kill.
3. Buses: fast travel between zone nodes for $5, 20-minute skip, with a short transition screen.
4. Subway: E ↔ H, $3, 10-minute skip.
5. Roadblocks at wanted level 4.

### Definition of Done
Ride a bicycle from one end of Zone E to the other measurably faster than walking. Steal a car, run a red-light-free island at speed, hit a pedestrian, get wanted +2. Take a bus to another zone and confirm the clock advanced 20 minutes and the destination zone's NPCs are in schedule-correct positions.

---

## M13 — Aging, Life Events & The Full Life Arc
**Weight: M** · *This is what turns a sandbox into a life.*

### Build
1. Birthdays and aging for the player and every NPC.
2. Education transitions and the truancy consequence for under-18s who skip school.
3. NPC death by age, with the knock-on effects: job opens, home frees, friends' mood drops, the funeral shows up on the Calendar.
4. Player natural death and the full GAME OVER life summary with real statistics.
5. Long-run stability: the world must survive decades of simulated time without the population collapsing or every job going unfilled.

### Definition of Done
Use debug time-skip to run 20 in-game years. At the end: the population is still functional, jobs are still staffed, NPCs have aged and some have died, the player has aged 20 years and their max health has begun declining. No crashes, no NaN needs, no negative money spirals, no orphaned relationships pointing at dead NPCs.

---

## M14 — Full Island Buildout & Polish
**Weight: XL** · *The long tail. Everything above was the game; this is the world.*

### Build
1. Author Zones A, B, C, D, F, G and H in the map editor, to the same standard as E.
2. Inter-zone transitions and the full transit network.
3. All remaining locations from §6.1.
4. Remaining sprite poses: sitting, panic, dead.
5. Festivals and seasonal events on the calendar — a beach festival in summer, a temple festival, a graveyard visit day. Each just changes NPC schedules and spawns decorations. Cheap, and they make the year feel like it turns.
6. Weather: rain and fog as simple full-screen overlays. Rain lowers everyone's mood slightly and pushes NPCs indoors. It's a two-hour feature that buys enormous atmosphere.
7. The Extras gallery, commentary, and the six splash screenshots.
8. All audio: 8 music tracks, full SFX set, diegetic location music.
9. Balance pass on every number in this document.

### Definition of Done
A new player starts a fresh game, plays for an hour without a crash, visits at least four zones, holds a job, makes a friend, and gets in trouble with the police at least once — entirely through systems, with no scripted content anywhere in the build.

---

## 9.1 Suggested ordering if you want something playable sooner

If you need a demo before M14, the shortest path to something that *feels like the real game* is:

**M0 → M1 → M2 → M3 → M4 → M5 → M6 → M7**

That gets you: a character you made, a living town, NPCs with jobs and schedules going about their day, and emoji conversations with all of them. That's already the pitch. M8–M13 make it a game you can lose; M14 makes it an island.

---

## 9.2 Traps to avoid

Things that will cost you weeks if you get them wrong.

| Trap | Avoid it by |
|---|---|
| Putting simulation in Lua | Lua is re-parsed every frame. Java for everything simulated. Lua only fires events. |
| Pathfinding over pixels | A* over the coarse nav graph only. Never per-cell. |
| Simulating all 340 NPCs | LOD is not optional. It's the reason the design works. |
| Real vehicle physics in a raycaster | Constrained speed/turn model. Nothing else. |
| Building all 8 zones before the systems work | Zone E only until M14. Authoring maps for systems you haven't written is wasted work. |
| Writing emoji responses by hand as special cases | The moment you hard-code one, the system's magic dies. Generate everything. |
| Retrofitting saves | M2. Early. Always. |
| Letting the phone or inventory pause the world | It kills tension and it hides simulation bugs you want to see. |
| Building the mystery/lore content | `[DEFERRED]`. It's great. It's not v1. |

---

# 10. Appendices

## Appendix A — Needs Table (full)

| Need | Decay/hr | Warning at | Critical at | Consequence at 0 | Restored by |
|---|---|---|---|---|---|
| Hunger | 4.2 | 30 | 15 | −5 health/hr | Eating (food items, cafe, supermarket, home kitchen) |
| Energy | 3.5 | 25 | 10 | Blackout: 6h skip, −10 health | Sleeping (bed +40/hr), sitting (+5/hr), coffee item (+15 instant) |
| Hygiene | 2.0 | 30 | 15 | −20 to all social interactions; venue denial | Shower (+50 instant), bath, swimming at beach (+25) |
| Social | 2.5 | 30 | 15 | Mood floor −40; −2 health/hr | Any emoji exchange (+8 to +20 by response class), being in a crowd (+2/hr) |
| Fun | 2.2 | 30 | 15 | Mood −25; skill gain halved | Arcade (+30), TV (+15/hr), music (+8/hr), camera (+5), concerts (+40) |
| Bladder | 5.0 | 25 | 10 | Public accident; hygiene→0; −10 friendship nearby; wanted +0.5 | Toilet (+100 instant) |

**Note:** these rates mean a fully-rested player needs to eat roughly every 24 in-game hours and sleep every 28 — which, at 24 minutes per day, is about one meal and one sleep per real-time half hour. That's the right pacing. Slower feels dead; faster feels like a chore.

## Appendix B — Action Table

| Action | Location types | Duration | Effect |
|---|---|---|---|
| Eat meal | Home, Cafe, Supermarket, Bar | 30 min | Hunger +45, costs $5–20 |
| Sleep | Home (bed) | Until 07:00 | Energy to 100, health +5 |
| Nap | Bench, Sofa | 2 hr | Energy +25 |
| Shower | Home, Resort, Gym | 15 min | Hygiene to 100 |
| Swim | Beach | Variable | Hygiene +25, Fun +15 |
| Toilet | Anywhere with one | 5 min | Bladder to 100 |
| Watch TV | Home, Bar | Variable | Fun +15/hr, Energy +3/hr |
| Play arcade | Arcade | 5 min | Fun +30, costs $1 |
| Work shift | Workplace | Job-defined | Wage paid, Energy −30, Fun −15, Social +10 |
| Attend school | ES/HS/UNI | 8 hr | Education progress, Social +20, Fun −20 |
| Socialize | Anywhere with an NPC | Per exchange | Social +8 to +20 |
| Attend concert | Concert Hall | 2 hr | Fun +40, costs $15 |
| Visit temple | Temple | 20 min | Mood +15 |
| Get haircut | Barber | 20 min | Appearance change, costs $12 |

## Appendix C — Emoji Seed Table (starter set)

Format: `RESPONSECLASS|INTENT = emoji:weight, ...`

The table below is a **starter**, covering the most common intents. Fill out the remaining `(class, intent)` combinations in the same shape during M7. Weights are relative within a row.

```
# GREET
ENTHUSIASTIC|GREET = 👋:35, 😄:30, 🤗:20, 🎉:15
POSITIVE|GREET     = 👋:45, 😊:30, 🙂:25
NEUTRAL|GREET      = 🙂:40, 👋:30, 😐:30
NEGATIVE|GREET     = 😒:45, 😐:30, 🙄:25
HOSTILE|GREET      = 😠:55, 🚪:25, 👊:20

# FRIENDLY
ENTHUSIASTIC|FRIENDLY = 😄:35, 🤝:30, 💬:20, 🎉:15
POSITIVE|FRIENDLY     = 😊:45, 🤝:30, 💬:25
NEUTRAL|FRIENDLY      = 🙂:45, 😐:35, 💬:20
NEGATIVE|FRIENDLY     = 😒:50, 🙄:30, 🚪:20
HOSTILE|FRIENDLY      = 😠:60, 🚪:40

# FLIRT
ENTHUSIASTIC|FLIRT = ❤️:40, 😍:30, 😳:20, 💐:10
POSITIVE|FLIRT     = 😊:35, 😳:30, ❤️:20, 🙂:15
NEUTRAL|FLIRT      = 😅:40, 😐:35, 🤔:25
NEGATIVE|FLIRT     = 😒:40, 🙄:30, 🚪:30
HOSTILE|FLIRT      = 😠:45, 🤮:30, 👊:25

# JOKE
ENTHUSIASTIC|JOKE = 😂:50, 🤣:30, 👏:20
POSITIVE|JOKE     = 😄:45, 🙂:30, 😅:25
NEUTRAL|JOKE      = 😅:40, 🙂:30, 😐:30
NEGATIVE|JOKE     = 😐:45, 🙄:35, 😒:20
HOSTILE|JOKE      = 😠:50, 🙄:30, 🚪:20

# INSULT
ENTHUSIASTIC|INSULT = 😂:60, 😏:40          # they think you're joking
POSITIVE|INSULT     = 😅:50, 🙄:30, 😐:20
NEUTRAL|INSULT      = 😐:40, 🙄:35, 😒:25
NEGATIVE|INSULT     = 😠:50, 😤:30, 🚪:20
HOSTILE|INSULT      = 😡:45, 👊:35, 🔪:20

# REQUEST
ENTHUSIASTIC|REQUEST = 👍:45, 🤝:35, 😄:20
POSITIVE|REQUEST     = 👍:50, 🙂:30, 🤝:20
NEUTRAL|REQUEST      = 🤔:45, 😐:35, 🤷:20
NEGATIVE|REQUEST     = 🤷:40, 👎:35, 🚪:25
HOSTILE|REQUEST      = 👎:50, 😠:30, 🚪:20

# GIFT
ENTHUSIASTIC|GIFT = 🤩:40, 🙏:30, 🎉:20, ❤️:10
POSITIVE|GIFT     = 😊:45, 🙏:35, 👍:20
NEUTRAL|GIFT      = 🙂:40, 🤔:35, 👍:25
NEGATIVE|GIFT     = 🤨:45, 👎:35, 🙄:20
HOSTILE|GIFT      = 👎:50, 😠:30, 🚪:20

# THREATEN
ENTHUSIASTIC|THREATEN = 😂:55, 😏:45         # unimpressed and amused
POSITIVE|THREATEN     = 😅:50, 🤨:30, 🙄:20
NEUTRAL|THREATEN      = 😨:40, 🤨:35, 😐:25
NEGATIVE|THREATEN     = 😰:45, 🏃:35, 😱:20
HOSTILE|THREATEN      = 😡:40, 👊:35, 🚨:25   # 🚨 = calling the police
```

**Intent base values** (added to the response score before class selection):

| Intent | Base | Notes |
|---|---|---|
| GREET | +5 | Low risk, low reward |
| FRIENDLY | +8 | |
| FLIRT | −5 | Harder; scales hard on existing romance |
| JOKE | +3 | Amplified by the NPC's mood — funny people are in a good mood |
| ASK | 0 | |
| REQUEST | −10 | Asking for things costs goodwill |
| TRADE | +2 | Modified by the money offered |
| GIFT | +12 | Modified by item value: `+item.price / 10`, capped +25 |
| SUMMON | −8 | |
| INSULT | −35 | |
| THREATEN | −45 | Modified by whether the player is visibly armed |
| LEAVE | 0 | Always ends cleanly |

## Appendix D — Relationship Thresholds

| `knownAs` | Condition |
|---|---|
| STRANGER | Never interacted |
| ACQUAINTANCE | friendship 1–29 |
| FRIEND | friendship 30–59 |
| CLOSE_FRIEND | friendship 60–100 |
| PARTNER | romance ≥ 60 AND friendship ≥ 40 |
| FAMILY | Set at worldgen, never changes |
| ENEMY | friendship ≤ −30 |

Gating rules:
- Romance can only increase if friendship ≥ 40.
- Romance decays 1/day if you don't interact.
- Friendship decays 0.5/day past 7 days of no contact, down to a floor of 10 for FAMILY.
- PARTNER status unlocks moving in together; two incomes, shared rent.

## Appendix E — Population Age Distribution

Roll at worldgen. Target population 340.

| Age band | Share | Count |
|---|---|---|
| 0–5 | 6% | 20 |
| 6–11 | 8% | 27 |
| 12–17 | 9% | 31 |
| 18–24 | 13% | 44 |
| 25–39 | 22% | 75 |
| 40–54 | 20% | 68 |
| 55–69 | 14% | 48 |
| 70+ | 8% | 27 |

## Appendix F — Location Code Reference

| Code | Location | Zone |
|---|---|---|
| BCH | Beach | B |
| BB | Beach Bungalows | B |
| BH | Beach Resort / Hotel | B |
| D | Dock | B |
| T | Temple | A |
| L | Lighthouse | A |
| R | Ranch | C |
| C | Cabin in the Woods | C |
| MT | Mountain | D |
| LH | Lover's Hill | D |
| GY | Graveyard | G |
| HOS | Hospital | E |
| ES | Elementary School | E |
| HS | High School | E |
| UNI | University | E |
| PK | Park | E |
| SM | Supermarket | E |
| AIR | Airport | E |
| CN | Cinema | E |
| PS | Police Station | E |
| BR | Barber | E |
| CAF | Cafe | E |
| LIB | Library | E |
| ART | Art Gallery | E |
| ARC | Arcade | E |
| FS | Fire Station | E |
| SAM | Stadium | E |
| BAR | Bar | H |
| STR | Strip Club | H |
| CRT | Concert Hall | H |
| JH | Jailhouse | H |
| H# | Houses | E, H, B, C |
| RU | Ruins | `[DEFERRED]` |
| UB | Underground Bunker | `[DEFERRED]` |

## Appendix G — Deferred Content Register

Recorded so it isn't lost, explicitly out of scope for v1:

- The schizophrenia thread
- The Devil Goat high school girl / corrupted priest conspiracy
- Zombie Tobacco
- The Angel Beats afterlife reference — MC turning himself into an NPC
- The underground computer lab where the programmer exists
- The "25 mystery"
- Ghosts / Shadows / Shiki-style town presence
- Ruins (RU) and Underground Bunker (UB) locations

**When to revisit:** after M14 ships and the systems are stable. Every one of these is a *content* layer on top of working simulation — which means none of them get cheaper by being built early, and all of them get more expensive.

---

*Aya Island — Design Spec v1.0. Build M0 first.*
