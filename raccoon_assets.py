"""
raccoon_assets.py — asset generation with three cost tiers.

    ASSET_PROVIDER=placeholder   free, instant, offline, no key   <- default
    ASSET_PROVIDER=pollinations  free online AI images, no key, slower
    ASSET_PROVIDER=bedrock       paid AI images, ~$0.04 each, best quality

WHY PROCEDURAL PLACEHOLDERS BEAT AI ONES
----------------------------------------
For 40 sprites at 3 frames each you'd be making 40 * 3 * 8 = 960 image
generations. On Bedrock that's about $38 per iteration, for art you intend
to throw away.

Procedural placeholders cost nothing, take milliseconds, work on a plane,
and are actually BETTER at the one thing AI is worst at here: staying
consistent across the 8 facings. A diffusion model given "goblin, side view"
and "goblin, back view" produces two different goblins. A drawn figure
rotated 45 degrees is the same figure.

They look like placeholders, which is the point — you can see at a glance
what is still programmer art.

Every generator is deterministic: the same description always yields the
same asset, so re-running a build doesn't reshuffle your art.
"""

from __future__ import annotations

import base64
import colorsys
import hashlib
import io
import json
import math
import os
import struct
import wave
from pathlib import Path
from typing import List, Optional, Tuple

from PIL import Image, ImageDraw, ImageFilter

# --------------------------------------------------------------------------
# Deterministic randomness: same words in, same art out.
# --------------------------------------------------------------------------
def _seed(text: str) -> int:
    return int(hashlib.md5(text.lower().encode()).hexdigest()[:8], 16)


class _Rng:
    """Tiny deterministic PRNG so results never depend on global state."""

    def __init__(self, seed: int):
        self.s = seed & 0xFFFFFFFF or 1

    def next(self) -> int:
        self.s ^= (self.s << 13) & 0xFFFFFFFF
        self.s ^= self.s >> 17
        self.s ^= (self.s << 5) & 0xFFFFFFFF
        return self.s

    def rand(self) -> float:
        return self.next() / 0xFFFFFFFF

    def between(self, a: float, b: float) -> float:
        return a + (b - a) * self.rand()

    def pick(self, items):
        return items[self.next() % len(items)]


# --------------------------------------------------------------------------
# Colour: pull a hue out of the words, so "mossy stone" is green-grey and
# "lava rock" is red. Crude keyword matching, but it reads correctly.
# --------------------------------------------------------------------------
# Order matters: the first match wins, so distinctive modifiers ("mossy",
# "rusted") are listed ahead of generic materials ("stone", "metal").
# Otherwise "mossy stone" matches "stone" and comes out grey.
COLOR_WORDS = {
    # modifiers first
    "moss": (0.25, 0.40, 0.35), "rust": (0.05, 0.55, 0.40),
    "lava": (0.02, 0.85, 0.45), "fire": (0.05, 0.85, 0.50),
    "blood": (0.00, 0.70, 0.30), "gold": (0.13, 0.70, 0.55),
    "ice": (0.53, 0.35, 0.72), "snow": (0.58, 0.08, 0.85),
    "slime": (0.30, 0.65, 0.45), "shadow": (0.70, 0.15, 0.18),
    "grass": (0.28, 0.45, 0.42), "water": (0.55, 0.50, 0.45),
    "sand": (0.11, 0.35, 0.65), "marble": (0.60, 0.05, 0.78),
    "flesh": (0.04, 0.35, 0.55), "dirt": (0.08, 0.35, 0.35),
    # generic materials last
    "brick": (0.03, 0.45, 0.42), "wood": (0.07, 0.45, 0.40),
    "plank": (0.08, 0.40, 0.45), "metal": (0.58, 0.08, 0.55),
    "stone": (0.08, 0.10, 0.45), "rock": (0.07, 0.12, 0.42),
    "dark": (0.65, 0.15, 0.20), "bright": (0.15, 0.25, 0.75),
}

