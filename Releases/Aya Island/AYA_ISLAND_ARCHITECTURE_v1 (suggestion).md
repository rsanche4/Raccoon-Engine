# AYA ISLAND — Technical Architecture

**Version 1.0 — companion to `AYA_ISLAND_SPEC_v1.md`**
**Target:** Java 21, Raccoon Engine V2, LuaJ 3.0.2

---

## 0. How This Document Relates To The Spec

The spec answers *what the game is*. This answers *what the code is*.

- **§1–§3** — the layering, the package map, and the one rule that keeps this from turning to mud.
- **§4–§11** — every class you will write, with method signatures, grouped by package.
- **§12** — call-flow traces: the exact sequence of calls for the seven flows that matter.
- **§13** — the dependency matrix. Who is allowed to call whom.
- **§14** — milestone → class map. Which classes get built in which milestone.
- **§15** — timeline.

Signatures here are **contracts, not suggestions**. If you change one, change it here first — this file is the source of truth for interfaces, and a spec that drifts from the code is worse than no spec.

---

## 1. The Five Layers

```
┌───────────────────────────────────────────────────────────────┐
│  L5  PRESENTATION      aya.ui, aya.render, aya.debug          │
│      Reads snapshots. Writes nothing. Publishes intents.      │
├───────────────────────────────────────────────────────────────┤
│  L4  SIMULATION        aya.sim.*                              │
│      The only layer allowed to mutate WorldState.             │
├───────────────────────────────────────────────────────────────┤
│  L3  WORLD & DATA      aya.world, aya.data, aya.persist       │
│      Static definitions, worldgen, serialization.             │
├───────────────────────────────────────────────────────────────┤
│  L2  STATE             aya.state                              │
│      Pure data. Zero logic. Zero dependencies.                │
├───────────────────────────────────────────────────────────────┤
│  L1  CORE              aya.core, aya.event                    │
│      Loop, config, services, event bus.                       │
└───────────────────────────────────────────────────────────────┘
              ↓ depends on ↓   (never upward)

┌───────────────────────────────────────────────────────────────┐
│  L0  ENGINE            raccoon.*      ← DO NOT MODIFY         │
└───────────────────────────────────────────────────────────────┘
```

### 1.1 The one rule

> **Only `aya.sim.*` mutates `WorldState`. Everything else reads a snapshot.**

UI does not set `player.money`. UI publishes a `PurchaseRequest` event; `EconomySystem` handles it on the next tick and mutates state. This feels like ceremony for the first week and then saves you every time a bug appears, because there is exactly one place a value can change.

Corollary: **the renderer can never cause a simulation bug**, because it physically cannot write. If the sim is wrong, the sim is wrong, and you know where to look.

### 1.2 Why the engine is L0 and untouchable

The Raccoon Engine is a separate project with its own git history. Aya Island is a *consumer* of it. If you find yourself editing `raccoon.*` to make a game feature work, stop — either it belongs in `aya.render` as an adapter, or it's a genuine engine feature and should be committed to the engine repo on its own merits.

The one exception: implementing the stubbed `RaccoonAPI` methods (audio, save-state, UI drawing). Those are engine gaps, not game features. Fill them in the engine repo.

---

## 2. Package Map

```
aya/
├── core/
│   ├── AyaGame.java                  // top-level object, owns everything
│   ├── SimulationLoop.java           // fixed timestep accumulator
│   ├── SimulationPipeline.java       // ordered system registry
│   ├── SimSystem.java                // interface every system implements
│   ├── TickContext.java              // per-tick immutable context
│   ├── Config.java                   // aya.cfg loader
│   ├── Services.java                 // service locator
│   └── RandomSource.java             // seeded, deterministic RNG
│
├── event/
│   ├── EventBus.java
│   ├── GameEvent.java                // marker interface
│   └── events/                       // ~25 small event records
│
├── state/
│   ├── WorldState.java
│   ├── Player.java
│   ├── NPC.java
│   ├── Actor.java                    // interface: Player and NPC share it
│   ├── NeedsVector.java
│   ├── PersonalityVector.java
│   ├── Relationship.java
│   ├── GameClock.java
│   ├── ScheduleSlot.java
│   ├── Position.java
│   ├── ItemStack.java
│   ├── EconomyState.java
│   ├── WantedState.java
│   └── enums/                        // NeedType, Intent, ResponseClass, SimLevel, ...
│
├── data/
│   ├── DataRegistry.java
│   ├── TextTableParser.java
│   ├── defs/                         // ItemDef, JobDef, ActionDef, LocationDef
│   └── NeedsConfig.java
│
├── world/
│   ├── WorldGenerator.java
│   ├── LocationRegistry.java
│   ├── ZoneManager.java
│   ├── NavGraph.java
│   ├── NavNode.java
│   ├── Pathfinder.java
│   └── NavPath.java
│
├── sim/
│   ├── needs/     NeedsSystem, MoodCalculator
│   ├── npc/       LodSystem, NpcActionSystem, ScheduleSystem, NpcSpawner
│   ├── social/    SocialSystem, EmojiGenerator, EmojiCorpus,
│   │              TransitionTable, ResponseScorer, ScoreBreakdown,
│   │              EmojiResponse, RelationshipSystem, BarkSystem
│   ├── economy/   EconomySystem, JobSystem, ShopSystem, HousingSystem
│   ├── crime/     CrimeSystem, WantedSystem, PoliceSystem, WitnessDetector
│   ├── life/      AgingSystem, LifeEventSystem, DeathSystem
│   └── player/    PlayerControlSystem, InteractionSystem, InventorySystem
│
├── persist/
│   ├── SaveManager.java
│   ├── SaveWriter.java
│   ├── SaveReader.java
│   └── SaveSlotInfo.java
│
├── ui/
│   ├── Screen.java
│   ├── ScreenManager.java
│   ├── WorldSnapshot.java            // the read-only bridge
│   ├── InputState.java
│   ├── screens/                      // 12 screen classes
│   ├── phone/                        // PhoneScreen + 10 PhoneApp classes
│   └── widgets/                      // Hud, EmojiWheel, NeedBar, SpeechBubble
│
├── render/
│   ├── WorldRenderBridge.java        // aya → raccoon adapter
│   ├── NpcSpriteBinder.java
│   ├── SpriteCache.java
│   └── AudioDirector.java
│
└── debug/
    ├── DebugTools.java
    ├── DebugConsole.java
    ├── DebugOverlay.java
    └── commands/
```

---

## 3. Threading Model

Three threads, and only three.

| Thread | Owns | Notes |
|---|---|---|
| **Main / Render** | The engine's game loop, all rendering, all input | Calls `AyaGame.onEngineFrame()` |
| **Simulation** | `WorldState` mutation | Runs `SimulationPipeline.tick()`. Never touches the canvas. |
| **Engine raster workers** | Column rendering | Already exist in the engine. Don't add to them. |

**The handoff.** At the end of each sim tick, the simulation thread builds a fresh `WorldSnapshot` and publishes it via an `AtomicReference`. The render thread reads the latest snapshot every frame and interpolates positions with `SimulationLoop.interpolationAlpha()`. No locks in the render path.

```java
// simulation thread, end of tick
snapshotRef.set(WorldSnapshot.capture(world, 1.0f));

// render thread, every frame
WorldSnapshot snap = snapshotRef.get();   // always a complete, consistent snapshot
screens.render(canvas, snap);
```

If you take one thing from this section: **snapshots are immutable and whole**. Never hand the UI a live reference to `WorldState`. The moment you do, you'll spend a weekend chasing a flicker caused by an NPC dying halfway through a frame.

---

## 4. `aya.core`

### 4.1 `AyaGame`

The root object. Created once at boot, owns everything, shuts everything down.

```java
public final class AyaGame {
    public AyaGame(RaccoonEngine engine, Path configPath);

    public void boot();                                 // loads config+data, pushes SplashScreen
    public void shutdown();                             // saves config, joins sim thread
    public void onEngineFrame(float deltaSeconds);      // engine calls this every frame

    public Services       services();
    public ScreenManager  screens();
    public SimulationLoop loop();

    public void startNewGame(BackstoryData backstory, long seed, ProgressSink progress);
    public void loadGame(int slot);
    public void endGame(DeathCause cause);
}
```

`onEngineFrame` is the entire per-frame contract:

```java
public void onEngineFrame(float dt) {
    InputState in = InputState.poll(engine);
    screens.update(dt, in);
    loop.setPaused(screens.anyPausesSimulation());
    loop.accumulate(dt);                       // sim thread picks up pending ticks
    WorldSnapshot snap = snapshotRef.get();
    renderBridge.submitWorld(snap);            // hands sprites to the engine
    screens.render(engine.canvas(), snap);
}
```

### 4.2 `SimulationLoop`

```java
public final class SimulationLoop {
    public SimulationLoop(int tickHz, SimulationPipeline pipeline, RandomSource rng);

    public void  start(WorldState world);
    public void  stop();
    public void  accumulate(float deltaSeconds);
    public float interpolationAlpha();              // 0..1, for render smoothing
    public void  setPaused(boolean paused);
    public boolean isPaused();
    public long  tickIndex();

    /** Time skips: sleep, work shift, jail, bus ride. Runs the pipeline in coarse mode. */
    public void  runTimeSkip(WorldState world, int inGameMinutes, TimeSkipReason reason);
    public void  setSpeedMultiplier(float multiplier);   // debug only, 1.0 normal
}
```

