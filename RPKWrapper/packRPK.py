#!/usr/bin/env python3
"""
packRPK.py  —  Raccoon Package Builder
=======================================
Takes a `data/` folder and packs everything into `data.rpk`.

RPK FILE LAYOUT
---------------
The file is split into two parts:

  1. HEADER  — plain UTF-8 text you can read in Notepad.
               Describes every entry: what section it belongs to,
               its byte offset in the DATA block, and its size + metadata.

  2. DATA    — raw binary blobs, one after another, starting at the
               byte offset noted in the header as DATA_START.

Java (or any language) reads the header as text lines, parses the
metadata it needs, then seeks to the right offset to grab binary data.

SECTION BREAKDOWN
-----------------
  bgm      — Background music.  Stored as raw PCM: 16-bit signed
              little-endian samples.  Header carries sample rate,
              channel count, and sample count so Java can reconstruct audio.

  se       — Sound effects.  Identical format to bgm.

  fonts    — TTF files turned into a bitmap glyph atlas (PNG → RGBA pixels).
              Every printable ASCII character is rendered into a grid.
              Header carries atlas width/height and a glyph map:
              char=x,y,w,h pairs so Java can blit individual characters.

  maps     — Plain-text .txt files.  Stored as raw UTF-8 bytes.

  scripts  — Lua .lua files.  Also stored as raw UTF-8 bytes.

  pics     — Images (PNG/JPG/JPEG/BMP).  Stored as raw RGBA pixels,
              width × height × 4 bytes, row-major top-to-bottom.
              Alpha=0 means "no pixel drawn here" (transparent).
              Header carries width and height.

  sprites  — Same pixel format as pics.
              Expected shape: width = 8 × height (8-directional sprite sheet).

  skybox   — Same pixel format as pics.
              Expected dimensions: 2560 × 480.

  tex      — Same pixel format as pics.  Used for wall/floor textures.

HEADER FORMAT (text lines)
---------------------------
  RACCOON_PACKAGE
  DATA_START:<byte_offset>
  [SECTION:<name>]
  ENTRY:<key> OFFSET:<n> SIZE:<n> <extra metadata fields...>
  ...
  [END_HEADER]

After [END_HEADER] the binary DATA block begins immediately.

REQUIREMENTS
------------
  pip install Pillow pydub freetype-py

  For audio decoding pydub also needs ffmpeg on PATH
  (for mp3/ogg/flac).  WAV works without it.
"""

import os
import io
import sys
import struct
import argparse

# ── optional deps with friendly error messages ────────────────────────────────
try:
    from PIL import Image
except ImportError:
    sys.exit("ERROR: Pillow not found.  Run:  pip install Pillow")

try:
    from pydub import AudioSegment
except ImportError:
    sys.exit("ERROR: pydub not found.  Run:  pip install pydub")

try:
    import freetype
except ImportError:
    sys.exit("ERROR: freetype-py not found.  Run:  pip install freetype-py")


# ══════════════════════════════════════════════════════════════════════════════
#  CONSTANTS
# ══════════════════════════════════════════════════════════════════════════════

RPK_MAGIC   = "RACCOON_PACKAGE"   # first line of every .rpk file
HEADER_END  = "[END_HEADER]"          # sentinel that marks end of text header

# Printable ASCII range we bake into every font atlas (space=32 … tilde=126)
GLYPH_CHARS = [chr(c) for c in range(32, 127)]   # 95 characters

# Audio output: 16-bit signed PCM, little-endian, 44 100 Hz stereo
PCM_SAMPLE_RATE = 44100
PCM_CHANNELS    = 2
PCM_SAMPLE_WIDTH = 2   # bytes per sample (16-bit)


# ══════════════════════════════════════════════════════════════════════════════
#  AUDIO  (bgm / se)
# ══════════════════════════════════════════════════════════════════════════════