# Same rule: specific layout words before generic material words.
PATTERN_WORDS = {
    "tile": "tile", "flagstone": "tile", "checker": "tile",
    "plank": "plank", "board": "plank", "beam": "plank",
    "panel": "plate", "plate": "plate",
    "water": "wave", "lava": "wave", "liquid": "wave", "wave": "wave",
    "brick": "brick", "masonry": "brick",
    "wood": "plank", "metal": "plate", "steel": "plate", "iron": "plate",
    "stone": "brick", "wall": "brick",
    "grass": "noise", "dirt": "noise", "sand": "noise", "gravel": "noise",
}


def _base_hsv(description: str, rng: _Rng) -> Tuple[float, float, float]:
    d = description.lower()
    for word, hsv in COLOR_WORDS.items():
        if word in d:
            h, s, v = hsv
            return (h + rng.between(-0.02, 0.02)) % 1.0, s, v
    return rng.rand(), rng.between(0.25, 0.5), rng.between(0.35, 0.6)


def _rgb(h: float, s: float, v: float) -> Tuple[int, int, int]:
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, max(0, min(1, s)), max(0, min(1, v)))
    return int(r * 255), int(g * 255), int(b * 255)


def _pattern_for(description: str) -> str:
    d = description.lower()
    for word, pattern in PATTERN_WORDS.items():
        if word in d:
            return pattern
    return "noise"