**`runTimeSkip` is important and easy to get wrong.** It must not run 480 individual ticks to sleep eight hours. It calls `SimSystem.onCoarseTick()` once per system with the elapsed minutes, and each system does its own bulk math. Needs fast-forward in one multiply; schedules jump straight to the destination slot; the economy fires any day boundaries that were crossed.

### 4.3 `SimSystem` and `SimulationPipeline`

Every simulation behavior is a `SimSystem`. This is the single most important interface in the codebase.

```java
public interface SimSystem {
    String id();

    /** Normal 10 Hz tick. */
    void onTick(WorldState world, TickContext ctx);

    /** Bulk update over a time skip. Default: run onTick once. Override for correctness. */
    default void onCoarseTick(WorldState world, TickContext ctx, int minutesSkipped) {
        onTick(world, ctx);
    }

    /** Fired once when the calendar day rolls over. */
    default void onDayBoundary(WorldState world, TickContext ctx) {}

    /** Fired when a world is loaded or generated, before the first tick. */
    default void onWorldReady(WorldState world, Services services) {}
}
```

```java
public final class SimulationPipeline {
    public void register(SimSystem system, int order);   // lower order runs first
    public void tick(WorldState world, TickContext ctx);
    public void coarseTick(WorldState world, TickContext ctx, int minutes);
    public void worldReady(WorldState world, Services services);
    public SimSystem byId(String id);
    public List<SimSystem> inOrder();
}
```

### 4.4 The registration order (memorize this)

Order matters enormously. This is the canonical registration:

| Order | System | Why here |
|---|---|---|
| 10 | `ClockSystem` | Everything downstream reads the new time |
| 20 | `NeedsSystem` | Decay before anyone decides what to do |
| 25 | `MoodCalculator` (invoked by NeedsSystem, not registered) | Mood derives from fresh needs |
| 30 | `LodSystem` | Promote/demote before anyone is simulated |
| 40 | `ScheduleSystem` | Slot transitions before action selection |
| 50 | `NpcActionSystem` | NPCs decide and move |
| 60 | `PlayerControlSystem` | Player movement and collision |
| 70 | `InteractionSystem` | Resolve pending interaction requests |
| 80 | `SocialSystem` | Emoji exchanges and barks |
| 90 | `CrimeSystem` | Witness detection needs final positions |
| 100 | `WantedSystem` | Consumes crimes reported at 90 |
| 110 | `PoliceSystem` | Reacts to the wanted level set at 100 |
| 120 | `EconomySystem` | Day-boundary money movement |
| 130 | `JobSystem` | Shift completion, firing |
| 140 | `HousingSystem` | Rent, eviction |
| 150 | `AgingSystem` | Birthdays |
| 160 | `LifeEventSystem` | Consequences of everything above |
| 170 | `DeathSystem` | Last, so a death this tick is fully consistent |
| 999 | `EventBus.drain()` | Not a system; called by the pipeline at the end |

**The rule behind the ordering:** a system may only depend on data written by a system with a *lower* order number. If you need to reverse a dependency, you have a design problem, not an ordering problem.

### 4.5 `TickContext`

Immutable, constructed fresh each tick, passed to every system.

```java
public final class TickContext {
    public final long       tickIndex;
    public final float      dtMinutes;        // in-game minutes this tick (0.1667 at 10 Hz)
    public final GameClock  clockBefore;
    public final GameClock  clockAfter;
    public final boolean    minuteChanged;
    public final boolean    hourChanged;
    public final boolean    phaseChanged;
    public final boolean    dayChanged;
    public final boolean    seasonChanged;
    public final boolean    isCoarse;         // true during a time skip
    public final EventBus   events;
    public final RandomSource rng;
    public final Services   services;
}
```

The `*Changed` booleans exist so systems can do `if (ctx.hourChanged) {...}` instead of comparing clocks themselves. Compute them once, in `ClockSystem`, use them everywhere.

### 4.6 `Config` and `RandomSource`

```java
public final class Config {
    public static Config load(Path file);
    public int     getInt(String key, int def);
    public float   getFloat(String key, float def);
    public boolean getBool(String key, boolean def);
    public String  getString(String key, String def);
    public void    set(String key, String value);
    public void    save(Path file);
    public void    reload();                  // debug hot reload
}
```

```java
/** Deterministic, seedable, and splittable so subsystems don't interfere. */
public final class RandomSource {
    public RandomSource(long seed);
    public RandomSource split(String subsystemName);   // derives a child stream
    public int     nextInt(int boundExclusive);
    public int     nextIntRange(int minInclusive, int maxInclusive);
    public float   nextFloat();
    public float   nextFloatRange(float min, float max);
    public boolean chance(float probability0to1);
    public <T> T   pick(List<T> options);
    public <T> T   pickWeighted(List<T> options, float[] weights);
    public long    seed();
}
```

**`split()` matters more than it looks.** If worldgen and the emoji generator draw from the same stream, then generating one extra emoji changes which NPC gets which name. Split per subsystem and your seeds stay reproducible forever.

### 4.7 `Services`

```java
public final class Services {
    public Config          config();
    public EventBus        events();
    public DataRegistry    data();
    public WorldState      world();
    public void            setWorld(WorldState w);
    public LocationRegistry locations();
    public NavGraph        navGraph();
    public SpriteCache     sprites();
    public SaveManager     saves();
    public AudioDirector   audio();
    public DebugTools      debug();
    public RandomSource    rng();
}
```

A service locator rather than full dependency injection, deliberately: you're one person, and constructor injection across 60 classes is a tax with no payer.

---

## 5. `aya.event`

```java
public interface GameEvent {
    default String type() { return getClass().getSimpleName(); }
}

public final class EventBus {
    public <E extends GameEvent> void subscribe(Class<E> type, Consumer<E> handler);
    public <E extends GameEvent> void unsubscribe(Class<E> type, Consumer<E> handler);

    public void publish(GameEvent event);           // queued, delivered at drain()
    public void publishImmediate(GameEvent event);  // synchronous — use sparingly
    public void drain();                            // called at end of pipeline
    public int  queueDepth();
    public void setTracing(boolean on);             // debug: log every event
}
```

### 5.1 The event catalogue

Java records. Small, immutable, no behavior.

```java
// --- clock
record HourChangedEvent(int hour) implements GameEvent {}
record DayBoundaryEvent(int day, Season season, int year) implements GameEvent {}
record SeasonChangedEvent(Season from, Season to) implements GameEvent {}

// --- needs & health
record NeedWarningEvent(int actorId, NeedType need, float value) implements GameEvent {}
record NeedCriticalEvent(int actorId, NeedType need) implements GameEvent {}
record NeedZeroConsequenceEvent(int actorId, NeedType need, String consequence) implements GameEvent {}
record HealthChangedEvent(int actorId, int from, int to, String cause) implements GameEvent {}

// --- npc lifecycle
record LodChangedEvent(int npcId, SimLevel from, SimLevel to) implements GameEvent {}
record ScheduleSlotChangedEvent(int npcId, ScheduleSlot from, ScheduleSlot to) implements GameEvent {}
record NpcDiedEvent(int npcId, DeathCause cause) implements GameEvent {}
record BirthdayEvent(int actorId, int newAge) implements GameEvent {}

// --- social
record EmojiExchangeEvent(int npcId, Intent intent, ResponseClass cls, List<String> reply) implements GameEvent {}
record RelationshipChangedEvent(int npcId, int friendshipDelta, int romanceDelta) implements GameEvent {}
record BarkEvent(int npcId, String emoji) implements GameEvent {}

// --- economy
record TransactionEvent(int actorId, int delta, TransactionReason reason) implements GameEvent {}
record JobHiredEvent(int actorId, String jobId) implements GameEvent {}
record JobLostEvent(int actorId, String jobId, FireReason reason) implements GameEvent {}
record ShiftCompletedEvent(int actorId, String jobId, int wage) implements GameEvent {}
record RentDueEvent(int actorId, int amount) implements GameEvent {}
record EvictionEvent(int actorId, String locationId) implements GameEvent {}

// --- crime
record CrimeCommittedEvent(int perpId, CrimeType type, Position at, int witnessCount) implements GameEvent {}
record WantedLevelChangedEvent(int from, int to) implements GameEvent {}
record ArrestEvent(int actorId, int sentenceDays) implements GameEvent {}
record BodyDiscoveredEvent(int victimId, int discovererId) implements GameEvent {}

// --- player intents (published by UI, consumed by sim)
record InteractionRequest(int targetId, InteractionKind kind) implements GameEvent {}
record EmojiIntentRequest(int npcId, Intent intent, String offeredItemId) implements GameEvent {}
record PurchaseRequest(String itemId, int quantity, String locationId) implements GameEvent {}
record JobApplicationRequest(String jobId) implements GameEvent {}
record TravelRequest(String transitNodeId, TransitMode mode) implements GameEvent {}
record UseItemRequest(int inventorySlot) implements GameEvent {}
record PhotoTakenEvent(String filename) implements GameEvent {}
```

**The `*Request` events are the UI→sim channel.** UI never calls a system directly. It publishes a request; the relevant system picks it up at its pipeline slot. That's how the "only sim mutates state" rule is enforced in practice.

