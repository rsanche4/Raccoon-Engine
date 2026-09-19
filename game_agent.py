"""
Raccoon Engine Game-Building Agent — first working draft
==========================================================

Implements the whiteboard workflow:

  Terminal (prompt file)
    -> Market research (Tavily web search, togglable)
    -> Show market report to user
    -> User refines prompt (loop)
    -> Clarification needed? -> yes: ask & loop / no: build
    -> Agent builds game iteration n
       tools: Raccoon Engine src, Map Editor, Asset generation, Internet
    -> User satisfied with iteration n? -> no: n++ & rebuild / yes: package
    -> Final package: .rpk-style bundle + source + assets + dev notes

See SETUP_GUIDE.md for how to configure AWS Bedrock, Tavily, image/music
APIs, and the Raccoon Engine build endpoint. Anything not configured is
mocked locally so this script runs end-to-end out of the box.

Run:
    pip install -r requirements.txt
    python game_agent.py
"""

import base64
import json
import os
import shutil
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

import boto3
import requests
from langchain_aws import ChatBedrockConverse
from langchain_core.messages import (
    AIMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langchain_core.tools import tool

try:
    from tavily import TavilyClient
except ImportError:
    TavilyClient = None


# ---------------------------------------------------------------------------
# CONFIG — edit these or set as environment variables (see SETUP_GUIDE.md)
# ---------------------------------------------------------------------------
AWS_REGION = os.environ.get("AWS_REGION", "us-west-2")
BEDROCK_TEXT_MODEL_ID = os.environ.get(
    "BEDROCK_TEXT_MODEL_ID", "us.anthropic.claude-sonnet-4-5-20250929-v1:0"
)
BEDROCK_IMAGE_MODEL_ID = os.environ.get(
    "BEDROCK_IMAGE_MODEL_ID", "stability.stable-image-core-v1:1"
)

TAVILY_API_KEY = os.environ.get("TAVILY_API_KEY")

MUSIC_API_ENDPOINT = os.environ.get("MUSIC_API_ENDPOINT", "")
MUSIC_API_KEY = os.environ.get("MUSIC_API_KEY", "")

RACCOON_ENGINE_ENDPOINT = os.environ.get("RACCOON_ENGINE_ENDPOINT", "")
MAP_EDITOR_ENDPOINT = os.environ.get("MAP_EDITOR_ENDPOINT", "")

OUTPUT_ROOT = Path("./builds")
MAX_BUILD_STEPS = 8  # tool-call steps allowed per build iteration

# Mutable "current iteration" context the tools read from. Set fresh at the
# top of each build iteration. Simple globals keep this a single file.
CURRENT_BUILD_DIR: Optional[Path] = None
CURRENT_ITERATION: int = 0


# ---------------------------------------------------------------------------
# Bedrock clients
# ---------------------------------------------------------------------------
def get_llm(temperature: float = 0.4) -> ChatBedrockConverse:
    return ChatBedrockConverse(
        model=BEDROCK_TEXT_MODEL_ID,
        region_name=AWS_REGION,
        temperature=temperature,
    )


def get_bedrock_runtime():
    return boto3.client("bedrock-runtime", region_name=AWS_REGION)


# ---------------------------------------------------------------------------
# Tools available to the build agent
# ---------------------------------------------------------------------------
@tool
def web_search(query: str) -> str:
    """Search the internet for information that would help build the game
    (engine techniques, genre conventions, reference material). Returns a
    short text summary of the top results."""
    if not TAVILY_API_KEY or TavilyClient is None:
        return "[web_search unavailable — TAVILY_API_KEY not configured]"
    client = TavilyClient(api_key=TAVILY_API_KEY)
    results = client.search(query, max_results=4)
    lines = []
    for r in results.get("results", []):
        lines.append(f"- {r.get('title')}: {r.get('content', '')[:300]}")
    return "\n".join(lines) or "No results found."


@tool
def raccoon_engine_build(build_spec: str) -> str:
    """Build or update the Raccoon Engine game project for this iteration.
    build_spec should fully describe the scenes, entities, mechanics, and
    Lua scripts this iteration needs. Returns a description of what was
    generated and where."""
    assert CURRENT_BUILD_DIR is not None
    if RACCOON_ENGINE_ENDPOINT:
        try:
            resp = requests.post(
                RACCOON_ENGINE_ENDPOINT,
                json={"spec": build_spec, "iteration": CURRENT_ITERATION},
                timeout=120,
            )
            resp.raise_for_status()
            data = resp.json()
            out_path = CURRENT_BUILD_DIR / "engine_build_result.json"
            out_path.write_text(json.dumps(data, indent=2))
            return f"Raccoon Engine build service responded; saved to {out_path}"
        except Exception as exc:  # noqa: BLE001
            return f"Raccoon Engine build service call failed: {exc}"

    # --- Mock fallback: no real build endpoint configured yet ---
    src_dir = CURRENT_BUILD_DIR / "src"
    src_dir.mkdir(parents=True, exist_ok=True)
    (src_dir / "build_spec.md").write_text(build_spec)
    (src_dir / "main.lua").write_text(
        "-- Placeholder Raccoon Engine entry point.\n"
        "-- Replace this mock with a real call to your Raccoon Engine build\n"
        "-- service once RACCOON_ENGINE_ENDPOINT is set (see SETUP_GUIDE.md).\n"
    )
    return (
        f"[mocked] Wrote build_spec.md and main.lua to {src_dir} "
        f"(no RACCOON_ENGINE_ENDPOINT configured)"
    )


@tool
def map_editor_generate_level(level_spec: str) -> str:
    """Generate or edit a game level/map via the Raccoon Engine Map Editor.
    level_spec should describe the layout, sectors, and portals needed."""
    assert CURRENT_BUILD_DIR is not None
    if MAP_EDITOR_ENDPOINT:
        try:
            resp = requests.post(
                MAP_EDITOR_ENDPOINT,
                json={"spec": level_spec, "iteration": CURRENT_ITERATION},
                timeout=120,
            )
            resp.raise_for_status()
            data = resp.json()
            out_path = CURRENT_BUILD_DIR / "map_editor_result.json"
            out_path.write_text(json.dumps(data, indent=2))
            return f"Map Editor service responded; saved to {out_path}"
        except Exception as exc:  # noqa: BLE001
            return f"Map Editor service call failed: {exc}"

    # --- Mock fallback ---
    maps_dir = CURRENT_BUILD_DIR / "src" / "maps"
    maps_dir.mkdir(parents=True, exist_ok=True)
    (maps_dir / "level_01.map").write_text(level_spec)
    return (
        f"[mocked] Wrote level_01.map to {maps_dir} "
        f"(no MAP_EDITOR_ENDPOINT configured)"
    )


@tool
def generate_asset_image(prompt: str, filename: str) -> str:
    """Generate a game sprite/texture/icon image from a text prompt using
    Stable Image Core on Bedrock. filename should end in .png."""
    assert CURRENT_BUILD_DIR is not None
    assets_dir = CURRENT_BUILD_DIR / "assets" / "images"
    assets_dir.mkdir(parents=True, exist_ok=True)
    out_path = assets_dir / filename

    try:
        client = get_bedrock_runtime()
        resp = client.invoke_model(
            modelId=BEDROCK_IMAGE_MODEL_ID,
            body=json.dumps(
                {"prompt": prompt, "aspect_ratio": "1:1", "output_format": "png"}
            ),
        )
        body = json.loads(resp["body"].read())
        image_b64 = body["images"][0]
        out_path.write_bytes(base64.b64decode(image_b64))
        return f"Generated image saved to {out_path}"
    except Exception as exc:  # noqa: BLE001
        out_path.with_suffix(".txt").write_text(f"[mock] prompt was: {prompt}")
        return f"Image generation failed ({exc}); wrote placeholder note instead."


@tool
def generate_asset_music(prompt: str, filename: str) -> str:
    """Generate background music or a sound effect from a text prompt.
    filename should end in .mp3 or .wav."""
    assert CURRENT_BUILD_DIR is not None
    assets_dir = CURRENT_BUILD_DIR / "assets" / "audio"
    assets_dir.mkdir(parents=True, exist_ok=True)
    out_path = assets_dir / filename

    if MUSIC_API_ENDPOINT and MUSIC_API_KEY:
        try:
            resp = requests.post(
                MUSIC_API_ENDPOINT,
                headers={"Authorization": f"Bearer {MUSIC_API_KEY}"},
                json={"prompt": prompt},
                timeout=180,
            )
            resp.raise_for_status()
            out_path.write_bytes(resp.content)
            return f"Generated audio saved to {out_path}"
        except Exception as exc:  # noqa: BLE001
            return f"Music generation failed: {exc}"

    # --- Mock fallback: no music API configured yet ---
    out_path.with_suffix(".txt").write_text(
        f"[mock] No MUSIC_API_ENDPOINT configured.\nTrack prompt was: {prompt}\n"
        f"See SETUP_GUIDE.md #4 for ElevenLabs Music / Mubert setup."
    )
    return f"[mocked] Wrote placeholder note for '{filename}' (no music API configured)"


BUILD_TOOLS = [
    raccoon_engine_build,
    map_editor_generate_level,
    generate_asset_image,
    generate_asset_music,
    web_search,
]


# ---------------------------------------------------------------------------
# Manual tool-calling loop (works the same on Bedrock as any other backend,
# since Bedrock only supports client-side/custom tools — see SETUP_GUIDE.md)
# ---------------------------------------------------------------------------
def run_tool_agent(system_prompt: str, user_prompt: str, tools) -> str:
    llm = get_llm().bind_tools(tools)
    tool_map = {t.name: t for t in tools}
    messages: List[Any] = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=user_prompt),
    ]

    for _ in range(MAX_BUILD_STEPS):
        ai_msg: AIMessage = llm.invoke(messages)
        messages.append(ai_msg)

        if not getattr(ai_msg, "tool_calls", None):
            return ai_msg.content

        for call in ai_msg.tool_calls:
            tool_fn = tool_map.get(call["name"])
            if tool_fn is None:
                result = f"Unknown tool: {call['name']}"
            else:
                try:
                    result = tool_fn.invoke(call["args"])
                except Exception as exc:  # noqa: BLE001
                    result = f"Tool raised an error: {exc}"
                print(f"    [tool] {call['name']}({call['args']}) -> {result}")
            messages.append(ToolMessage(content=str(result), tool_call_id=call["id"]))

    return "(Stopped after max tool-call steps without a final summary.)"


