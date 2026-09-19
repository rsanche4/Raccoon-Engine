# Complete Setup Guide — Zero to Your First Game

Assumes you have nothing: no AWS account, no keys, no idea what a Bedrock is.
About 40 minutes, most of it waiting on AWS approval.

**No `.gitignore`, no `.env`.** Keys live in a text file somewhere else on
your computer. Delete `.env.example` and `.gitignore.additions` from the repo
— they aren't used anymore.

---

## What you actually need

| | Needed? | Cost |
|---|---|---|
| Python 3.10+ | yes | free |
| A JDK (for `javac`) | yes | free |
| AWS account + Bedrock | **yes** — the agent's brain | pennies per build |
| Tavily | no | free tier |
| Replicate | no | skip it |
| ffmpeg | no | free |

**AWS is the only account you have to make.** Art and music are free and
offline now.

---

## Step 1 — Python and Java

### Python

Windows: [python.org/downloads](https://www.python.org/downloads/) — **tick
"Add python.exe to PATH"** on the first installer screen. Everyone else
probably has it.

```
python --version
```

### JDK (not just Java)

You need `javac`, the compiler. Having `java` isn't enough — the agent
recompiles the engine on every build.

```
javac -version
```

If that fails:
- **Windows:** [adoptium.net](https://adoptium.net) → Temurin 21 (LTS) →
  `.msi`. During install, enable **"Set JAVA_HOME variable"**. Then close and
  reopen your terminal — PATH changes don't apply to windows already open.
- macOS: `brew install openjdk`
- Ubuntu: `sudo apt install default-jdk`

### Dependencies

```
cd Raccoon-Engine
pip install -r requirements.txt
```

### Check

```
python preflight.py
```

Sections 2–5 should be green — including the engine compiling, the map builder
producing sectors, and free asset generation. **All of that works with zero
accounts.** Only section 6 (AWS) will fail. Get here before signing up for
anything.

---

## Step 2 — Your keys file

```
python raccoon_keys.py --create
```

That writes `raccoon_keys.txt` to your home folder
(`C:\Users\YourName\raccoon_keys.txt` on Windows) — **outside the repo**, so
git can never see it. Open it in Notepad. You'll fill it in over the next two
steps.

Want it somewhere else? Put it at `C:\raccoon_keys.txt`, or set a
`RACCOON_KEYS` environment variable to any path. Run `python raccoon_keys.py`
to see every location it checks.

---

## Step 3 — AWS (the one account you need)

This is the fiddly part. Take it slowly.

### 3a. Make the account

1. [aws.amazon.com](https://aws.amazon.com) → **Create an AWS Account**
2. Needs email, password, and a card. No charge for the account itself.
3. Pick the **Basic support plan** (free) when offered.

### 3b. Set a spending alarm — do this now

1. Search **Billing** in the top bar → **Budgets** → **Create budget**
2. Template: **Monthly cost budget**, set $10, enter your email
3. Create

Two minutes, and it means a mistake emails you instead of surprising you.

### 3c. Make a user for the agent

Don't use your root login's keys — they can do anything to your account and
can't be limited.

1. Search **IAM** → **Users** → **Create user**
2. Name: `raccoon-agent`. **Don't** tick console access. Next.
3. **Attach policies directly** → **Create policy** (opens a new tab)
4. Click the **JSON** tab, delete what's there, paste:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "bedrock:InvokeModel",
      "bedrock:InvokeModelWithResponseStream"
    ],
    "Resource": "*"
  }]
}
```

5. Next → name it `RaccoonBedrockAccess` → Create policy
6. Back in the original tab, hit the refresh icon, search for
   `RaccoonBedrockAccess`, tick it → Next → Create user

### 3d. Get the keys

1. Click into `raccoon-agent` → **Security credentials** tab
2. **Create access key** → **Application running outside AWS** → Next → Create
3. You now see an **Access key** and a **Secret access key**

**The secret is shown exactly once.** Paste both into `raccoon_keys.txt` right
now:

```
AWS_REGION=us-west-2
AWS_ACCESS_KEY_ID=AKIA...
AWS_SECRET_ACCESS_KEY=...
```

If you lose it, delete the key and make a new one. There's no recovery.

### 3e. Turn on Claude

Most Bedrock models switch on by themselves. Anthropic's are the exception.

1. Search **Bedrock** in the top bar
2. **Check the region** in the top-right corner — set it to **US West
   (Oregon) us-west-2**. Model availability is per-region, and this is the one
   your keys file names.
3. Sidebar → **Model catalog**
4. Find a **Claude** model → **Request access**
5. Fill the short use-case form. *"Internal developer tool that generates game
   content"* is a fine answer.
6. Submit. Usually approved within minutes — refresh until it says access
   granted.

### 3f. Check

```
python preflight.py
```

Section 6 should now be green, ending with Claude replying "ready". That's one
real API call, costing a fraction of a cent.

**If it fails:**

| Message | What it means |
|---|---|
| `AccessDeniedException` | Step 3e not done, not approved yet, or wrong region |
| `ResourceNotFoundException` | Model not in your region — use `us-west-2`, keep the `us.` prefix on the model ID |
| `UnrecognizedClientException` | Key typo. Re-copy both values |
| `InvalidSignatureException` | Secret got truncated. Make a new key pair |

---

## Step 4 — Tavily (optional, free, 2 minutes)

Only needed for market research and the agent's web lookups. It builds games
fine without it.

[tavily.com](https://tavily.com) → sign up (no card) → copy the `tvly-...` key
into your keys file:

```
TAVILY_API_KEY=tvly-...
```

---

## Step 5 — Write your game prompt

One text file in the repo folder. The more specific, the better the result.

```
GAME: Cellar