---

## 6. `aya.state` — Pure Data

**Zero logic.** The only methods allowed are trivial accessors, clamped setters, and derivations that are pure functions of the object's own fields. If a method needs to look at another object, it belongs in `aya.sim`.

### 6.1 `Actor` — the shared interface

Player and NPC share enough that duplicating the systems would be a mistake.

```java
public interface Actor {
    int             id();
    String          name();
    int             age();
    int             health();
    void            setHealth(int h);
    int             money();
    void            setMoney(int m);
    NeedsVector     needs();
    PersonalityVector personality();
    Position        position();
    JobRef          job();
    void            setJob(JobRef j);
    LocationRef     home();
    void            setHome(LocationRef l);
    boolean         isAlive();
    boolean         isPlayer();
}
```

`NeedsSystem`, `EconomySystem`, `JobSystem`, `AgingSystem` and `DeathSystem` all operate on `Actor`. That's five systems you write once instead of twice.

### 6.2 `NeedsVector`

```java
public final class NeedsVector {
    public float hunger, energy, hygiene, social, fun, bladder;

    public float       get(NeedType t);
    public void        set(NeedType t, float value);          // clamps 0..100
    public void        add(NeedType t, float delta);          // clamps 0..100
    public NeedType    lowest();
    public float       lowestValue();
    public float       average();
    public boolean     anyBelow(float threshold);
    public List<NeedType> below(float threshold);
    public NeedsVector copy();
    public void        copyFrom(NeedsVector other);
}
```

### 6.3 `GameClock`

```java
public final class GameClock {
    public int    minute, hour, day, year;
    public Season season;

    public Phase   phase();                         // derived, never stored
    public long    totalMinutes();                  // absolute, for diffing
    public int     dayOfWeekIndex();                // 0..6
    public boolean isRentDay();                     // day in {1, 8, 15, 22}
    public void    advance(int minutes);
    public GameClock copy();

    public static int minutesBetween(GameClock a, GameClock b);
    public static float hoursBetween(GameClock a, GameClock b);
    public static GameClock atStart();              // SPRING 01, year 1, 08:00
}
```

### 6.4 `WorldState`

```java
public final class WorldState {
    public long          seed;
    public GameClock     clock;
    public Player        player;
    public NPC[]         npcs;
    public EconomyState  economy;
    public WantedState   wanted;
    public Zone          currentZone;
    public Map<String,Object> flags;
    public List<DiaryEntry>   diary;
    public long          playtimeSeconds;

    public NPC           npc(int id);
    public List<NPC>     npcsAtLevel(SimLevel level);
    public List<NPC>     npcsNear(Position p, float radius);
    public List<NPC>     npcsAtLocation(String locationId);
    public Actor         actor(int id);             // id 0 == player
    public int           countAlive();
}
```

### 6.5 `NPC`

```java
public final class NPC implements Actor {
    public int       id;
    public String    name;
    public int       age;
    public int       health;
    public int       money;
    public String    spriteId;
    public SimLevel  simLevel;
    public NeedsVector       needs;
    public PersonalityVector personality;
    public float     mood;                  // derived each tick, stored for the renderer
    public float     situationalMood;       // decaying modifier
    public JobRef    job;
    public LocationRef home;
    public ScheduleSlot[] schedule;         // exactly 8
    public ActionState   action;
    public Position  position;
    public Map<Integer,Relationship> relationships;
    public ItemStack[] inventory;           // 4 slots
    public boolean   alive;
    public int       missedShifts;
    public long      lastBarkMinute;
    public long      lastInteractionMinute;
    public GameClock lastSimulatedAt;       // for LOD fast-forward
}
```

```java
public final class ActionState {
    public ActionPhase phase;      // IDLE, PATHING, PERFORMING, REACTING
    public String      actionId;
    public NavPath     path;
    public float       remainingMinutes;
    public ReactionCause reactionCause;
    public int         reactionTargetId;
}
```

### 6.6 `Relationship`

```java
public final class Relationship {
    public int       friendship;     // -100..100
    public int       romance;        // 0..100
    public long      lastInteractedMinute;
    public boolean   isFamily;
    public int       exchangeCount;

    public RelationshipTier tier();      // derived — see spec Appendix D
    public boolean   canRomance();       // friendship >= 40
}
```

---

## 7. `aya.data` — Static Definitions

Everything here is loaded once from plain text at boot and never mutated.

### 7.1 `DataRegistry`

```java
public final class DataRegistry {
    public static DataRegistry loadAll(Path dataDir, ProgressSink progress);

    public ItemDef      item(String id);
    public JobDef       job(String id);
    public ActionDef    action(String id);
    public LocationDef  location(String id);
    public List<JobDef> jobsAt(String locationId);
    public List<ItemDef> itemsInCategory(ItemCategory c);
    public NeedsConfig  needs();
    public EmojiCorpus  emoji();
    public NamePool     names();
    public CrimeTable   crimes();

    public void reload(String which);          // "items" | "jobs" | "emoji" | "all"
    public List<String> validate();            // returns human-readable problems
}
```

**`validate()` earns its keep.** Call it at boot and log the results. It catches: a `JobDef` pointing at a nonexistent location, an `ActionDef` restoring a need that doesn't exist, an emoji seed pool referencing an emoji absent from the vocabulary. Every one of those is a silent runtime bug otherwise.

### 7.2 The definition records

```java
public record ItemDef(
    String id, String displayName, String description,
    int price, ItemCategory category,
    List<ItemVerb> verbs,
    Map<NeedType,Float> needsRestored,
    boolean isWeapon, int damage,
    String spriteAsset
) {}

public record JobDef(
    String id, String title, String locationId,
    int wagePerShift, int shiftStartHour, int shiftEndHour,
    EducationLevel requiredEducation, int minAge, int maxAge,
    SkillType requiredSkill, int requiredSkillLevel,
    boolean isCriminal, float wantedRiskPerShift,
    int slots                                   // how many NPCs+player can hold it
) {}

public record ActionDef(
    String id, String displayName,
    List<LocationType> validLocationTypes,
    int durationMinutes,
    Map<NeedType,Float> needsRestored,          // per hour, or instant if duration==0
    int cost,
    Pose pose,
    boolean advancesTimeBySkip
) {}

public record LocationDef(
    String id, String displayName, Zone zone, LocationType type,
    int[] sectorIds, Position entrance,
    int openHour, int closeHour,
    List<NeedType> needsServed,
    String transitNodeId,
    int residentCapacity
) {}

public record NeedsConfig(
    Map<NeedType,Float> decayPerHour,
    Map<NeedType,Float> warningThreshold,
    Map<NeedType,Float> criticalThreshold
) {}
```

### 7.3 `TextTableParser`

One parser, used by every data file. Keeps the format uniform so you only ever learn it once.

```java
public final class TextTableParser {
    /** Parses a `# comment` / `[SECTION]` / `key = a:1, b:2` text file. */
    public static Map<String,List<Map<String,String>>> parse(Path file);
    public static List<String[]> parseRows(Path file, String sectionName);
    public static Map<String,Float> parseWeightedPool(String value);   // "😊:40, 👋:30"
}
```

---

## 8. `aya.world`

### 8.1 `WorldGenerator`

```java
public final class WorldGenerator {
    public static WorldState generate(long seed,
                                      BackstoryData backstory,
                                      DataRegistry data,
                                      LocationRegistry locations,
                                      SpriteCache sprites,
                                      ProgressSink progress);

    static List<NPC> generatePopulation(RandomSource rng, DataRegistry d, int count);
    static NPC       generateNpc(int id, int age, RandomSource rng, DataRegistry d);
    static void      buildFamilies(List<NPC> npcs, RandomSource rng);
    static void      assignHomes(List<NPC> npcs, LocationRegistry locs, RandomSource rng);
    static void      assignJobs(List<NPC> npcs, JobPool pool, LocationRegistry locs, RandomSource rng);
    static void      seedRelationships(List<NPC> npcs, RandomSource rng);
    static void      generateAllSchedules(List<NPC> npcs, DataRegistry d, RandomSource rng);
    static Player    createPlayer(BackstoryData b, RandomSource rng, LocationRegistry locs);
}

public interface ProgressSink {
    void step(String label, float fraction);     // drives the WORLDGEN_LOADING screen
}
```

**Determinism contract:** `generate(12345, sameBackstory, ...)` must produce a byte-identical save every time. Achieve it by splitting the RNG per stage:

```java
RandomSource root  = new RandomSource(seed);
RandomSource pop   = root.split("population");
RandomSource fam   = root.split("families");
RandomSource homes = root.split("homes");
RandomSource jobs  = root.split("jobs");
RandomSource rel   = root.split("relationships");
```

Now adding a stage, or changing how many numbers a stage draws, doesn't disturb the others.

### 8.2 `LocationRegistry`

```java
public final class LocationRegistry {
    public Location       byId(String id);
    public List<Location> inZone(Zone z);
    public List<Location> ofType(LocationType t);
    public List<Location> servingNeed(NeedType t);
    public Location       containing(Position p);
    public Location       nearest(Position p, LocationType type);
    public boolean        isOpen(Location l, GameClock clock);
    public List<Location> residencesWithVacancy(WorldState w);
}
```

### 8.3 Navigation

```java
public final class NavGraph {
    public static NavGraph loadForZone(Zone z, Path mapDir);

