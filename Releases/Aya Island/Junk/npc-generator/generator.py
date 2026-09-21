"""
Core NPC generation logic.

cli.py, gui.py and app.py all import from here, so there is exactly one copy of
the asset loading, ID scheme, and compositing rules.

Template naming
---------------
Back layers (the naked skin body) define which poses exist:

    standing_backlayer.png      -> pose "standing"
    walking1_backlayer.png      -> pose "walking1"
    walking2_backlayer.png      -> pose "walking2"

Front layers are either pose-specific or generic:

    hair1_walking1_frontlayer.png   -> hair1, only for pose "walking1"
    hair1_frontlayer.png            -> hair1, used for any pose lacking a
                                       pose-specific file

The generic form is the fallback, which is useful when a part does not move
between frames (a head that barely bobs can reuse one hair layer for every
walk frame). Draw pose-specific files only where the art actually differs.
"""

import re
from pathlib import Path

from palette_parser import parse_gpl
from recolorer import NPCRecolorer, composite_layers

BASE_DIR = Path(__file__).resolve().parent
TEMPLATES_DIR = BASE_DIR / "assets" / "templates"
OUTPUT_DIR = BASE_DIR / "assets" / "output"

NO_STYLE = 99   # sentinel used in the NPC ID when a layer is omitted
NO_COLOR = 999

GENERIC = "__generic__"


def _natural_key(text):
    """Sort 'walking2' before 'walking10'."""
    return [int(p) if p.isdigit() else p for p in re.split(r"(\d+)", text)]


