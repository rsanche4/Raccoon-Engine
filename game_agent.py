"""
Raccoon Engine Game-Building Agent
==================================

Terminal (prompt file)
  -> Market research (Tavily, togglable)
  -> Show market report
  -> User refines prompt
  -> Clarification needed? -> yes: ask & loop / no: build
  -> Agent builds iteration n  (assets, map, Lua, compile, smoke test)
  -> Satisfied with n? -> no: n++ & rebuild / yes: package data.rpk
  -> Final bundle + development notes

All tools are live — see raccoon_tools.py. Setup: SETUP_GUIDE.md.

    pip install -r requirements.txt
    python game_agent.py
"""

import json
import os
import shutil
import sys
from pathlib import Path
from typing import Any, List, Optional

from langchain_aws import ChatBedrockConverse
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import tool

import raccoon_tools as rt

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

try:
    from tavily import TavilyClient
except ImportError:
    TavilyClient = None


AWS_REGION = os.environ.get("AWS_REGION", "us-west-2")
BEDROCK_TEXT_MODEL_ID = os.environ.get(
    "BEDROCK_TEXT_MODEL_ID", "us.anthropic.claude-sonnet-4-5-20250929-v1:0"
)
TAVILY_API_KEY = os.environ.get("TAVILY_API_KEY")

MAX_BUILD_STEPS = 40   # tool calls allowed per build iteration
BUILD_LOG: List[str] = []


def get_llm(temperature: float = 0.4) -> ChatBedrockConverse:
    return ChatBedrockConverse(
        model=BEDROCK_TEXT_MODEL_ID, region_name=AWS_REGION, temperature=temperature
    )


# ===========================================================================
# What the agent needs to know about the engine to write correct content.
# Pulled from RaccoonAPI.java, ResourceManager.java and the data/scripts
# examples — keep it in sync if the engine's API changes.
# ===========================================================================
ENGINE_BRIEF = """
RACCOON ENGINE REFERENCE
========================

The engine is a Java retro raycaster (DOOM-style sectors and portals, not
true 3D). Content is data + Lua. You never write Java.

ASSET FOLDERS (data/), each with a required extension:
  tex/ .png      wall, floor and ceiling textures
  sprites/ .png  8-direction sprite sheets (width MUST equal 8 * height)
  skybox/ .png   2560x880 exactly
  pics/ .png     full-screen images (title cards, HUD art)
  bgm/ .wav      music        se/ .wav   sound effects
  maps/ .txt     levels       scripts/ .lua
  fonts/ .ttf    system_font.ttf already exists
All images are snapped to a fixed 256-colour palette at load time, so art
reads as retro pixel art regardless of what you ask for.

MAPS. You never write map text or place individual walls. You describe
rooms and doorways; make_map runs Manhattan partitioning (the same algorithm
the Map Editor uses) to turn them into a legal sector graph.

  rooms:    id (0..n-1, no gaps), x1, z1, x2, z2 (integer grid corners),
            floor_height, ceil_height, floor_tex, ceil_tex, wall_tex,
            brightness (0..1), skip_ceiling (true = open sky)
  doorways: sector_a, sector_b, x1, z1, x2, z2, solid

Rules that matter, because make_map enforces them and will reject you:
  * A room is a REGION, not one sector. Partitioning splits it into several
    sectors with invisible seams between them. That is normal. Do not try to
    compensate for it.
  * Two rooms can only be joined by a doorway if they are FLUSH — sharing an
    exact wall line. Rooms with a gap between them cannot have a doorway;
    put a corridor room in between instead.
  * A doorway must be axis-aligned (x1==x2 or z1==z2) and must lie on the
    shared line.
  * Every distinct x or z coordinate you use cuts a grid line across the
    WHOLE map, and every grid cell becomes a sector. So rooms at sloppy
    offsets multiply the sector count fast and can blow the 1024 limit.
    Align rooms to shared coordinates wherever you can.
  * Room 0's corner should sit at or near the origin; the world rectangle is
    measured from (0,0).

make_map also writes an editor project file so the user can open your layout
in the Map Editor and adjust it by hand.

LUA. Two script styles, both valid:

  (a) PLAIN — the whole file body runs every frame.
  (b) update() — the body runs ONCE as setup, then only update() runs each
      frame. This is the cheaper and more usual style. Ordinary locals
      declared in the body survive between frames.

init.lua is the startup script. It should be plain style, do its setup, spawn
the scripts the game needs, and then remove itself:

    local script_index = ...
    RA:playerSetPosition(2, 2, 2, 0)
    RA:playerSetWalk(2, 0.15, 0.03)
    RA:worldSetSkybox("sky.png", 0.5)
    RA:worldLoadMapAsync("map.txt", "LOADING")
    RA:scriptAdd("hud.lua", 10)
    RA:scriptEnd(script_index)

The scriptEnd call is what stops init.lua running again on frame 2. Always
include it. Per-frame scripts use update() instead and do NOT call scriptEnd.

Use worldLoadMapAsync (with a loading message) rather than worldLoadMap for
anything bigger than a test room — it loads on a background thread behind a
loading screen. You can call it again later from any script to change level,
so multi-level games do not need a new init.lua per level; write one
level-manager script with an update() that loads the next map when needed.

KEY API (RA:...)
  world:  worldLoadMap(name) / worldLoadMapAsync(name, loading_text)
          worldSetSkybox(name, brightness) / worldSetSpriteRenderDistance(d)
  player: playerSetPosition(x, y, z, dir) / playerSetWalk(floor_offset,
          bob_speed, bob_amount) / playerSetFly() / playerSetGravity(g)
          playerGetPosition(dim) / playerGetSector() / playerGetDirection()
  entity: entityUpsertSprite(id, x, y, z, length, brightness, spritename,
          behavior_script, radius, dir)   -- same id updates in place
          entityRemoveSprite(id) / entityCount()
  ui:     uiText(text,x,y,font,brightness) / uiTextDefault(text,x,y,bright)
          uiFillRect(x,y,w,h,palette_index,brightness) / uiDraw(pic,x,y,scale,b)
          uiTextWidth(text,font) / uiScreenWidth() / uiScreenHeight()
  audio:  audioPlayBGM(name, loop, volume) / audioPlaySE(name, loop, volume)
          audioStopBGM() / audioChangeBGMVol(v)
  input:  inputGetKeyStatus(is_once, keyname)   -- is_once = fire per press
  state:  storeSet(key, val) / storeGet(key) / storeSaveGameState(file)
  script: scriptAdd(name, priority) / scriptEnd(index) / scriptReload(name)
  system: systemLog(msg, where) / systemGetFrameNumber() / systemQuit()

BRIGHTNESS is 0..1 where 0.5 is true colour, below fades to black, above
fades to white. Use 0.5 unless you want a lighting effect.

SPRITE BEHAVIOUR SCRIPTS receive the sprite's state as arguments:
  local x, y, z, length, brightness, spritename, id, radius, dir = ...
Every sprite sharing one behaviour file shares one Lua environment, so keep
per-entity state in the store keyed by id, and write new state back with
entityUpsertSprite using the same id.
"""


