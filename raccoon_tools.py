"""
raccoon_tools.py — the hands of the game-building agent.

Everything here talks to something real:

  * PALETTE / quantize      exact Python port of Table.java's 256-colour palette,
                            so generated art looks in-engine, not approximately
  * asset pipeline          Bedrock (Stability) -> downscale -> quantize ->
                            correct folder + correct dimensions
  * map builder             writes data/maps/*.txt directly in the engine's
                            own format. No browser editor needed.
  * engine control          javac / java / --pack, driven by subprocess

Nothing in here is a mock. Drop this next to game_agent.py.
"""

from __future__ import annotations

import base64
import io
import json
import math
import os
import shutil
import subprocess
import time
import wave
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import boto3
import requests
from PIL import Image

# ---------------------------------------------------------------------------
# Repo layout
# ---------------------------------------------------------------------------
REPO_ROOT = Path(os.environ.get("RACCOON_REPO", ".")).resolve()
ENGINE_DIR = REPO_ROOT / "RaccoonEngineV2"
ENGINE_SRC = ENGINE_DIR / "src"
ENGINE_BIN = ENGINE_DIR / "bin"
ENGINE_LIB = ENGINE_DIR / "lib"
DATA_DIR = ENGINE_DIR / "data"

# Folders ResourceManager.ALL_FOLDERS knows about, and the extension each
# one requires (ResourceManager.REQUIRED_EXT).
FOLDER_EXT = {
    "bgm": ".wav",
    "fonts": ".ttf",
    "maps": ".txt",
    "pics": ".png",
    "scripts": ".lua",
    "se": ".wav",
    "skybox": ".png",
    "sprites": ".png",
    "tex": ".png",
}

# Engine constants mirrored from Main.java / Table.java / Screen.java.
GAME_WID = 640
GAME_HEI = 480
MAX_PITCH = 200
SKYBOX_WID = GAME_WID * 4          # 2560
SKYBOX_HEI = GAME_HEI + 2 * MAX_PITCH  # 880
SPRITE_NUM_DIRECTIONS = 8
LIMIT_MAP_COORD = 512
MAX_NUM_SECTORS = 1024

AWS_REGION = os.environ.get("AWS_REGION", "us-west-2")
BEDROCK_IMAGE_MODEL_ID = os.environ.get(
    "BEDROCK_IMAGE_MODEL_ID", "stability.stable-image-core-v1:1"
)

# Solid magenta backdrop we ask the model for, then key out for transparency.
CHROMA_KEY = (255, 0, 255)
CHROMA_TOLERANCE = 90


