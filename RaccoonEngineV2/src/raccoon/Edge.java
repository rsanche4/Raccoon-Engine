package raccoon;

/**
 * Edge settings on one interior grid line.
 *
 * Most lines have none: Screen.verticals holds null for them. A line only gets an Edge
 * when something is set on it, and goes back to null as soon as nothing is.
 * The map's outer lines never have one; they always use the map-wide boundary surface
 * (Screen.boundary_*).
 *
 *   solid   - blocks the player whatever the heights say (fences, invisible walls).
 *
 * Three bands, each with an enabled flag and a surface (texture / brightness / tiled /
 * skip_texture):
 *
 *   bottom  - when enabled, replaces the lower wall that the cell sticking up would
 *             normally show on this line, for this line only (a door on one face of a wall).
 *   middle  - when enabled, drawn in the opening between the two cells: fences, glass,
 *             grates. Visible from both sides; transparent texels show what is behind.
 *   top     - when enabled, replaces the upper wall on this line only.
 *
 * Brightness is the light level int, as in Cell.
 */
public class Edge {

	public boolean solid = false;

	public boolean bottom_enabled = false;
	public String bottom_texture;
	public int bottom_brightness;
	public double bottom_tiled;
	public boolean bottom_skip_texture;

	public boolean middle_enabled = false;
	public String middle_texture;
	public int middle_brightness;
	public double middle_tiled;
	public boolean middle_skip_texture;

	public boolean top_enabled = false;
	public String top_texture;
	public int top_brightness;
	public double top_tiled;
	public boolean top_skip_texture;

	/** True when nothing is set: not solid and no band enabled. Such a line is stored as null. */
	public boolean isEmpty() {
		return !solid && !bottom_enabled && !middle_enabled && !top_enabled;
	}
}