# ===========================================================================
# Tools
# ===========================================================================
def _log(msg: str) -> str:
    BUILD_LOG.append(msg)
    return msg


@tool
def list_existing_assets() -> str:
    """List every asset the engine can currently see, by folder. Check this
    before generating anything so you reuse what already exists."""
    return rt.list_assets()


@tool
def make_texture(description: str, filename: str, size: int = 64) -> str:
    """Generate a tileable wall/floor/ceiling texture into data/tex/.
    size should be a power of two; 32 or 64 is typical."""
    return _log(rt.generate_texture(description, filename, size))


@tool
def make_sprite_sheet(description: str, filename: str, size: int = 64) -> str:
    """Generate an 8-direction character/object sprite sheet into
    data/sprites/. Handles the 8 facings and required dimensions for you.
    Costs 8 image generations, so use it only for things that need to be
    seen from multiple angles."""
    return _log(rt.generate_sprite_sheet(description, filename, size))


@tool
def make_skybox(description: str, filename: str) -> str:
    """Generate a skybox into data/skybox/ at the required 2560x880."""
    return _log(rt.generate_skybox(description, filename))


@tool
def make_pic(description: str, filename: str) -> str:
    """Generate a full-screen 640x480 image into data/pics/ for title
    screens, HUD panels or cutscene art."""
    return _log(rt.generate_pic(description, filename))


@tool
def make_audio(description: str, filename: str, folder: str = "bgm",
               duration: int = 20) -> str:
    """Generate music or a sound effect as .wav. folder is 'bgm' for music
    or 'se' for sound effects. Keep sound effects short (2-4 seconds)."""
    return _log(rt.generate_audio(description, filename, folder, duration))


@tool
def make_map(spec: dict, filename: str = "map.txt") -> str:
    """Write a level into data/maps/. spec is a dict with 'rooms' and
    'doorways' as described in the engine reference. Validation is strict:
    if it returns an error, fix the geometry and call again."""
    try:
        return _log(rt.write_map(spec, filename))
    except Exception as exc:  # noqa: BLE001
        return f"MAP REJECTED: {exc}"


@tool
def write_lua(source: str, filename: str) -> str:
    """Write a Lua script into data/scripts/. init.lua is the entry point."""
    return _log(rt.write_script(source, filename))


