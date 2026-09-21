"""
HTTP API for the NPC generator -- for the engine to call at world-gen time.

    python backend/app.py
"""

import sys
from io import BytesIO
from pathlib import Path

from flask import Flask, jsonify, request, send_file

sys.path.insert(0, str(Path(__file__).resolve().parent))

from generator import NPCGenerator

app = Flask(__name__)
GEN = NPCGenerator()


def _params(data):
    """Pull generation params out of a JSON body, accepting ints or style names."""
    return {
        "skin_color": data.get("skin_color", data.get("palette_index", 0)),
        "hair_style": data.get("hair_style"),
        "clothing_style": data.get("clothing_style"),
        "hair_color": data.get("hair_color"),
        "clothing_color": data.get("clothing_color"),
    }


@app.errorhandler(ValueError)
def bad_request(e):
    return jsonify({"error": str(e)}), 400


@app.route("/api/palette")
def palette():
    return jsonify({
        "colors": GEN.colors,
        "columns": GEN.palette_columns,
        "rows": GEN.palette_rows,
        "total": len(GEN.colors),
    })


@app.route("/api/styles")
def styles():
    return jsonify({
        "hair_styles": GEN.hair_names,
        "clothing_styles": GEN.clothing_names,
        "poses": GEN.poses,
        "frame_size": [GEN.frame_width, GEN.frame_height],
        "angles": 8,
    })


@app.route("/api/preview", methods=["POST"])
def preview():
    """Composite in memory and stream the PNG back. Writes nothing to disk."""
    data = request.get_json(silent=True) or {}
    image, meta = GEN.build(pose=data.get("pose"), **_params(data))

    angle = data.get("angle")
    if angle is not None:
        image = GEN.back[meta["pose"]].get_angle_frame(image, int(angle))

    buf = BytesIO()
    image.save(buf, format="PNG")
    buf.seek(0)
    return send_file(buf, mimetype="image/png")


@app.route("/api/generate", methods=["POST"])
def generate():
    """Generate one NPC and write it to assets/output/."""
    data = request.get_json(silent=True) or {}
    meta = GEN.generate(
        poses=data.get("poses"),
        split_angles=bool(data.get("split_angles")),
        **_params(data)
    )
    return jsonify(meta)


@app.route("/api/generate_batch", methods=["POST"])
def generate_batch():
    """
    Body: {"npcs": [{...}, {...}]}
    Each entry takes the same fields as /api/generate.
    """
    data = request.get_json(silent=True) or {}
    specs = data.get("npcs", [])
    if not isinstance(specs, list):
        return jsonify({"error": "'npcs' must be a list"}), 400

    results, errors = [], []
    for i, spec in enumerate(specs):
        try:
            results.append(GEN.generate(
                poses=spec.get("poses"),
                split_angles=bool(spec.get("split_angles")),
                **_params(spec)
            ))
        except (ValueError, FileNotFoundError) as e:
            errors.append({"index": i, "error": str(e)})

    return jsonify({"generated": len(results), "npcs": results, "errors": errors})


@app.route("/health")
def health():
    return jsonify({
        "status": "ok",
        "colors": len(GEN.colors),
        "hair": len(GEN.hair_names),
        "clothing": len(GEN.clothing_names),
        "poses": GEN.poses,
    })


if __name__ == "__main__":
    app.run(debug=True, port=5000)