# ---------------------------------------------------------------------------
# Workflow steps
# ---------------------------------------------------------------------------
def load_prompt_file() -> str:
    while True:
        filename = input("Please type name of file that contains prompt: ").strip()
        path = Path(filename)
        if path.exists():
            return path.read_text()
        print(f"Couldn't find '{filename}', try again.")


def run_market_research(prompt: str) -> Optional[str]:
    choice = input("Run market research before building? (y/n): ").strip().lower()
    if choice != "y":
        return None
    if not TAVILY_API_KEY or TavilyClient is None:
        print("  (TAVILY_API_KEY not set — skipping; see SETUP_GUIDE.md #2)")
        return None

    print("  Searching the web for similar games, pricing, and gaps...")
    client = TavilyClient(api_key=TAVILY_API_KEY)
    raw_chunks = []
    for query in [
        f"games similar to: {prompt[:200]}",
        f"indie game pricing {prompt[:100]}",
        f"market gap opportunities {prompt[:100]} genre",
    ]:
        try:
            res = client.search(query, max_results=4)
            for r in res.get("results", []):
                raw_chunks.append(f"[{query}] {r.get('title')}: {r.get('content', '')[:400]}")
        except Exception as exc:  # noqa: BLE001
            raw_chunks.append(f"[{query}] search failed: {exc}")

    llm = get_llm()
    synthesis_prompt = (
        "You are helping a solo game developer. Below are raw web search "
        "results about similar games, pricing, and market gaps for their "
        "game idea. Turn this into a short market report, organized as one "
        "short section PER comparable game you can identify (name, how "
        "it's similar, typical price, one notable gap/opportunity). Keep it "
        "concise.\n\nGame idea:\n"
        f"{prompt}\n\nRaw search results:\n" + "\n".join(raw_chunks)
    )
    report = llm.invoke([HumanMessage(content=synthesis_prompt)]).content
    return report