def encode_audio(path: str) -> tuple[bytes, dict]:
    """
    Load any audio file pydub can handle (wav, mp3, ogg, flac, …),
    convert to 44 100 Hz stereo 16-bit PCM, and return raw bytes + metadata.

    Returns:
        (raw_bytes, metadata_dict)
        metadata keys: samplerate, channels, numsamples
    """
    audio = AudioSegment.from_file(path)

    # Normalise to our standard PCM spec
    audio = audio.set_frame_rate(PCM_SAMPLE_RATE)
    audio = audio.set_channels(PCM_CHANNELS)
    audio = audio.set_sample_width(PCM_SAMPLE_WIDTH)

    # raw_data is already little-endian 16-bit signed integers (pydub guarantees this)
    raw = audio.raw_data
    num_samples = len(raw) // (PCM_CHANNELS * PCM_SAMPLE_WIDTH)

    meta = {
        "samplerate":  PCM_SAMPLE_RATE,
        "channels":    PCM_CHANNELS,
        "numsamples":  num_samples,
    }
    return raw, meta


# ══════════════════════════════════════════════════════════════════════════════
#  IMAGES  (pics / sprites / skybox / tex)
# ══════════════════════════════════════════════════════════════════════════════

def encode_image(path: str) -> tuple[bytes, dict]:
    """
    Load any image Pillow can read.  Convert to RGBA so we always have
    four channels.  For images that had no alpha (e.g. JPEG / RGB PNG)
    every pixel gets alpha=255 (fully opaque) automatically.

    Pixels are stored row-major, top-to-bottom, left-to-right.
    Each pixel = 4 bytes: R G B A.
    Alpha=0 means "nothing drawn here" (transparent).

    Returns:
        (raw_rgba_bytes, metadata_dict)
        metadata keys: width, height
    """
    img = Image.open(path).convert("RGBA")
    raw = img.tobytes()   # R G B A R G B A … one row at a time
    meta = {
        "width":  img.width,
        "height": img.height,
    }
    return raw, meta


# ══════════════════════════════════════════════════════════════════════════════
#  TEXT  (maps / scripts)
# ══════════════════════════════════════════════════════════════════════════════

def encode_text(path: str) -> tuple[bytes, dict]:
    """
    Read a text file as UTF-8 bytes.
    We store no extra metadata for text — the SIZE field in the header is enough.
    """
    with open(path, "rb") as f:
        raw = f.read()
    return raw, {}


# ══════════════════════════════════════════════════════════════════════════════
#  FONTS  (bitmap atlas)
# ══════════════════════════════════════════════════════════════════════════════

def _measure_glyph_size(face) -> int:
    """
    Find the smallest point size at which freetype renders glyphs that are
    at least 1 pixel tall.  We try sizes 6, 8, 10, 12 and pick the first
    that gives us full pixels.  Falls back to 12 if nothing works.
    """
    for pt in (6, 8, 10, 12):
        # 96 dpi is a common screen resolution
        face.set_char_size(pt * 64, 0, 96, 0)
        face.load_char('A', freetype.FT_LOAD_RENDER)
        if face.glyph.bitmap.rows > 0:
            return pt
    return 12