# ==========================================================================
# TEXTURES — procedural, tileable
# ==========================================================================
def placeholder_texture(description: str, size: int = 64) -> Image.Image:
    rng = _Rng(_seed(description))
    h, s, v = _base_hsv(description, rng)
    img = Image.new("RGB", (size, size), _rgb(h, s, v))
    d = ImageDraw.Draw(img)
    pattern = _pattern_for(description)

    def shade(dv: float, ds: float = 0.0):
        return _rgb(h, s + ds, v + dv)

    if pattern == "brick":
        rows, bh = 4, size // 4
        for row in range(rows):
            offset = (size // 4) if row % 2 else 0
            y = row * bh
            for col in range(-1, 3):
                x = col * (size // 2) + offset
                d.rectangle([x + 1, y + 1, x + size // 2 - 2, y + bh - 2],
                            fill=shade(rng.between(-0.08, 0.08)))
    elif pattern == "plank":
        planks = 4
        pw = size // planks
        for i in range(planks):
            x = i * pw
            d.rectangle([x, 0, x + pw - 2, size], fill=shade(rng.between(-0.07, 0.07)))
            for _ in range(3):  # grain
                gx = x + int(rng.between(1, pw - 2))
                d.line([gx, 0, gx, size], fill=shade(-0.10))
    elif pattern == "tile":
        half = size // 2
        for i in range(2):
            for j in range(2):
                dv = 0.06 if (i + j) % 2 == 0 else -0.06
                d.rectangle([i * half, j * half, i * half + half - 1,
                             j * half + half - 1], fill=shade(dv))
    elif pattern == "plate":
        d.rectangle([0, 0, size - 1, size - 1], fill=shade(0.0))
        d.rectangle([2, 2, size - 3, size - 3], outline=shade(-0.12))
        for cx, cy in ((5, 5), (size - 6, 5), (5, size - 6), (size - 6, size - 6)):
            d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=shade(0.15))
    elif pattern == "wave":
        for y in range(size):
            dv = 0.07 * math.sin(y * 2 * math.pi / (size / 3))
            d.line([0, y, size, y], fill=shade(dv))

    # Speckle on top of everything; this is what sells it as a texture.
    px = img.load()
    for y in range(size):
        for x in range(size):
            if rng.rand() < 0.30:
                r, g, b = px[x, y]
                k = int(rng.between(-18, 18))
                px[x, y] = (max(0, min(255, r + k)), max(0, min(255, g + k)),
                            max(0, min(255, b + k)))
    return img


# ==========================================================================
# SPRITES — a figure drawn at 8 facings, consistent by construction
# ==========================================================================
def _sprite_frame(description: str, size: int, facing_index: int,
                  anim_frame: int = 0, anim_total: int = 1) -> Image.Image:
    """One facing of a placeholder character.

    facing_index 0 is the front view (the engine's frame 0 is the view with
    the player straight ahead of the sprite's facing direction). Each step
    rotates 45 degrees.
    """
    rng = _Rng(_seed(description))
    h, s, v = _base_hsv(description, rng)

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    angle = facing_index * (2 * math.pi / 8)
    facing_toward = math.cos(angle)      # +1 = facing us, -1 = facing away
    side = math.sin(angle)               # left/right lean

    body = _rgb(h, s, v)
    body_dark = _rgb(h, s + 0.05, v - 0.15)
    head_c = _rgb(h, s - 0.12, v + 0.12)

    cx = size / 2
    unit = size / 16.0

    # Walk cycle: legs swing, whole body bobs.
    phase = (anim_frame / max(1, anim_total)) * 2 * math.pi
    swing = math.sin(phase) * unit * 1.4 if anim_total > 1 else 0.0
    bob = abs(math.cos(phase)) * unit * 0.4 if anim_total > 1 else 0.0

    # Ground shadow — grounds the sprite so it doesn't look like it floats.
    d.ellipse([cx - 3.4 * unit, size - 2.0 * unit, cx + 3.4 * unit, size - 0.4 * unit],
              fill=(0, 0, 0, 90))

    # Legs
    leg_w = 1.5 * unit
    for sign in (-1, 1):
        lx = cx + sign * 1.5 * unit + (swing if sign > 0 else -swing) * 0.5
        d.rounded_rectangle(
            [lx - leg_w / 2, size - 7 * unit + bob, lx + leg_w / 2, size - 1.2 * unit],
            radius=int(unit * 0.5), fill=body_dark)

    # Torso — narrower when seen edge-on, which is what sells the rotation.
    torso_w = (2.6 + 1.5 * abs(facing_toward)) * unit
    torso_top = size - 12.5 * unit + bob
    torso_bot = size - 6.5 * unit + bob
    d.rounded_rectangle([cx - torso_w, torso_top, cx + torso_w, torso_bot],
                        radius=int(unit), fill=body)

    # Arms, offset by the lean so they read as being in front/behind.
    arm_w = 1.1 * unit
    for sign in (-1, 1):
        ax = cx + sign * (torso_w + arm_w * 0.4) - side * unit * 0.8
        shade = body if sign * side <= 0 else body_dark
        d.rounded_rectangle(
            [ax - arm_w / 2, torso_top + 0.4 * unit,
             ax + arm_w / 2, torso_bot - 0.2 * unit],
            radius=int(unit * 0.4), fill=shade)

    # Head
    head_r = 2.4 * unit
    hy = torso_top - head_r * 0.9
    d.ellipse([cx - head_r, hy - head_r, cx + head_r, hy + head_r], fill=head_c)

    # Face, only on the side actually pointing at us. This is the main cue
    # for which way the sprite is facing.
    if facing_toward > 0.2:
        eye_r = max(1.0, unit * 0.42)
        spread = head_r * 0.45 * (0.35 + 0.65 * facing_toward)
        ex = cx - side * head_r * 0.45
        for sign in (-1, 1):
            d.ellipse([ex + sign * spread - eye_r, hy - eye_r * 0.7,
                       ex + sign * spread + eye_r, hy + eye_r * 0.7],
                      fill=(20, 20, 20))
    elif facing_toward < -0.2:
        # Back of the head: a hint of hair so it's clearly turned away.
        d.chord([cx - head_r, hy - head_r, cx + head_r, hy + head_r],
                180, 360, fill=_rgb(h, s + 0.1, v - 0.22))

    # Facing nub: unambiguous direction marker at the feet.
    nub = 1.0 * unit
    nx = cx + side * 2.6 * unit
    ny = size - 1.6 * unit - facing_toward * 0.9 * unit
    d.ellipse([nx - nub, ny - nub * 0.6, nx + nub, ny + nub * 0.6],
              fill=_rgb((h + 0.5) % 1.0, 0.75, 0.85))

    return img


def placeholder_sprite_sheet(description: str, size: int = 64,
                             anim_frame: int = 0, anim_total: int = 1
                             ) -> Image.Image:
    """8 facings tiled left to right. Width is always 8 * size, which is
    what ResourceManager.validateImage requires."""
    sheet = Image.new("RGBA", (size * 8, size), (0, 0, 0, 0))
    for i in range(8):
        sheet.paste(_sprite_frame(description, size, i, anim_frame, anim_total),
                    (i * size, 0))
    return sheet


# ==========================================================================
# SKYBOX / PICS
# ==========================================================================
def placeholder_skybox(description: str, width: int, height: int) -> Image.Image:
    rng = _Rng(_seed(description))
    h, s, v = _base_hsv(description, rng)
    img = Image.new("RGB", (width, height))
    d = ImageDraw.Draw(img)

    horizon = int(height * 0.55)
    for y in range(height):
        if y < horizon:                       # sky: light at horizon
            t = y / max(1, horizon)
            d.line([0, y, width, y], fill=_rgb(h, s * (0.35 + 0.5 * t), v + 0.35 - 0.2 * t))
        else:                                 # ground
            t = (y - horizon) / max(1, height - horizon)
            d.line([0, y, width, y], fill=_rgb(h, s + 0.1, v - 0.12 - 0.15 * t))

    for _ in range(14):                       # clouds / hills
        cw = rng.between(width * 0.04, width * 0.16)
        cx = rng.between(0, width)
        cy = rng.between(height * 0.12, horizon * 0.85)
        d.ellipse([cx - cw, cy - cw * 0.22, cx + cw, cy + cw * 0.22],
                  fill=_rgb(h, s * 0.2, v + 0.45))
    return img.filter(ImageFilter.GaussianBlur(1.2))


def placeholder_pic(description: str, width: int, height: int) -> Image.Image:
    rng = _Rng(_seed(description))
    h, s, v = _base_hsv(description, rng)
    img = Image.new("RGB", (width, height), _rgb(h, s * 0.5, v * 0.35))
    d = ImageDraw.Draw(img)
    for i in range(10):
        t = i / 10
        d.rectangle([width * t * 0.5, height * t * 0.5,
                     width * (1 - t * 0.5), height * (1 - t * 0.5)],
                    outline=_rgb(h, s, v + t * 0.25))
    d.rectangle([4, 4, width - 5, height - 5], outline=_rgb(h, s, v + 0.4))
    label = description[:44]
    d.text((14, height - 24), f"[placeholder] {label}", fill=(255, 255, 255))
    return img


# ==========================================================================
# AUDIO — synthesised WAV, no API, no ffmpeg
# ==========================================================================
MINOR = [0, 3, 7, 10, 12, 10, 7, 3]
MAJOR = [0, 4, 7, 12, 7, 4, 0, 7]


def placeholder_music(description: str, path: Path, seconds: int = 20,
                      sample_rate: int = 22050) -> None:
    """A loopable drone plus a slow arpeggio. Deterministic from the text.

    Deliberately plain — it fills the audio slot so you can hear pacing and
    mixing without paying for music you're going to replace.
    """
    rng = _Rng(_seed(description))
    d = description.lower()

    dark = any(w in d for w in ("dark", "tense", "scary", "dungeon", "horror",
                                "unsettling", "ominous", "sad", "drone"))
    scale = MINOR if dark else MAJOR
    root = rng.between(98, 147) if dark else rng.between(131, 196)
    note_len = 0.75 if dark else 0.5

    n = int(sample_rate * seconds)
    samples = []
    for i in range(n):
        t = i / sample_rate
        # Drone: root plus a fifth, slowly detuned so it never sits still.
        val = 0.16 * math.sin(2 * math.pi * root * 0.5 * t)
        val += 0.10 * math.sin(2 * math.pi * root * 0.75 * t + 0.4)
        val *= 0.85 + 0.15 * math.sin(2 * math.pi * 0.08 * t)

        # Arpeggio over the scale.
        step = int(t / note_len) % len(scale)
        freq = root * (2 ** (scale[step] / 12.0))
        into = (t % note_len) / note_len
        env = math.exp(-3.2 * into) * (1 - math.exp(-40 * into))
        val += 0.22 * env * math.sin(2 * math.pi * freq * t)
        val += 0.06 * env * math.sin(2 * math.pi * freq * 2 * t)

        # Fade the ends so the loop point isn't a click.
        edge = min(t, seconds - t)
        if edge < 0.4:
            val *= max(0.0, edge / 0.4)

        samples.append(int(max(-1.0, min(1.0, val)) * 32767 * 0.8))

    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(struct.pack(f"<{len(samples)}h", *samples))


def placeholder_sfx(description: str, path: Path, sample_rate: int = 22050) -> None:
    """A short blip/thud/zap, picked from keywords in the description."""
    rng = _Rng(_seed(description))
    d = description.lower()

    if any(w in d for w in ("hit", "thud", "impact", "door", "step", "land")):
        kind, dur, base = "thud", 0.22, rng.between(70, 130)
    elif any(w in d for w in ("zap", "laser", "magic", "shoot", "beam")):
        kind, dur, base = "zap", 0.30, rng.between(600, 1100)
    elif any(w in d for w in ("pickup", "coin", "collect", "item", "chime")):
        kind, dur, base = "chime", 0.28, rng.between(700, 1000)
    else:
        kind, dur, base = "blip", 0.16, rng.between(320, 620)

    n = int(sample_rate * dur)
    samples = []
    for i in range(n):
        t = i / sample_rate
        p = i / n
        if kind == "thud":
            freq = base * (1 - 0.55 * p)
            val = math.sin(2 * math.pi * freq * t) * math.exp(-7 * p)
            val += 0.35 * (rng.rand() * 2 - 1) * math.exp(-22 * p)
        elif kind == "zap":
            freq = base * (1 - 0.72 * p)
            val = (1 if math.sin(2 * math.pi * freq * t) > 0 else -1) * math.exp(-6 * p)
        elif kind == "chime":
            val = (math.sin(2 * math.pi * base * t)
                   + 0.5 * math.sin(2 * math.pi * base * 1.5 * t)) * math.exp(-6 * p)
        else:
            step = base * (1 + 0.5 * int(p * 3))
            val = (1 if math.sin(2 * math.pi * step * t) > 0 else -1) * math.exp(-5 * p)
        samples.append(int(max(-1.0, min(1.0, val * 0.55)) * 32767))

    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(struct.pack(f"<{len(samples)}h", *samples))


# ==========================================================================
# POLLINATIONS — free online AI images, no key, no account
# ==========================================================================
def pollinations_image(prompt: str, seed: Optional[int] = None,
                       width: int = 512, height: int = 512) -> Image.Image:
    """Free image generation with no signup. Slower and less reliable than
    Bedrock, but it costs nothing. Rate-limited, so expect occasional waits."""
    import urllib.parse

    import requests

    url = (f"https://image.pollinations.ai/prompt/{urllib.parse.quote(prompt)}"
           f"?width={width}&height={height}&nologo=true")
    if seed is not None:
        url += f"&seed={seed}"
    resp = requests.get(url, timeout=180)
    resp.raise_for_status()
    return Image.open(io.BytesIO(resp.content))


# ==========================================================================
# BEDROCK — paid, best quality
# ==========================================================================
def bedrock_image(prompt: str, seed: Optional[int] = None) -> Image.Image:
    import boto3

    body = {"prompt": prompt, "aspect_ratio": "1:1", "output_format": "png"}
    if seed is not None:
        body["seed"] = seed
    import raccoon_config as cfg

    client = boto3.client("bedrock-runtime", region_name=cfg.AWS_REGION)
    resp = client.invoke_model(modelId=cfg.IMAGE_MODEL, body=json.dumps(body))
    payload = json.loads(resp["body"].read())
    return Image.open(io.BytesIO(base64.b64decode(payload["images"][0])))


def provider() -> str:
    # Read live rather than cached, so preflight can flip it for one test.
    import raccoon_config as cfg
    return os.environ.get("ASSET_PROVIDER", cfg.ASSET_PROVIDER).strip().lower()


def ai_image(prompt: str, seed: Optional[int] = None) -> Image.Image:
    """Routes to whichever AI provider is configured. Only called when the
    provider is not 'placeholder'."""
    p = provider()
    if p == "bedrock":
        return bedrock_image(prompt, seed)
    if p == "pollinations":
        return pollinations_image(prompt, seed)
    raise RuntimeError(f"ai_image called with provider={p!r}")