class NPCGenerator:
    def __init__(self, templates_dir=TEMPLATES_DIR, output_dir=OUTPUT_DIR):
        self.templates_dir = Path(templates_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

        if not self.templates_dir.is_dir():
            raise FileNotFoundError(f"Templates directory not found: {self.templates_dir}")

        self.colors, self.palette_columns, self.palette_rows = parse_gpl(
            self.templates_dir / "palette.gpl"
        )

        self.back = self._load_back_layers()
        self.poses = self._order_poses(self.back.keys())

        self.hair, self.clothing = self._load_front_layers()
        self.hair_names = sorted(self.hair.keys(), key=_natural_key)
        self.clothing_names = sorted(self.clothing.keys(), key=_natural_key)

        self._check_sizes()

        first = self.back[self.poses[0]]
        self.strip_width, self.strip_height = first.width, first.height
        self.frame_width = self.strip_width // 8
        self.frame_height = self.strip_height

    # ---------------------------------------------------------------- #
    # asset discovery
    # ---------------------------------------------------------------- #

    def _load_back_layers(self):
        """Every <pose>_backlayer.png becomes an available pose."""
        back = {}
        for path in self.templates_dir.glob("*_backlayer.png"):
            pose = path.stem[: -len("_backlayer")]
            if pose:
                back[pose] = NPCRecolorer(path)
        if not back:
            raise FileNotFoundError(
                f"No *_backlayer.png files in {self.templates_dir}. "
                "At minimum you need standing_backlayer.png."
            )
        return back

    @staticmethod
    def _order_poses(names):
        """Standing first, then everything else in natural order."""
        names = list(names)
        standing = [n for n in names if n.lower().startswith("stand")]
        rest = sorted((n for n in names if n not in standing), key=_natural_key)
        return sorted(standing, key=_natural_key) + rest

    def _load_front_layers(self):
        """
        Parse <style>[_<pose>]_frontlayer.png into
        {style_name: {pose_or_GENERIC: NPCRecolorer}}.
        """
        hair, clothing = {}, {}

        for path in sorted(self.templates_dir.glob("*_frontlayer.png")):
            stem = path.stem[: -len("_frontlayer")]
            parts = stem.split("_")

            # Trailing token that matches a known pose makes this pose-specific.
            if len(parts) > 1 and parts[-1] in self.back:
                pose = parts[-1]
                style = "_".join(parts[:-1])
            else:
                pose = GENERIC
                style = stem

            lowered = style.lower()
            if lowered.startswith("hair"):
                target = hair
            elif lowered.startswith("cloth"):
                target = clothing
            else:
                print(f"WARNING: ignoring {path.name} "
                      "(name must start with 'hair' or 'clothing')")
                continue

            target.setdefault(style, {})[pose] = NPCRecolorer(path)

        return hair, clothing

    def _check_sizes(self):
        """Every layer must match the first back layer's dimensions."""
        first_pose = self.poses[0]
        expected = (self.back[first_pose].width, self.back[first_pose].height)

        def verify(tpl, label):
            if (tpl.width, tpl.height) != expected:
                raise ValueError(
                    f"{label} is {tpl.width}x{tpl.height}, "
                    f"expected {expected[0]}x{expected[1]}"
                )

        for pose, tpl in self.back.items():
            verify(tpl, f"{pose}_backlayer.png")
        for group, kind in ((self.hair, "hair"), (self.clothing, "clothing")):
            for style, by_pose in group.items():
                for pose, tpl in by_pose.items():
                    verify(tpl, f"{kind} {style} ({pose})")

    def _pick_layer(self, by_pose, pose):
        """Pose-specific art if it exists, otherwise the generic fallback."""
        return by_pose.get(pose) or by_pose.get(GENERIC)

    def missing_pose_art(self):
        """Report styles relying on a generic fallback for a given pose."""
        gaps = []
        for kind, group in (("hair", self.hair), ("clothing", self.clothing)):
            for style, by_pose in group.items():
                for pose in self.poses:
                    if pose not in by_pose:
                        gaps.append({
                            "kind": kind, "style": style, "pose": pose,
                            "has_fallback": GENERIC in by_pose,
                        })
        return gaps

    # ---------------------------------------------------------------- #
    # validation
    # ---------------------------------------------------------------- #

    def resolve_style(self, group, names, value, label):
        """Accept a style name ('hair1'), an index ('0'), or None."""
        if value is None or (isinstance(value, str) and value.lower() == "none"):
            return None, NO_STYLE

        value = str(value)
        if value in group:
            return value, names.index(value)

        if value.isdigit():
            idx = int(value)
            if 0 <= idx < len(names):
                return names[idx], idx
            raise ValueError(f"{label} index {idx} out of range (0-{len(names) - 1})")

        raise ValueError(f"Unknown {label}: {value!r}. Available: {names or ['<none>']}")

    def check_color(self, index, label):
        if not isinstance(index, int) or isinstance(index, bool):
            raise ValueError(f"{label} must be an integer, got {index!r}")
        if not 0 <= index < len(self.colors):
            raise ValueError(f"{label} {index} out of range (0-{len(self.colors) - 1})")
        return self.colors[index]

    def check_pose(self, pose):
        if pose is None:
            return self.poses[0]
        if pose not in self.back:
            raise ValueError(f"Unknown pose {pose!r}. Available: {self.poses}")
        return pose

    @staticmethod
    def make_id(skin_c, hair_i, hair_c, cloth_i, cloth_c):
        """Numeric ID: skin, hairStyle, hairColor, clothStyle, clothColor."""
        return f"NPC_{skin_c:03d}_{hair_i:02d}_{hair_c:03d}_{cloth_i:02d}_{cloth_c:03d}"

    # ---------------------------------------------------------------- #
    # generation
    # ---------------------------------------------------------------- #

    def resolve(self, skin_color, hair_style=None, clothing_style=None,
                hair_color=None, clothing_color=None):
        """Validate inputs once and return a reusable spec dict."""
        skin_rgb = self.check_color(skin_color, "skin color")

        hair_name, hair_idx = self.resolve_style(
            self.hair, self.hair_names, hair_style, "hair style"
        )
        cloth_name, cloth_idx = self.resolve_style(
            self.clothing, self.clothing_names, clothing_style, "clothing style"
        )

        hair_color = skin_color if hair_color is None else hair_color
        clothing_color = skin_color if clothing_color is None else clothing_color

        hair_rgb = self.check_color(hair_color, "hair color") if hair_name else None
        cloth_rgb = self.check_color(clothing_color, "clothing color") if cloth_name else None

        return {
            "npc_id": self.make_id(
                skin_color, hair_idx,
                hair_color if hair_name else NO_COLOR,
                cloth_idx,
                clothing_color if cloth_name else NO_COLOR,
            ),
            "skin": {"index": skin_color, "rgb": list(skin_rgb)},
            "hair": None if not hair_name else {
                "style": hair_name, "style_index": hair_idx,
                "color_index": hair_color, "rgb": list(hair_rgb),
            },
            "clothing": None if not cloth_name else {
                "style": cloth_name, "style_index": cloth_idx,
                "color_index": clothing_color, "rgb": list(cloth_rgb),
            },
            "_skin_rgb": skin_rgb,
            "_hair": (hair_name, hair_rgb),
            "_cloth": (cloth_name, cloth_rgb),
        }

    def render_pose(self, spec, pose):
        """Composite a single pose from an already-resolved spec."""
        pose = self.check_pose(pose)

        back_img = self.back[pose].recolor(spec["_skin_rgb"])

        hair_name, hair_rgb = spec["_hair"]
        hair_img = None
        if hair_name:
            layer = self._pick_layer(self.hair[hair_name], pose)
            hair_img = layer.recolor(hair_rgb) if layer else None

        cloth_name, cloth_rgb = spec["_cloth"]
        cloth_img = None
        if cloth_name:
            layer = self._pick_layer(self.clothing[cloth_name], pose)
            cloth_img = layer.recolor(cloth_rgb) if layer else None

        return composite_layers(back_img, hair_img, cloth_img)

    def build(self, skin_color, hair_style=None, clothing_style=None,
              hair_color=None, clothing_color=None, pose=None):
        """Composite one pose in memory. Returns (PIL.Image, metadata)."""
        spec = self.resolve(skin_color, hair_style, clothing_style,
                            hair_color, clothing_color)
        pose = self.check_pose(pose)
        image = self.render_pose(spec, pose)

        meta = {k: v for k, v in spec.items() if not k.startswith("_")}
        meta.update({"pose": pose, "size": list(image.size), "angles": 8})
        return image, meta

    def generate(self, skin_color, hair_style=None, clothing_style=None,
                 hair_color=None, clothing_color=None, poses=None,
                 split_angles=False):
        """
        Build every pose and write them to assets/output/<npc_id>/.

        Adding walking1_backlayer.png is enough for a Walking1.png to appear
        in each NPC folder -- no code change required.
        """
        spec = self.resolve(skin_color, hair_style, clothing_style,
                            hair_color, clothing_color)

        poses = self.poses if poses is None else [self.check_pose(p) for p in poses]

        npc_id = spec["npc_id"]
        npc_dir = self.output_dir / npc_id
        npc_dir.mkdir(parents=True, exist_ok=True)

        files = []
        for pose in poses:
            image = self.render_pose(spec, pose)
            label = pose[0].upper() + pose[1:]
            name = f"{npc_id}_{label}.png"
            image.save(npc_dir / name)
            files.append(name)

            if split_angles:
                for i in range(8):
                    frame = self.back[pose].get_angle_frame(image, i)
                    frame_name = f"{npc_id}_{label}_a{i}.png"
                    frame.save(npc_dir / frame_name)
                    files.append(frame_name)

        meta = {k: v for k, v in spec.items() if not k.startswith("_")}
        meta.update({
            "poses": poses,
            "files": files,
            "output_path": str(npc_dir),
            "size": [self.strip_width, self.strip_height],
            "angles": 8,
        })
        return meta