def refine_prompt(current_prompt: str, market_report: Optional[str],
                   clarification_question: Optional[str]) -> str:
    if market_report:
        print("\n=== Market Research Report ===")
        print(market_report)
        print("===============================\n")
    if clarification_question:
        print(f"\nThe agent needs clarification: {clarification_question}")
    else:
        print("\nCurrent game prompt:\n" + current_prompt)

    print(
        "\nEnter a refined prompt (adjustments, answer to the clarification, "
        "or notes on how you edited the game yourself)."
    )
    print("Leave blank to keep the current prompt as-is.")
    addition = input("> ").strip()
    if not addition:
        return current_prompt
    return current_prompt + "\n\n[User refinement]\n" + addition


def needs_clarification(prompt: str) -> Optional[str]:
    llm = get_llm(temperature=0)
    check_prompt = (
        "You are about to build a game based on the prompt below using the "
        "Raccoon Engine (a Java retro raycasting engine). Decide if you have "
        "enough information to start building a first playable iteration, or "
        "if you need one clarifying question first. Respond with ONLY a JSON "
        'object: {"needs_clarification": true/false, "question": "..."} '
        "(question can be empty string if false).\n\nPrompt:\n" + prompt
    )
    raw = llm.invoke([HumanMessage(content=check_prompt)]).content.strip()
    try:
        if raw.startswith("```"):
            raw = raw.strip("`").split("\n", 1)[-1]
        data = json.loads(raw)
        if data.get("needs_clarification"):
            return data.get("question") or "Could you add more detail?"
        return None
    except Exception:  # noqa: BLE001
        return None  # if parsing fails, don't block the loop — just build