    public NavNode        node(int id);
    public NavNode        nearest(Position p);
    public List<NavNode>  neighbors(NavNode n);
    public float          cost(NavNode a, NavNode b);
    public NavNode        entranceOf(String locationId);
    public int            nodeCount();
    public List<Integer>  findOrphans();          // debug: unreachable nodes
}

public record NavNode(int id, Position pos, String locationId, NavNodeType type) {}

public final class Pathfinder {
    public static NavPath find(NavGraph g, NavNode start, NavNode goal);
    public static NavPath findToLocation(NavGraph g, Position from, String locationId);
    public static NavPath findToNearest(NavGraph g, Position from, LocationType type);
    public static int     lastNodesExpanded();    // profiling hook
}

public final class NavPath {
    public NavNode  currentTarget();
    public void     advance();
    public boolean  isComplete();
    public float    remainingDistance();
    public int      length();
}
```

**Budget:** A* over the nav graph should expand under 200 nodes for any island-scale path. If `lastNodesExpanded()` is regularly above that, your graph is too dense — the nodes are for doorways, junctions and points of interest, not for floor tiles.

### 8.4 `ZoneManager`

```java
public final class ZoneManager {
    public Zone     current();
    public boolean  isLoading();
    public void     requestTransition(Zone target, TransitMode via, WorldState w);
    public void     onZoneLoaded(Zone z, WorldState w, NavGraph graph);
    public float    loadProgress();
    public List<String> transitNodesIn(Zone z);
}
```

Zone transition is: demote every NPC in the old zone to DORMANT → unload the old map → load the new map and nav graph → promote NPCs in the new zone from DORMANT (fast-forwarding their needs) → place the player at the arrival node.

---

## 9. `aya.sim` — The Systems

### 9.1 `NeedsSystem`

```java
public final class NeedsSystem implements SimSystem {
    public NeedsSystem(NeedsConfig config, EventBus bus);

    public String id() { return "needs"; }
    public void onTick(WorldState w, TickContext ctx);
    public void onCoarseTick(WorldState w, TickContext ctx, int minutesSkipped);

    /** Pure. Used by the tick path, the LOD fast-forward, and the time skip alike. */
    public static void decay(NeedsVector needs, float hours, NeedsConfig cfg, float multiplier);

    public void  applyRestoration(NeedsVector needs, ActionDef action, float hours);
    public float decayMultiplierFor(Actor a, WorldState w);   // homeless = 1.5x energy, 2x hygiene
    private void checkThresholds(Actor a, TickContext ctx);
    private void fireZeroConsequence(Actor a, NeedType need, WorldState w, TickContext ctx);
}
```

`decay` is `static` and pure on purpose: it's called from three very different places and must produce identical results in all of them. A hungry NPC you walked away from for two hours and a hungry NPC you watched for two hours must arrive at the same number.

### 9.2 `MoodCalculator`

```java
public final class MoodCalculator {
    public static float compute(NeedsVector needs, PersonalityVector p, float situational);
    public static float decaySituational(float current, float hours);
    public static void  applyEvent(NPC npc, float delta, float capMagnitude);   // ±30 max
    public static MoodTier tier(float mood);         // MISERABLE..ELATED, for expressions
}
```

### 9.3 `LodSystem`

```java
public final class LodSystem implements SimSystem {
    public LodSystem(float activeRadius, int activeCap, EventBus bus, NeedsConfig needsCfg);

    public String id() { return "lod"; }
    public void onTick(WorldState w, TickContext ctx);

    public SimLevel evaluate(NPC npc, Player player, WorldState w);
    public void     promote(NPC npc, SimLevel to, WorldState w, TickContext ctx);
    public void     demote(NPC npc, SimLevel to, WorldState w, TickContext ctx);

    /** The core of the whole performance story. */
    public void     fastForward(NPC npc, GameClock now, WorldState w);

    public int      countAt(SimLevel level);
    public void     forceLevel(int npcId, SimLevel level);      // debug
}
```

`fastForward` in full, because it's the function the design rests on:

```java
public void fastForward(NPC npc, GameClock now, WorldState w) {
    float hours = GameClock.hoursBetween(npc.lastSimulatedAt, now);
    if (hours <= 0) return;

    NeedsSystem.decay(npc.needs, hours, needsCfg, 1.0f);      // one multiply per need
    ScheduleSlot slot = scheduleSystem.currentSlot(npc, now);
    npc.position = locations.byId(slot.locationId()).entrance();   // inferred, not tracked
    npc.action.phase = ActionPhase.IDLE;
    npc.mood = MoodCalculator.compute(npc.needs, npc.personality, 0f);
    npc.lastSimulatedAt = now.copy();
}
```

Six multiplies and a lookup. That is the entire cost of an NPC having "lived" while you weren't there.

### 9.4 `ScheduleSystem`

```java
public final class ScheduleSystem implements SimSystem {
    public String id() { return "schedule"; }
    public void onTick(WorldState w, TickContext ctx);

    public static ScheduleSlot[] generate(NPC npc, JobDef job, DataRegistry d, RandomSource rng);
    public ScheduleSlot currentSlot(NPC npc, GameClock clock);
    public ScheduleSlot slotAtHour(NPC npc, int hour);
    public boolean      rollAdherence(NPC npc, ScheduleSlot slot, RandomSource rng);
    public void         onSlotChanged(NPC npc, ScheduleSlot from, ScheduleSlot to,
                                      WorldState w, TickContext ctx);
    public void         regenerate(NPC npc, WorldState w, RandomSource rng);   // after job change
}

public record ScheduleSlot(int startHour, int endHour, Activity activity, String locationId) {}
```

### 9.5 `NpcActionSystem`

```java
public final class NpcActionSystem implements SimSystem {
    public String id() { return "npcAction"; }
    public void onTick(WorldState w, TickContext ctx);

    public ActionDef chooseAction(NPC npc, Location at, WorldState w, RandomSource rng);
    public void      beginAction(NPC npc, ActionDef action, WorldState w);
    public void      interrupt(NPC npc, ReactionCause cause, int sourceId, WorldState w);
    public boolean   canInterrupt(NPC npc, ReactionCause cause);   // priority check

    private void stepIdle(NPC npc, WorldState w, TickContext ctx);
    private void stepPathing(NPC npc, float dtMinutes, WorldState w);
    private void stepPerforming(NPC npc, float dtMinutes, WorldState w, TickContext ctx);
    private void stepReacting(NPC npc, float dtMinutes, WorldState w, TickContext ctx);
}
```

Interrupt priority, highest first — `canInterrupt` enforces it:

```
1. GUN_DRAWN_NEARBY      2. GUNSHOT_HEARD        3. PLAYER_INTERACTION
4. CRIME_WITNESSED       5. NEED_CRITICAL        6. FRIEND_NEARBY
```

### 9.6 The emoji subsystem — `aya.sim.social`

This is the most-specified part of the codebase because it's the part that defines the game.

```java
public final class EmojiCorpus {
    public static EmojiCorpus load(Path file);

    public List<String>    vocabulary();
    public boolean         isInVocabulary(String emoji);
    public WeightedPool    seedPool(ResponseClass cls, Intent intent);
    public TransitionTable transitions();
    public float           needBias(NeedType need, String emoji);      // 1.0 = no bias
    public String          needEmoji(NeedType need);                   // for the need-leak
    public void            reload();
    public List<String>    validate();
}
```

```java
public final class TransitionTable {
    public static TransitionTable build(List<EmojiSentence> corpus);

    /** Order-2 lookup, automatic fallback to order-1, then to the global pool. */
    public WeightedPool next(String prev2, String prev1, MoodTier moodTier);
    public float         terminatorWeight(String prev1);
    public int           entryCount();
}

public record EmojiSentence(List<String> emoji, MoodTier moodTag) {}

public final class WeightedPool {
    public String pick(RandomSource rng);
    public String pickBiased(RandomSource rng, Function<String,Float> biasFn);
    public Set<String> candidates();
    public float weight(String item);
    public boolean isEmpty();
}
```

```java
public final class ResponseScorer {
    public static ScoreBreakdown score(NPC npc, Player player, Intent intent,
                                       ExchangeContext ctx, RandomSource rng);
    public static ResponseClass  classify(float totalScore);

    static float moodContribution(NPC npc);                          // mood * 0.4
    static float relationshipContribution(Relationship r, Intent i); // friendship*0.5 (+romance*0.3 if FLIRT)
    static float intentBase(Intent i, ExchangeContext ctx);          // table + item value
    static float personalityModifier(PersonalityVector p, Intent i);
    static float needsUrgency(NeedsVector n);                        // large negative if any < 15
    static float spamPenalty(NPC npc, GameClock now);                // -10 stacking within 10 min
}

public final class ScoreBreakdown {
    public float mood, relationship, intentBase, personality, needsUrgency, spam, jitter, total;
    public ResponseClass resultClass;
    public String toDebugString();     // prints in the debug console — build this early
}

public record ExchangeContext(
    ItemDef offeredItem,
    boolean playerIsArmed,
    boolean playerHygieneLow,
    boolean isRemote,               // true for phone Text app
    Location at,
    GameClock now
) {}
```

```java
public final class EmojiGenerator {
    public EmojiGenerator(EmojiCorpus corpus, RandomSource rng);