# ===========================================================================
# 1. PALETTE — exact port of Table.init()
# ===========================================================================
def _build_palette() -> List[Tuple[int, int, int]]:
    """Rebuilds the engine's 256-colour palette exactly as Table.java does."""
    pal: List[Tuple[int, int, int]] = []

    base = [
        0x000000, 0x800000, 0x008000, 0x808000, 0x000080, 0x800080,
        0x008080, 0xC0C0C0, 0x808080, 0xFF0000, 0x00FF00, 0xFFFF00,
        0x0000FF, 0xFF00FF, 0x00FFFF, 0xFFFFFF,
    ]
    for c in base:
        pal.append(((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF))

    steps = [0x00, 0x5F, 0x87, 0xAF, 0xD7, 0xFF]
    for r in steps:
        for g in steps:
            for b in steps:
                pal.append((r, g, b))

    for i in range(24):
        v = 8 + i * 10
        pal.append((v, v, v))

    assert len(pal) == 256
    return pal


PALETTE = _build_palette()

# Pillow wants a flat [r,g,b, r,g,b, ...] list padded to 256 entries.
_PIL_PALETTE_IMG = Image.new("P", (1, 1))
_PIL_PALETTE_IMG.putpalette([v for rgb in PALETTE for v in rgb])


def quantize_to_engine_palette(img: Image.Image) -> Image.Image:
    """Snaps an RGB(A) image to the engine's exact palette.

    The engine does this itself at load time via findClosestColorIndex, but
    doing it here means what you see in the PNG is what you get in-game, and
    it gives generated art the flat, banded look retro assets need.
    Transparency is preserved.
    """
    has_alpha = img.mode == "RGBA"
    alpha = img.getchannel("A") if has_alpha else None

    quantized = img.convert("RGB").quantize(
        palette=_PIL_PALETTE_IMG, dither=Image.Dither.NONE
    ).convert("RGB")

    if has_alpha:
        quantized = quantized.convert("RGBA")
        quantized.putalpha(alpha)
    return quantized


def pixelate(img: Image.Image, size: int) -> Image.Image:
    """Downscales to size x size with box-averaging, which reads as real pixel
    art far better than nearest-neighbour on a diffusion output."""
    return img.resize((size, size), Image.Resampling.BOX)


def chroma_key(img: Image.Image, key=CHROMA_KEY, tol=CHROMA_TOLERANCE) -> Image.Image:
    """Makes the magenta backdrop transparent. Run BEFORE downscaling so the
    edge pixels average against the subject, not against magenta."""
    img = img.convert("RGBA")
    px = img.load()
    kr, kg, kb = key
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if abs(r - kr) < tol and abs(g - kg) < tol and abs(b - kb) < tol:
                px[x, y] = (0, 0, 0, 0)
    return img


# ===========================================================================
# 2. ASSET GENERATION — Bedrock (Stability) + music provider
# ===========================================================================
def _bedrock_runtime():
    return boto3.client("bedrock-runtime", region_name=AWS_REGION)


def _generate_raw_image(prompt: str, seed: Optional[int] = None) -> Image.Image:
    """One Stable Image Core call -> a PIL image at the model's native size."""
    body: Dict = {"prompt": prompt, "aspect_ratio": "1:1", "output_format": "png"}
    if seed is not None:
        body["seed"] = seed

    resp = _bedrock_runtime().invoke_model(
        modelId=BEDROCK_IMAGE_MODEL_ID, body=json.dumps(body)
    )
    payload = json.loads(resp["body"].read())
    return Image.open(io.BytesIO(base64.b64decode(payload["images"][0])))


def _asset_path(folder: str, filename: str) -> Path:
    ext = FOLDER_EXT[folder]
    if not filename.endswith(ext):
        filename = Path(filename).stem + ext
    out = DATA_DIR / folder / filename
    out.parent.mkdir(parents=True, exist_ok=True)
    return out


# -- textures ---------------------------------------------------------------
PIXEL_STYLE = (
    "retro DOOM-era pixel art, low colour count, flat shading, hard edges, "
    "no text, no watermark, no border"
)


def generate_texture(description: str, filename: str, size: int = 64) -> str:
    """Wall/floor/ceiling texture -> data/tex/. Should tile, so we ask for a
    seamless pattern and fill the whole frame."""
    prompt = (
        f"Seamless tileable square texture of {description}. "
        f"Top-down flat pattern filling the entire frame edge to edge. "
        f"{PIXEL_STYLE}."
    )
    img = _generate_raw_image(prompt)
    img = quantize_to_engine_palette(pixelate(img.convert("RGB"), size))
    out = _asset_path("tex", filename)
    img.save(out)
    return f"texture -> {out.relative_to(REPO_ROOT)} ({size}x{size})"


# -- sprites ----------------------------------------------------------------
# Screen.drawSprites: relative_angle = atan2(player_z - sprite_z,
# player_x - sprite_x) - direction_rad, sliced into 8 frames laid out
# left-to-right. Frame 0 is the view with the player straight ahead of the
# sprite's facing direction, i.e. the FRONT view; each later frame rotates
# 45 degrees. ResourceManager.validateImage throws unless width == 8 * height.
SPRITE_VIEW_LABELS = [
    "front view, facing the viewer directly",
    "front three-quarter view, turned 45 degrees to its right",
    "side profile view, facing right",
    "rear three-quarter view, turned 135 degrees",
    "back view, facing directly away from the viewer",
    "rear three-quarter view from the other side, turned 225 degrees",
    "side profile view, facing left",
    "front three-quarter view, turned 315 degrees to its left",
]


def generate_sprite_sheet(description: str, filename: str, size: int = 64) -> str:
    """Builds an 8-direction sprite sheet -> data/sprites/.

    Makes 8 generations (one per facing) with a shared seed so the character
    stays recognisable across frames, keys out the magenta backdrop, then
    tiles them horizontally into the 8*size x size sheet the engine wants.
    """
    seed = int(time.time()) % 4_000_000_000
    sheet = Image.new("RGBA", (size * SPRITE_NUM_DIRECTIONS, size), (0, 0, 0, 0))

    for i, view in enumerate(SPRITE_VIEW_LABELS):
        prompt = (
            f"A single {description}, {view}. "
            f"Full body, centred, standing upright, isolated on a plain solid "
            f"magenta (#FF00FF) background. Consistent character design. "
            f"{PIXEL_STYLE}."
        )
        frame = _generate_raw_image(prompt, seed=seed)
        frame = chroma_key(frame)
        frame = quantize_to_engine_palette(pixelate(frame, size))
        sheet.paste(frame, (i * size, 0), frame)

    out = _asset_path("sprites", filename)
    sheet.save(out)
    return (
        f"sprite sheet -> {out.relative_to(REPO_ROOT)} "
        f"({size * SPRITE_NUM_DIRECTIONS}x{size}, 8 facings)"
    )


# -- skybox / pics ----------------------------------------------------------
def generate_skybox(description: str, filename: str) -> str:
    """Skybox -> data/skybox/. validateImage requires width == 2560 exactly
    and height >= 480; 880 gives the full look-up/down range. Generated as a
    square then stretched, since the engine wraps it horizontally anyway."""
    prompt = (
        f"A wide panoramic sky: {description}. Horizon line across the middle. "
        f"{PIXEL_STYLE}."
    )
    img = _generate_raw_image(prompt).convert("RGB")
    img = img.resize((SKYBOX_WID, SKYBOX_HEI), Image.Resampling.LANCZOS)
    img = quantize_to_engine_palette(img)
    out = _asset_path("skybox", filename)
    img.save(out)
    return f"skybox -> {out.relative_to(REPO_ROOT)} ({SKYBOX_WID}x{SKYBOX_HEI})"


def generate_pic(description: str, filename: str,
                 width: int = GAME_WID, height: int = GAME_HEI) -> str:
    """Full-screen picture (title card, HUD art) -> data/pics/."""
    prompt = f"{description}. {PIXEL_STYLE}."
    img = _generate_raw_image(prompt).convert("RGB")
    img = quantize_to_engine_palette(
        img.resize((width, height), Image.Resampling.BOX)
    )
    out = _asset_path("pics", filename)
    img.save(out)
    return f"pic -> {out.relative_to(REPO_ROOT)} ({width}x{height})"


# -- audio ------------------------------------------------------------------
# The engine only reads .wav (ResourceManager.REQUIRED_EXT for bgm and se).
REPLICATE_API_TOKEN = os.environ.get("REPLICATE_API_TOKEN", "")
REPLICATE_MUSIC_MODEL = os.environ.get(
    "REPLICATE_MUSIC_MODEL", "meta/musicgen"
)


def _to_wav(raw: bytes, out: Path) -> bool:
    """Converts whatever the provider returned into a .wav. Uses ffmpeg when
    it's on PATH; if the bytes are already RIFF we just write them."""
    if raw[:4] == b"RIFF":
        out.write_bytes(raw)
        return True
    if shutil.which("ffmpeg") is None:
        return False
    tmp = out.with_suffix(".tmp_audio")
    tmp.write_bytes(raw)
    subprocess.run(
        ["ffmpeg", "-y", "-i", str(tmp), "-ar", "44100", "-ac", "2", str(out)],
        capture_output=True, check=False,
    )
    tmp.unlink(missing_ok=True)
    return out.exists()


def _silent_wav(out: Path, seconds: float = 2.0) -> None:
    """Placeholder track so a missing music provider can't crash the build."""
    with wave.open(str(out), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(22050)
        w.writeframes(b"\x00\x00" * int(22050 * seconds))


def generate_audio(description: str, filename: str, folder: str = "bgm",
                   duration: int = 20) -> str:
    """Music/SFX -> data/bgm/ or data/se/, always as .wav.

    Uses Replicate because it's a single token and a single HTTP endpoint —
    no SDK, no account plumbing. Swap the block below for ElevenLabs or
    Mubert and nothing else in the agent has to change.
    """
    out = _asset_path(folder, filename)

    if not REPLICATE_API_TOKEN:
        _silent_wav(out)
        return (f"[no REPLICATE_API_TOKEN] wrote silent placeholder "
                f"{out.relative_to(REPO_ROOT)} for: {description}")

    try:
        resp = requests.post(
            f"https://api.replicate.com/v1/models/{REPLICATE_MUSIC_MODEL}/predictions",
            headers={
                "Authorization": f"Bearer {REPLICATE_API_TOKEN}",
                "Content-Type": "application/json",
                "Prefer": "wait",
            },
            json={"input": {
                "prompt": f"{description}, retro video game soundtrack, chiptune",
                "duration": duration,
                "output_format": "wav",
            }},
            timeout=300,
        )
        resp.raise_for_status()
        result = resp.json()

        # "Prefer: wait" usually returns a finished prediction; poll if not.
        for _ in range(60):
            if result.get("status") in ("succeeded", "failed", "canceled"):
                break
            time.sleep(5)
            result = requests.get(
                result["urls"]["get"],
                headers={"Authorization": f"Bearer {REPLICATE_API_TOKEN}"},
                timeout=60,
            ).json()

        if result.get("status") != "succeeded":
            _silent_wav(out)
            return f"audio generation did not succeed; wrote placeholder at {out.name}"

        url = result["output"]
        if isinstance(url, list):
            url = url[0]
        raw = requests.get(url, timeout=180).content

        if not _to_wav(raw, out):
            _silent_wav(out)
            return (f"got audio but could not convert to .wav (install ffmpeg); "
                    f"wrote placeholder at {out.name}")
        return f"audio -> {out.relative_to(REPO_ROOT)} ({duration}s)"

    except Exception as exc:  # noqa: BLE001
        _silent_wav(out)
        return f"audio generation failed ({exc}); wrote placeholder at {out.name}"


# ===========================================================================
# 3. MAP BUILDING — Manhattan partitioning, same algorithm as the editor
# ===========================================================================
# WHY THIS IS NOT JUST "EMIT SOME LINEDEFS"
# -----------------------------------------
# Screen.verticals is a flat grid: one Edge per (x, z, orientation) cell,
# indexed by makeWallIndex((int) x, (int) z, is_vertical). So every cell edge
# in the world holds EXACTLY ONE edge record. If two sectors each emit their
# own wall along a line they share, the second write silently clobbers the
# first and the wall ends up attributed to the wrong sector — wrong heights,
# wrong textures, no error message.
#
# That is what the editor's Manhattan partitioning prevents, and it is why
# the editor exists. The invariant it maintains:
#
#   1. Collect every x and every z coordinate used by the world boundary,
#      by any room, and by any doorway.
#   2. The full cartesian grid of those coordinates tiles the world with no
#      gaps and no overlaps. EVERY cell becomes a sector, including cells no
#      room covers ("void" sectors).
#   3. Each cell edge is therefore adjacent to either one sector (it lies on
#      the world boundary -> [BOUNDARIES]) or exactly two (-> [EDGES]).
#      Emitted once per edge, never twice. No clobbering.
#
# A big room becomes several sectors with invisible passable portals between
# them; that is normal and correct. Solidity, not geometry, is what makes a
# portal read as a wall.
#
# FORMAT (from RaccoonAPI.worldLoadMap + editor.js downloadMap)
#   [SIZE]        width height
#   [SECTORS]     id floor_h ceil_h floor_tex floor_b floor_tiled floor_skip
#                    ceil_tex ceil_b ceil_tiled ceil_skip
#   [BOUNDARIES]  x1 z1 x2 z2 sector tex bright tiled skip
#   [EDGES]       x1 z1 x2 z2 sec_a sec_b  bot(4)  mid(4)  top(4)  solid


@dataclass
class Room:
    """An axis-aligned rectangular area. Corners are integer grid coords.

    A room is a REGION, not a sector: partitioning may split it into several
    sectors joined by invisible portals. That is expected.
    """
    id: int
    x1: int
    z1: int
    x2: int
    z2: int
    floor_height: float = 0.0
    ceil_height: float = 4.0
    floor_tex: str = "tex.png"
    ceil_tex: str = "tex.png"
    wall_tex: str = "wood.png"
    brightness: float = 0.5
    floor_tiled: int = 1
    ceil_tiled: int = 1
    skip_ceiling: bool = False


@dataclass
class Doorway:
    """A passable opening on the line two rooms share. Axis-aligned only."""
    sector_a: int
    sector_b: int
    x1: int
    z1: int
    x2: int
    z2: int
    solid: bool = False
    tex: str = "wood.png"
    brightness: float = 0.5


@dataclass
class MapSpec:
    rooms: List[Room] = field(default_factory=list)
    doorways: List[Doorway] = field(default_factory=list)
    # The world rectangle. The editor requires it to touch the origin, so we
    # do too, which keeps agent output loadable there. Auto-sized if omitted.
    world_x2: Optional[int] = None
    world_z2: Optional[int] = None
    void_tex: str = "wood.png"
    boundary_tex: str = "wood.png"


@dataclass
class _Cell:
    """One grid cell after partitioning. Becomes exactly one sector."""
    id: int
    x1: int
    z1: int
    x2: int
    z2: int
    room: Optional[Room]   # None = void


def _covering_room(rooms: List[Room], cx: float, cz: float) -> Optional[Room]:
    for r in rooms:
        if r.x1 <= cx <= r.x2 and r.z1 <= cz <= r.z2:
            return r
    return None


def _on_doorway(d: Doorway, x1: int, z1: int, x2: int, z2: int) -> bool:
    """Is this cell edge inside the doorway's span? (editor: isEdgePartOfLine)"""
    if x1 == x2 == d.x1 == d.x2:
        return min(d.z1, d.z2) <= min(z1, z2) and max(z1, z2) <= max(d.z1, d.z2)
    if z1 == z2 == d.z1 == d.z2:
        return min(d.x1, d.x2) <= min(x1, x2) and max(x1, x2) <= max(d.x1, d.x2)
    return False


def partition(spec: MapSpec) -> Tuple[List[_Cell], int, int]:
    """Manhattan partitioning: rooms + doorways -> a gapless grid of sectors."""
    if not spec.rooms:
        raise ValueError("map has no rooms")

    ids = sorted(r.id for r in spec.rooms)
    if ids != list(range(len(ids))):
        raise ValueError(f"room ids must be 0..n-1 with no gaps, got {ids}")

    for r in spec.rooms:
        if r.x2 <= r.x1 or r.z2 <= r.z1:
            raise ValueError(f"room {r.id} has a non-positive size")
        if min(r.x1, r.z1) < 0 or max(r.x2, r.z2) > LIMIT_MAP_COORD:
            raise ValueError(f"room {r.id} falls outside 0..{LIMIT_MAP_COORD}")
        if r.ceil_height <= r.floor_height:
            raise ValueError(f"room {r.id} has its ceiling at or below its floor")
        if not 0.0 <= r.brightness <= 1.0:
            raise ValueError(f"room {r.id} brightness must be within 0..1")

    by_id = {r.id: r for r in spec.rooms}
    for d in spec.doorways:
        if d.sector_a not in by_id or d.sector_b not in by_id:
            raise ValueError(f"doorway names a room that doesn't exist: {d}")
        if d.x1 != d.x2 and d.z1 != d.z2:
            raise ValueError(f"doorway {d} is diagonal; must be axis-aligned")
        if d.x1 == d.x2 and d.z1 == d.z2:
            raise ValueError(f"doorway {d} has zero length")
        # It must actually lie on a line both rooms touch, or it opens onto void.
        a, b = by_id[d.sector_a], by_id[d.sector_b]
        if d.x1 == d.x2:
            shared = d.x1 in (a.x1, a.x2) and d.x1 in (b.x1, b.x2)
        else:
            shared = d.z1 in (a.z1, a.z2) and d.z1 in (b.z1, b.z2)
        if not shared:
            raise ValueError(
                f"doorway between rooms {d.sector_a} and {d.sector_b} is not on "
                f"a wall line they share — the rooms must be flush against "
                f"each other for a doorway to connect them"
            )

    world_x2 = spec.world_x2 if spec.world_x2 is not None else max(r.x2 for r in spec.rooms)
    world_z2 = spec.world_z2 if spec.world_z2 is not None else max(r.z2 for r in spec.rooms)

    # Step 1: every coordinate that matters becomes a cut line.
    xs, zs = {0, world_x2}, {0, world_z2}
    for r in spec.rooms:
        xs.update((r.x1, r.x2))
        zs.update((r.z1, r.z2))
    for d in spec.doorways:
        xs.update((d.x1, d.x2))
        zs.update((d.z1, d.z2))
    xs = sorted(x for x in xs if 0 <= x <= world_x2)
    zs = sorted(z for z in zs if 0 <= z <= world_z2)

    n_cells = (len(xs) - 1) * (len(zs) - 1)
    if n_cells > MAX_NUM_SECTORS:
        raise ValueError(
            f"partitioning produces {n_cells} sectors, over the engine's limit "
            f"of {MAX_NUM_SECTORS}. Rooms sharing x/z coordinates cut fewer "
            f"lines — align them to a common grid, or use fewer rooms."
        )

    # Step 2: every cell becomes a sector. Void cells included, so the world
    # is tiled with no gaps.
    cells: List[_Cell] = []
    cid = 0
    for i in range(len(xs) - 1):
        for j in range(len(zs) - 1):
            x1, x2, z1, z2 = xs[i], xs[i + 1], zs[j], zs[j + 1]
            room = _covering_room(spec.rooms, (x1 + x2) / 2, (z1 + z2) / 2)
            cells.append(_Cell(cid, x1, z1, x2, z2, room))
            cid += 1

    return cells, world_x2, world_z2


def build_map_text(spec: MapSpec) -> str:
    """Rooms + doorways -> a valid map.txt with no grid-slot collisions."""
    cells, world_x2, world_z2 = partition(spec)

    lines = ["[SIZE]", f"{world_x2 + 1} {world_z2 + 1}", "[SECTORS]"]
    for c in cells:
        r = c.room
        fh = r.floor_height if r else 0.0
        ch = r.ceil_height if r else 4.0
        ftex = r.floor_tex if r else spec.void_tex
        ctex = r.ceil_tex if r else spec.void_tex
        b = r.brightness if r else 0.5
        ft = r.floor_tiled if r else 1
        ct = r.ceil_tiled if r else 1
        skip_c = str(bool(r.skip_ceiling)).lower() if r else "false"
        lines.append(
            f"{c.id} {fh} {ch} {ftex} {b} {ft} false {ctex} {b} {ct} {skip_c}"
        )

    # Step 3: collect each cell edge once, recording which sectors touch it.
    edge_map: Dict[Tuple[int, int, int, int], List[int]] = {}
    for c in cells:
        for (x1, z1, x2, z2) in (
            (c.x1, c.z1, c.x2, c.z1),   # south
            (c.x2, c.z1, c.x2, c.z2),   # east
            (c.x1, c.z2, c.x2, c.z2),   # north
            (c.x1, c.z1, c.x1, c.z2),   # west
        ):
            key = (x1, z1, x2, z2) if (x1, z1) <= (x2, z2) else (x2, z2, x1, z1)
            edge_map.setdefault(key, []).append(c.id)

    by_cell = {c.id: c for c in cells}
    boundaries, edges = [], []

    for (x1, z1, x2, z2), sector_ids in edge_map.items():
        on_world_edge = (
            (x1 == x2 == 0) or (x1 == x2 == world_x2)
            or (z1 == z2 == 0) or (z1 == z2 == world_z2)
        )

        if on_world_edge:
            # Adjacent to exactly one cell — the ray stops here.
            for sid in sector_ids:
                c = by_cell[sid]
                tex = c.room.wall_tex if c.room else spec.boundary_tex
                b = c.room.brightness if c.room else 0.5
                boundaries.append(f"{x1} {z1} {x2} {z2} {sid} {tex} {b} 0.5 false")
            continue

        if len(sector_ids) != 2:
            continue  # degenerate; partitioning shouldn't produce these

        a_id, b_id = sector_ids
        ca, cb = by_cell[a_id], by_cell[b_id]

        door = next(
            (d for d in spec.doorways if _on_doorway(d, x1, z1, x2, z2)
             and ca.room is not None and cb.room is not None
             and {ca.room.id, cb.room.id} == {d.sector_a, d.sector_b}),
            None,
        )

        if door is not None:
            # A doorway: passable, nothing drawn.
            band = f"{door.tex} {door.brightness} 0.5 true"
            solid = str(bool(door.solid)).lower()
        elif ca.room is not None and cb.room is not None and ca.room.id == cb.room.id:
            # Interior seam within one room: invisible and walk-through.
            band = f"{ca.room.wall_tex} {ca.room.brightness} 0.5 true"
            solid = "false"
        else:
            # Room meets void, or two rooms with no doorway here: a real wall.
            src = ca.room or cb.room
            tex = src.wall_tex if src else spec.boundary_tex
            b = src.brightness if src else 0.5
            band = f"{tex} {b} 0.5 false"
            solid = "true"

        edges.append(
            f"{x1} {z1} {x2} {z2} {a_id} {b_id} {band} {band} {band} {solid}"
        )

    lines.append("[BOUNDARIES]")
    lines.extend(boundaries)
    lines.append("[EDGES]")
    lines.extend(edges)

    text = "\n".join(lines) + "\n"
    _assert_no_slot_collisions(text)
    return text


def _assert_no_slot_collisions(map_text: str) -> None:
    """Replays the engine's verticals[] indexing and fails loudly if any cell
    edge is written twice. This is the invariant that makes the whole thing
    correct, so it is checked on every build rather than trusted."""
    slots: Dict[Tuple[int, int, int], int] = {}
    section = None
    for line in map_text.splitlines():
        if line.startswith("["):
            section = line
            continue
        if section not in ("[BOUNDARIES]", "[EDGES]") or not line.strip():
            continue
        p = line.split()
        x1, z1, x2, z2 = (int(float(v)) for v in p[:4])
        cells = ([(px, z1, 1) for px in range(min(x1, x2), max(x1, x2))]
                 if z1 == z2 else
                 [(x1, pz, 0) for pz in range(min(z1, z2), max(z1, z2))])
        for cell in cells:
            if cell in slots:
                raise AssertionError(
                    f"grid slot {cell} written twice — partitioning is wrong. "
                    f"This would silently corrupt the map."
                )
            slots[cell] = 1


def build_editor_project(spec: MapSpec) -> dict:
    """Emits a project.json the browser Map Editor can open.

    This is the round trip: the agent drafts a layout, you load the project
    in RaccoonMapEditor, drag things around, re-partition and re-export.
    The editor reads userRectangles + worldBoundary (editor.js loadProject).
    """
    def rect_lines(x1, z1, x2, z2):
        return [
            {"x1": x1, "z1": z1, "x2": x2, "z2": z1},
            {"x1": x2, "z1": z1, "x2": x2, "z2": z2},
            {"x1": x1, "z1": z2, "x2": x2, "z2": z2},
            {"x1": x1, "z1": z1, "x2": x1, "z2": z2},
        ]

    def verts(x1, z1, x2, z2):
        return [{"x": x1, "z": z1}, {"x": x2, "z": z1},
                {"x": x2, "z": z2}, {"x": x1, "z": z2}]

    def edge_cfg(tex, bright, solid):
        band = {"Texture": tex, "Brightness": bright, "Tiled": 1, "Skip": not solid}
        return {
            "topTexture": tex, "topBrightness": bright, "topTiled": 1,
            "topSkip": not solid,
            "midTexture": tex, "midBrightness": bright, "midTiled": 1,
            "midSkip": not solid,
            "botTexture": tex, "botBrightness": bright, "botTiled": 1,
            "botSkip": not solid,
            "solid": solid,
        }

    user_rects = []
    for r in spec.rooms:
        # Which side of this room has a doorway on it stops being solid.
        open_sides = set()
        for d in spec.doorways:
            if r.id not in (d.sector_a, d.sector_b):
                continue
            if d.x1 == d.x2 == r.x2:
                open_sides.add("east")
            elif d.x1 == d.x2 == r.x1:
                open_sides.add("west")
            elif d.z1 == d.z2 == r.z2:
                open_sides.add("north")
            elif d.z1 == d.z2 == r.z1:
                open_sides.add("south")

        user_rects.append({
            "vertices": verts(r.x1, r.z1, r.x2, r.z2),
            "lines": rect_lines(r.x1, r.z1, r.x2, r.z2),
            "floorHeight": r.floor_height,
            "ceilingHeight": r.ceil_height,
            "floorTexture": r.floor_tex,
            "floorBrightness": r.brightness,
            "floorTiled": r.floor_tiled,
            "floorSkipTexture": False,
            "ceilingTexture": r.ceil_tex,
            "ceilingBrightness": r.brightness,
            "ceilingTiled": r.ceil_tiled,
            "ceilingSkipTexture": bool(r.skip_ceiling),
            "edges": {
                side: edge_cfg(r.wall_tex, r.brightness, side not in open_sides)
                for side in ("north", "south", "east", "west")
            },
        })

    _, wx2, wz2 = partition(spec)
    wall = {"texture": spec.boundary_tex, "brightness": 0.5,
            "tiled": 1, "skipTexture": False}
    world = {
        "vertices": verts(0, 0, wx2, wz2),
        "lines": rect_lines(0, 0, wx2, wz2),
        "floorHeight": 0, "ceilingHeight": 4,
        "floorTexture": spec.void_tex, "floorBrightness": 0.5,
        "floorTiled": 1, "floorSkipTexture": False,
        "ceilingTexture": spec.void_tex, "ceilingBrightness": 0.5,
        "ceilingTiled": 1, "ceilingSkipTexture": False,
        "walls": {d: dict(wall) for d in ("north", "south", "east", "west")},
    }
    return {"userRectangles": user_rects, "worldBoundary": world}


def write_map(spec_dict: dict, filename: str = "map.txt") -> str:
    """Agent-facing entry point. Validates, partitions, writes data/maps/
    <filename>, and drops a matching project.json beside it for editing."""
    spec = MapSpec(
        rooms=[Room(**r) for r in spec_dict.get("rooms", [])],
        doorways=[Doorway(**d) for d in spec_dict.get("doorways", [])],
        world_x2=spec_dict.get("world_x2"),
        world_z2=spec_dict.get("world_z2"),
    )
    text = build_map_text(spec)
    out = _asset_path("maps", filename)
    out.write_text(text)

    project = REPO_ROOT / "editor_projects" / f"{Path(filename).stem}.project.json"
    project.parent.mkdir(parents=True, exist_ok=True)
    project.write_text(json.dumps(build_editor_project(spec), indent=2))

    n_sectors = text.split("[BOUNDARIES]")[0].strip().splitlines()
    return (
        f"map -> {out.relative_to(REPO_ROOT)} "
        f"({len(n_sectors) - 3} sectors from {len(spec.rooms)} rooms, "
        f"{len(spec.doorways)} doorways); "
        f"editable project -> {project.relative_to(REPO_ROOT)}"
    )


# ===========================================================================
# 4. SCRIPTS
# ===========================================================================
def write_script(lua_source: str, filename: str) -> str:
    """Writes a Lua file into data/scripts/. init.lua is the entry point the
    engine runs on startup (see ScriptRunner)."""
    out = _asset_path("scripts", filename)
    out.write_text(lua_source)
    return f"script -> {out.relative_to(REPO_ROOT)} ({len(lua_source.splitlines())} lines)"


# ===========================================================================
# 5. ENGINE CONTROL
# ===========================================================================
def _classpath() -> str:
    jars = [str(p) for p in ENGINE_LIB.glob("*.jar")]
    return os.pathsep.join([str(ENGINE_BIN), *jars])


def compile_engine() -> str:
    """javac every source file into bin/. This is the real build step —
    if the agent broke something, it shows up here."""
    if shutil.which("javac") is None:
        return "javac not found on PATH — install a JDK to build the engine."

    ENGINE_BIN.mkdir(parents=True, exist_ok=True)
    sources = [str(p) for p in ENGINE_SRC.rglob("*.java")]
    if not sources:
        return f"no .java sources found under {ENGINE_SRC}"

    proc = subprocess.run(
        ["javac", "-cp", _classpath(), "-d", str(ENGINE_BIN), *sources],
        capture_output=True, text=True,
    )
    if proc.returncode != 0:
        return f"COMPILE FAILED:\n{proc.stderr[-3000:]}"
    return f"compiled {len(sources)} java files into {ENGINE_BIN.relative_to(REPO_ROOT)}"


def run_engine(timeout: int = 25) -> str:
    """Launches the game. Opens a Swing window, so this is a smoke test:
    it runs for `timeout` seconds and reports anything the engine logged.
    A clean run with no stderr means the map and assets loaded."""
    if shutil.which("java") is None:
        return "java not found on PATH."

    proc = subprocess.Popen(
        ["java", "-cp", _classpath(), "raccoon.Main"],
        cwd=str(ENGINE_DIR),
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    try:
        out, err = proc.communicate(timeout=timeout)
        status = f"engine exited early with code {proc.returncode}"
    except subprocess.TimeoutExpired:
        proc.kill()
        out, err = proc.communicate()
        status = f"engine ran for {timeout}s without crashing"

    problems = [ln for ln in (err or "").splitlines() if ln.strip()]
    tail = "\n".join(problems[-25:]) if problems else "(no errors reported)"
    return f"{status}\n--- engine stderr ---\n{tail}"


def pack_rpk() -> str:
    """`java raccoon.Main --pack` writes data/ out as a single data.rpk
    (see Rpk.java) and exits. That file is the shippable bundle."""
    if shutil.which("java") is None:
        return "java not found on PATH."

    proc = subprocess.run(
        ["java", "-cp", _classpath(), "raccoon.Main", "--pack"],
        cwd=str(ENGINE_DIR), capture_output=True, text=True, timeout=180,
    )
    rpk = ENGINE_DIR / "data.rpk"
    if rpk.exists():
        mb = rpk.stat().st_size / 1_048_576
        return f"packed -> {rpk.relative_to(REPO_ROOT)} ({mb:.2f} MB)"
    return f"pack did not produce data.rpk.\n{proc.stdout[-1000:]}\n{proc.stderr[-1000:]}"


def list_assets() -> str:
    """What the engine can currently see. Lets the agent reference existing
    art instead of regenerating something that's already there."""
    out = []
    for folder in sorted(FOLDER_EXT):
        d = DATA_DIR / folder
        names = sorted(p.name for p in d.glob(f"*{FOLDER_EXT[folder]}")) if d.is_dir() else []
        out.append(f"{folder}/: {', '.join(names) if names else '(empty)'}")
    return "\n".join(out)
