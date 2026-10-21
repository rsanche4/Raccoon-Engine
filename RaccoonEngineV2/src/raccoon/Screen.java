package raccoon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Screen {

	// ================================================================ the world
	//
	// The map is a grid of map_width x map_height cells of 1 x 1. All of this is
	// replaced in one go by installWorld, on the game thread, when a map loads.

	public static int map_width;
	public static int map_height;
	public static int LIMIT_MAP_COORD = 2000;

	/** Every cell, index z * map_width + x. null until a map is loaded. */
	public static Cell[] cells = null;

	/**
	 * Edge settings per grid line, at lineSlot(...). null = nothing set on that line.
	 * Every line has a slot, outer ones included, though outer lines never get an Edge.
	 */
	public static Edge[] verticals;

	/**
	 * Per grid line: does the renderer have to stop at it? True for outer lines, lines
	 * with edge settings, and lines between two cells that don't look the same. A ray
	 * walks straight past every other line. Kept up to date by refreshLine.
	 */
	public static boolean[] line_active;

	/** The face drawn on every outer line of the map. Brightness as a light level. */
	public static String boundary_texture;
	public static int boundary_brightness;
	public static double boundary_tiled;
	public static boolean boundary_skip_texture;

	/** Ids of the sprites the current map put in, so the next map removes just those. */
	public static final ArrayList<String> map_sprite_ids = new ArrayList<>();

	/**
	 * A map RA:worldLoadMapAsync finished reading on the loading thread. update() installs
	 * it at the start of the next frame, on the game thread. See Rmap.
	 */
	public static volatile Rmap.World pending_world = null;

	public static String skybox = null;
	public static int sky_offset = 0;
	public static int skybox_brightness = 31;

	/** Sprites past this distance are skipped entirely. RA:worldSetSpriteRenderDistance. */
	public static double sprite_render_distance = 64.0;

	private double[] depth_buffer;

	public Screen() {
		this.depth_buffer = new double[Main.GAME_WID * Main.GAME_HEI];
	}

	// ============================================================ grid helpers

	/** The cell index at a world position, or -1 when there is no map or it is outside. */
	public static int cellIndexAt(double x, double z) {
		if (cells == null || x < 0 || z < 0 || x >= map_width || z >= map_height) {
			return -1;
		}
		return (int) Math.floor(z) * map_width + (int) Math.floor(x);
	}

	/**
	 * Where a grid line lives in verticals / line_active, for a map `width` cells wide.
	 *
	 *   orientation 0 = a line of constant X at x (0..W), running from z to z+1.
	 *                   The ray hits these when hit_x_aligned is true.
	 *   orientation 1 = a line of constant Z at z (0..H), running from x to x+1.
	 *
	 * (This is the old makeWallIndex's is_vertical, which reads oddly from above: it means
	 * "a constant-X line", not "a vertical surface".) No range check here; see lineIndex.
	 */
	public static int lineSlot(int width, int x, int z, int orientation) {
		return ((z * (width + 1)) + x) * 2 + orientation;
	}

	/**
	 * The slot of a grid line in the loaded map, or -1 if there is no such line. Unlike the
	 * old makeWallIndex this accepts the far outer lines (x == map_width, z == map_height).
	 */
	public static int lineIndex(int x, int z, int orientation) {
		if (cells == null || x < 0 || z < 0) return -1;
		if (orientation == 0 && (x > map_width || z >= map_height)) return -1;
		if (orientation == 1 && (x >= map_width || z > map_height)) return -1;
		if (orientation != 0 && orientation != 1) return -1;
		return lineSlot(map_width, x, z, orientation);
	}

	public static boolean isOuterLine(int width, int height, int x, int z, int orientation) {
		if (orientation == 0) return x == 0 || x == width;
		return z == 0 || z == height;
	}

	/**
	 * Whether the renderer has to stop at a line: it is an outer line, it has edge settings,
	 * or the two cells beside it don't look the same (Rule 0: otherwise nothing is drawn
	 * there). Takes the arrays as arguments so Rmap can use it on a world still being built.
	 */
	public static boolean isLineActive(Cell[] cells, Edge[] verticals, int width, int height,
			int x, int z, int orientation) {
		if (isOuterLine(width, height, x, z, orientation)) return true;
		if (verticals[lineSlot(width, x, z, orientation)] != null) return true;
		Cell a;
		Cell b;
		if (orientation == 0) {
			a = cells[z * width + (x - 1)];
			b = cells[z * width + x];
		} else {
			a = cells[(z - 1) * width + x];
			b = cells[z * width + x];
		}
		return !a.looksSameAs(b);
	}

	/** Works out line_active again for one line of the loaded map, after it or a cell beside it changed. */
	public static void refreshLine(int x, int z, int orientation) {
		line_active[lineSlot(map_width, x, z, orientation)] =
				isLineActive(cells, verticals, map_width, map_height, x, z, orientation);
	}

	/** The same for the four lines around one cell, after that cell changed. */
	public static void refreshCellLines(int x, int z) {
		refreshLine(x, z, 0);
		refreshLine(x + 1, z, 0);
		refreshLine(x, z, 1);
		refreshLine(x, z + 1, 1);
	}

	/**
	 * Swaps a freshly read map in. GAME THREAD ONLY: update() calls it for async loads and
	 * RA:worldLoadMap calls it from a script, so nothing ever sees a half-built world.
	 */
	public static void installWorld(Rmap.World w) {
		map_width = w.map_width;
		map_height = w.map_height;
		verticals = w.verticals;
		line_active = w.line_active;
		boundary_texture = w.boundary_texture;
		boundary_brightness = w.boundary_brightness;
		boundary_tiled = w.boundary_tiled;
		boundary_skip_texture = w.boundary_skip_texture;
		cells = w.cells;

		// Map sprites: the previous map's go, sprites made by scripts stay.
		for (String id : map_sprite_ids) {
			ResourceManager.sprites.remove(id);
		}
		map_sprite_ids.clear();
		for (Sprite s : w.sprites) {
			ResourceManager.sprites.put(s.ID, s);
			map_sprite_ids.add(s.ID);
		}
	}

	// ================================================================== frame

	public void update(int frame_num, int[] game_pixels) {
		// While a background load is running, draw the loading screen and touch nothing
		// else - no world render, and no user scripts either.
		if (ResourceManager.is_loading) {
			drawLoadingScreen(game_pixels);
			return;
		}
		// A map finished on the loading thread goes in now, before anything reads the world.
		Rmap.World loaded = pending_world;
		if (loaded != null) {
			pending_world = null;
			installWorld(loaded);
		}

		Arrays.fill(game_pixels, -1);
		Arrays.fill(depth_buffer, Table.MAX_DOUBLE_VAL);
		if (cells != null) {
			double player_dir = Camera.direction_rad;
			double player_x = Camera.player_x;
			double player_y = Camera.player_y;
			double player_z = Camera.player_z;
			int player_pitch = (int) Camera.pitch;
			Camera.player_cell = cellIndexAt(player_x, player_z);
			// Outside the map there is no cell to see from, so only the sky and sprites are drawn.
			if (Camera.player_cell >= 0) {
				CountDownLatch latch = new CountDownLatch(Main.GAME_WID);
				for (int x = 0; x < Main.GAME_WID; x++) {
					int ray_num = x;
					Main.executor_threads.submit(() -> {
						try {
							castRayAndRenderScreenColumn(ray_num, game_pixels, player_dir, player_x,
									player_y, player_z, player_pitch);
						} finally {
							latch.countDown();
						}
					});
				}
				try {
					latch.await();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			drawSky(player_dir, player_pitch, game_pixels);
			drawSprites(game_pixels, player_x, player_y, player_z, player_dir, player_pitch);
		}
		RaccoonAPI.runUserScripts(game_pixels);
		if (RaccoonAPI.debug_console) {
			RaccoonAPI.systemDrawConsole();
		}
	}

	/**
	 * Draws one screen column by walking a ray across the grid.
	 *
	 * The ray steps from grid line to grid line exactly as before (DDA). Most lines are
	 * walked straight past: line_active is false when the cells on both sides look the
	 * same and nothing is set on the line, so there is nothing to draw there (Rule 0).
	 *
	 * At every other line, A is the cell the ray is leaving and B the cell it enters.
	 * In this order:
	 *   - A's ceiling and A's floor, up to this line
	 *   - outer line: the boundary face from A's floor to A's ceiling, and stop  (Rule A)
	 *   - B's ceiling hangs lower: B's upper wall, or the line's top override    (Rule C)
	 *   - B's floor sticks up: B's lower wall, or the line's bottom override     (Rule B)
	 *   - the line's middle band, in the opening between the two cells          (Rule D)
	 * Each face is drawn between its own top and bottom edge, as it always was.
	 *
	 * THE CLIP WINDOW
	 * ---------------
	 * top / bottom are the rows of this column you can still see through every opening
	 * the ray has passed (top = the first such row, bottom = one past the last). Everything
	 * drawn for a cell stays inside them, so a cell only appears where you can really see
	 * it. At each line the window shrinks to the opening between A and B - the gap between
	 * the higher floor and the lower ceiling - whether or not anything was painted there
	 * (V1). That keeps a skipped (sky) ceiling black: a farther ceiling can no longer smear
	 * itself into the empty sky pixels in front of it, which the old renderer did. Once the
	 * window closes, nothing further along can be seen, so the ray stops (Rule F).
	 *
	 * One exception (V2): where A and B BOTH have skipped ceilings, the top of the window
	 * is left alone (likewise the bottom for two skipped floors). Two outdoor cells next to
	 * each other then don't cut off a tall building behind them at the outdoor "ceiling"
	 * height.
	 *
	 * Rows inside the window can already be painted (the middle band doesn't shrink it, so
	 * a fence's see-through pixels show what's behind), hence the per-pixel "already
	 * painted?" check in drawHorizontalTexture / drawVerticalTexture stays.
	 */
	private void castRayAndRenderScreenColumn(int x, int[] game_pixels, double player_dir,
			double player_x, double player_y, double player_z, int player_pitch) {
		double ray_angle = player_dir + Table.ray_offset[x];
		double tan_ray = Math.tan(ray_angle);
		double dir_theta_x = Math.signum(Math.cos(ray_angle));
		double dir_theta_z = Math.signum(Math.sin(ray_angle));
		double start_x = player_x;
		double start_z = player_z;
		double ray_angle_correct = ray_angle - player_dir;
		int horizon_line = Table.half_screen_height + player_pitch;

		int top = 0;
		int bottom = Main.GAME_HEI;

		while (true) {
			double dx_1, dz_1, dx_2, dz_2;

			if (dir_theta_x > 0) {
				dx_1 = Math.floor(start_x + 1) - start_x;
			} else {
				dx_1 = Math.ceil(start_x - 1) - start_x;
			}
			dz_1 = dir_theta_z * Math.abs(dx_1 * tan_ray);
			double dist_horizontal = Math.abs(dx_1) + Math.abs(dz_1);

			if (dir_theta_z > 0) {
				dz_2 = Math.floor(start_z + 1) - start_z;
			} else {
				dz_2 = Math.ceil(start_z - 1) - start_z;
			}
			dx_2 = dir_theta_x * Math.abs(dz_2 / tan_ray);
			double dist_vertical = Math.abs(dx_2) + Math.abs(dz_2);

			boolean hit_x_aligned;
			if (dist_horizontal < dist_vertical) {
				start_x = start_x + dx_1;
				start_z = start_z + dz_1;
				hit_x_aligned = true;
			} else {
				start_x = start_x + dx_2;
				start_z = start_z + dz_2;
				hit_x_aligned = false;
			}

			// Which line was crossed, and the cells A (leaving) and B (entering) beside it.
			int line_x, line_z, orientation, ax, az, bx, bz;
			double perc_wall_hit;
			if (hit_x_aligned) {
				orientation = 0;
				line_x = (int) Math.round(start_x);
				line_z = (int) Math.floor(start_z);
				ax = dir_theta_x > 0 ? line_x - 1 : line_x;
				bx = dir_theta_x > 0 ? line_x : line_x - 1;
				az = line_z;
				bz = line_z;
				double abs_startz = Math.abs(start_z);
				perc_wall_hit = abs_startz - Math.floor(abs_startz);
			} else {
				orientation = 1;
				line_x = (int) Math.floor(start_x);
				line_z = (int) Math.round(start_z);
				ax = line_x;
				bx = line_x;
				az = dir_theta_z > 0 ? line_z - 1 : line_z;
				bz = dir_theta_z > 0 ? line_z : line_z - 1;
				double abs_startx = Math.abs(start_x);
				perc_wall_hit = abs_startx - Math.floor(abs_startx);
			}

			int line = lineIndex(line_x, line_z, orientation);
			if (line < 0 || ax < 0 || az < 0 || ax >= map_width || az >= map_height) {
				return;   // only a ray running exactly through a map corner ends up here
			}
			if (!line_active[line]) {
				continue;
			}

			Cell a = cells[az * map_width + ax];
			double full_euclid_dist = euclidDist(player_x, player_z, start_x, start_z);
			double perp_dist = full_euclid_dist * Math.cos(ray_angle_correct);
			int a_ceil_row = screenRow(a.ceil_height, player_y, perp_dist, horizon_line);
			int a_floor_row = screenRow(a.floor_height, player_y, perp_dist, horizon_line);

			// A's ceiling and floor, from the window's edge to this line.
			if (!a.ceil_skip_texture) {
				Texture tex = ResourceManager.getImage(a.ceil_texture);
				double cl_h = a.ceil_height - player_y;
				int end = clipColumn(Math.min(bottom, a_ceil_row));
				for (int y = top; y < end; y++) {
					drawHorizontalTexture(x, y, cl_h, horizon_line - y, ray_angle_correct, full_euclid_dist,
							start_x, start_z, tex, a.ceil_brightness, a.ceil_tiled, game_pixels, player_x, player_z);
				}
			}
			if (!a.floor_skip_texture) {
				Texture tex = ResourceManager.getImage(a.floor_texture);
				double fl_h = player_y - a.floor_height;
				for (int y = clipColumn(Math.max(top, a_floor_row)); y < bottom; y++) {
					drawHorizontalTexture(x, y, fl_h, y - horizon_line, ray_angle_correct, full_euclid_dist,
							start_x, start_z, tex, a.floor_brightness, a.floor_tiled, game_pixels, player_x, player_z);
				}
			}

			// Rule A: the map's edge. One face from A's floor to A's ceiling, and the ray stops.
			if (isOuterLine(map_width, map_height, line_x, line_z, orientation)) {
				if (!boundary_skip_texture) {
					drawFace(x, game_pixels, a_ceil_row, a_floor_row, top, bottom, perc_wall_hit,
							boundary_texture, boundary_brightness, boundary_tiled, full_euclid_dist);
				}
				return;
			}

			Cell b = cells[bz * map_width + bx];
			Edge edge = verticals[line];
			int b_ceil_row = screenRow(b.ceil_height, player_y, perp_dist, horizon_line);
			int b_floor_row = screenRow(b.floor_height, player_y, perp_dist, horizon_line);

			// Rule C: B's ceiling hangs below A's. The cell that hangs down owns its face.
			if (b.ceil_height < a.ceil_height) {
				if (edge != null && edge.top_enabled) {
					if (!edge.top_skip_texture) {
						drawFace(x, game_pixels, a_ceil_row, b_ceil_row, top, bottom, perc_wall_hit,
								edge.top_texture, edge.top_brightness, edge.top_tiled, full_euclid_dist);
					}
				} else if (!b.upper_skip_texture) {
					drawFace(x, game_pixels, a_ceil_row, b_ceil_row, top, bottom, perc_wall_hit,
							b.upper_texture, b.upper_brightness, b.upper_tiled, full_euclid_dist);
				}
			}
			// Rule B: B's floor sticks up above A's. The cell that sticks up owns its side.
			if (b.floor_height > a.floor_height) {
				if (edge != null && edge.bottom_enabled) {
					if (!edge.bottom_skip_texture) {
						drawFace(x, game_pixels, b_floor_row, a_floor_row, top, bottom, perc_wall_hit,
								edge.bottom_texture, edge.bottom_brightness, edge.bottom_tiled, full_euclid_dist);
					}
				} else if (!b.lower_skip_texture) {
					drawFace(x, game_pixels, b_floor_row, a_floor_row, top, bottom, perc_wall_hit,
							b.lower_texture, b.lower_brightness, b.lower_tiled, full_euclid_dist);
				}
			}

			// Rule D: the opening, between the higher floor and the lower ceiling. Higher on
			// screen is a smaller row, so the lower ceiling is the bigger row.
			int open_top = Math.max(a_ceil_row, b_ceil_row);
			int open_bottom = Math.min(a_floor_row, b_floor_row);
			if (edge != null && edge.middle_enabled && !edge.middle_skip_texture) {
				drawFace(x, game_pixels, open_top, open_bottom, top, bottom, perc_wall_hit,
						edge.middle_texture, edge.middle_brightness, edge.middle_tiled, full_euclid_dist);
			}

			// Shrink the window to the opening (V1), except where two sky areas meet (V2).
			if (!(a.ceil_skip_texture && b.ceil_skip_texture)) {
				top = Math.max(top, open_top);
			}
			if (!(a.floor_skip_texture && b.floor_skip_texture)) {
				bottom = Math.min(bottom, open_bottom);
			}
			if (top >= bottom) {
				return;   // the opening is closed: nothing further along can be seen
			}
		}
	}

	/**
	 * Draws one wall face whose (unclipped) rows run from face_top to face_bottom, keeping
	 * to the clip window. The texture is laid over the whole face, as it always was, and
	 * looked up once for the whole span.
	 */
	private void drawFace(int x, int[] game_pixels, int face_top, int face_bottom, int top, int bottom,
			double perc_wall_hit, String texture, int brightness, double tiled, double full_euclid_dist) {
		int from = clipColumn(Math.max(top, face_top));
		int to = clipColumn(Math.min(bottom, face_bottom));
		if (from >= to) {
			return;
		}
		Texture tex = ResourceManager.getImage(texture);
		int column_pixel_size = face_bottom - face_top;
		for (int y = from; y < to; y++) {
			drawVerticalTexture(x, y, perc_wall_hit, face_top, face_bottom, tex, brightness, tiled,
					column_pixel_size, full_euclid_dist, game_pixels);
		}
	}

	public static double euclidDist(double x1, double z1, double x2, double z2) {
		return Math.sqrt((z2 - z1) * (z2 - z1) + (x2 - x1) * (x2 - x1));
	}

	private int clipColumn(int column_n) {
		return Math.max(0, Math.min(column_n, Main.GAME_HEI));
	}

	/**
	 * The screen row where a height shows up at the current wall hit. Smaller rows are
	 * higher on screen. A hit almost exactly where the player stands projects to an
	 * enormous number, so it is kept well inside int range before "horizon - it".
	 */
	private static int screenRow(double height, double player_y, double perp_dist, int horizon_line) {
		double projected = ((height - player_y) / perp_dist) * Camera.retina_dist;
		projected = Math.max(-1_000_000, Math.min(1_000_000, projected));
		return horizon_line - (int) projected;
	}

	private double reverseProject(double fl_h, int screen_y_offset, double ray_angle_correct) {
		return ((fl_h * Camera.retina_dist) / screen_y_offset) / Math.cos(ray_angle_correct);
	}

	private double figureOutTile(double full_euclid_distance, double full_euclid_minus_perp_dist,
			double wallhit_x, double player_x) {
		return wallhit_x + (full_euclid_minus_perp_dist * (player_x - wallhit_x) / full_euclid_distance);
	}

	private int getTextureTileColor(double tilehit_x, double tilehit_z, Texture tex, int brightness) {
		double local_x = tilehit_x - Math.floor(tilehit_x);
		double local_z = tilehit_z - Math.floor(tilehit_z);
		int u = (int) (local_x * tex.IMG_WID);
		int v = (int) (local_z * tex.IMG_HEI);
		int texture_color = tex.pixels[v * tex.IMG_WID + u];
		if (texture_color >= 0) {
			return Table.SHADE_TABLE[brightness][texture_color];
		}
		return -1;
	}

	private void drawHorizontalTexture(int x, int y, double height_offset, int screen_y_offset,
			double ray_angle_correct, double full_euclid_dist, double start_x, double start_z,
			Texture tex, int brightness, double tiled, int[] game_pixels,
			double player_x, double player_z) {
		int i = y * Main.GAME_WID + x;
		if (game_pixels[i] < 0) {
			double perp_dist = reverseProject(height_offset, screen_y_offset, ray_angle_correct);
			double full_euclid_minus_perp_dist = full_euclid_dist - perp_dist;
			double tile_scale = 1 + tiled;
			double tile_x = figureOutTile(full_euclid_dist, full_euclid_minus_perp_dist, start_x, player_x) / tile_scale;
			double tile_z = figureOutTile(full_euclid_dist, full_euclid_minus_perp_dist, start_z, player_z) / tile_scale;
			depth_buffer[i] = perp_dist;
			game_pixels[i] = getTextureTileColor(tile_x, tile_z, tex, brightness);
		}
	}

	private void drawVerticalTexture(int x, int y, double perc_wall_hit, int dy_walltop, int dy_wallbottom,
			Texture tex, int brightness, double tiled, int wall_column_pixel_size,
			double full_euclid_dist, int[] game_pixels) {
		int i = y * Main.GAME_WID + x;
		if (game_pixels[i] < 0) {
			int u = (int) (perc_wall_hit * tex.IMG_WID);
			double perc_vert = ((double) (y - dy_walltop) / wall_column_pixel_size) * (1 + tiled);
			int v = (int) ((perc_vert - Math.floor(perc_vert)) * tex.IMG_HEI);
			int texture_color = tex.pixels[v * tex.IMG_WID + u];
			if (texture_color >= 0) {
				depth_buffer[i] = full_euclid_dist;
				game_pixels[i] = Table.SHADE_TABLE[brightness][texture_color];
			}
		}
	}

	/**
	 * Fills whatever the world did not cover.
	 *
	 * Horizontally the sky scrolls with yaw, as before. Vertically it now slides
	 * with pitch, so looking up shows the top of the image and looking down
	 * shows the bottom - a parallax rather than a fixed band.
	 *
	 * The vertical travel comes from whatever height the image has SPARE above
	 * GAME_HEI. Table.SKYBOX_HEI is the size that gives the full range. A skybox
	 * that is exactly GAME_HEI tall has no spare rows, so it simply stays
	 * locked - which is what old skyboxes did, so they keep working untouched.
	 */
	private void drawSky(double dir, int pitch, int[] game_pixels) {
		if (skybox == null) {
			for (int y = 0; y < Main.GAME_HEI; y++) {
				for (int x = 0; x < Main.GAME_WID; x++) {
					int i = y * Main.GAME_WID + x;
					if (game_pixels[i] < 0) {
						game_pixels[i] = 0;
						depth_buffer[i] = Table.MAX_DOUBLE_VAL;
					}
				}
			}
			return;
		}

		Texture sky = ResourceManager.getImage(skybox);
		int sky_w = sky.IMG_WID;
		int sky_h = sky.IMG_HEI;

		int offset_x = ((int) ((dir / Table.pi2) * sky_w) + sky_offset) % sky_w;

		int spare_rows = sky_h - Main.GAME_HEI;
		int offset_y = 0;
		if (spare_rows > 0) {
			// pitch +MAX_PITCH (looking up)   -> t = 0 -> top of the image
			// pitch -MAX_PITCH (looking down) -> t = 1 -> bottom of the image
			double t = (Table.MAX_PITCH - pitch) / (2.0 * Table.MAX_PITCH);
			if (t < 0) t = 0;
			if (t > 1) t = 1;
			offset_y = (int) (t * spare_rows);
		}

		for (int y = 0; y < Main.GAME_HEI; y++) {
			int sky_row = (y + offset_y) * sky_w;
			for (int x = 0; x < Main.GAME_WID; x++) {
				int i = y * Main.GAME_WID + x;
				if (game_pixels[i] < 0) {
					int sky_x = (x - offset_x + sky_w) % sky_w;
					game_pixels[i] = Table.SHADE_TABLE[skybox_brightness][sky.pixels[sky_row + sky_x]];
					depth_buffer[i] = Table.MAX_DOUBLE_VAL;
				}
			}
		}
	}

	private void drawSprites(int[] game_pixels, double player_x, double player_y, double player_z,
			double player_dir, int player_pitch) {
		// Sprites hang off the same horizon line as the walls, so they stay put against the
		// world when looking up or down. (They used to ignore pitch and drift.)
		int horizon_line = Table.half_screen_height + player_pitch;
		for (Map.Entry<String, Sprite> entry : ResourceManager.sprites.entrySet()) {
			Sprite entity = entry.getValue();
			double vectorx = entity.sprite_x_pos - player_x;
			double vectory = entity.sprite_y_pos - player_y;
			double vectorz = entity.sprite_z_pos - player_z;
			double cam_x = (double) (vectorx * Math.cos(-player_dir) - vectorz * Math.sin(-player_dir));

			// CULL 1: behind the camera.
			if (cam_x <= 0) {
				continue;
			}
			// CULL 2: too far away to care about.
			if (cam_x > sprite_render_distance) {
				continue;
			}

			double cam_z = (double) (vectorx * Math.sin(-player_dir) + vectorz * Math.cos(-player_dir));
			double screen_offset_x = (cam_z / cam_x) * Camera.retina_dist;
			double screen_offset_y = (vectory / cam_x) * Camera.retina_dist;
			int screen_sprite_x = Table.half_screen_width - (int) (screen_offset_x);
			int screen_sprite_y = horizon_line - (int) (screen_offset_y);
			int screen_sprite_length = (int) (entity.sprite_length / cam_x);
			int half = screen_sprite_length / 2;
			int start_x_og = screen_sprite_x - half;
			int end_x_og = screen_sprite_x + half;
			int start_y_og = screen_sprite_y - half;
			int end_y_og = screen_sprite_y + half;

			// CULL 3: footprint lands entirely off screen. Also throws out
			// zero-size sprites, which would divide by zero below.
			if (end_x_og <= start_x_og || end_y_og <= start_y_og) {
				continue;
			}
			if (end_x_og < 0 || start_x_og >= Main.GAME_WID) {
				continue;
			}
			if (end_y_og < 0 || start_y_og >= Main.GAME_HEI) {
				continue;
			}

			int startx = Math.max(0, start_x_og);
			int starty = Math.max(0, start_y_og);
			int endx = Math.min(Main.GAME_WID - 1, end_x_og);
			int endy = Math.min(Main.GAME_HEI - 1, end_y_og);

			// Looked up ONCE per sprite instead of once per pixel.
			Texture tex = ResourceManager.getImage(entity.spritename);
			int sprite_size = tex.IMG_HEI;
			double relative_angle = Math.atan2(player_z - entity.sprite_z_pos,
					player_x - entity.sprite_x_pos) - entity.direction_rad;
			while (relative_angle < 0) relative_angle += Table.pi2;
			while (relative_angle >= Table.pi2) relative_angle -= Table.pi2;
			int directional_frame = (int) (relative_angle / Table.DIRECTIONAL_SLICE_ANGLE);
			int frame_u_offset = directional_frame * sprite_size;

			for (int y = starty; y <= endy; y++) {
				for (int x = startx; x <= endx; x++) {
					int i = y * Main.GAME_WID + x;
					if (depth_buffer[i] > cam_x) {
						double spritex = (double) (x - start_x_og) / (end_x_og - start_x_og);
						double spritey = (double) (y - start_y_og) / (end_y_og - start_y_og);
						int u = frame_u_offset + (int) ((spritex - Math.floor(spritex)) * sprite_size);
						int v = (int) ((spritey - Math.floor(spritey)) * sprite_size);
						int color = tex.pixels[v * tex.IMG_WID + u];
						if (color >= 0) {
							depth_buffer[i] = cam_x;
							game_pixels[i] = Table.SHADE_TABLE[entity.sprite_brightness][color];
						}
					}
				}
			}
		}
	}

	/**
	 * Shown while something is loading on a background thread. Deliberately
	 * plain: a message, and a bar when the job knows how far along it is.
	 */
	private void drawLoadingScreen(int[] game_pixels) {
		Arrays.fill(game_pixels, 0);

		String msg = ResourceManager.loading_message;
		if (msg == null) msg = "LOADING";
		int text_y = Table.half_screen_height - 16;
		RaccoonAPI.drawString(msg, 20, text_y, RaccoonAPI.systemFont(),
				Table.NUM_LIGHT_LEVELS - 1, game_pixels);

		double progress = ResourceManager.loading_progress;
		int bar_x = 20;
		int bar_w = Main.GAME_WID - 40;
		int bar_y = Table.half_screen_height + 8;
		int bar_h = 10;
		int bright = Table.NUM_LIGHT_LEVELS - 1;
		int white = Table.SHADE_TABLE[bright][Table.findClosestColorIndex(255, 255, 255)];

		// Outline.
		for (int x = bar_x; x < bar_x + bar_w; x++) {
			game_pixels[bar_y * Main.GAME_WID + x] = white;
			game_pixels[(bar_y + bar_h) * Main.GAME_WID + x] = white;
		}
		for (int y = bar_y; y <= bar_y + bar_h; y++) {
			game_pixels[y * Main.GAME_WID + bar_x] = white;
			game_pixels[y * Main.GAME_WID + bar_x + bar_w - 1] = white;
		}
		// Fill. A negative progress means "length unknown", so sweep a block
		// back and forth instead of pretending to know a percentage.
		int fill_start = bar_x + 1;
		int fill_end;
		if (progress >= 0) {
			fill_end = bar_x + 1 + (int) ((bar_w - 2) * Math.min(1.0, progress));
		} else {
			int sweep_w = (bar_w - 2) / 5;
			int travel = (bar_w - 2) - sweep_w;
			int pos = (int) ((System.currentTimeMillis() / 8) % (travel * 2L));
			if (pos > travel) pos = travel * 2 - pos;
			fill_start = bar_x + 1 + pos;
			fill_end = fill_start + sweep_w;
		}
		for (int y = bar_y + 1; y < bar_y + bar_h; y++) {
			for (int x = fill_start; x < fill_end; x++) {
				game_pixels[y * Main.GAME_WID + x] = white;
			}
		}
	}
}
