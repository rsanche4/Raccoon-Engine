package raccoon;

/**
 * One 1x1 cell of the map grid. This replaces the old Sector.
 *
 * Every cell carries its own full settings. There are no tile types: two cells that look
 * the same are simply two cells with the same values.
 *
 *   floor_height, ceil_height - 0 <= floor <= ceiling <= 2000. floor == ceiling is how
 *                               solid wall cells are usually made.
 *
 * Four surfaces, each with texture / brightness / tiled / skip_texture:
 *
 *   floor   - what you walk on
 *   ceil    - overhead. skip_texture shows the sky (an outdoor cell)
 *   lower   - this cell's side where its floor sticks UP above a neighbour's floor.
 *             Seen from the lower neighbour: "the cell that sticks up owns its side".
 *   upper   - this cell's face where its ceiling hangs DOWN below a neighbour's ceiling.
 *
 * Brightness is stored as the light level int (0 .. NUM_LIGHT_LEVELS-1), as everywhere
 * else in the engine. The map file stores 0.0 - 1.0; the loader converts.
 *
 * WORLD SHAPE NEVER CHANGES at runtime: scripts may change a cell's surfaces, but
 * never its heights.
 */
public class Cell {

	double floor_height;
	double ceil_height;

	String floor_texture;
	int floor_brightness;
	double floor_tiled;
	boolean floor_skip_texture;

	String ceil_texture;
	int ceil_brightness;
	double ceil_tiled;
	boolean ceil_skip_texture;

	String lower_texture;
	int lower_brightness;
	double lower_tiled;
	boolean lower_skip_texture;

	String upper_texture;
	int upper_brightness;
	double upper_tiled;
	boolean upper_skip_texture;

	/**
	 * True when every setting matches: both heights and all four surfaces.
	 * The renderer skips a grid line entirely when the cells on both sides look the same
	 * and the line has no edge settings (Screen.refreshLine).
	 */
	public boolean looksSameAs(Cell other) {
		return floor_height == other.floor_height && ceil_height == other.ceil_height
				&& floor_texture.equals(other.floor_texture) && floor_brightness == other.floor_brightness
				&& floor_tiled == other.floor_tiled && floor_skip_texture == other.floor_skip_texture
				&& ceil_texture.equals(other.ceil_texture) && ceil_brightness == other.ceil_brightness
				&& ceil_tiled == other.ceil_tiled && ceil_skip_texture == other.ceil_skip_texture
				&& lower_texture.equals(other.lower_texture) && lower_brightness == other.lower_brightness
				&& lower_tiled == other.lower_tiled && lower_skip_texture == other.lower_skip_texture
				&& upper_texture.equals(other.upper_texture) && upper_brightness == other.upper_brightness
				&& upper_tiled == other.upper_tiled && upper_skip_texture == other.upper_skip_texture;
	}
}
