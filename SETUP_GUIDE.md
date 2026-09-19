# Raccoon Engine Game Agent — Setup

Drop `game_agent.py`, `raccoon_tools.py`, `requirements.txt` and
`.env.example` in the repo root, next to `RaccoonEngineV2/`.

```bash
pip install -r requirements.txt
cp .env.example .env     # then fill in your keys
python game_agent.py
```

---

## Keys and the public repo

Everything reads from `.env` via `python-dotenv`. `.env.example` is the one
that gets committed (placeholders only); `.env` never does. Append the lines
in `.gitignore.additions` to your `.gitignore` **before** you create `.env`.

If you ever want this running somewhere shared, move the keys to AWS Secrets
Manager or GitHub Actions secrets — the code reads `os.environ`, so nothing
changes.

---

## 1. AWS Bedrock (required)

**CLI + credentials**
```bash
aws configure          # needs AWS CLI v2.13.23+
aws sts get-caller-identity
```

**Claude model access.** Most Bedrock models enable themselves on first
invocation, but Anthropic's are the exception: AWS Console → Bedrock → Model
catalog → pick a Claude model → request access → fill the one-time use-case
form. Usually approved in minutes. Availability varies by region;
`us-east-1` and `us-west-2` are safest.

**IAM.** The user or role needs at minimum:
```json
{"Version":"2012-10-17","Statement":[{"Effect":"Allow",
 "Action":["bedrock:InvokeModel","bedrock:InvokeModelWithResponseStream"],
 "Resource":"*"}]}
```

**Model IDs.** The text model uses a cross-region inference profile (the
`us.` prefix) for on-demand throughput. Images use Stability on Bedrock, so
the same credentials cover both — no second image vendor to set up.

**Sanity check**
```python
import boto3
c = boto3.client("bedrock-runtime", region_name="us-west-2")
print(c.converse(modelId="us.anthropic.claude-sonnet-4-5-20250929-v1:0",
      messages=[{"role":"user","content":[{"text":"hi"}]}]
     )["output"]["message"]["content"][0]["text"])
```

---

## 2. Tavily (recommended)

Powers both the market research phase and the agent's `web_search` tool.
1,000 free credits/month, no card. Sign up at tavily.com, paste the key into
`.env`.

Worth knowing: **Bedrock does not support Anthropic's native `web_search`
server tool.** That tool runs on Anthropic's own infrastructure and Bedrock's
tool validator rejects it. Any "internet access" on Bedrock means you call a
search API yourself and hand results back as a tool result — which is what
this code does.

---

## 3. Replicate (optional — audio)

The engine only reads `.wav` for `bgm/` and `se/`. Replicate was the easiest
thing to hook up: one token, one HTTP endpoint, no SDK. Get a token at
replicate.com/account/api-tokens.

Install `ffmpeg` too (`brew install ffmpeg` / `apt install ffmpeg`) — MusicGen
returns mp3 by default and `_to_wav()` shells out to convert.

Without the token, audio tools write silent placeholder `.wav` files so a
build never fails on missing music. To swap in ElevenLabs Music or Mubert,
replace the request block in `generate_audio()`; nothing else changes.

---

## 4. Maps — partitioning and the editor round trip

### You were right about the partitioning

`Screen.verticals` is a flat grid with **one Edge per (x, z, orientation)
cell**, indexed by `makeWallIndex`. So if two sectors each emit their own
wall along a line they share, the second write silently clobbers the first —
the wall ends up attributed to the wrong sector, with the wrong heights and
textures, and the engine never complains.

My first version did exactly that. Two adjacent rooms sharing the line
`x=10` collided on three grid cells. Caught and fixed.

The builder now runs **the same Manhattan partitioning the editor does**,
ported from `runManhattanPartitioning` and `classifyEdges`:

1. Collect every x and z coordinate used by the world boundary, any room, or
   any doorway.
2. The cartesian grid of those coordinates tiles the world with no gaps and
   no overlaps. **Every cell becomes a sector**, including cells no room
   covers — those are the "void" sectors, same as the editor's.
3. Each cell edge is therefore adjacent to either one sector (on the world
   boundary → `[BOUNDARIES]`) or exactly two (→ `[EDGES]`). Emitted once,
   never twice.

A room is a *region*, not a sector: a big room becomes several sectors with
invisible passable portals between them. Solidity, not geometry, is what
makes a portal read as a wall. Room-meets-void and room-meets-room-without-
a-doorway get `solid=true` with the middle band drawn; interior seams get
`skip=true, solid=false` and are invisible.

