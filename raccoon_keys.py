"""
raccoon_keys.py — read API keys from a plain text file outside the repo.

No .env, no .gitignore, nothing git can ever see. Put a text file somewhere
on your machine, point at it, done.

WHERE IT LOOKS (first one that exists wins):
  1. the path in the RACCOON_KEYS environment variable
  2. C:\\raccoon_keys.txt            (Windows)
     ~/raccoon_keys.txt              (macOS / Linux)
  3. ~/Documents/raccoon_keys.txt
  4. ~/.raccoon_keys.txt

FILE FORMAT — one KEY=VALUE per line. Blank lines and # comments ignored.
Quotes around values are stripped, so pasting from anywhere works.

    # my raccoon keys
    AWS_ACCESS_KEY_ID=AKIA...
    AWS_SECRET_ACCESS_KEY=...
    AWS_REGION=us-west-2
    TAVILY_API_KEY=tvly-...
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Dict, List, Optional

KEYS_FILENAME = "raccoon_keys.txt"
_loaded_from: Optional[Path] = None


def candidate_paths() -> List[Path]:
    """Every place we'll look, in order."""
    paths: List[Path] = []

    env = os.environ.get("RACCOON_KEYS")
    if env:
        paths.append(Path(env).expanduser())

    if os.name == "nt":
        paths.append(Path("C:/") / KEYS_FILENAME)
    paths.append(Path.home() / KEYS_FILENAME)
    paths.append(Path.home() / "Documents" / KEYS_FILENAME)
    paths.append(Path.home() / f".{KEYS_FILENAME}")
    return paths


def parse(text: str) -> Dict[str, str]:
    """KEY=VALUE lines -> dict. Forgiving about quotes, spaces and comments."""
    out: Dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip().lstrip("\ufeff")          # strip BOM from Notepad
        value = value.strip().strip('"').strip("'")
        if key and value and value.upper() != "PLACEHOLDER":
            out[key] = value
    return out


def load(verbose: bool = False) -> Dict[str, str]:
    """Finds the keys file and puts its contents into os.environ.

    Existing environment variables win, so you can still override a single
    key from the shell for a one-off run.
    """
    global _loaded_from

    for path in candidate_paths():
        try:
            if not path.is_file():
                continue
        except OSError:
            continue

        # utf-8-sig handles files Notepad saved with a BOM.
        values = parse(path.read_text(encoding="utf-8-sig", errors="replace"))
        for key, value in values.items():
            os.environ.setdefault(key, value)

        _loaded_from = path
        if verbose:
            print(f"Loaded {len(values)} keys from {path}")
        return values

    if verbose:
        print("No keys file found. Looked in:")
        for p in candidate_paths():
            print(f"  {p}")
    return {}


def loaded_from() -> Optional[Path]:
    return _loaded_from


TEMPLATE = """\
# Raccoon Engine agent — API keys
# Keep this file OUTSIDE your git repo. Nothing here is ever committed.
#
# Only fill in what you actually use. Everything is optional except AWS,
# and even that is only needed to let the agent think and write scripts.

# --- AWS Bedrock: the agent's brain (required) -----------------------------
AWS_REGION=us-west-2
AWS_ACCESS_KEY_ID=PLACEHOLDER
AWS_SECRET_ACCESS_KEY=PLACEHOLDER

# --- Asset generation ------------------------------------------------------
# placeholder = free, instant, offline, no key  (recommended while building)
# pollinations = free online AI images, no key needed, slower
# bedrock      = paid AI images, best quality, ~$0.04 each
ASSET_PROVIDER=placeholder

# --- Tavily: market research + web search (optional, free tier) ------------
TAVILY_API_KEY=PLACEHOLDER

# --- Replicate: AI music (optional; placeholder music is free) -------------
REPLICATE_API_TOKEN=PLACEHOLDER
"""


def write_template(path: Path) -> Path:
    path = Path(path).expanduser()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(TEMPLATE, encoding="utf-8")
    return path


if __name__ == "__main__":
    import sys

    if "--create" in sys.argv:
        target = Path.home() / KEYS_FILENAME
        if target.exists():
            print(f"Already exists: {target}")
        else:
            print(f"Created {write_template(target)}")
            print("Open it and fill in your keys.")
    else:
        found = load(verbose=True)
        if found:
            print(f"\nKeys found: {', '.join(sorted(found))}")
        else:
            print("\nRun:  python raccoon_keys.py --create")