def encode_font(path: str) -> tuple[bytes, dict]:
    """
    Render every printable ASCII character (GLYPH_CHARS) using freetype-py
    into a single RGBA atlas image.

    Layout:
      - All glyphs are placed in a single horizontal row (simple strip atlas).
      - Atlas height = max glyph height across all rendered glyphs.
      - Atlas width  = sum of all glyph advance widths.
      - Each glyph is drawn at its bitmap_left / bitmap_top offsets relative
        to the baseline so descenders (g, p, y …) look correct.

    The glyph map in the header uses ordinal values, not raw characters,
    to avoid any encoding surprises.  Format per glyph:
        <decimal_ascii>=<x>,<y>,<w>,<h>,<bearing_x>,<bearing_y>,<advance>

    Java can decode: for char 'A' (65), look up "65=…" to get its atlas rect.
    bearing_x / bearing_y help with correct sub-pixel placement if desired.
    advance is the horizontal step to the next character (in pixels).

    Returns:
        (raw_rgba_bytes_of_atlas, metadata_dict)
        metadata keys: atlaswidth, atlasheight, glyphs (the glyph map string)
    """
    face = freetype.Face(path)
    pt   = _measure_glyph_size(face)

    # ── First pass: render every glyph, store bitmaps + metrics ───────────────
    glyph_data = []   # list of dicts, one per character in GLYPH_CHARS
    max_height  = 0   # tallest glyph — sets atlas height
    max_bearing = 0   # highest baseline offset — needed for y alignment

    for ch in GLYPH_CHARS:
        face.load_char(ch, freetype.FT_LOAD_RENDER)
        bm = face.glyph.bitmap

        rows    = bm.rows          # pixel height of this glyph's bitmap
        width   = bm.width         # pixel width of this glyph's bitmap
        pitch   = abs(bm.pitch)    # bytes per row in the bitmap buffer

        bearing_x = face.glyph.bitmap_left    # pixels right from pen origin
        bearing_y = face.glyph.bitmap_top     # pixels up from baseline
        advance   = face.glyph.advance.x >> 6 # advance width in pixels (26.6 fixed → int)

        # Copy the freetype bitmap buffer into a plain Python bytes object.
        # freetype gives us one byte per pixel (grey 0-255).
        buf = bytes(bm.buffer)

        glyph_data.append({
            "ch":        ch,
            "rows":      rows,
            "width":     width,
            "pitch":     pitch,
            "bearing_x": bearing_x,
            "bearing_y": bearing_y,
            "advance":   advance,
            "buf":       buf,
        })

        if bearing_y > max_bearing:
            max_bearing = bearing_y
        if rows > max_height:
            max_height  = rows

    # Atlas height = enough to fit tallest glyph below the common baseline.
    # We give each glyph `max_bearing` pixels above the bottom of the atlas
    # for its top, so descenders below baseline all have room.
    atlas_h = max_bearing + max(
        (g["rows"] - g["bearing_y"]) for g in glyph_data if g["rows"] > 0
    ) if any(g["rows"] > 0 for g in glyph_data) else max_height or 16

    # ── Second pass: build atlas, record each glyph's x position ─────────────
    # We use advance width as the column slot so spacing matches real text.
    atlas_w = sum(max(g["advance"], g["width"], 1) for g in glyph_data)
    atlas_w = max(atlas_w, 1)

    # RGBA image, fully transparent background
    atlas = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))

    glyph_map_parts = []  # will become the GLYPHS= field in the header
    cursor_x = 0

    for g in glyph_data:
        slot_w = max(g["advance"], g["width"], 1)

        if g["rows"] > 0 and g["width"] > 0:
            # y position: align glyph so its top sits at (max_bearing - bearing_y)
            y_off = max_bearing - g["bearing_y"]
            x_off = g["bearing_x"]

            # Convert greyscale freetype bitmap → RGBA.
            # We use the grey value as the alpha channel (anti-aliasing preserved),
            # colour is white (255,255,255) so the engine can tint it any colour.
            for row in range(g["rows"]):
                for col in range(g["width"]):
                    # freetype pitch may be wider than width (alignment padding)
                    grey = g["buf"][row * g["pitch"] + col] if row * g["pitch"] + col < len(g["buf"]) else 0
                    if grey > 0:
                        px = cursor_x + x_off + col
                        py = y_off + row
                        if 0 <= px < atlas_w and 0 <= py < atlas_h:
                            atlas.putpixel((px, py), (255, 255, 255, grey))

        # Record glyph rect as: ordinal=x,y,w,h,bx,by,adv
        # x,y,w,h define the atlas sub-image.
        # bx,by are bearings.  adv is advance width (pen step).
        ordinal = ord(g["ch"])
        glyph_map_parts.append(
            f"{ordinal}={cursor_x},{0},{slot_w},{atlas_h},"
            f"{g['bearing_x']},{g['bearing_y']},{g['advance']}"
        )
        cursor_x += slot_w

    glyph_map_str = ";".join(glyph_map_parts)
    raw = atlas.tobytes()   # RGBA pixels, same layout as encode_image

    meta = {
        "atlaswidth":  atlas_w,
        "atlasheight": atlas_h,
        "glyphs":      glyph_map_str,
    }
    return raw, meta