Because this is the invariant the whole thing rests on,
`_assert_no_slot_collisions()` replays the engine's exact indexing on every
single build and raises rather than writing a corrupt map. It isn't trusted,
it's checked.

Verified against your parser: two rooms + doorway → 10 sectors, 138 wall
cells; four rooms in a ring with three doorways → 24 sectors, 240 cells. No
errors either time.

It also rejects, with a fixable message: gaps in room IDs, diagonal or
zero-length doorways, doorways between rooms that aren't flush, inverted
ceilings, out-of-range brightness, coordinates past 512, and partitions that
blow the 1024-sector limit.

**The one constraint to know:** every distinct coordinate cuts a grid line
across the whole map, so rooms at sloppy offsets multiply sectors fast. Forty
scattered rooms produced 3,160 sectors and got rejected. The agent's prompt
tells it to align rooms to shared coordinates.

### Yes — you can edit what it generates

`make_map` writes two files:

- `RaccoonEngineV2/data/maps/<name>.txt` — playable immediately
- `editor_projects/<name>.project.json` — openable in the Map Editor

The editor's **Load Project** button reads `{userRectangles, worldBoundary}`
(`editor.js:loadProject`), which is exactly what the second file contains —
rooms as rectangles with their lines, vertices, edge configs and a world
boundary anchored at the origin, since the editor requires that.

So the loop you wanted is:

```
agent drafts layout  ->  project.json  ->  Load Project in raccoon_editor.html
   ->  drag things around  ->  re-partition  ->  Download map.txt
```

You get the agent's rough draft as *editable rectangles*, not as a finished
map you'd have to reverse-engineer. Sides with a doorway come through with
`solid` already unchecked.

---

## 5. Engine integration

Real, not stubbed. `raccoon_tools` drives it by subprocess:

| Tool | What runs |
|---|---|
| `build_engine()` | `javac` over `src/**/*.java` → `bin/` |
| `smoke_test()` | `java raccoon.Main` for N seconds, returns stderr |
| `pack_rpk()` | `java raccoon.Main --pack` → `data.rpk` |

`smoke_test` is the agent's feedback loop: it launches the game, lets it run,
and reads back what the engine logged. Missing textures and malformed maps
surface there, and the agent fixes and retests.

Needs a **JDK** on PATH (not just a JRE — `javac` is required).

---

## 6. Asset pipeline

`raccoon_tools` ports `Table.init()`'s 256-colour palette to Python exactly
(16 base + 216 colour cube + 24 greys) and quantizes every generated image to
it. The engine does this at load time anyway via `findClosestColorIndex`, but
doing it up front means the PNG on disk is what you see in-game, and the flat
banding is what makes diffusion output read as pixel art at all.

| Asset | Pipeline | Output |
|---|---|---|
| Texture | generate → box-downscale → quantize | `tex/` 64×64 |
| Sprite sheet | 8 generations (shared seed, one per facing) → magenta chroma-key → downscale → quantize → tile | `sprites/` 512×64 |
| Skybox | generate → stretch → quantize | `skybox/` 2560×880 |
| Pic | generate → downscale → quantize | `pics/` 640×480 |

On **pixel art specifically**: Stable Image Core won't give you clean pixel
art directly — diffusion models produce anti-aliased fake pixel art. Your
instinct was right, and it's what the pipeline does: generate at native
resolution, then box-downscale and palette-quantize. Box averaging beats
nearest-neighbour here because it blends detail down instead of dropping it.

**Sprite facings.** From `Screen.drawSprites`, frame index is
`(atan2(player_z - sprite_z, player_x - sprite_x) - direction_rad) / 45°`,
and `validateImage` throws unless `width == 8 * height`. So frame 0 is the
front view and each subsequent frame rotates 45°. `generate_sprite_sheet`
generates all 8 with a shared seed for consistency and assembles the sheet.
It's 8 image calls per character, so it's the expensive tool — the agent's
system prompt tells it so.

I ran generated assets through your `ResourceManager`: `stone.png` 64×64,
`goblin.png` 512×64, `dusk.png` 2560×880 all loaded clean.

---

## 7. How scripts run now (your init.lua question)

You changed this yourself, so here's what `ScriptRunner` does today.

**Before:** every frame, for every script and every sprite, the engine built
a fresh Lua environment, re-lexed and re-compiled the source, then ran it.
Steps 1 and 2 produced identical results after frame 1 — pure waste, plus a
pile of short-lived allocations causing GC hitches. That's why `init.lua` had
to delete itself: the whole file genuinely did re-run every frame.

