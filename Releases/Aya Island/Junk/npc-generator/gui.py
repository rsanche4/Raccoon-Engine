#!/usr/bin/env python3
"""
NPC generator GUI. Standard library Tkinter -- no extra install, no server.

    python backend/gui.py

Pick which layer you are painting (Skin / Hair / Clothing), click a palette
swatch, and the preview updates immediately. Export writes every pose to
assets/output/<npc_id>/.
"""

import sys
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk

from PIL import Image, ImageTk

sys.path.insert(0, str(Path(__file__).resolve().parent))

from generator import NPCGenerator

SWATCH = 15          # palette swatch size in pixels
SWATCH_GAP = 1
BG = "#1e1e24"
PANEL = "#2a2a33"
FG = "#e0e0e0"
ACCENT = "#4CAF50"
CHECKER_A = "#3a3a44"
CHECKER_B = "#33333c"

NONE_LABEL = "(none)"


class NPCStudio(tk.Tk):
    def __init__(self, gen):
        super().__init__()
        self.gen = gen

        self.title("NPC Studio")
        self.configure(bg=BG)
        self.resizable(True, True)

        # --- state ---
        self.target = tk.StringVar(value="skin")
        self.skin_color = 63
        self.hair_color = 12
        self.clothing_color = 176

        self.hair_style = tk.StringVar(
            value=gen.hair_names[0] if gen.hair_names else NONE_LABEL
        )
        self.clothing_style = tk.StringVar(
            value=gen.clothing_names[0] if gen.clothing_names else NONE_LABEL
        )
        self.pose = tk.StringVar(value=gen.poses[0])
        self.angle = tk.IntVar(value=0)
        self.zoom = tk.IntVar(value=3)
        self.show_all_angles = tk.BooleanVar(value=False)
        self.split_angles = tk.BooleanVar(value=False)

        self.playing = False
        self._play_job = None
        self._play_index = 0
        self._photo = None          # must hold a reference or Tk drops the image
        self._swatch_ids = {}
        self._selection_marker = None

        self._build_ui()
        self.refresh()

    # ---------------------------------------------------------------- #
    # layout
    # ---------------------------------------------------------------- #

    def _build_ui(self):
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure("TCombobox", fieldbackground=PANEL, background=PANEL)

        root = tk.Frame(self, bg=BG, padx=10, pady=10)
        root.pack(fill="both", expand=True)

        left = tk.Frame(root, bg=PANEL, padx=10, pady=10)
        left.pack(side="left", fill="y")

        right = tk.Frame(root, bg=PANEL, padx=10, pady=10)
        right.pack(side="left", fill="both", expand=True, padx=(10, 0))

        self._build_controls(left)
        self._build_preview(right)

    def _label(self, parent, text, size=10, bold=False, color=FG):
        return tk.Label(
            parent, text=text, bg=parent["bg"], fg=color,
            font=("Segoe UI", size, "bold" if bold else "normal"),
            anchor="w",
        )

    def _build_controls(self, parent):
        self._label(parent, "PAINT TARGET", 9, True, ACCENT).pack(fill="x")

        row = tk.Frame(parent, bg=PANEL)
        row.pack(fill="x", pady=(4, 10))
        for key, text in (("skin", "Skin"), ("hair", "Hair"), ("clothing", "Clothing")):
            tk.Radiobutton(
                row, text=text, value=key, variable=self.target,
                bg=PANEL, fg=FG, selectcolor=BG, activebackground=PANEL,
                activeforeground=ACCENT, font=("Segoe UI", 9),
                command=self._move_marker, indicatoron=False,
                width=8, padx=4, pady=3, borderwidth=0,
            ).pack(side="left", padx=2)

        self.swatch_info = self._label(parent, "", 9)
        self.swatch_info.pack(fill="x", pady=(0, 4))

        cols = self.gen.palette_columns
        rows = (len(self.gen.colors) + cols - 1) // cols
        pw = cols * (SWATCH + SWATCH_GAP) + SWATCH_GAP
        ph = rows * (SWATCH + SWATCH_GAP) + SWATCH_GAP

        self.palette_canvas = tk.Canvas(
            parent, width=pw, height=ph, bg=BG, highlightthickness=0
        )
        self.palette_canvas.pack(pady=(0, 10))
        self._draw_palette(cols)

        # style selectors
        self._label(parent, "HAIR STYLE", 9, True, ACCENT).pack(fill="x")
        self._combo(parent, self.hair_style, [NONE_LABEL] + self.gen.hair_names)

        self._label(parent, "CLOTHING STYLE", 9, True, ACCENT).pack(fill="x", pady=(8, 0))
        self._combo(parent, self.clothing_style, [NONE_LABEL] + self.gen.clothing_names)

        self._label(parent, "POSE", 9, True, ACCENT).pack(fill="x", pady=(8, 0))
        self._combo(parent, self.pose, self.gen.poses)

        # export
        tk.Frame(parent, bg="#444", height=1).pack(fill="x", pady=12)

        tk.Checkbutton(
            parent, text="also export 8 separate angle files",
            variable=self.split_angles, bg=PANEL, fg=FG, selectcolor=BG,
            activebackground=PANEL, activeforeground=FG, font=("Segoe UI", 8),
            anchor="w",
        ).pack(fill="x")

        tk.Button(
            parent, text="EXPORT NPC", command=self.export,
            bg=ACCENT, fg="white", font=("Segoe UI", 11, "bold"),
            relief="flat", pady=8, cursor="hand2",
        ).pack(fill="x", pady=(8, 4))

        self.status = self._label(parent, "", 8, color="#9a9aa5")
        self.status.pack(fill="x")

    def _combo(self, parent, var, values):
        box = ttk.Combobox(parent, textvariable=var, values=values,
                           state="readonly", font=("Segoe UI", 9))
        box.pack(fill="x", pady=2)
        box.bind("<<ComboboxSelected>>", lambda e: self.refresh())
        return box

    def _build_preview(self, parent):
        top = tk.Frame(parent, bg=PANEL)
        top.pack(fill="x")

        self.id_label = self._label(top, "", 12, True)
        self.id_label.pack(side="left")

        self.preview_canvas = tk.Canvas(parent, bg=BG, highlightthickness=0)
        self.preview_canvas.pack(fill="both", expand=True, pady=8)

        bar = tk.Frame(parent, bg=PANEL)
        bar.pack(fill="x")

        tk.Checkbutton(
            bar, text="all 8 angles", variable=self.show_all_angles,
            command=self.refresh, bg=PANEL, fg=FG, selectcolor=BG,
            activebackground=PANEL, activeforeground=FG, font=("Segoe UI", 9),
        ).pack(side="left")

        self._label(bar, "  angle", 9).pack(side="left")
        tk.Scale(
            bar, from_=0, to=7, orient="horizontal", variable=self.angle,
            command=lambda e: self.refresh(), bg=PANEL, fg=FG,
            highlightthickness=0, troughcolor=BG, length=140,
            font=("Segoe UI", 8),
        ).pack(side="left")

        self._label(bar, "  zoom", 9).pack(side="left")
        tk.Scale(
            bar, from_=1, to=6, orient="horizontal", variable=self.zoom,
            command=lambda e: self.refresh(), bg=PANEL, fg=FG,
            highlightthickness=0, troughcolor=BG, length=100,
            font=("Segoe UI", 8),
        ).pack(side="left")

        self.play_button = tk.Button(
            bar, text="> PLAY", command=self.toggle_play,
            bg=PANEL, fg=FG, relief="flat", font=("Segoe UI", 9, "bold"),
            cursor="hand2", padx=10,
        )
        self.play_button.pack(side="right")
        if len(self.gen.poses) < 2:
            self.play_button.configure(state="disabled")

    # ---------------------------------------------------------------- #
    # palette
    # ---------------------------------------------------------------- #

    def _draw_palette(self, cols):
        for i, (r, g, b) in enumerate(self.gen.colors):
            cx, cy = i % cols, i // cols
            x = SWATCH_GAP + cx * (SWATCH + SWATCH_GAP)
            y = SWATCH_GAP + cy * (SWATCH + SWATCH_GAP)
            rect = self.palette_canvas.create_rectangle(
                x, y, x + SWATCH, y + SWATCH,
                fill=f"#{r:02x}{g:02x}{b:02x}", outline="",
            )
            self.palette_canvas.tag_bind(
                rect, "<Button-1>", lambda e, idx=i: self.pick_color(idx)
            )
            self.palette_canvas.tag_bind(
                rect, "<Enter>", lambda e, idx=i: self._hover(idx)
            )
            self._swatch_ids[i] = (rect, x, y)

        self._selection_marker = self.palette_canvas.create_rectangle(
            0, 0, 0, 0, outline="#ffffff", width=2
        )
        self._move_marker()

    def _hover(self, index):
        r, g, b = self.gen.colors[index]
        self.swatch_info.configure(
            text=f"#{index}  rgb({r},{g},{b})  #{r:02X}{g:02X}{b:02X}"
        )

    def current_index(self):
        return {
            "skin": self.skin_color,
            "hair": self.hair_color,
            "clothing": self.clothing_color,
        }[self.target.get()]

    def pick_color(self, index):
        setattr(self, f"{self.target.get()}_color", index)
        self._move_marker()
        self.refresh()

    def _move_marker(self):
        rect, x, y = self._swatch_ids[self.current_index()]
        self.palette_canvas.coords(
            self._selection_marker, x - 2, y - 2, x + SWATCH + 2, y + SWATCH + 2
        )
        self.palette_canvas.tag_raise(self._selection_marker)

    # ---------------------------------------------------------------- #
    # rendering
    # ---------------------------------------------------------------- #

    def _style(self, var):
        value = var.get()
        return None if value == NONE_LABEL else value

    def render(self, pose=None):
        return self.gen.build(
            skin_color=self.skin_color,
            hair_style=self._style(self.hair_style),
            clothing_style=self._style(self.clothing_style),
            hair_color=self.hair_color,
            clothing_color=self.clothing_color,
            pose=pose or self.pose.get(),
        )

    def refresh(self, pose=None):
        try:
            image, meta = self.render(pose)
        except ValueError as e:
            self.status.configure(text=f"Error: {e}")
            return

        if not self.show_all_angles.get():
            image = self.gen.back[meta["pose"]].get_angle_frame(
                image, self.angle.get()
            )

        z = self.zoom.get()
        image = image.resize(
            (image.width * z, image.height * z), Image.NEAREST
        )

        canvas = self._checkerboard(image.size)
        canvas.alpha_composite(image)

        self._photo = ImageTk.PhotoImage(canvas)
        self.preview_canvas.delete("all")
        self.preview_canvas.configure(width=canvas.width, height=canvas.height)
        self.preview_canvas.create_image(
            canvas.width // 2, canvas.height // 2, image=self._photo
        )

        self.id_label.configure(text=meta["npc_id"])
        self.status.configure(
            text=f"pose {meta['pose']}  |  skin {self.skin_color}  "
                 f"hair {self.hair_color}  clothing {self.clothing_color}"
        )

    @staticmethod
    def _checkerboard(size, tile=8):
        """Transparency backdrop so light sprites stay visible."""
        img = Image.new("RGBA", size, CHECKER_A)
        px = img.load()
        b = tuple(int(CHECKER_B[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
        for y in range(size[1]):
            for x in range(size[0]):
                if ((x // tile) + (y // tile)) % 2:
                    px[x, y] = b
        return img

    # ---------------------------------------------------------------- #
    # playback + export
    # ---------------------------------------------------------------- #

    def toggle_play(self):
        self.playing = not self.playing
        self.play_button.configure(text="|| STOP" if self.playing else "> PLAY")
        if self.playing:
            self._play_index = 0
            self._tick()
        elif self._play_job:
            self.after_cancel(self._play_job)
            self._play_job = None
            self.refresh()

    def _tick(self):
        if not self.playing:
            return
        pose = self.gen.poses[self._play_index % len(self.gen.poses)]
        self._play_index += 1
        self.refresh(pose=pose)
        self._play_job = self.after(140, self._tick)

    def export(self):
        try:
            meta = self.gen.generate(
                skin_color=self.skin_color,
                hair_style=self._style(self.hair_style),
                clothing_style=self._style(self.clothing_style),
                hair_color=self.hair_color,
                clothing_color=self.clothing_color,
                split_angles=self.split_angles.get(),
            )
        except ValueError as e:
            messagebox.showerror("Export failed", str(e))
            return

        self.status.configure(
            text=f"Exported {len(meta['files'])} file(s) to {meta['npc_id']}/"
        )
        messagebox.showinfo(
            "Exported",
            f"{meta['npc_id']}\n\n"
            f"{len(meta['files'])} file(s) across {len(meta['poses'])} pose(s)\n"
            f"{meta['output_path']}",
        )


def main():
    try:
        gen = NPCGenerator()
    except Exception as e:
        print(f"Startup failed: {e}", file=sys.stderr)
        try:
            root = tk.Tk()
            root.withdraw()
            messagebox.showerror("Startup failed", str(e))
        except tk.TclError:
            pass
        return 1

    NPCStudio(gen).mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