# ══════════════════════════════════════════════════════════════════════════════
#  FILE DISCOVERY
# ══════════════════════════════════════════════════════════════════════════════

# Maps section name → tuple(accepted extensions, encoder function)
SECTION_CONFIG = {
    "bgm":     ((".wav", ".mp3", ".ogg", ".flac", ".aac", ".m4a"), encode_audio),
    "se":      ((".wav", ".mp3", ".ogg", ".flac", ".aac", ".m4a"), encode_audio),
    "fonts":   ((".ttf", ".otf"),                                   encode_font),
    "maps":    ((".txt",),                                          encode_text),
    "scripts": ((".lua",),                                          encode_text),
    "pics":    ((".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tga"),  encode_image),
    "sprites": ((".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tga"),  encode_image),
    "skybox":  ((".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tga"),  encode_image),
    "tex":     ((".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tga"),  encode_image),
}


def collect_files(data_dir: str) -> dict[str, list[str]]:
    """
    Walk each sub-folder of data_dir that matches a known section name.
    Return a dict: section_name → list of absolute file paths (sorted).
    Only files whose extension matches the section's accepted list are included.
    """
    result = {}
    for section, (exts, _) in SECTION_CONFIG.items():
        folder = os.path.join(data_dir, section)
        if not os.path.isdir(folder):
            print(f"  [skip] Section folder not found: {folder}")
            continue

        files = sorted(
            os.path.join(folder, fn)
            for fn in os.listdir(folder)
            if os.path.isfile(os.path.join(folder, fn))
            and os.path.splitext(fn)[1].lower() in exts
        )
        result[section] = files
    return result


# ══════════════════════════════════════════════════════════════════════════════
#  RPK WRITER
# ══════════════════════════════════════════════════════════════════════════════