**Now:** environment and compiled chunk are cached per script *name*. Two
styles, both supported:

- **Plain** — the file body runs every frame, exactly as before. Existing
  scripts keep working unchanged.
- **`update()`** — the body runs *once* as setup, and after that only
  `update()` runs each frame.

```lua
local script_index = ...
local timer = 0                    -- survives between frames now
RA:resourceLoad("system_font.ttf") -- one-time setup, off the hot path

function update()
    timer = timer + 1
end
```

**So, to your question: yes, `init.lua` still needs `RA:scriptEnd(script_index)`,
and your pattern is still the right one.** Run it, spawn other scripts, delete
itself. The reason is subtler now though — it's not that the chunk gets
recompiled (it doesn't anymore), it's that with no `update()` defined, `run()`
falls through to `chunk.invoke(args)` and re-runs the body. Without the
`scriptEnd`, you'd re-teleport the player and reload the map every frame.

There's a detail in `ScriptRunner` worth knowing: after running a body for the
first time it checks `if (envs.containsKey(script_name))` before calling
`update()`. That's there specifically so a script that removed itself during
setup doesn't get an `update()` call on the way out. `init.lua` self-deleting
is a supported path, not something that works by accident.

**Two ways to write init.lua, both fine:**

```lua
-- (a) self-deleting, your pattern — clearest for pure setup
local script_index = ...
RA:playerSetPosition(2, 2, 2, 0)
RA:worldLoadMapAsync("map.txt", "LOADING")
RA:scriptAdd("hud.lua", 10)
RA:scriptEnd(script_index)
```

```lua
-- (b) define an empty update() and never delete it — the script stays
-- resident and you can give it work later without re-registering
local script_index = ...
RA:playerSetPosition(2, 2, 2, 0)
RA:worldLoadMapAsync("map.txt", "LOADING")
function update() end
```

(b) costs one no-op call per frame and keeps the environment alive. (a) is
cleaner. The agent is told to use (a).

**For multi-level games:** `worldLoadMapAsync` can be called at any time from
any script, so you don't need a new `init.lua` per level. One level-manager
script with an `update()` that watches for the exit condition and loads the
next map is the shape that fits. I've told the agent this, so it can build
multi-level games in a single iteration.

Also worth knowing: `ScriptRunner.forget(name)` drops a script's cached
environment and chunk, so the next run re-reads from disk. That's a hot-reload
primitive, exposed to Lua as `RA:scriptReload`. Handy while iterating.

---

## 8. One small engine bug I hit

`Main.main()` handles `--pack` *after* `Table.init()`, and `Table`'s static
initializer calls `Toolkit.getDefaultToolkit().getScreenSize()`. So packing
throws `HeadlessException` on any machine without a display. It works fine on
your laptop, but it means you can't pack in CI or on a build server.

Fix is to make the screen-size lookup lazy, or guard it:

```java
public static int USER_SCREEN_SIZE_W = GraphicsEnvironment.isHeadless()
    ? 1920 : Toolkit.getDefaultToolkit().getScreenSize().width;
```

Not urgent — just flagging it since it'll bite the moment you automate builds.

---

## 9. Files

| File | |
|---|---|
| `game_agent.py` | the workflow + tool definitions |
| `raccoon_tools.py` | palette, assets, map builder, engine control |
| `requirements.txt` | deps |
| `.env.example` | commit this |
| `.env` | never commit this |
| `.gitignore.additions` | append to your `.gitignore`, don't commit as-is |

Generated at runtime (gitignored): `editor_projects/`, `releases/`,
`RaccoonEngineV2/data.rpk`.

---

## Quickstart

```bash
# 1. in your repo root
cp game_agent.py raccoon_tools.py requirements.txt .env.example .
cat .gitignore.additions >> .gitignore

# 2. deps  (needs Python 3.10+, a JDK, and ideally ffmpeg)
pip install -r requirements.txt

# 3. keys
cp .env.example .env    # then fill it in

# 4. describe a game
cat > my_game.txt <<'EOF'
A short dungeon crawl. The player starts in a torchlit stone entry hall,
passes through a corridor into a larger flooded chamber, and finds a locked
door at the far end. Slow, atmospheric, no combat in this first pass.
Three rooms, one skybox, ambient music, a simple HUD showing a torch meter.
EOF

# 5. go
python game_agent.py
# -> "Please type name of file that contains prompt:"  my_game.txt
```

First run, answer **n** to market research to skip straight to building —
it's faster while you're checking the plumbing works.
