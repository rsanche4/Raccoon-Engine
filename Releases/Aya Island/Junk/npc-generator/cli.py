#!/usr/bin/env python3
"""
NPC generator CLI. Runs standalone -- no server required.

    python backend/cli.py 47 hair1 clothing1
    python backend/cli.py 47 0 0 --hair-color 12 --clothing-color 90
    python backend/cli.py 47 hair1 none
    python backend/cli.py 47 hair1 clothing1 --pose walking1
    python backend/cli.py --list
    python backend/cli.py --batch 100
"""

import argparse
import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from generator import NPCGenerator


def cmd_list(gen):
    print(f"Palette: {len(gen.colors)} colors "
          f"(grid {gen.palette_columns}x{gen.palette_rows})")
    print(f"Hair styles ({len(gen.hair_names)}):     "
          f"{', '.join(f'{i}={n}' for i, n in enumerate(gen.hair_names)) or '<none>'}")
    print(f"Clothing styles ({len(gen.clothing_names)}): "
          f"{', '.join(f'{i}={n}' for i, n in enumerate(gen.clothing_names)) or '<none>'}")
    print(f"Poses ({len(gen.poses)}):           {', '.join(gen.poses)}")
    print(f"Frame: {gen.frame_width}x{gen.frame_height} x 8 angles "
          f"= {gen.strip_width}x{gen.strip_height}")
    print(f"Output: {gen.output_dir}")

    gaps = [g for g in gen.missing_pose_art() if len(gen.poses) > 1]
    if gaps:
        print("\nPose art falling back to the generic layer:")
        for g in gaps:
            tag = "generic" if g["has_fallback"] else "MISSING - layer skipped"
            print(f"  {g['kind']:<9} {g['style']:<12} {g['pose']:<12} -> {tag}")


def cmd_colors(gen):
    for i, c in enumerate(gen.colors):
        print(f"{i:4d}  rgb{c}  #{c[0]:02X}{c[1]:02X}{c[2]:02X}")


def cmd_batch(gen, count, seed, split):
    rng = random.Random(seed)
    made, skipped = 0, 0
    for _ in range(count):
        meta = gen.generate(
            skin_color=rng.randrange(len(gen.colors)),
            hair_style=rng.choice(gen.hair_names) if gen.hair_names else None,
            clothing_style=rng.choice(gen.clothing_names) if gen.clothing_names else None,
            hair_color=rng.randrange(len(gen.colors)),
            clothing_color=rng.randrange(len(gen.colors)),
            split_angles=split,
        )
        made += 1
        print(f"[{made}/{count}] {meta['npc_id']}")
    print(f"\nDone. {made} NPCs in {gen.output_dir}")


def main():
    p = argparse.ArgumentParser(
        description="Generate layered NPC sprite sheets.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    p.add_argument("skin_color", nargs="?", type=int,
                   help="palette index for the skin / back layer")
    p.add_argument("hair", nargs="?", default=None,
                   help="hair style name or index, or 'none'")
    p.add_argument("clothing", nargs="?", default=None,
                   help="clothing style name or index, or 'none'")
    p.add_argument("--hair-color", type=int, default=None,
                   help="palette index for hair (defaults to skin_color)")
    p.add_argument("--clothing-color", type=int, default=None,
                   help="palette index for clothing (defaults to skin_color)")
    p.add_argument("--pose", action="append", default=None, metavar="NAME",
                   help="only render this pose (repeatable); default is all poses")
    p.add_argument("--split-angles", action="store_true",
                   help="also write each of the 8 angles as its own 96x128 PNG")
    p.add_argument("--json", action="store_true", help="print metadata as JSON")
    p.add_argument("--list", action="store_true", help="show available styles and exit")
    p.add_argument("--colors", action="store_true", help="dump the palette and exit")
    p.add_argument("--batch", type=int, metavar="N", help="generate N random NPCs")
    p.add_argument("--seed", type=int, default=None, help="RNG seed for --batch")

    args = p.parse_args()

    try:
        gen = NPCGenerator()
    except Exception as e:
        print(f"Startup failed: {e}", file=sys.stderr)
        return 1

    if args.list:
        cmd_list(gen)
        return 0

    if args.colors:
        cmd_colors(gen)
        return 0

    if args.batch is not None:
        cmd_batch(gen, args.batch, args.seed, args.split_angles)
        return 0

    if args.skin_color is None:
        p.print_help()
        return 1

    try:
        meta = gen.generate(
            skin_color=args.skin_color,
            hair_style=args.hair,
            clothing_style=args.clothing,
            hair_color=args.hair_color,
            clothing_color=args.clothing_color,
            poses=args.pose,
            split_angles=args.split_angles,
        )
    except (ValueError, FileNotFoundError) as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(meta, indent=2))
    else:
        print(f"{meta['npc_id']}  ->  {meta['output_path']}")
        print(f"  skin     {meta['skin']['index']:>4} rgb{tuple(meta['skin']['rgb'])}")
        for layer in ("hair", "clothing"):
            info = meta[layer]
            if info:
                print(f"  {layer:<8} {info['color_index']:>4} "
                      f"rgb{tuple(info['rgb'])}  [{info['style']}]")
            else:
                print(f"  {layer:<8}    -  (omitted)")
        print(f"  poses    {', '.join(meta['poses'])}")
        print(f"  files    {len(meta['files'])} written")

    return 0


if __name__ == "__main__":
    sys.exit(main())