def build_rpk(data_dir: str, output_path: str) -> None:
    """
    Main packing routine.

    Steps:
      1. Discover files in each section folder.
      2. Encode every file to bytes + metadata using the section's encoder.
      3. Compute byte offsets (all blobs are concatenated in one DATA block).
      4. Write the text header.
      5. Write a binary sentinel so we know where DATA starts.
      6. Write all binary blobs in the same order as the header.
    """
    print(f"\nRaccoon Package Builder")
    print(f"  Input  : {data_dir}")
    print(f"  Output : {output_path}\n")

    section_files = collect_files(data_dir)

    # ── Phase 1: encode everything, accumulate blobs ──────────────────────────
    # We process section by section, file by file.
    # `sections_encoded` is an ordered list of:
    #   (section_name, key, raw_bytes, metadata_dict)

    sections_encoded = []   # final ordered list of all entries

    for section in SECTION_CONFIG:   # iterate in definition order
        files = section_files.get(section, [])
        if not files:
            print(f"  [empty] {section}/")
            continue

        _, encoder = SECTION_CONFIG[section]
        print(f"  Packing {section}/")

        for fpath in files:
            key = os.path.basename(fpath)   # filename including extension
            print(f"    → {key}")

            try:
                raw, meta = encoder(fpath)
            except Exception as exc:
                print(f"      ERROR encoding {key}: {exc}")
                continue

            sections_encoded.append((section, key, raw, meta))

    # ── Phase 2: assign byte offsets ─────────────────────────────────────────
    # DATA_START is a placeholder for now; we fill it in after the header is
    # serialised.  All offsets are relative to the start of the binary blob,
    # NOT to the start of the file.  (Java does: seek(DATA_START + offset).)

    cursor = 0
    for i, (section, key, raw, meta) in enumerate(sections_encoded):
        meta["_offset"] = cursor
        meta["_size"]   = len(raw)
        cursor += len(raw)

    total_data_bytes = cursor

    # ── Phase 3: build header lines ──────────────────────────────────────────
    # We write the header to a BytesIO first so we can measure its length
    # and thus compute the real DATA_START offset.

    def make_header_lines(data_start_placeholder: int) -> list[str]:
        """Return list of text lines (no newlines) for the header."""
        lines = []
        lines.append(RPK_MAGIC)
        lines.append(f"DATA_START:{data_start_placeholder}")

        # Group entries by section for readability in Notepad
        current_section = None
        for section, key, raw, meta in sections_encoded:
            if section != current_section:
                if current_section is not None:
                    lines.append(f"[/SECTION:{current_section}]")
                lines.append(f"[SECTION:{section}]")
                current_section = section

            # Build the ENTRY line.  Core fields first, then any extra metadata.
            entry = f"ENTRY:{key} OFFSET:{meta['_offset']} SIZE:{meta['_size']}"

            # Audio-specific fields
            if "samplerate" in meta:
                entry += f" SAMPLERATE:{meta['samplerate']}"
                entry += f" CHANNELS:{meta['channels']}"
                entry += f" NUMSAMPLES:{meta['numsamples']}"

            # Image-specific fields (pics, sprites, skybox, tex)
            if "width" in meta:
                entry += f" WIDTH:{meta['width']} HEIGHT:{meta['height']}"

            # Font atlas fields
            if "atlaswidth" in meta:
                entry += f" ATLASWIDTH:{meta['atlaswidth']} ATLASHEIGHT:{meta['atlasheight']}"
                entry += f" GLYPHS:{meta['glyphs']}"

            lines.append(entry)

        if current_section is not None:
            lines.append(f"[/SECTION:{current_section}]")

        lines.append(HEADER_END)
        return lines

    # First draft: DATA_START=0 just to measure header byte length
    draft_lines  = make_header_lines(0)
    draft_text   = "\n".join(draft_lines) + "\n"
    draft_bytes  = draft_text.encode("utf-8")

    # The real DATA_START is the length of the text header in bytes.
    # Because DATA_START appears in the header itself, adding digits might
    # change its length.  We iterate until stable (usually 1–2 rounds).
    data_start = len(draft_bytes)
    for _ in range(5):
        final_lines = make_header_lines(data_start)
        final_text  = "\n".join(final_lines) + "\n"
        final_bytes = final_text.encode("utf-8")
        if len(final_bytes) == data_start:
            break
        data_start = len(final_bytes)   # adjust and retry

    # ── Phase 4: write the file ───────────────────────────────────────────────
    print(f"\n  Writing {output_path}  …")
    with open(output_path, "wb") as f:
        # 1. Write the header as UTF-8 text
        f.write(final_bytes)

        # 2. Immediately after the header write all binary blobs in order
        for section, key, raw, meta in sections_encoded:
            f.write(raw)

    total_size = os.path.getsize(output_path)
    print(f"  Header : {data_start:,} bytes")
    print(f"  Data   : {total_data_bytes:,} bytes")
    print(f"  Total  : {total_size:,} bytes")
    print(f"\n  Done!  →  {output_path}\n")


# ══════════════════════════════════════════════════════════════════════════════
#  ENTRY POINT
# ══════════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="Pack a data/ folder into a Raccoon Package (.rpk) file."
    )
    parser.add_argument(
        "data_dir",
        nargs="?",
        default="data",
        help="Path to the data folder (default: ./data)",
    )
    parser.add_argument(
        "-o", "--output",
        default="data.rpk",
        help="Output .rpk file path (default: data.rpk)",
    )
    args = parser.parse_args()

    if not os.path.isdir(args.data_dir):
        sys.exit(f"ERROR: data directory not found: {args.data_dir}")

    build_rpk(args.data_dir, args.output)


if __name__ == "__main__":
    main()
