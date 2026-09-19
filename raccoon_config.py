"""
raccoon_config.py — every setting in the project, in one place.

Previously model IDs and provider choices were spread across four files,
which made "how do I switch models?" a genuinely hard question. Now this is
the only file that reads configuration. Everything else imports from here.

Settings come from your keys file (see raccoon_keys.py), falling back to
the defaults below. Nothing is hardcoded anywhere else.
"""

from __future__ import annotations

import os
from pathlib import Path

import raccoon_keys

# Load the keys file into the environment before reading anything.
raccoon_keys.load()


def _get(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


# ===========================================================================
# THE BRAIN — which LLM the agent thinks with
# ===========================================================================
# LLM_PROVIDER options:
#
#   nvidia     build.nvidia.com — FREE, no credit card, OpenAI-compatible.
#              Needs NVIDIA_API_KEY (starts nvapi-). Recommended starting
#              point: no AWS account, no access approval, no billing.
#   bedrock    AWS Bedrock. Needs AWS keys and model access approval.
#   openai     api.openai.com. Needs OPENAI_API_KEY.
#   openrouter openrouter.ai — many models behind one key.
#   ollama     a model running locally. Free, no key, no internet.
#   custom     any OpenAI-compatible server: set LLM_BASE_URL yourself.
#
# Every provider except bedrock speaks the OpenAI chat-completions protocol,
# so they all go through the same client.
LLM_PROVIDER = _get("LLM_PROVIDER", "nvidia").lower()

# Sensible default model per provider. Override with LLM_MODEL.
_DEFAULT_MODELS = {
    "nvidia": "nvidia/nemotron-3-super-120b-a12b",
    "bedrock": "us.anthropic.claude-sonnet-4-5-20250929-v1:0",
    "openai": "gpt-4o",
    "openrouter": "anthropic/claude-sonnet-4.5",
    "ollama": "qwen2.5:14b",
    "custom": "",
}

_DEFAULT_BASE_URLS = {
    "nvidia": "https://integrate.api.nvidia.com/v1",
    "openai": "https://api.openai.com/v1",
    "openrouter": "https://openrouter.ai/api/v1",
    "ollama": "http://localhost:11434/v1",
    "custom": "",
}

LLM_MODEL = _get("LLM_MODEL") or _DEFAULT_MODELS.get(LLM_PROVIDER, "")
LLM_BASE_URL = _get("LLM_BASE_URL") or _DEFAULT_BASE_URLS.get(LLM_PROVIDER, "")


def llm_api_key() -> str:
    """The key for whichever provider is selected."""
    if LLM_PROVIDER == "nvidia":
        return _get("NVIDIA_API_KEY")
    if LLM_PROVIDER == "openai":
        return _get("OPENAI_API_KEY")
    if LLM_PROVIDER == "openrouter":
        return _get("OPENROUTER_API_KEY")
    if LLM_PROVIDER == "ollama":
        return "ollama"            # local server ignores it, but one is required
    if LLM_PROVIDER == "custom":
        return _get("LLM_API_KEY") or "none"
    return ""                       # bedrock uses AWS credentials instead


def llm_key_name() -> str:
    """What to call the missing key in an error message."""
    return {"nvidia": "NVIDIA_API_KEY", "openai": "OPENAI_API_KEY",
            "openrouter": "OPENROUTER_API_KEY", "custom": "LLM_API_KEY",
            "ollama": "(none needed)", "bedrock": "AWS_ACCESS_KEY_ID"
            }.get(LLM_PROVIDER, "?")


# ===========================================================================
# AWS — only used when LLM_PROVIDER=bedrock or ASSET_PROVIDER=bedrock
# ===========================================================================
AWS_REGION = _get("AWS_REGION", "us-west-2")
AWS_ACCESS_KEY_ID = _get("AWS_ACCESS_KEY_ID")


# ===========================================================================
# ART AND MUSIC
# ===========================================================================
#   placeholder   free, instant, offline, no key      <- default
#   pollinations  free online AI images, no key
#   bedrock       paid AI images via AWS, ~$0.04 each
ASSET_PROVIDER = _get("ASSET_PROVIDER", "placeholder").lower()
IMAGE_MODEL = _get("BEDROCK_IMAGE_MODEL_ID", "stability.stable-image-core-v1:1")

REPLICATE_API_TOKEN = _get("REPLICATE_API_TOKEN")
REPLICATE_MUSIC_MODEL = _get("REPLICATE_MUSIC_MODEL", "meta/musicgen")


# ===========================================================================
# OTHER
# ===========================================================================
TAVILY_API_KEY = _get("TAVILY_API_KEY")
REPO_ROOT = Path(_get("RACCOON_REPO", ".")).resolve()
MAX_BUILD_STEPS = int(_get("MAX_BUILD_STEPS", "40"))


def describe() -> str:
    """Human-readable summary of the active configuration."""
    lines = [
        f"  brain      {LLM_PROVIDER}  ({LLM_MODEL or 'no model set'})",
    ]
    if LLM_PROVIDER != "bedrock":
        lines.append(f"  endpoint   {LLM_BASE_URL or 'not set'}")
    else:
        lines.append(f"  region     {AWS_REGION}")
    lines.append(f"  art/music  {ASSET_PROVIDER}")
    lines.append(f"  research   {'tavily' if TAVILY_API_KEY else 'off'}")
    src = raccoon_keys.loaded_from()
    lines.append(f"  keys file  {src if src else 'NOT FOUND'}")
    return "\n".join(lines)


if __name__ == "__main__":
    print("Active configuration:\n")
    print(describe())