    /** The five-step algorithm from the spec, §5.6.3. */
    public EmojiResponse generate(NPC npc, Player player, Intent intent, ExchangeContext ctx);

    String       pickSeed(ResponseClass cls, Intent intent);
    List<String> extend(String seed, NPC npc, int targetLength);
    float        biasWeight(String emoji, NPC npc);
    void         applyNeedLeak(List<String> sequence, NPC npc);
    int          rollLength();                 // 1:40%, 2:35%, 3:18%, 4:7%
    BehaviorOutcome deriveOutcome(ResponseClass cls, Intent intent, NPC npc);
}

public final class EmojiResponse {
    public List<String>    emoji;
    public ResponseClass   responseClass;
    public ScoreBreakdown  breakdown;
    public BehaviorOutcome outcome;
    public int             friendshipDelta;
    public int             romanceDelta;
}

public enum BehaviorOutcome {
    NONE, FOLLOW_PLAYER, WALK_AWAY, REFUSE_FOR_2H, ATTACK_PLAYER,
    FLEE, GIFT_ITEM, CALL_POLICE, ACCEPT_TRADE
}
```

```java
public final class SocialSystem implements SimSystem {
    public String id() { return "social"; }
    public void onTick(WorldState w, TickContext ctx);   // drains EmojiIntentRequest events

    public EmojiResponse exchange(Player p, NPC npc, Intent intent,
                                  ItemRef offered, WorldState w, TickContext ctx);
    public void    applyEffects(Player p, NPC npc, EmojiResponse r,
                                WorldState w, TickContext ctx);
    public boolean canInteract(NPC npc, GameClock now);
    public EmojiResponse remoteExchange(Player p, NPC npc, Intent intent,
                                        WorldState w, TickContext ctx);   // phone Text
    public boolean acceptsCall(NPC npc, Player p, GameClock now);         // phone Call
}
```

```java
public final class BarkSystem implements SimSystem {
    public String id() { return "barks"; }
    public void onTick(WorldState w, TickContext ctx);

    public boolean shouldBark(NPC npc, WorldState w, GameClock now);
    public String  chooseBark(NPC npc, WorldState w);
    public void    emit(NPC npc, String emoji, TickContext ctx);
}
```

```java
public final class RelationshipSystem implements SimSystem {
    public String id() { return "relationships"; }
    public void onDayBoundary(WorldState w, TickContext ctx);   // decay

    public Relationship between(Actor a, Actor b, WorldState w);   // creates if absent
    public void modify(Actor a, Actor b, int friendshipDelta, int romanceDelta, WorldState w);
    public RelationshipTier tier(Relationship r);
    public List<NPC> knownTo(Player p, WorldState w);              // drives the Phone app
    public void onNpcDied(int npcId, WorldState w, TickContext ctx);  // mood hit to friends
}
```

### 9.7 `aya.sim.economy`

```java
public final class EconomySystem implements SimSystem {
    public String id() { return "economy"; }
    public void onTick(WorldState w, TickContext ctx);
    public void onDayBoundary(WorldState w, TickContext ctx);

    public boolean canAfford(Actor a, int amount);
    public boolean transact(Actor a, int delta, TransactionReason reason, TickContext ctx);
    public void    payWage(Actor a, JobDef job, TickContext ctx);
}

public final class JobSystem implements SimSystem {
    public String id() { return "jobs"; }
    public void onTick(WorldState w, TickContext ctx);

    public ApplicationResult apply(Actor a, JobDef job, WorldState w, TickContext ctx);
    public boolean  meetsRequirements(Actor a, JobDef job);
    public List<JobDef> openingsAt(String locationId, WorldState w);
    public int      slotsFilled(JobDef job, WorldState w);
    public void     beginShift(Actor a, JobDef job, WorldState w);
    public void     completeShift(Actor a, JobDef job, WorldState w, TickContext ctx);
    public void     recordMissedShift(Actor a, WorldState w, TickContext ctx);
    public void     fire(Actor a, FireReason reason, WorldState w, TickContext ctx);
    public void     quit(Actor a, WorldState w, TickContext ctx);
}

public final class ShopSystem implements SimSystem {
    public String id() { return "shops"; }
    public void onTick(WorldState w, TickContext ctx);   // drains PurchaseRequest

    public List<ItemDef> stockAt(String locationId);
    public PurchaseResult buy(Player p, ItemDef item, int qty, Location at,
                              WorldState w, TickContext ctx);
    public int  sellPrice(ItemDef item);                 // 40% of price
    public void flagTheft(Player p, ItemDef item, Location at, WorldState w, TickContext ctx);
}

public final class HousingSystem implements SimSystem {
    public String id() { return "housing"; }
    public void onDayBoundary(WorldState w, TickContext ctx);

    public int     rentFor(Location residence);
    public boolean payRent(Actor a, WorldState w, TickContext ctx);
    public void    evict(Actor a, WorldState w, TickContext ctx);
    public boolean purchase(Player p, Location residence, WorldState w, TickContext ctx);
    public boolean isHomeless(Actor a);
    public void    placeFurniture(Player p, ItemDef furniture, Position at, WorldState w);
}
```

### 9.8 `aya.sim.crime`

```java
public final class CrimeSystem implements SimSystem {
    public String id() { return "crime"; }
    public void onTick(WorldState w, TickContext ctx);

    public void reportCrime(Actor perp, CrimeType type, Position at,
                            WorldState w, TickContext ctx);
    public List<NPC> findWitnesses(Position at, float radius, WorldState w);
    public void  registerUnwitnessedBody(int victimId, Position at, WorldState w);
    public void  checkBodyDiscovery(WorldState w, TickContext ctx);
}

public final class WitnessDetector {
    public static boolean hasLineOfSight(Position from, Position to, WorldState w);
    public static boolean canWitness(NPC npc, Position crimeAt, float radius, WorldState w);
    public static List<NPC> scan(Position at, float radius, WorldState w);
}

public final class WantedSystem implements SimSystem {
    public String id() { return "wanted"; }
    public void onTick(WorldState w, TickContext ctx);      // hourly decay

    public void addPoints(float points, CrimeType from, WorldState w, TickContext ctx);
    public int  level(WantedState s);
    public void clear(WorldState w, TickContext ctx);
    public boolean isBeingPursued(WorldState w);
}

public final class PoliceSystem implements SimSystem {
    public String id() { return "police"; }
    public void onTick(WorldState w, TickContext ctx);

    public void onWantedLevelChanged(int from, int to, WorldState w, TickContext ctx);
    public void dispatch(int officerCount, boolean armed, Position target,
                         WorldState w, TickContext ctx);
    public List<NPC> activeOfficers(WorldState w);
    public void arrest(Player p, NPC officer, WorldState w, TickContext ctx);
    public int  sentenceLength(WorldState w);      // level*3 + criminalHistoryCount
    public void serveSentence(Player p, int days, WorldState w, TickContext ctx);
    public void spawnRoadblocks(WorldState w);
    public void lockdownTransit(WorldState w, boolean locked);
}
```

### 9.9 `aya.sim.life`

```java
public final class AgingSystem implements SimSystem {
    public String id() { return "aging"; }
    public void onDayBoundary(WorldState w, TickContext ctx);

    public void      birthday(Actor a, WorldState w, TickContext ctx);
    public int       maxHealthFor(int age);           // declines past 60
    public boolean   rollNaturalDeath(Actor a, RandomSource rng);
    public EducationLevel expectedEducation(int age);
}

public final class LifeEventSystem implements SimSystem {
    public String id() { return "lifeEvents"; }
    public void onDayBoundary(WorldState w, TickContext ctx);

    public void checkTruancy(Player p, WorldState w, TickContext ctx);
    public void checkEducationTransition(Actor a, WorldState w, TickContext ctx);
    public void logToDiary(WorldState w, String text);
    public void scheduleFestival(WorldState w, FestivalType f);
}

public final class DeathSystem implements SimSystem {
    public String id() { return "death"; }
    public void onTick(WorldState w, TickContext ctx);

    public void kill(Actor a, DeathCause cause, WorldState w, TickContext ctx);
    public void onNpcDeath(NPC npc, WorldState w, TickContext ctx);   // free job+home, mood hits
    public void onPlayerDeath(Player p, DeathCause cause, WorldState w, TickContext ctx);
    public LifeSummary buildSummary(Player p, WorldState w);
}

public record LifeSummary(
    String name, int age, DeathCause cause,
    int jobsHeld, int friendsMade, int crimesCommitted,
    int daysInJail, int moneyAtDeath, int photosTaken,
    int daysLived
) {}
```

### 9.10 `aya.sim.player`

```java
public final class PlayerControlSystem implements SimSystem {
    public String id() { return "playerControl"; }
    public void onTick(WorldState w, TickContext ctx);

    public void setMovementMode(MovementMode mode, WorldState w);
    public float speedMultiplier(MovementMode mode, Player p);
    public void enterVehicle(Player p, VehicleRef v, WorldState w, TickContext ctx);
    public void exitVehicle(Player p, WorldState w, TickContext ctx);
}

