"""
preflight.py — check your setup before running the agent.

    python preflight.py

Tells you what works, what doesn't, and what to do about each failure.
Costs nothing beyond two tiny requests to whichever model you chose.
"""

import shutil
import subprocess
import sys
from pathlib import Path

import raccoon_config as cfg
import raccoon_keys

OK, WARN, FAIL = "  [ OK ]", "  [WARN]", "  [FAIL]"
counts = {"ok": 0, "warn": 0, "fail": 0}


def report(level, msg, fix=None):
    print(f"{level} {msg}")
    if fix and level != OK:
        for line in str(fix).strip().splitlines():
            print(f"         {line}")
    counts["ok" if level == OK else "warn" if level == WARN else "fail"] += 1


def section(name):
    print(f"\n{name}\n" + "-" * len(name))


print("Raccoon Engine agent — preflight\n")
print(cfg.describe())


# ------------------------------------------------------------------ 1. keys
section("1. Keys file")

path = raccoon_keys.loaded_from()
if path is None:
    report(FAIL, "No keys file found",
           "python raccoon_keys.py --create\n"
           "Then open it and fill in your settings.\nLooked in:\n  "
           + "\n  ".join(str(p) for p in raccoon_keys.candidate_paths()))
else:
    report(OK, f"Found: {path}")
    try:
        inside = Path(path).resolve().is_relative_to(Path.cwd().resolve())
    except (AttributeError, ValueError):
        inside = str(Path(path).resolve()).startswith(str(Path.cwd().resolve()))
    report(FAIL if inside else OK,
           "Keys file is INSIDE the repo — move it out" if inside
           else "Outside the repo, so git can never see it",
           "Move it to your home folder or C:\\raccoon_keys.txt" if inside else None)


# ---------------------------------------------------------- 2. local tools
section("2. Local tools")

v = sys.version_info
if v >= (3, 10):
    report(OK, f"Python {v.major}.{v.minor}")
    if v >= (3, 14):
        report(WARN, f"Python {v.major}.{v.minor} is very new",
               "Some packages may not have prebuilt wheels yet. If a pip\n"
               "install fails trying to compile something, install Python\n"
               "3.12 alongside it and use that.")
else:
    report(FAIL, f"Python {v.major}.{v.minor} — need 3.10+")

if shutil.which("javac"):
    r = subprocess.run(["javac", "-version"], capture_output=True, text=True)
    report(OK, f"javac ({(r.stdout + r.stderr).strip()})")
else:
    report(FAIL, "javac not found — you need a full JDK, not just a JRE",
           "Windows: adoptium.net -> Temurin 21 -> tick 'Set JAVA_HOME',\n"
           "         then CLOSE AND REOPEN your terminal.\n"
           "macOS:   brew install openjdk\n"
           "Ubuntu:  sudo apt install default-jdk")

report(OK if shutil.which("java") else FAIL,
       "java" if shutil.which("java") else "java not found — install a JDK")


# ------------------------------------------------------ 3. python packages
section("3. Python packages")

needed = [("PIL", "images", True), ("requests", "HTTP", True),
          ("langchain_core", "agent core", True)]
if cfg.LLM_PROVIDER == "bedrock" or cfg.ASSET_PROVIDER == "bedrock":
    needed += [("boto3", "AWS", True), ("langchain_aws", "Bedrock chat", True)]
if cfg.LLM_PROVIDER != "bedrock":
    needed += [("langchain_openai", "OpenAI-compatible chat", True)]
needed += [("tavily", "web search (optional)", False)]

for mod, why, required in needed:
    try:
        __import__(mod)
        report(OK, f"{mod} ({why})")
    except ImportError:
        report(FAIL if required else WARN, f"{mod} missing ({why})",
               "pip install -r requirements.txt")


# --------------------------------------------------------------- 4. engine
section("4. Raccoon Engine")

if not Path("RaccoonEngineV2").is_dir():
    report(FAIL, "RaccoonEngineV2/ not found — run this from the repo root")
else:
    report(OK, "RaccoonEngineV2/ found")
    try:
        import raccoon_tools as rt
        res = rt.compile_engine()
        report(OK if "compiled" in res else FAIL, f"compile: {res[:86]}")

        out = rt.write_map({"rooms": [{"id": 0, "x1": 0, "z1": 0, "x2": 8, "z2": 8},
                                      {"id": 1, "x1": 8, "z1": 2, "x2": 14, "z2": 6}],
                            "doorways": [{"sector_a": 0, "sector_b": 1,
                                          "x1": 8, "z1": 3, "x2": 8, "z2": 5}]},
                           "preflight_test.txt")
        report(OK, f"map builder: {out.splitlines()[0][:82]}")
        (rt.DATA_DIR / "maps" / "preflight_test.txt").unlink(missing_ok=True)
        (cfg.REPO_ROOT / "editor_projects"
         / "preflight_test.project.json").unlink(missing_ok=True)
    except Exception as exc:  # noqa: BLE001
        report(FAIL, f"engine tooling error: {exc}")


# ------------------------------------------------------- 5. art and music
section("5. Art and music")

