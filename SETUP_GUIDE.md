# Setup Guide

Three things you raised, all real. Plus a bug audit.

---

## 1. Why you got Access Denied

Not your fault, and probably not fixable by fiddling with settings. There are
three separate causes and you may have hit all of them:

**a) New AWS accounts get a quota of zero on the bigger models.** This is the
big one. AWS gates frontier models on account history — a brand-new account
routinely shows zero quota across every region, including on Amazon's own
models. It looks exactly like a misconfiguration and isn't one. The only fix
is an AWS Support ticket asking for Bedrock quota provisioning, and they
prioritise accounts with existing usage. That's why the small models work and
Claude doesn't.

**b) My IAM policy was too narrow — my mistake.** I gave you only
`bedrock:InvokeModel`. Invoking a model your account has never used also needs
AWS Marketplace permissions. Corrected policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "bedrock:InvokeModel",
      "bedrock:InvokeModelWithResponseStream",
      "bedrock:ListFoundationModels",
      "bedrock:ListInferenceProfiles",
      "aws-marketplace:ViewSubscriptions",
      "aws-marketplace:Subscribe"
    ],
    "Resource": "*"
  }]
}
```

**c) The Anthropic use-case form.** Claude models need it submitted and
approved separately, per region.

**You don't have to fight any of this.** See section 3 — there's a free
option with no account, no card, and no approval queue.

---

## 2. The `os.environ` thing — you spotted a real design smell

Short version: it *was* reading your text file, but the plumbing was
confusing and also had a bug.

How it worked: `raccoon_keys.load()` read your text file and copied the
values into `os.environ`, then every other module read them back out with
`os.environ.get()`. Functionally fine, but it meant "where does this setting
come from?" had no single answer — and you had to trust that `load()` ran
before anything read the values.

**The bug it was hiding:** the parser skipped any value equal to
`PLACEHOLDER`, case-insensitively. So the line `ASSET_PROVIDER=placeholder` —
which the template itself wrote — was silently discarded. It happened to keep
working because `placeholder` was also the fallback default, but if you'd
switched to `bedrock` and then tried to switch back, the revert would have
silently done nothing. Fixed: the comparison is now case-sensitive.

**The restructure:** there's now `raccoon_config.py`, and it's the only file
in the project that reads configuration. Everything else imports from it.

```
raccoon_keys.py    finds and parses your text file
raccoon_config.py  the ONE place every setting is defined   <- new
raccoon_llm.py     builds the chat model for any provider   <- new
raccoon_tools.py   engine, maps, assets   (reads config)
raccoon_assets.py  art and music          (reads config)
game_agent.py      the workflow           (reads config)
```

Run `python raccoon_config.py` any time to see exactly what's active:

```
brain      nvidia  (nvidia/nemotron-3-super-120b-a12b)
endpoint   https://integrate.api.nvidia.com/v1
art/music  placeholder
research   off
keys file  C:\Users\You\raccoon_keys.txt
```

The agent prints that on startup too, so you always know what you're running.

---

## 3. Switching models — you were right, it was hardcoded everywhere

Model IDs were scattered across four files. Switching meant editing code in
several places and hoping you found them all. That's fixed.

**Nemotron is a good call, and better than I'd have guessed.** NVIDIA hosts
it free at [build.nvidia.com](https://build.nvidia.com) behind an
OpenAI-compatible endpoint. No credit card, no approval queue, and Nemotron
supports function calling — which the agent depends on completely.

**It's now the default.** Switching is one line in your keys file:

```
LLM_PROVIDER=nvidia
NVIDIA_API_KEY=nvapi-...
```

Supported providers:

| `LLM_PROVIDER` | What it is | Key needed |
|---|---|---|
| `nvidia` | build.nvidia.com — **free, no card** | `NVIDIA_API_KEY` |
| `bedrock` | AWS | AWS keys + approval |
| `openai` | api.openai.com | `OPENAI_API_KEY` |
| `openrouter` | many models, one key | `OPENROUTER_API_KEY` |
| `ollama` | running on your own PC | none |
| `custom` | any OpenAI-compatible server | `LLM_API_KEY` |

Want a different model on the same provider? One more line:

```
LLM_MODEL=nvidia/nemotron-3-ultra-550b-a55b
```

Leave it blank for the provider's default.

Everything except Bedrock speaks the OpenAI chat-completions protocol, so
they share one client with a different base URL. Adding a provider later is
two lines in `raccoon_config.py`, not a new code path.

### The check that actually matters

The agent works entirely by calling tools. A model that chats beautifully but
can't emit tool calls produces an agent that *describes* building your game
instead of building it — and that failure is confusing to diagnose.

So preflight now tests it directly: it binds a real tool, asks the model to
call it, and verifies a tool call comes back.

```
[ OK ] model replies: ready
[ OK ] tool calling works (called add_numbers({'a': 17, 'b': 25}))
```

If you pick a model that can't, you get told immediately rather than after a
confusing build. Small models (under ~7B) often fail this.

---

## 4. Other things the audit turned up

**You're on Python 3.14.** I saw it in the committed `__pycache__` filenames.
It'll probably work, but it's new enough that some packages may not have
prebuilt wheels. If `pip install` starts trying to compile C code and fails,
install Python 3.12 alongside it. Preflight warns about this now.

**`__pycache__/` and `editor_projects/` got committed.** Harmless — no
secrets in either, since keys live outside the repo now. If the noise bothers
you, a one-line `.gitignore` with `__pycache__/` fixes it. Entirely optional.

**`requirements.txt` was pulling AWS packages you may not need.** Now
`boto3`/`langchain-aws` are marked as Bedrock-only, and `langchain-openai` is
added for everything else. Preflight only checks for the packages your chosen
provider actually needs.

---

## Setup from scratch (the easy path)

### Step 1 — Python and Java

```
python --version      (need 3.10+)
javac -version        (need a JDK, not just a JRE)
```

No `javac`? Windows: [adoptium.net](https://adoptium.net) → Temurin 21 →
tick **"Set JAVA_HOME"** → then **close and reopen your terminal**, since PATH
changes don't reach windows that are already open.

```
pip install -r requirements.txt
```

### Step 2 — Keys file

```
python raccoon_keys.py --create
```

Writes `raccoon_keys.txt` to your home folder, outside the repo. Open it in
Notepad.

### Step 3 — Free NVIDIA key (2 minutes, no card)

1. [build.nvidia.com](https://build.nvidia.com) → sign in (free developer
   account)
2. Open any model card, e.g. Nemotron
3. **Get API Key** → copy the `nvapi-...` value

In your keys file:

```
LLM_PROVIDER=nvidia
NVIDIA_API_KEY=nvapi-...
ASSET_PROVIDER=placeholder
```

That's everything. No AWS, no billing, no approvals.

### Step 4 — Check

```
python preflight.py
```

All seven sections should pass. Art, music, map building and the engine
compile all work offline with no account at all — only section 6 needs a key.

### Step 5 — Build a game

```
python game_agent.py
```

Point it at a prompt file, answer **n** to market research the first time,
and let the `smoke_test` window open and close on its own.

---

## If you still want AWS later

Everything's still there — `LLM_PROVIDER=bedrock` and the AWS keys. Use the
corrected IAM policy in section 1. But given the new-account quota situation,
NVIDIA is the pragmatic starting point, and you can switch whenever a support
ticket comes through.

You can also mix: NVIDIA for thinking, Bedrock for final art.

```
LLM_PROVIDER=nvidia
ASSET_PROVIDER=bedrock
```

---

## Files

**New:** `raccoon_config.py`, `raccoon_llm.py`
**Changed:** `raccoon_keys.py` (bug fix + new template), `raccoon_tools.py`,
`raccoon_assets.py`, `game_agent.py`, `preflight.py`, `requirements.txt`

Delete your old `raccoon_keys.txt` and re-run `--create` to get the new
template, or just add the `LLM_PROVIDER` and `NVIDIA_API_KEY` lines to the
one you have.

## Quick reference

```
python raccoon_keys.py --create    make the keys file
python raccoon_config.py           show active settings
python raccoon_llm.py              test the model and its tool calling
python preflight.py                check everything
python game_agent.py               build a game
```