public final class InteractionSystem implements SimSystem {
    public String id() { return "interaction"; }
    public void onTick(WorldState w, TickContext ctx);   // drains InteractionRequest

    public InteractionTarget findTarget(Player p, WorldState w, float range);
    public List<InteractionVerb> availableVerbs(InteractionTarget t, Player p, WorldState w);
    public void execute(Player p, InteractionTarget t, InteractionVerb verb,
                        WorldState w, TickContext ctx);
}

public final class InventorySystem implements SimSystem {
    public String id() { return "inventory"; }
    public void onTick(WorldState w, TickContext ctx);   // drains UseItemRequest

    public boolean add(Actor a, ItemDef item, int qty);
    public boolean remove(Actor a, String itemId, int qty);
    public boolean has(Actor a, String itemId, int qty);
    public void    use(Actor a, int slot, WorldState w, TickContext ctx);
    public void    equip(Player p, int slot);
    public void    drop(Player p, int slot, WorldState w);
    public void    confiscateAll(Player p, WorldState w);   // arrest
    public int     freeSlots(Actor a);
}
```

---

## 10. `aya.ui`

### 10.1 `Screen` and `ScreenManager`

```java
public abstract class Screen {
    public abstract String id();
    public abstract void onEnter(Services s);
    public abstract void onExit();
    public abstract void update(float dtSeconds, InputState in);
    public abstract void render(Canvas c, WorldSnapshot snap);

    public boolean pausesSimulation() { return false; }
    public boolean rendersWorldBehind() { return false; }
    public boolean consumesInput()     { return true; }
}

public final class ScreenManager {
    public void    push(Screen s);
    public void    pop();
    public void    replace(Screen s);
    public Screen  top();
    public boolean isOpen(String screenId);
    public boolean anyPausesSimulation();
    public void    update(float dt, InputState in);
    public void    render(Canvas c, WorldSnapshot snap);
    public int     depth();
}
```

Overlay behavior falls straight out of these flags:

| Screen | `pausesSimulation` | `rendersWorldBehind` |
|---|---|---|
| GameplayScreen | false | — |
| InventoryScreen | **false** | **true** |
| PhoneScreen | **false** | **true** |
| EmojiWheel (widget, not a screen) | false | true |
| PauseScreen | **true** | true |
| All menu screens | true | false |

### 10.2 `WorldSnapshot` — the read-only bridge

```java
public final class WorldSnapshot {
    public static WorldSnapshot capture(WorldState w, float alpha);

    public GameClockView    clock();
    public PlayerView       player();
    public List<NpcView>    npcsInView();
    public NpcView          npc(int id);
    public LocationView     currentLocation();
    public WantedView       wanted();
    public List<BarkView>   activeBarks();
    public InteractionPromptView prompt();
    public long             tickIndex();
}

public record PlayerView(
    String name, int age, int health, int money,
    float hunger, float energy, float hygiene, float social, float fun, float bladder,
    MoodTier moodTier, Position pos, float angle,
    String equippedItemId, int equippedCount,
    ItemStackView[] inventory
) {}

public record NpcView(
    int id, String name, String spriteId, Pose pose,
    Position pos, float angle, MoodTier moodTier,
    SimLevel simLevel, RelationshipTier relationshipTier, boolean isPolice
) {}
```

**Views are records, everything is copied, nothing is a live reference.** This is the wall between the simulation and the renderer, and it's the reason you'll never debug a rendering-caused simulation bug.

### 10.3 Widgets

```java
public final class Hud {
    public void render(Canvas c, WorldSnapshot s);
    public void showInteractionPrompt(String verb, String iconAsset);
    public void clearPrompt();
    public void flashNeed(NeedType t);
}

public final class EmojiWheel {
    public void    open(int targetNpcId);
    public void    close();
    public boolean isOpen();
    public Intent  hoveredIntent();
    public void    update(float dt, InputState in);      // publishes EmojiIntentRequest on select
    public void    render(Canvas c, WorldSnapshot snap);
    public void    showResponse(List<String> emoji, float durationSeconds);
}

public final class SpeechBubble {
    public void    show(int npcId, List<String> emoji, float seconds);
    public void    update(float dt);
    public void    render(Canvas c, WorldSnapshot snap, Camera cam);
    public boolean isActiveFor(int npcId);
}

public final class NeedBar {
    public void render(Canvas c, int x, int y, NeedType type, float value);
}
```

### 10.4 The phone

```java
public final class PhoneScreen extends Screen {
    public boolean pausesSimulation()  { return false; }
    public boolean rendersWorldBehind(){ return true; }

    public void openApp(String appId);
    public void closeApp();
    public PhoneApp currentApp();
    public void registerApp(PhoneApp app);
}

public abstract class PhoneApp {
    public abstract String id();
    public abstract String displayName();
    public abstract String iconAsset();
    public abstract void   onOpen(Services s);
    public abstract void   update(float dt, InputState in, Services s);
    public abstract void   render(Canvas c, Rect screenArea, WorldSnapshot snap);
    public boolean hasNotification(WorldSnapshot snap) { return false; }
}
```

The ten apps, all extending `PhoneApp`:

```java
MissionsApp, CalendarApp, RelationshipsApp, MusicApp, CameraApp,
CallApp, TextApp, DiaryApp, SaveApp, ExitApp
```

Two of them need real logic rather than just display:

```java
public final class TextApp extends PhoneApp {
    public void sendTo(int npcId, Intent intent);   // publishes EmojiIntentRequest{isRemote=true}
    public List<TextThread> threads(WorldSnapshot s);
}

public final class CallApp extends PhoneApp {
    public void   dial(int npcId);                  // publishes CallRequest
    public CallResult lastResult();                 // ACCEPTED (summons), DECLINED, ASLEEP, UNKNOWN
}
```

---

## 11. `aya.render` and `aya.debug`

```java
public final class WorldRenderBridge {
    public WorldRenderBridge(RaccoonEngine engine, SpriteCache cache);
    public void submitWorld(WorldSnapshot snap);
    public void setCameraFrom(PlayerView p, float interpolationAlpha);
    public int  spritesSubmittedLastFrame();
}

public final class NpcSpriteBinder {
    public String stripPath(String spriteId, Pose pose);
    public int    angleIndex(float npcAngle, float cameraAngle);   // 0..7
    public Rect   frameRect(int angleIndex, int walkFrame);        // 96x128 within the 768x128 strip
    public void   submit(RaccoonRenderer r, NpcView npc, Camera cam);
}

public final class SpriteCache {
    public boolean has(String spriteId, Pose pose);
    public Path    pathFor(String spriteId, Pose pose);
    public void    requestBatch(List<SpriteRequest> requests, ProgressSink progress);
    public int     cachedCount();
    public void    evictUnused(Set<String> liveSpriteIds);
}

public final class AudioDirector {
    public void playTrack(String trackId, boolean loop);
    public void crossfadeTo(String trackId, float seconds);
    public void playSfx(String sfxId, Position at);
    public void setWorldDucking(float factor);      // 0.3 when headphones are on
    public void setMusicVolume(float v);
    public void setSfxVolume(float v);
    public void onZoneChanged(Zone z, Phase phase);
}
```

```java
public final class DebugTools {
    public void execute(String commandLine);
    public void registerCommand(String name, DebugCommand cmd);
    public void setOverlay(OverlayMode mode);   // NONE, LOD, NAVGRAPH, NEEDS, PATHS, SCORES
    public void renderOverlay(Canvas c, WorldSnapshot s);
    public boolean isEnabled();
}

public interface DebugCommand {
    String name();
    String usage();
    void   run(String[] args, Services s);
}
```

The command set to build (each is ten lines):

```
time <hh:mm>        day <n>         season <name>       freeze
need <type> <val>   health <val>    money <val>         age <n>
give <itemId> [n]   tp <locationId> wanted <0-5>        clearwanted
npc <id>            lod <id> <lvl>  kill <id>           revive
emoji <npcId> <intent>              emojiscore <npcId> <intent>
reloaddata <which>  overlay <mode>  spawn <npcId> here
```

`emojiscore` prints the full `ScoreBreakdown`. Build it in M7 on day one — you will run it a thousand times while tuning.

---

## 12. Call-Flow Traces

The seven flows that define the system. Read these once and the architecture clicks.

### 12.1 Boot

```
main()
 └─ RaccoonEngine.init()
     └─ new AyaGame(engine, "aya.cfg")
         └─ boot()
             ├─ Config.load("aya.cfg")
             ├─ DataRegistry.loadAll("data/")        ── items, jobs, actions, locations,
             │   └─ validate() → log problems           needs config, emoji corpus, names
             ├─ new EventBus()
             ├─ new SpriteCache("cache/sprites/")
             ├─ new AudioDirector(engine)
             ├─ new SimulationPipeline()
             │   └─ register all 17 systems in the §4.4 order
             ├─ new SimulationLoop(10, pipeline, rng)
             └─ screens.push(new SplashScreen())
