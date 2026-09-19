"""
preflight.py — check your setup before running the agent.

    python preflight.py

Tells you what works, what doesn't, and what to do about each failure.
Costs nothing unless you pass --ai, which makes one paid image call.
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

import raccoon_keys

OK, WARN, FAIL = "  [ OK ]", "  [WARN]", "  [FAIL]"
counts = {"ok": 0, "warn": 0, "fail": 0}


def report(level, msg, fix=None):
    print(f"{level} {msg}")
    if fix and level != OK:
        for line in fix.strip().splitlines():
            print(f"         {line}")
    counts["ok" if level == OK else "warn" if level == WARN else "fail"] += 1


def section(name):
    print(f"\n{name}\n" + "-" * len(name))


# ------------------------------------------------------------------ 1. keys
section("1. Keys file")

found = raccoon_keys.load()
path = raccoon_keys.loaded_from()

if path is None:
    report(FAIL, "No keys file found",
           "python raccoon_keys.py --create\n"
           "Then open the file it makes and paste your keys in.\n"
           "Looked in:\n  " + "\n  ".join(str(p) for p in raccoon_keys.candidate_paths()))
else:
    report(OK, f"Keys file: {path}")
    try:
        inside_repo = Path(path).resolve().is_relative_to(Path.cwd().resolve())
    except (AttributeError, ValueError):
        inside_repo = str(Path(path).resolve()).startswith(str(Path.cwd().resolve()))
    if inside_repo:
        report(FAIL, "Keys file is INSIDE the repo folder — move it out",
               "Put it in your home folder or C:\\raccoon_keys.txt instead.")
    else:
        report(OK, "Keys file is outside the repo, so git can never see it")
    report(OK if found else WARN,
           f"{len(found)} keys loaded" if found else "File found but no keys filled in",
           None if found else "Open the file and replace PLACEHOLDER values.")


# ---------------------------------------------------------- 2. local tools
section("2. Local tools")

if sys.version_info >= (3, 10):
    report(OK, f"Python {sys.version_info.major}.{sys.version_info.minor}")
else:
    report(FAIL, f"Python {sys.version_info.major}.{sys.version_info.minor} — need 3.10+")

if shutil.which("javac"):
    v = subprocess.run(["javac", "-version"], capture_output=True, text=True)
    report(OK, f"javac ({(v.stdout + v.stderr).strip()})")
else:
    report(FAIL, "javac not found — you need a full JDK, not just a JRE",
           "Windows: install Temurin JDK from adoptium.net, then reopen your terminal\n"
           "macOS:   brew install openjdk\n"
           "Ubuntu:  sudo apt install default-jdk")

report(OK if shutil.which("java") else FAIL,
       "java" if shutil.which("java") else "java not found — install a JDK")

report(OK if shutil.which("ffmpeg") else WARN,
       "ffmpeg" if shutil.which("ffmpeg")
       else "ffmpeg not found — only needed for paid AI music, not placeholders")


# ------------------------------------------------------ 3. python packages
section("3. Python packages")

for mod, why in [("boto3", "AWS"), ("PIL", "images"),
                 ("langchain_aws", "Bedrock chat"), ("requests", "HTTP"),
                 ("tavily", "web search (optional)")]:
    try:
        __import__(mod)
        report(OK, f"{mod} ({why})")
    except ImportError:
        report(WARN if "optional" in why else FAIL, f"{mod} missing ({why})",
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
        report(OK if "compiled" in res else FAIL, f"compile: {res[:88]}")

        out = rt.write_map({"rooms": [{"id": 0, "x1": 0, "z1": 0, "x2": 8, "z2": 8},
                                      {"id": 1, "x1": 8, "z1": 2, "x2": 14, "z2": 6}],
                            "doorways": [{"sector_a": 0, "sector_b": 1,
                                          "x1": 8, "z1": 3, "x2": 8, "z2": 5}]},
                           "preflight_test.txt")
        report(OK, f"map builder: {out.splitlines()[0][:84]}")
        (rt.DATA_DIR / "maps" / "preflight_test.txt").unlink(missing_ok=True)
    except Exception as exc:  # noqa: BLE001
        report(FAIL, f"engine tooling error: {exc}")


# ------------------------------------------------------- 5. asset provider
section("5. Asset generation")

try:
    import raccoon_tools as rt
    prov = rt.asset_provider()
    if prov == "placeholder":
        report(OK, "provider = placeholder (free, instant, offline, no key)")
        try:
            import raccoon_assets as ra
            import time
            t0 = time.time()
            ra.placeholder_texture("stone wall", 64)
            ra.placeholder_sprite_sheet("test goblin", 64)
            ra.placeholder_music("test drone", Path("_preflight_test.wav"), 2)
            Path("_preflight_test.wav").unlink(missing_ok=True)
            report(OK, f"generated a texture, a sprite sheet and music in "
                       f"{time.time() - t0:.2f}s for $0.00")
        except Exception as exc:  # noqa: BLE001
            report(FAIL, f"placeholder generation failed: {exc}")
    elif prov == "pollinations":
        report(OK, "provider = pollinations (free online AI, no key, slower)")
    elif prov == "bedrock":
        report(WARN, "provider = bedrock (paid, about $0.04 per image)",
               "A sprite sheet is 8 images (~$0.32). Set ASSET_PROVIDER=placeholder\n"
               "in your keys file while you're still iterating.")
    else:
        report(FAIL, f"unknown ASSET_PROVIDER={prov!r}",
               "Use placeholder, pollinations, or bedrock.")
except Exception as exc:  # noqa: BLE001
    report(FAIL, f"could not read provider: {exc}")


# ------------------------------------------------------------------ 6. AWS
section("6. AWS Bedrock (the agent's brain)")

key = os.environ.get("AWS_ACCESS_KEY_ID", "")
region = os.environ.get("AWS_REGION", "")

if not key:
    report(FAIL, "AWS_ACCESS_KEY_ID not set in your keys file",
           "This one is required — it's what lets the agent think.\n"
           "See the guide, section 3.")
else:
    report(OK, f"credentials present, region {region or 'us-west-2 (default)'}")
    try:
        import boto3
        who = boto3.client("sts").get_caller_identity()
        report(OK, f"AWS login works (account {who['Account']})")
    except Exception as exc:  # noqa: BLE001
        report(FAIL, f"AWS login failed: {str(exc)[:100]}",
               "Re-copy the key and secret — they're easy to truncate.")

    try:
        import boto3
        model = os.environ.get("BEDROCK_TEXT_MODEL_ID",
                               "us.anthropic.claude-sonnet-4-5-20250929-v1:0")
        c = boto3.client("bedrock-runtime", region_name=region or "us-west-2")
        r = c.converse(modelId=model,
                       messages=[{"role": "user",
                                  "content": [{"text": "Reply with: ready"}]}],
                       inferenceConfig={"maxTokens": 10})
        report(OK, f"Claude responds: "
                   f"{r['output']['message']['content'][0]['text'].strip()[:30]}")
    except Exception as exc:  # noqa: BLE001
        m = str(exc)
        if "AccessDenied" in m or "not authorized" in m:
            fix = ("Request model access: Console > Bedrock > Model catalog >\n"
                   "pick a Claude model > request access > fill the short form.\n"
                   "Also confirm your IAM user has bedrock:InvokeModel.")
        elif "ResourceNotFound" in m or "ValidationException" in m:
            fix = ("Keep the 'us.' prefix on the model ID, and try\n"
                   "AWS_REGION=us-west-2.")
        else:
            fix = None
        report(FAIL, f"Bedrock call failed: {m[:100]}", fix)

if "--ai" in sys.argv and key:
    try:
        import raccoon_assets as ra
        os.environ["ASSET_PROVIDER"] = "bedrock"
        ra.bedrock_image("a grey stone wall texture")
        report(OK, "paid image generation works")
    except Exception as exc:  # noqa: BLE001
        report(FAIL, f"image model failed: {str(exc)[:100]}")


# ------------------------------------------------------------- 7. optional
section("7. Optional")

tav = os.environ.get("TAVILY_API_KEY", "")
if not tav:
    report(WARN, "No Tavily key — market research and web search are off",
           "Free at tavily.com. The agent builds games fine without it.")
else:
    try:
        from tavily import TavilyClient
        TavilyClient(api_key=tav).search("doom engine", max_results=1)
        report(OK, "Tavily works")
    except Exception as exc:  # noqa: BLE001
        report(FAIL, f"Tavily failed: {str(exc)[:90]}")

report(OK if os.environ.get("REPLICATE_API_TOKEN") else WARN,
       "Replicate token present" if os.environ.get("REPLICATE_API_TOKEN")
       else "No Replicate token — music uses free synthesised placeholders")


print("\n" + "=" * 60)
print(f"  {counts['ok']} ok   {counts['warn']} warnings   {counts['fail']} failures")
print("\n  Fix the FAIL lines above." if counts["fail"]
      else "\n  Ready. Run:  python game_agent.py")
print("=" * 60)
