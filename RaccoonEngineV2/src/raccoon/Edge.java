package raccoon;

/**
 * An Edge is any vertical surface standing on a grid line.
 *
 * This class replaces the old Wall + Portal pair. There is only one shape of
 * data now, and a "type" field says how the renderer should treat it:
 *
 *   TYPE_WORLD_BOUNDARY - the outer shell of the map. Belongs to ONE sector
 *                         (sector_a). A ray that hits one stops dead.
 *                         This is what used to be called a "Wall".
 *
 *   TYPE_WALL           - a surface between TWO sectors (sector_a / sector_b).
 *                         A ray passes THROUGH it into the next sector, drawing
 *                         whichever of the three bands are visible.
 *                         This is what used to be called a "Portal".
 *
 * Every edge carries three texture bands, so both types share one layout:
 *
 *      top     - the strip where the ceiling steps DOWN going into sector_b
 *      middle  - the full opening (glass, a grate, a force field...)
 *      bottom  - the strip where the floor steps UP going into sector_b
 *
 * A TYPE_WORLD_BOUNDARY has no neighbour to step to, so it only ever uses the
 * MIDDLE band - that band is its single wall texture, floor to ceiling. Its
 * top/bottom bands are left skipped and are never drawn.
 *
 * Use the two factory methods below rather than a constructor; an 18-argument
 * constructor is very easy to get subtly wrong at the call site.
 */
public class Edge {

	public static final int TYPE_WORLD_BOUNDARY = 0;
	public static final int TYPE_WALL = 1;

	public int type;

	public double x1, z1, x2, z2;

	public int sector_a = -1;
	/** -1 for TYPE_WORLD_BOUNDARY: there is no sector on the other side. */
	public int sector_b = -1;

	/** Only meaningful for TYPE_WALL. Whether the player is blocked by it. */
	public boolean solid = false;

	public String bottom_texture;
	public int bottom_brightness;
	public double bottom_tiled;
	public boolean bottom_skip_texture;

	public String middle_texture;
	public int middle_brightness;
	public double middle_tiled;
	public boolean middle_skip_texture;

	public String top_texture;
	public int top_brightness;
	public double top_tiled;
	public boolean top_skip_texture;

	private Edge() { }

	/**
	 * The outer shell of the map - one sector, one texture, rays stop here.
	 * (Formerly "new Wall(...)".)
	 */
	public static Edge worldBoundary(double x1, double z1, double x2, double z2, int sector_a,
			String texture, int brightness, double tiled, boolean skip_texture) {
		Edge e = new Edge();
		e.type = TYPE_WORLD_BOUNDARY;
		e.x1 = x1; e.z1 = z1; e.x2 = x2; e.z2 = z2;
		e.sector_a = sector_a;
		e.sector_b = -1;
		e.solid = true;
		// A boundary's single texture lives in the middle band.
		e.middle_texture = texture;
		e.middle_brightness = brightness;
		e.middle_tiled = tiled;
		e.middle_skip_texture = skip_texture;
		// The other two bands exist but are never drawn for this type.
		e.top_skip_texture = true;
		e.bottom_skip_texture = true;
		return e;
	}

	/**
	 * A surface between two sectors - rays pass through it.
	 * (Formerly "new Portal(...)".)
	 */
	public static Edge wall(double x1, double z1, double x2, double z2, int sector_a, int sector_b,
			String bottom_texture, int bottom_brightness, double bottom_tiled, boolean bottom_skip_texture,
			String middle_texture, int middle_brightness, double middle_tiled, boolean middle_skip_texture,
			String top_texture, int top_brightness, double top_tiled, boolean top_skip_texture,
			boolean solid) {
		Edge e = new Edge();
		e.type = TYPE_WALL;
		e.x1 = x1; e.z1 = z1; e.x2 = x2; e.z2 = z2;
		e.sector_a = sector_a;
		e.sector_b = sector_b;
		e.solid = solid;
		e.bottom_texture = bottom_texture;
		e.bottom_brightness = bottom_brightness;
		e.bottom_tiled = bottom_tiled;
		e.bottom_skip_texture = bottom_skip_texture;
		e.middle_texture = middle_texture;
		e.middle_brightness = middle_brightness;
		e.middle_tiled = middle_tiled;
		e.middle_skip_texture = middle_skip_texture;
		e.top_texture = top_texture;
		e.top_brightness = top_brightness;
		e.top_tiled = top_tiled;
		e.top_skip_texture = top_skip_texture;
		return e;
	}

	public boolean isWorldBoundary() {
		return type == TYPE_WORLD_BOUNDARY;
	}
}