```

### 12.2 New Game

```
MainMenuScreen: "NEW GAME"
 └─ screens.replace(new CharacterCreationScreen())
     └─ [4 panels] → BackstoryData + seed
         └─ AyaGame.startNewGame(backstory, seed, progress)
             ├─ screens.replace(new WorldgenLoadingScreen(progress))
             └─ [worker thread]
                 └─ WorldGenerator.generate(seed, backstory, data, locations, sprites, progress)
                     ├─ rng = new RandomSource(seed)
                     ├─ generatePopulation(rng.split("population"), data, 340)
                     │   └─ ×340: generateNpc() → roll age, personality, appearance, name
                     ├─ sprites.requestBatch(allSpriteRequests, progress)   ── Flask batch call
                     ├─ buildFamilies(rng.split("families"))
                     ├─ assignHomes(rng.split("homes"))
                     ├─ assignJobs(rng.split("jobs"))
                     ├─ seedRelationships(rng.split("relationships"))
                     ├─ generateAllSchedules()
                     └─ createPlayer(backstory)
                 ├─ services.setWorld(world)
                 ├─ pipeline.worldReady(world, services)
                 ├─ loop.start(world)
                 └─ screens.replace(new GameplayScreen())
```

### 12.3 One simulation tick

```
SimulationLoop.accumulate(dt) → pendingTicks > 0
 └─ SimulationPipeline.tick(world, ctx)
     ├─ [10]  ClockSystem        clock.advance(dtMinutes); set ctx.*Changed flags
     ├─ [20]  NeedsSystem        decay player + ACTIVE npcs; fire threshold events
     ├─ [30]  LodSystem          evaluate() each npc; promote/demote; fastForward on promote
     ├─ [40]  ScheduleSystem     if hourChanged: currentSlot() diff → onSlotChanged()
     ├─ [50]  NpcActionSystem    step each ACTIVE npc's state machine
     ├─ [60]  PlayerControlSystem apply movement input, collision, vehicle speed
     ├─ [70]  InteractionSystem  drain InteractionRequest events
     ├─ [80]  SocialSystem       drain EmojiIntentRequest → exchange() → applyEffects()
     │        BarkSystem         shouldBark() → chooseBark() → emit()
     ├─ [90]  CrimeSystem        findWitnesses() on pending crimes; body discovery rolls
     ├─ [100] WantedSystem       addPoints from crimes; hourly decay
     ├─ [110] PoliceSystem       react to level change; move officers; arrest on contact
     ├─ [120] EconomySystem      if dayChanged: onDayBoundary()
     ├─ [130] JobSystem          shift completion, missed-shift accounting, firing
     ├─ [140] HousingSystem      if isRentDay: payRent() or evict()
     ├─ [150] AgingSystem        if dayChanged: birthdays, natural death rolls
     ├─ [160] LifeEventSystem    truancy, education transitions, diary logging
     ├─ [170] DeathSystem        process pending deaths, build summary if player died
     └─ [999] EventBus.drain()   deliver all queued events to subscribers
 └─ snapshotRef.set(WorldSnapshot.capture(world, 1.0f))
```

### 12.4 An emoji exchange — the flow to know by heart

```
Player presses [E] near NPC #17
 └─ GameplayScreen.update()
     └─ InteractionSystem.findTarget(player, world, 3.0f)  → NpcTarget(17)
         └─ EmojiWheel.open(17)

Player selects 😂 (JOKE)
 └─ EmojiWheel publishes EmojiIntentRequest(17, JOKE, null)
     │  ── UI's job is now DONE. It has mutated nothing. ──
     └─ [next tick, pipeline order 80]
         └─ SocialSystem.onTick() drains the request
             ├─ canInteract(npc17, clock)       → is it refusing? asleep? dead?
             └─ exchange(player, npc17, JOKE, null, world, ctx)
                 ├─ ExchangeContext(offeredItem=null, playerIsArmed=false,
                 │                  playerHygieneLow=false, isRemote=false, at=CAF, now)
                 ├─ ResponseScorer.score(npc17, player, JOKE, ctx, rng)
                 │   ├─ moodContribution          npc.mood(-30) * 0.4        = -12.0
                 │   ├─ relationshipContribution  friendship(45) * 0.5       = +22.5
                 │   ├─ intentBase                JOKE                       =  +3.0
                 │   ├─ personalityModifier       temper 20, sociability 70  =  +6.0
                 │   ├─ needsUrgency              hunger 12 → below 15       = -18.0
                 │   ├─ spamPenalty               last talked 40 min ago     =   0.0
                 │   ├─ jitter                    rng ±12                    =  +4.2
                 │   └─ total                                                =   5.7
                 ├─ ResponseScorer.classify(5.7) → NEUTRAL
                 └─ EmojiGenerator.generate(...)
                     ├─ pickSeed(NEUTRAL, JOKE)                → 😅
                     ├─ rollLength()                           → 2
                     ├─ extend("😅", npc17, 2)
                     │   └─ transitions.next(null,"😅",NEUTRAL)
                     │       └─ pickBiased(rng, e -> biasWeight(e, npc17))
                     │           └─ hunger 12 → 🍔 boosted ×3.0 → picks 🙂
                     ├─ applyNeedLeak(["😅","🙂"], npc17)
                     │   └─ p = (100-12)/100 * 0.6 = 0.53 → hit → append 🍔
                     └─ deriveOutcome(NEUTRAL, JOKE, npc17)    → NONE

                 result: 😅🙂🍔   class=NEUTRAL   friendship +1

         └─ SocialSystem.applyEffects(...)
             ├─ RelationshipSystem.modify(player, npc17, +1, 0, world)
             ├─ npc17.needs.add(SOCIAL, +8)
             ├─ npc17.lastInteractionMinute = clock.totalMinutes()
             └─ events.publish(EmojiExchangeEvent(17, JOKE, NEUTRAL, [😅,🙂,🍔]))

 └─ [event drain] EmojiWheel.showResponse([😅,🙂,🍔], 2.5f)
     └─ SpeechBubble.show(17, [😅,🙂,🍔], 2.5f)
```

Read the `applyNeedLeak` step again: that 🍔 turns a bland neutral reply into *"heh, yeah — hey, I'm starving."* One line of code, no writing, and it's the moment the whole design justifies itself.

### 12.5 LOD promotion

```
LodSystem.onTick()
 └─ for each npc:
     lvl = evaluate(npc, player, world)
       ├─ different zone?                      → DORMANT
       ├─ same zone, distance > 40?            → STATISTICAL
       ├─ within 40, ACTIVE count < 24?        → ACTIVE
       └─ within 40, ACTIVE count at cap?      → STATISTICAL (nearest win)

     if (lvl != npc.simLevel):
         DORMANT/STATISTICAL → ACTIVE:
           ├─ fastForward(npc, clock, world)      ── the one multiply per need
           ├─ slot = ScheduleSystem.currentSlot(npc, clock)
           ├─ npc.position = locations.byId(slot.locationId()).entrance()
           ├─ NpcSpawner.spawnSprite(npc, world)
           ├─ npc.action.phase = IDLE
           └─ events.publish(LodChangedEvent(npc.id, from, ACTIVE))

         ACTIVE → STATISTICAL:
           ├─ NpcSpawner.despawnSprite(npc, world)
           ├─ npc.position = nearest nav node
           ├─ npc.lastSimulatedAt = clock.copy()
           └─ events.publish(LodChangedEvent(...))
```

### 12.6 A crime, end to end

```
Player fires a weapon
 └─ InventorySystem.use(player, slot) → weapon
     └─ events.publish(CrimeCommittedEvent(0, FIRE_WEAPON_PUBLIC, pos, -1))

[pipeline 90] CrimeSystem.onTick() drains it
 ├─ WitnessDetector.scan(pos, 25f, world)         → [npc 22, npc 41]
 ├─ for each witness: NpcActionSystem.interrupt(npc, CRIME_WITNESSED, 0, world)
 │   └─ bravery < 40 → FLEE (panic pose) ; bravery >= 70 → CONFRONT
 ├─ witnessCount = 2 → crime registers
 └─ events.publish(CrimeCommittedEvent(0, FIRE_WEAPON_PUBLIC, pos, 2))

[pipeline 100] WantedSystem.addPoints(3.0, FIRE_WEAPON_PUBLIC, world, ctx)
 ├─ wanted.points += 3 → level 0 → 3
 └─ events.publish(WantedLevelChangedEvent(0, 3))

[pipeline 110] PoliceSystem.onWantedLevelChanged(0, 3, world, ctx)
 ├─ dispatch(4, armed=true, playerPos, world, ctx)
 │   └─ ×4: promote nearest police NPC to ACTIVE,
 │          interrupt(officer, POLICE_DISPATCH), Pathfinder.find(...) to player
 └─ AudioDirector.crossfadeTo("tense", 1.5f)

... officer reaches the player ...
PoliceSystem.arrest(player, officer, world, ctx)
 ├─ InventorySystem.confiscateAll(player, world)
 ├─ days = sentenceLength(world)              // 3*3 + history
 ├─ events.publish(ArrestEvent(0, days))
 ├─ ZoneManager.requestTransition(H, ARREST, world)   // Jailhouse is in zone H
 ├─ loop.runTimeSkip(world, days * 24 * 60, JAIL)
 │   └─ pipeline.coarseTick() ×systems — everyone else lives their lives
 ├─ wanted.clear(world, ctx)
 └─ player.criminalHistoryCount++