def build_iteration(prompt: str, iteration: int, game_slug: str) -> Path:
    global CURRENT_BUILD_DIR, CURRENT_ITERATION
    CURRENT_ITERATION = iteration
    CURRENT_BUILD_DIR = OUTPUT_ROOT / game_slug / f"iteration_{iteration}"
    CURRENT_BUILD_DIR.mkdir(parents=True, exist_ok=True)

    system_prompt = (
        "You are the build agent for a Raccoon Engine game project (a "
        "Java-based retro raycasting engine inspired by DOOM). You have "
        "tools to build engine source, generate a level via the map editor, "
        "generate image assets, generate music/audio assets, and search the "
        "web for reference. Use the tools to actually build iteration "
        f"{iteration} of the game described below. Call whichever tools you "
        "need (you may call several), then finish with a short plain-text "
        "summary of what you built."
    )
    print(f"\n--- Building iteration {iteration} ---")
    summary = run_tool_agent(system_prompt, prompt, BUILD_TOOLS)
    (CURRENT_BUILD_DIR / "ITERATION_SUMMARY.md").write_text(summary)
    print(f"\nIteration {iteration} summary:\n{summary}\n")
    return CURRENT_BUILD_DIR


def check_satisfaction(iteration_dir: Path, iteration: int):
    print(f"Files generated this iteration ({iteration_dir}):")
    for p in sorted(iteration_dir.rglob("*")):
        if p.is_file():
            print(f"  - {p.relative_to(iteration_dir)}")
    answer = input(f"\nAre you satisfied with iteration {iteration}? (y/n): ").strip().lower()
    return answer == "y"


def package_release(game_slug: str, final_iteration_dir: Path, iteration_notes: List[str]):
    release_dir = OUTPUT_ROOT / game_slug / "release"
    release_dir.mkdir(parents=True, exist_ok=True)

    # Copy the final iteration's contents into the release folder.
    for item in final_iteration_dir.iterdir():
        dest = release_dir / item.name
        if item.is_dir():
            shutil.copytree(item, dest, dirs_exist_ok=True)
        else:
            shutil.copy2(item, dest)

    notes_path = release_dir / "DEVELOPMENT_NOTES.md"
    notes_path.write_text(
        f"# Development Notes — {game_slug}\n\n"
        + "\n\n".join(iteration_notes)
        + "\n\n## Summary\n"
        f"Built over {len(iteration_notes)} iteration(s) using the Raccoon "
        "Engine agent pipeline (market research -> refinement -> build -> "
        "iterate). Package includes source, assets, and this notes file. "
        "NOTE: map-editor and engine-build integrations are mocked until "
        "real endpoints are configured — see SETUP_GUIDE.md."
    )

    # Zip the release folder and label it .rpk (placeholder convention —
    # swap for Raccoon Engine's real export/package format once you have it).
    zip_base = OUTPUT_ROOT / game_slug / f"{game_slug}_release"
    shutil.make_archive(str(zip_base), "zip", root_dir=release_dir)
    rpk_path = zip_base.with_suffix(".rpk")
    Path(str(zip_base) + ".zip").rename(rpk_path)

    print(f"\nPackaged release: {rpk_path}")
    print(f"Unpacked contents also available at: {release_dir}")
    return rpk_path


# ---------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------
def main():
    print("=== Raccoon Engine Game-Building Agent ===\n")

    raw_prompt = load_prompt_file()
    market_report = run_market_research(raw_prompt)
    current_prompt = refine_prompt(raw_prompt, market_report, None)

    # Clarification loop
    while True:
        question = needs_clarification(current_prompt)
        if not question:
            break
        current_prompt = refine_prompt(current_prompt, None, question)

    # Derive a short slug for folder/zip naming.
    llm = get_llm(temperature=0)
    slug_raw = llm.invoke(
        [HumanMessage(content=(
            "Give a short lowercase-hyphenated slug (2-4 words, no punctuation "
            "besides hyphens) for a game with this concept:\n" + current_prompt
        ))]
    ).content.strip().lower()
    game_slug = "".join(c if c.isalnum() or c == "-" else "" for c in slug_raw) or "game-project"

    iteration = 1
    iteration_notes = []
    while True:
        iteration_dir = build_iteration(current_prompt, iteration, game_slug)
        iteration_notes.append(
            f"### Iteration {iteration}\n{(iteration_dir / 'ITERATION_SUMMARY.md').read_text()}"
        )
        if check_satisfaction(iteration_dir, iteration):
            break
        iteration += 1
        current_prompt = refine_prompt(current_prompt, None, None)

    package_release(game_slug, iteration_dir, iteration_notes)
    print("\nDone.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("\nInterrupted.")