A short exploration game. No combat in iteration one.

LAYOUT (align rooms to shared coordinates)
- Entry hall: x 0-12, z 0-12. Dim, brightness 0.35.
- Corridor:   x 12-16, z 4-8. Narrow.
- Chamber:    x 16-28, z 0-12. Brighter, 0.5. Lower floor than corridor.
Doorways: entry->corridor at x=12 z 5-7; corridor->chamber at x=16 z 5-7.

ASSETS
- Textures: damp stone wall, mossy flagstone floor, wooden ceiling beams
- Sprites: a rat that patrols the chamber (3 animation frames)
- Music: slow dark ambient drone
- A door thud sound effect

SCRIPTS
- init.lua: player starts in the entry hall, loads the map, starts music
- HUD showing the current room name top-left
- Rat patrol behaviour script

ITERATION ONE
Three rooms walkable end to end, correct textures, rat animating and
moving, music playing, HUD visible.
```

Save it as `my_game.txt`.

---

## Step 6 — Run it

```
python game_agent.py
```

1. `Please type name of file that contains prompt:` → `my_game.txt`
2. `Run market research first? (y/n)` → **n** the first time. Go straight to
   building while you check everything works.
3. It may ask one clarifying question. Answer, or press Enter to skip.
4. Watch the tool calls stream past: `[make_texture]`, `[make_map]`,
   `[build_engine]`, `[smoke_test]`.
5. **A game window opens during `smoke_test`.** That's the agent checking its
   own work — let it run, it closes after ~20 seconds.
6. `Are you satisfied with iteration 1? (y/n)`

**n** lets you refine and rebuild. **y** packs `data.rpk` and writes
`releases/<slug>/` with `run.sh` / `run.bat`.

### Play it yourself any time

```
cd RaccoonEngineV2
java -cp "bin;lib/*" raccoon.Main      (Windows — semicolon)
java -cp "bin:lib/*" raccoon.Main      (macOS/Linux — colon)
```

---

## What changed this round

### Art and music are free now

Default is `ASSET_PROVIDER=placeholder`: everything is drawn and synthesised
locally in Python. No key, no network, no cost.

Your 40-sprites-at-3-frames case is 960 sprite facings. Measured:

| | Time | Cost |
|---|---|---|
| placeholder | 0.06 seconds | $0.00 |
| bedrock | many minutes | ~$38 |

Procedural is also **better** at the thing AI is worst at here. Ask a
diffusion model for "goblin, side view" then "goblin, back view" and you get
two different goblins. A drawn figure rotated 45° is the same figure. The
facings read correctly — eyes from the front, back-of-head from behind, torso
narrowing edge-on, and a coloured nub at the feet marking direction.

Textures key off words in the description: "mossy stone wall" → green
brickwork, "rusted metal panel" → rust-coloured plate, "flagstone tile" →
checkered tile, "still water" → blue waves. Six pattern types, palette-
quantized to your engine's exact 256 colours.

Music is synthesised: a drone plus a slow arpeggio, minor key if the
description says anything like dark/tense/dungeon, major otherwise. Sound
effects pick between thud/zap/chime/blip from keywords. Real playable audio,
not silence, so you can judge pacing.

All of it is deterministic — the same description always produces the same
asset, so rebuilding doesn't reshuffle your art.

Want real AI art later? One line in your keys file:

```
ASSET_PROVIDER=bedrock        # paid, best quality, ~$0.04/image
ASSET_PROVIDER=pollinations   # free online AI, no key, slower
```

The finishing pipeline is identical either way, so switching never breaks
compatibility.

### Animation frames

`make_sprite_sheet(..., frames=3)` writes `rat_0.png`, `rat_1.png`,
`rat_2.png`. The sheet format has no room for animation (width must be exactly
8 × height), so each frame is its own file. Swap between them from Lua with
`entityUpsertSprite` using the same sprite id and a different `spritename`.

### Sector limit removed

No hard cap anymore. The 40-scattered-rooms case that used to be rejected now
builds — 3,160 sectors — with a warning instead.

The warning matters, though, because of something I found in your engine:
`worldLoadMap` allocates `Screen.sectors` from `MAX_NUM_SECTORS` on entry, so
past the default 1024 it **silently stops** rather than erroring. Tested:

```
default limit = 1024
without raising it:                    1024 sectors loaded  (2,136 missing)
after worldSetSectorCountLimit(3224):  3160 sectors in 99ms
```

So the fix is a Lua call, and the map builder now prints the exact one:

```lua
RA:worldSetSectorCountLimit(3224)   -- BEFORE worldLoadMap
RA:worldLoadMapAsync("map.txt", "LOADING")
```

The agent knows this rule and emits the call when its map needs it.

One real limit worth keeping in mind: `portal_collision_data` is allocated as
`sectors_count²` booleans. 2,000 sectors is 4 MB, 10,000 is 100 MB, 30,000 is
900 MB and will fall over. The warning tells you the number. Aligning rooms to
shared coordinates is what keeps it down — every distinct x or z value cuts a
grid line across the entire map.

---

## Files

**New:** `raccoon_keys.py`, `raccoon_assets.py`
**Changed:** `raccoon_tools.py`, `game_agent.py`, `preflight.py`
**Delete:** `.env.example`, `.gitignore.additions`

---

## Quick reference

```
python raccoon_keys.py --create    make the keys file
python raccoon_keys.py             show where it looks and what it found
python preflight.py                check everything
python game_agent.py               build a game
```

Everything except section 6 of preflight works with no accounts at all.