```

Note the shape: **no system reaches across to another.** Crime publishes, Wanted consumes and publishes, Police consumes. Each one is independently testable, and you can disable any of them with a config flag to isolate a bug.

### 12.7 Save and load

```
SAVE
PhoneScreen → SaveApp.save(slot)
 └─ CameraApp.captureThumbnail() → BufferedImage
     └─ SaveManager.save(world, slot, thumbnail)
         ├─ loop.setPaused(true)
         ├─ SaveWriter.write(world, writer)
         │   └─ [META][CLOCK][PLAYER][WANTED][NPC]×340[DIARY][FLAGS]
         ├─ write thumbnail png
         └─ loop.setPaused(false)

LOAD
LoadSlotsScreen → AyaGame.loadGame(slot)
 ├─ loop.stop()
 ├─ world = SaveManager.load(slot)
 │   └─ SaveReader.read(reader)
 │       ├─ parse sections
 │       ├─ rebuild NPC[] and relationship maps
 │       └─ resolve LocationRef / JobRef against DataRegistry
 ├─ ZoneManager.onZoneLoaded(world.currentZone, world, navGraph)
 ├─ sprites.requestBatch(missing sprites)        // cache may have been cleared
 ├─ services.setWorld(world)
 ├─ pipeline.worldReady(world, services)
 ├─ loop.start(world)
 └─ screens.replace(new GameplayScreen())
```

---

## 13. The Dependency Matrix

Rows may call columns. **✗ is a rule, not a preference.**

| ↓ calls → | core | event | state | data | world | sim | persist | ui | render | raccoon |
|---|---|---|---|---|---|---|---|---|---|---|
| **core** | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **event** | ✗ | — | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| **state** | ✗ | ✗ | — | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| **data** | ✓ | ✗ | ✓ | — | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| **world** | ✓ | ✓ | ✓ | ✓ | — | ✗ | ✗ | ✗ | ✗ | ✓ |
| **sim** | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✗ | ✗ | ✗ | ✗ |
| **persist** | ✓ | ✗ | ✓ | ✓ | ✓ | ✗ | — | ✗ | ✗ | ✗ |
| **ui** | ✓ | ✓ | ✗* | ✓ | ✗ | ✗ | ✓ | — | ✓ | ✓ |
| **render** | ✓ | ✗ | ✗* | ✓ | ✗ | ✗ | ✗ | ✗ | — | ✓ |
| **debug** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

`✗*` — UI and render touch `aya.state` **only** through `WorldSnapshot` views, never the live objects.

Three consequences worth internalizing:

1. **`sim` cannot call `ui`.** A system that wants to show something publishes an event. This is why the event bus exists.
2. **`ui` cannot call `sim`.** It publishes a `*Request`. This is the other half of the same wall.
3. **`debug` may call anything**, because it's a development tool and should be strippable in one commit.

---

## 14. Milestone → Class Map

What you actually create, per milestone from the spec's build order.

| Milestone | Classes created |
|---|---|
| **M0** | `AyaGame`, `SimulationLoop`, `SimulationPipeline`, `SimSystem`, `TickContext`, `Config`, `RandomSource`, `Services`, `EventBus`, `GameEvent`, `GameClock`, `ClockSystem`, `DebugConsole` |
| **M1** | `Screen`, `ScreenManager`, `InputState`, all 12 screen stubs, `OptionsScreen` (complete), `Canvas` adapter |
| **M2** | `WorldState`, `Player`, `NPC`, `Actor`, `NeedsVector`, `PersonalityVector`, `Relationship`, `Position`, `ItemStack`, `EconomyState`, `WantedState`, `SaveManager`, `SaveWriter`, `SaveReader`, `SaveSlotInfo`, `CharacterCreationScreen`, `BackstoryData` |
| **M3** | `NeedsSystem`, `MoodCalculator`, `NeedsConfig`, `WorldSnapshot` + all views, `Hud`, `NeedBar`, `DeathSystem` (player only), `GameOverScreen`, `LifeSummary`, `DebugTools` + basic commands |
| **M4** | `LocationDef`, `Location`, `LocationRegistry`, `ZoneManager`, `NavGraph`, `NavNode`, `Pathfinder`, `NavPath`, `DataRegistry`, `TextTableParser`, `WorldRenderBridge` |
| **M5** | `WorldGenerator`, `ProgressSink`, `NamePool`, `JobDef`, `JobPool`, `SpriteCache`, `SpriteRequest`, `NpcSpriteBinder`, `WorldgenLoadingScreen`, `DebugNpcBrowser` |
| **M6** | `LodSystem`, `ScheduleSystem`, `ScheduleSlot`, `NpcActionSystem`, `ActionState`, `NpcSpawner`, `ActionDef` |
| **M7** | `EmojiCorpus`, `TransitionTable`, `WeightedPool`, `EmojiSentence`, `ResponseScorer`, `ScoreBreakdown`, `ExchangeContext`, `EmojiGenerator`, `EmojiResponse`, `BehaviorOutcome`, `SocialSystem`, `BarkSystem`, `RelationshipSystem`, `EmojiWheel`, `SpeechBubble` |
| **M8** | `ItemDef`, `InventorySystem`, `InteractionSystem`, `InteractionTarget`, `ShopSystem`, `InventoryScreen`, usable-object handlers |
| **M9** | `JobSystem`, `EconomySystem`, `HousingSystem`, `ApplicationResult`, `TransactionReason`, `FireReason` |
| **M10** | `CrimeSystem`, `WitnessDetector`, `WantedSystem`, `PoliceSystem`, `CrimeTable`, `CrimeType`, weapon handling in `InventorySystem` |
| **M11** | `PhoneScreen`, `PhoneApp`, and the 10 app classes |
| **M12** | `PlayerControlSystem`, `VehicleRef`, `MovementMode`, `TransitSystem`, `TransitionScreen` |
| **M13** | `AgingSystem`, `LifeEventSystem`, full `DeathSystem`, `DiaryEntry`, `FestivalType` |
| **M14** | `AudioDirector` (complete), `WeatherSystem`, `FestivalSystem`, `GalleryScreen`, `CommentaryScreen` |

**Total: roughly 115 classes.** That is a realistic size for this game. If you find yourself at 300, you're over-abstracting; if you're at 40, you've put simulation logic inside data classes.

---

## 15. Timeline

Two columns: **PT** assumes about 12 focused hours a week; **FT** assumes full days. Pick the one that matches your life and ignore the other.

| Milestone | Weight | PT | FT | Cumulative (PT) |
|---|---|---|---|---|
| M0 Foundations | S | 1 wk | 2 d | 1 wk |
| M1 Screen flow | M | 2 wk | 4 d | 3 wk |
| M2 Creation & save/load | M | 2 wk | 4 d | 5 wk |
| M3 Time, needs, HUD | M | 2 wk | 4 d | 7 wk |
| M4 World data & Zone E | L | 4 wk | 8 d | 11 wk |
| M5 Population gen | L | 3 wk | 6 d | 14 wk |
| M6 NPC sim, schedule, LOD | **XL** | 6 wk | 12 d | 20 wk |
| M7 Emoji system | L | 4 wk | 8 d | **24 wk** ← *vertical slice complete* |
| M8 Items & shops | M | 3 wk | 6 d | 27 wk |
| M9 Jobs & housing | M | 2.5 wk | 5 d | 29.5 wk |
| M10 Crime & police | L | 4 wk | 8 d | 33.5 wk |
| M11 The phone | L | 4 wk | 8 d | 37.5 wk |
| M12 Vehicles & transit | M | 2.5 wk | 5 d | 40 wk |
| M13 Aging & life arc | M | 2 wk | 4 d | 42 wk |
| M14 Full island & polish | **XL** | 10 wk | 20 d | **52 wk** |

**About a year part-time. About five months full-time.**

### 15.1 The two milestones that will overrun

**M6 (XL).** LOD promotion/demotion correctness is genuinely hard, and the bugs are subtle — an NPC whose needs fast-forwarded wrong looks fine until you notice the cashier is starving at 09:00 every single morning. Budget the full six weeks, and build the LOD debug overlay *first*, not last.

**M14 (XL).** Seven zones of map authoring is the single largest block of work in the project, and none of it is code. The estimate assumes ~1.2 weeks per zone. If that's slower than it sounds, it's because Zone E in M4 will teach you exactly how long a zone takes — recalibrate M14 the moment M4 is done, using real numbers instead of mine.

### 15.2 What can run in parallel

Art and audio don't block code:

- **Sprite templates and palette work** — anytime after M5, run alongside anything.
- **The emoji corpus** — authorable from day one. It's a text file. Write it on your phone in queues. By the time you reach M7 you'll have a much better corpus than if you'd written it in one sitting.
- **Map authoring for zones A–D, F–H** — start any time after M4 proves the pipeline. Don't start before; you'd be authoring against an unvalidated workflow.
- **Music tracks** — anytime.

### 15.3 The checkpoint that matters

**Week 24, end of M7.** At that point you have a character you created, a town full of people with jobs and schedules living their day, and emoji conversations with any of them. That is the entire pitch of Aya Island, playable.

Everything after M7 makes it a game you can *lose*. Everything in M14 makes it an *island*. But M0→M7 is the part that proves the idea, and it's the part worth protecting from scope creep — if something threatens the week-24 date, cut it, because a working vertical slice is worth more than any three features on the backlog.

---

*Aya Island Architecture v1.0 — the interfaces here are the contract. Change this file before you change the code.*