@tool
def build_engine() -> str:
    """Compile the engine. Run this before smoke testing."""
    return _log(rt.compile_engine())


@tool
def smoke_test(seconds: int = 20) -> str:
    """Launch the game for a few seconds and report anything the engine
    logged. Use this to confirm the map and assets actually load — missing
    textures and malformed maps show up here."""
    return _log(rt.run_engine(timeout=seconds))


@tool
def web_search(query: str) -> str:
    """Search the web for reference material, genre conventions or technique
    while building."""
    if not TAVILY_API_KEY or TavilyClient is None:
        return "[web_search unavailable — TAVILY_API_KEY not set]"
    res = TavilyClient(api_key=TAVILY_API_KEY).search(query, max_results=4)
    return "\n".join(
        f"- {r.get('title')}: {r.get('content','')[:300]}"
        for r in res.get("results", [])
    ) or "No results."


BUILD_TOOLS = [
    list_existing_assets, make_texture, make_sprite_sheet, make_skybox,
    make_pic, make_audio, make_map, write_lua, build_engine, smoke_test,
    web_search,
]


# ===========================================================================
# Tool-calling loop. Bedrock only supports client-side tools, so we run the
# loop ourselves rather than relying on a server-side agent runtime.
# ===========================================================================
def run_tool_agent(system_prompt: str, user_prompt: str, tools) -> str:
    llm = get_llm().bind_tools(tools)
    tool_map = {t.name: t for t in tools}
    messages: List[Any] = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=user_prompt),
    ]

    for _ in range(MAX_BUILD_STEPS):
        ai: AIMessage = llm.invoke(messages)
        messages.append(ai)

        if not getattr(ai, "tool_calls", None):
            return ai.content if isinstance(ai.content, str) else str(ai.content)

        for call in ai.tool_calls:
            fn = tool_map.get(call["name"])
            if fn is None:
                result = f"Unknown tool: {call['name']}"
            else:
                print(f"    [{call['name']}] ...", flush=True)
                try:
                    result = fn.invoke(call["args"])
                except Exception as exc:  # noqa: BLE001
                    result = f"Tool error: {exc}"
                first = str(result).splitlines()[0] if str(result).strip() else ""
                print(f"      {first[:150]}")
            messages.append(ToolMessage(content=str(result), tool_call_id=call["id"]))

    return "(Hit the tool-call limit before finishing. Partial build is on disk.)"


# ===========================================================================
# Workflow
# ===========================================================================
def load_prompt_file() -> str:
    while True:
        name = input("Please type name of file that contains prompt: ").strip()
        p = Path(name)
        if p.exists():
            return p.read_text()
        print(f"Couldn't find '{name}'. Try again.")


def run_market_research(prompt: str) -> Optional[str]:
    if input("Run market research first? (y/n): ").strip().lower() != "y":
        return None
    if not TAVILY_API_KEY or TavilyClient is None:
        print("  TAVILY_API_KEY not set — skipping (see SETUP_GUIDE.md).")
        return None

    print("  Searching for similar games, pricing and gaps...")
    client = TavilyClient(api_key=TAVILY_API_KEY)
    chunks = []
    for q in (
        f"indie games similar to: {prompt[:200]}",
        f"indie game pricing {prompt[:100]}",
        f"underserved market gaps {prompt[:100]} genre",
    ):
        try:
            for r in client.search(q, max_results=4).get("results", []):
                chunks.append(f"[{q}] {r.get('title')}: {r.get('content','')[:400]}")
        except Exception as exc:  # noqa: BLE001
            chunks.append(f"[{q}] failed: {exc}")

    return get_llm().invoke([HumanMessage(content=(
        "Below are raw web results about a solo developer's game idea. Write "
        "a short market report with ONE SECTION PER comparable game: name, "
        "how it's similar, typical price, and the gap it leaves open. Finish "
        "with two or three sentences on where this idea could differentiate. "
        "Be concise.\n\nGame idea:\n" + prompt +
        "\n\nRaw results:\n" + "\n".join(chunks)
    ))]).content


def refine_prompt(current: str, report: Optional[str], question: Optional[str]) -> str:
    if report:
        print("\n=== Market Research Report ===")
        print(report)
        print("==============================\n")
    if question:
        print(f"\nThe agent needs clarification: {question}")
    print("\nRefine the prompt (answer, adjustments, or notes on edits you made "
          "to the game yourself). Blank keeps it as-is.")
    extra = input("> ").strip()
    return current + "\n\n[User refinement]\n" + extra if extra else current


