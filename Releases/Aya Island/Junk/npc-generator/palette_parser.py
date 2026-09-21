"""Parse GIMP .gpl palette files."""

from pathlib import Path

DEFAULT_COLUMNS = 16


def parse_gpl(filepath):
    """
    Parse a GIMP .gpl palette file and extract RGB colors.

    Args:
        filepath: Path to .gpl file

    Returns:
        tuple: (colors_list, width, height)
            colors_list: list of (R, G, B) tuples, in file order
            width: grid width for display (Columns: field, or a sane default)
            height: number of rows implied by width
    """
    filepath = Path(filepath)
    if not filepath.exists():
        raise FileNotFoundError(f"Palette file not found: {filepath}")

    colors = []
    columns = 0
    saw_header = False

    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        for raw in f:
            line = raw.strip()

            if not line or line.startswith("#"):
                continue

            if line.startswith("GIMP Palette"):
                saw_header = True
                continue

            if line.startswith("Name:"):
                continue

            if line.startswith("Columns:"):
                try:
                    columns = int(line.split(":", 1)[1].strip())
                except ValueError:
                    columns = 0
                continue

            parts = line.split()
            if len(parts) >= 3:
                try:
                    r, g, b = int(parts[0]), int(parts[1]), int(parts[2])
                except ValueError:
                    continue
                if 0 <= r <= 255 and 0 <= g <= 255 and 0 <= b <= 255:
                    colors.append((r, g, b))

    if not saw_header:
        print(f"WARNING: {filepath.name} has no 'GIMP Palette' header; parsed anyway.")

    if not colors:
        raise ValueError(f"No colors parsed from {filepath}")

    # "Columns: 0" means "no preference" in the GIMP spec -- clamp to a usable value.
    if columns <= 0:
        columns = DEFAULT_COLUMNS

    height = (len(colors) + columns - 1) // columns

    return colors, columns, height
