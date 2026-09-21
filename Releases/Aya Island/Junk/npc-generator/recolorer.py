"""Layer recoloring and compositing for NPC sprite sheets."""

import numpy as np
from PIL import Image

FRAME_WIDTH = 96
FRAME_HEIGHT = 128
ANGLE_COUNT = 8


class NPCRecolorer:
    """Loads a sprite-strip template once and produces recolored copies."""

    def __init__(self, template_path):
        self.path = str(template_path)
        self.template = Image.open(template_path).convert("RGBA")
        self.width, self.height = self.template.size

        # Cache the pixel array and base color; both are immutable per template.
        self._rgba = np.array(self.template, dtype=np.uint8)
        self._opaque = self._rgba[:, :, 3] > 0
        self.base_color = self._compute_base_color()

        # Precompute per-pixel brightness ratio relative to the base color so
        # shaded pixels stay proportionally darker after recoloring.
        base_v = max(self.base_color)
        if base_v == 0:
            self._shade = np.ones(self._rgba.shape[:2], dtype=np.float32)
        else:
            pixel_v = self._rgba[:, :, :3].max(axis=2).astype(np.float32)
            self._shade = pixel_v / float(base_v)

    def _compute_base_color(self):
        """Most common fully-visible RGB value in the template."""
        visible = self._rgba[self._rgba[:, :, 3] > 128]
        if visible.size == 0:
            return (255, 255, 255)
        rgb = visible[:, :3].astype(np.int64)
        packed = (rgb[:, 0] << 16) | (rgb[:, 1] << 8) | rgb[:, 2]
        values, counts = np.unique(packed, return_counts=True)
        winner = int(values[counts.argmax()])
        return ((winner >> 16) & 255, (winner >> 8) & 255, winner & 255)

    def recolor(self, target_color):
        """
        Replace the template's base color with target_color.

        Every opaque pixel is mapped to target_color, scaled by how bright that
        pixel was relative to the base color. For a flat silhouette this is an
        exact fill; for shaded art the shading is preserved proportionally.
        Alpha is untouched.

        Args:
            target_color: (R, G, B) tuple, 0-255

        Returns:
            A new PIL RGBA Image.
        """
        target = np.array(target_color, dtype=np.float32).reshape(1, 1, 3)
        shaded = np.clip(self._shade[:, :, None] * target, 0, 255).astype(np.uint8)

        out = self._rgba.copy()
        mask = self._opaque
        out[:, :, :3][mask] = shaded[mask]

        return Image.fromarray(out, mode="RGBA")

    def get_angle_frame(self, image, angle_index):
        """
        Crop one 96x128 angle out of a horizontal 8-frame strip.

        Args:
            image: PIL Image to crop (usually a recolored strip)
            angle_index: 0-7

        Returns:
            PIL Image, FRAME_WIDTH x FRAME_HEIGHT
        """
        if not 0 <= angle_index < ANGLE_COUNT:
            raise ValueError(f"angle_index must be 0-{ANGLE_COUNT - 1}")
        x = angle_index * FRAME_WIDTH
        return image.crop((x, 0, x + FRAME_WIDTH, FRAME_HEIGHT))


def composite_layers(back_layer, hair_layer=None, clothing_layer=None):
    """
    Stack layers bottom-to-top: skin -> hair -> clothing.

    Uses true alpha compositing, so semi-transparent or antialiased edges blend
    correctly instead of punching holes in the layer beneath.
    """
    result = back_layer.convert("RGBA")

    for layer in (hair_layer, clothing_layer):
        if layer is None:
            continue
        if layer.size != result.size:
            raise ValueError(
                f"Layer size {layer.size} does not match base {result.size}"
            )
        result = Image.alpha_composite(result, layer.convert("RGBA"))

    return result