def needs_clarification(prompt: str) -> Optional[str]:
    raw = get_llm(temperature=0).invoke([HumanMessage(content=(
        "You are about to build a game in the Raccoon Engine (a retro "
        "raycaster with sector-based levels, 8-direction sprites and Lua "
        "scripting). Do you have enough to build a first playable iteration, "
        "or do you need ONE clarifying question? Reply with only JSON: "
        '{"needs_clarification": bool, "question": string}\n\nPrompt:\n' + prompt
    ))]).content.strip()
    try:
        if raw.startswith("```"):
            raw = raw.split("\n", 1)[1].rsplit("```", 1)[0]
        data = json.loads(raw)
        if data.get("needs_clarification"):
            return data.get("question") or None
        return None
    except Exception:  # noqa: BLE001
        return None


def build_iteration(prompt: str, n: int) -> str:
    BUILD_LOG.clear()
    system = (
        f"You are the build agent for a Raccoon Engine game, working on "
        f"iteration {n}.\n{ENGINE_BRIEF}\n"
        "Build the game described below by calling tools, in this order:\n"
        "1. list_existing_assets, so you reuse what's there.\n"
        "2. Generate only the assets this iteration genuinely needs. Image "
        "generation costs money — a sprite sheet is 8 generations. Prefer "
        "reusing a texture over making a near-duplicate.\n"
        "3. make_map for the level.\n"
        "4. write_lua for init.lua and any behaviour or HUD scripts.\n"
        "5. build_engine, then smoke_test.\n"
        "6. If smoke_test reports errors, fix them and test again.\n"
        "Finish with a short plain-text summary of what this iteration adds "
        "and what you would do next."
    )
    print(f"\n--- Building iteration {n} ---")
    summary = run_tool_agent(system, prompt, BUILD_TOOLS)
    print(f"\nIteration {n} summary:\n{summary}\n")
    return summary


def package_release(slug: str, notes: List[str]) -> None:
    print("\nPackaging...")
    print("  " + rt.pack_rpk())

    release = rt.REPO_ROOT / "releases" / slug
    release.mkdir(parents=True, exist_ok=True)

    rpk = rt.ENGINE_DIR / "data.rpk"
    if rpk.exists():
        shutil.copy2(rpk, release / "data.rpk")
    if rt.ENGINE_BIN.exists():
        shutil.copytree(rt.ENGINE_BIN, release / "bin", dirs_exist_ok=True)
    if rt.ENGINE_LIB.exists():
        shutil.copytree(rt.ENGINE_LIB, release / "lib", dirs_exist_ok=True)
    shutil.copytree(rt.DATA_DIR, release / "data", dirs_exist_ok=True)

    (release / "run.sh").write_text(
        '#!/bin/sh\ncd "$(dirname "$0")"\njava -cp "bin:lib/*" raccoon.Main\n'
    )
    (release / "run.bat").write_text(
        '@echo off\ncd /d "%~dp0"\njava -cp "bin;lib/*" raccoon.Main\n'
    )
    os.chmod(release / "run.sh", 0o755)

    (release / "DEVELOPMENT_NOTES.md").write_text(
        f"# {slug}\n\nBuilt with the Raccoon Engine game agent.\n\n"
        + "\n\n".join(notes)
        + "\n\n## Running\n`./run.sh` (macOS/Linux) or `run.bat` (Windows). "
        "Needs a JRE.\n\n## Shipping\n`data.rpk` bundles every asset into one "
        "file — ship it instead of the loose `data/` folder. The engine "
        "prefers `data/` when both are present, so delete `data/` from the "
        "shipped copy.\n"
    )
    print(f"  release -> {release}")


def main() -> None:
    print("=== Raccoon Engine Game-Building Agent ===\n")
    if not rt.ENGINE_DIR.is_dir():
        sys.exit(f"Can't find {rt.ENGINE_DIR}. Run this from the repo root, "
                 f"or set RACCOON_REPO to point at it.")

    raw = load_prompt_file()
    report = run_market_research(raw)
    prompt = refine_prompt(raw, report, None)

    while True:
        q = needs_clarification(prompt)
        if not q:
            break
        prompt = refine_prompt(prompt, None, q)

    slug_raw = get_llm(temperature=0).invoke([HumanMessage(content=(
        "Give a short lowercase-hyphenated slug (2-4 words) for this game:\n" + prompt
    ))]).content.strip().lower()
    slug = "".join(c for c in slug_raw if c.isalnum() or c == "-") or "raccoon-game"

    n = 1
    notes: List[str] = []
    while True:
        summary = build_iteration(prompt, n)
        notes.append(
            f"### Iteration {n}\n{summary}\n\n<details><summary>tool log</summary>"
            f"\n\n```\n" + "\n".join(BUILD_LOG) + "\n```\n</details>"
        )
        if input(f"Are you satisfied with iteration {n}? (y/n): ").strip().lower() == "y":
            break
        n += 1
        prompt = refine_prompt(prompt, None, None)

    package_release(slug, notes)
    print("\nDone.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nInterrupted.")