try:
    import raccoon_tools as rt
    prov = rt.asset_provider()
    if prov == "placeholder":
        report(OK, "placeholder (free, instant, offline, no key)")
        try:
            import time

            import raccoon_assets as ra
            t0 = time.time()
            ra.placeholder_texture("stone wall", 64)
            ra.placeholder_sprite_sheet("test goblin", 64)
            ra.placeholder_music("test drone", Path("_pf.wav"), 2)
            Path("_pf.wav").unlink(missing_ok=True)
            report(OK, f"texture + sprite sheet + music in "
                       f"{time.time() - t0:.2f}s for $0.00")
        except Exception as exc:  # noqa: BLE001
            report(FAIL, f"placeholder generation failed: {exc}")
    elif prov == "pollinations":
        report(OK, "pollinations (free online AI, no key, slower)")
    elif prov == "bedrock":
        report(WARN, "bedrock (paid, ~$0.04 per image, needs AWS)",
               "A sprite sheet is 8 images. Use ASSET_PROVIDER=placeholder\n"
               "while you're still iterating.")
    else:
        report(FAIL, f"unknown ASSET_PROVIDER={prov!r}",
               "Use placeholder, pollinations, or bedrock.")
except Exception as exc:  # noqa: BLE001
    report(FAIL, f"could not check: {exc}")


# ---------------------------------------------------------------- 6. brain
section(f"6. The brain — LLM_PROVIDER={cfg.LLM_PROVIDER}")

if cfg.LLM_PROVIDER == "bedrock" and not cfg.AWS_ACCESS_KEY_ID:
    report(FAIL, "LLM_PROVIDER=bedrock but no AWS_ACCESS_KEY_ID",
           "Either add AWS keys, or switch to the free option:\n"
           "    LLM_PROVIDER=nvidia\n"
           "    NVIDIA_API_KEY=nvapi-...   (free at build.nvidia.com)")
elif cfg.LLM_PROVIDER != "bedrock" and not cfg.llm_api_key():
    report(FAIL, f"No {cfg.llm_key_name()} in your keys file",
           "Free key at build.nvidia.com (no credit card) if using nvidia.")
else:
    report(OK, f"credentials present for {cfg.LLM_PROVIDER}")

    import raccoon_llm
    ok_chat = False
    try:
        reply = raccoon_llm.check()
        report(OK, f"model replies: {reply[:40]}")
        ok_chat = True
    except Exception as exc:  # noqa: BLE001
        m = str(exc)
        if "AccessDenied" in m or "not authorized" in m:
            fix = ("Bedrock access problem. The three usual causes:\n"
                   " 1. Anthropic use-case form not submitted\n"
                   "    (Console > Bedrock > Model catalog > model > request access)\n"
                   " 2. IAM policy too narrow — it needs aws-marketplace\n"
                   "    permissions too, not just bedrock:InvokeModel.\n"
                   "    See the setup guide for the full policy.\n"
                   " 3. NEW AWS ACCOUNTS commonly have a quota of 0 on the\n"
                   "    bigger models. That's normal and needs an AWS Support\n"
                   "    ticket to lift — it is not something you misconfigured.\n"
                   "Quickest way past all three:  LLM_PROVIDER=nvidia")
        elif "ResourceNotFound" in m or "ValidationException" in m:
            fix = (f"Model '{cfg.LLM_MODEL}' isn't available at this endpoint.\n"
                   "Check the exact model ID (and the region, on Bedrock).")
        elif "401" in m or "Unauthorized" in m or "invalid_api_key" in m:
            fix = f"{cfg.llm_key_name()} was rejected. Re-copy it."
        elif "Connection" in m or "timeout" in m.lower():
            fix = (f"Couldn't reach {cfg.LLM_BASE_URL}.\n"
                   "If you're using ollama, is it actually running?")
        else:
            fix = None
        report(FAIL, f"model call failed: {m[:110]}", fix)

    if ok_chat:
        # The check that actually matters: the agent is built on tool calls.
        try:
            detail = raccoon_llm.check_tools()
            report(OK, f"tool calling works ({detail[:48]})")
        except Exception as exc:  # noqa: BLE001
            report(FAIL, "model cannot call tools — the agent will not work",
                   f"{str(exc)[:200]}\n"
                   "Pick a model whose card lists function/tool calling.")


# ------------------------------------------------------------- 7. optional
section("7. Optional")

if not cfg.TAVILY_API_KEY:
    report(WARN, "No Tavily key — market research and web search are off",
           "Free at tavily.com. The agent builds games fine without it.")
else:
    try:
        from tavily import TavilyClient
        TavilyClient(api_key=cfg.TAVILY_API_KEY).search("doom engine", max_results=1)
        report(OK, "Tavily works")
    except Exception as exc:  # noqa: BLE001
        report(FAIL, f"Tavily failed: {str(exc)[:88]}")

report(OK if shutil.which("ffmpeg") else WARN,
       "ffmpeg" if shutil.which("ffmpeg")
       else "no ffmpeg — only needed for paid AI music, not placeholders")


print("\n" + "=" * 62)
print(f"  {counts['ok']} ok   {counts['warn']} warnings   {counts['fail']} failures")
print("\n  Fix the FAIL lines above." if counts["fail"]
      else "\n  Ready. Run:  python game_agent.py")
print("=" * 62)
