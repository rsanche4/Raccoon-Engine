package raccoon;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Screen {

	public static int MAX_NUM_SECTORS = 1024;
	public static int vertical_length;
	public static Sector[] sectors = null;
	/** Every grid edge in the map. Nulls are open space. */
	public static Edge[] verticals;
	public static int sectors_count = 0;
	public static int map_width;
	public static int map_height;
	public static int LIMIT_MAP_COORD = 512;
	public static String skybox = null;
	public static int sky_offset = 0;
	public static int skybox_brightness = 31;
	public static boolean[] portal_collision_data;

	/** Sprites past this distance are skipped entirely. RA:worldSetSpriteRenderDistance. */
	public static double sprite_render_distance = 64.0;

	private double[] depth_buffer;

	public Screen() {
		this.depth_buffer = new double[Main.GAME_WID * Main.GAME_HEI];
	}

	public static int updatePlayerSector(double player_x, double player_z) {
		for (int i = 0; i < sectors_count; i++) {
			Sector sector = sectors[i];
			if (player_z >= sector.boundary_coords[0] && player_z <= sector.boundary_coords[1]
					&& player_x >= sector.boundary_coords[2] && player_x <= sector.boundary_coords[3]) {
				return sector.ID;
			}
		}
		return -1;
	}

	public void update(int frame_num, int[] game_pixels) {
		// While a background load is running the world data is being rebuilt
		// underneath us, so draw the loading screen and touch nothing else -
		// no world render, and no user scripts either.
		if (ResourceManager.is_loading) {
			drawLoadingScreen(game_pixels);
			return;
		}

		Arrays.fill(game_pixels, -1);
		Arrays.fill(depth_buffer, Table.MAX_DOUBLE_VAL);
		if (sectors != null) {
			double player_dir = Camera.direction_rad;
			double player_x = Camera.player_x;
			double player_y = Camera.player_y;
			double player_z = Camera.player_z;
			int player_pitch = (int) Camera.pitch;
			Camera.player_sector = updatePlayerSector(Camera.player_x, Camera.player_z);
			CountDownLatch latch = new CountDownLatch(Main.GAME_WID);
			for (int x = 0; x < Main.GAME_WID; x++) {
				int ray_num = x;
				Main.executor_threads.submit(() -> {
					try {
						castRayAndRenderScreenColumn(ray_num, game_pixels, player_dir, player_x,
								player_y, player_z, Camera.player_sector, player_pitch);
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
			drawSky(player_dir, player_pitch, game_pixels);
			drawSprites(game_pixels, player_x, player_y, player_z, player_dir);
		}
		RaccoonAPI.runUserScripts(game_pixels);
		if (RaccoonAPI.debug_console) {
			RaccoonAPI.systemDrawConsole();
		}
	}

	private void castRayAndRenderScreenColumn(int x, int[] game_pixels, double player_dir,
			double player_x, double player_y, double player_z, int player_sector, int player_pitch) {
		double ray_angle = player_dir + Table.ray_offset[x];
		double tan_ray = Math.tan(ray_angle);
		double dir_theta_x = Math.signum(Math.cos(ray_angle));
		double dir_theta_z = Math.signum(Math.sin(ray_angle));
		double start_x = player_x;
		double start_z = player_z;

		int ray_sector = player_sector;
		int dy_wall_bottom_bottom;
		int dy_wall_bottom_top;
		int dy_wall_top_bottom;
		int dy_wall_top_top;

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

			int wall_index;
			double perc_wall_hit;
			if (hit_x_aligned) {
				double abs_startz = Math.abs(start_z);
				perc_wall_hit = abs_startz - Math.floor(abs_startz);
				wall_index = makeWallIndex((int) Math.round(start_x), (int) Math.floor(start_z), 0);
			} else {
				double abs_startx = Math.abs(start_x);
				perc_wall_hit = abs_startx - Math.floor(abs_startx);
				wall_index = makeWallIndex((int) Math.floor(start_x), (int) Math.round(start_z), 1);
			}

			if (wall_index < 0) {
				return;
			}

			Edge edge = verticals[wall_index];
			if (edge == null) {
				continue;
			}

			double full_euclid_dist = euclidDist(player_x, player_z, start_x, start_z);
			double ray_angle_correct = ray_angle - player_dir;
			int horizon_line = Table.half_screen_height + player_pitch;

			// ------------------------------------------------ world boundary
			// One sector, one texture (the middle band), ray stops here.
			if (edge.type == Edge.TYPE_WORLD_BOUNDARY) {
				Sector sector_info = sectors[edge.sector_a];
				int dy_walltop = horizon_line - projectColumn(start_x, sector_info.ceil_height, start_z,
						ray_angle_correct, player_x, player_y, player_z);
				int dy_wallbottom = horizon_line - projectColumn(start_x, sector_info.floor_height, start_z,
						ray_angle_correct, player_x, player_y, player_z);
				int dy_walltop_clipped = clipColumn(dy_walltop);
				int dy_wallbottom_clipped = clipColumn(dy_wallbottom);
				double cl_h = sector_info.ceil_height - player_y;
				double fl_h = player_y - sector_info.floor_height;

				if (!sector_info.ceil_skip_texture) {
					// Texture looked up ONCE for the whole span, not once per pixel.
					Texture tex = ResourceManager.getImage(sector_info.ceil_texture);
					for (int y = 0; y < dy_walltop_clipped; y++) {
						drawHorizontalTexture(x, y, cl_h, horizon_line - y, ray_angle_correct,
								full_euclid_dist, start_x, start_z, tex, sector_info.ceil_brightness,
								sector_info.ceil_tiled, game_pixels, player_x, player_z);
					}
				}
				if (!edge.middle_skip_texture) {
					Texture tex = ResourceManager.getImage(edge.middle_texture);
					int column_pixel_size = dy_wallbottom - dy_walltop;
					for (int y = dy_walltop_clipped; y < dy_wallbottom_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_walltop, dy_wallbottom, tex,
								edge.middle_brightness, edge.middle_tiled, column_pixel_size,
								full_euclid_dist, game_pixels);
					}
				}
				if (!sector_info.floor_skip_texture) {
					Texture tex = ResourceManager.getImage(sector_info.floor_texture);
					for (int y = dy_wallbottom_clipped; y < Main.GAME_HEI; y++) {
						drawHorizontalTexture(x, y, fl_h, y - horizon_line, ray_angle_correct,
								full_euclid_dist, start_x, start_z, tex, sector_info.floor_brightness,
								sector_info.floor_tiled, game_pixels, player_x, player_z);
					}
				}
				return;
			}

			// --------------------------------------------------------- wall
			// Between two sectors. The ray carries on into the next one.
			int prev_ray_sector = ray_sector;
			if (ray_sector == edge.sector_a) {
				ray_sector = edge.sector_b;
			} else {
				ray_sector = edge.sector_a;
			}
			Sector cur_sector = sectors[prev_ray_sector];
			Sector next_sector = sectors[ray_sector];

			if (cur_sector.floor_height < next_sector.floor_height) {
				dy_wall_bottom_bottom = horizon_line - projectColumn(start_x, cur_sector.floor_height,
						start_z, ray_angle_correct, player_x, player_y, player_z);
				dy_wall_bottom_top = horizon_line - projectColumn(start_x, next_sector.floor_height,
						start_z, ray_angle_correct, player_x, player_y, player_z);
			} else {
				dy_wall_bottom_bottom = horizon_line - projectColumn(start_x, cur_sector.floor_height,
						start_z, ray_angle_correct, player_x, player_y, player_z);
				dy_wall_bottom_top = dy_wall_bottom_bottom;
			}
			if (cur_sector.ceil_height > next_sector.ceil_height) {
				dy_wall_top_bottom = horizon_line - projectColumn(start_x, next_sector.ceil_height,
						start_z, ray_angle_correct, player_x, player_y, player_z);
				dy_wall_top_top = horizon_line - projectColumn(start_x, cur_sector.ceil_height,
						start_z, ray_angle_correct, player_x, player_y, player_z);
			} else {
				dy_wall_top_bottom = horizon_line - projectColumn(start_x, cur_sector.ceil_height,
						start_z, ray_angle_correct, player_x, player_y, player_z);
				dy_wall_top_top = dy_wall_top_bottom;
			}

			int dy_wall_top_top_clipped = clipColumn(dy_wall_top_top);
			int dy_wall_top_bottom_clipped = clipColumn(dy_wall_top_bottom);
			int dy_wall_bottom_top_clipped = clipColumn(dy_wall_bottom_top);
			int dy_wall_bottom_bottom_clipped = clipColumn(dy_wall_bottom_bottom);
			double cl_h = cur_sector.ceil_height - player_y;
			double fl_h = player_y - cur_sector.floor_height;

			if (!cur_sector.ceil_skip_texture) {
				Texture tex = ResourceManager.getImage(cur_sector.ceil_texture);
				for (int y = 0; y < dy_wall_top_top_clipped; y++) {
					drawHorizontalTexture(x, y, cl_h, horizon_line - y, ray_angle_correct,
							full_euclid_dist, start_x, start_z, tex, cur_sector.ceil_brightness,
							cur_sector.ceil_tiled, game_pixels, player_x, player_z);
				}
			}
			if (!edge.top_skip_texture) {
				Texture tex = ResourceManager.getImage(edge.top_texture);
				int column_pixel_size = dy_wall_top_bottom - dy_wall_top_top;
				for (int y = dy_wall_top_top_clipped; y < dy_wall_top_bottom_clipped; y++) {
					drawVerticalTexture(x, y, perc_wall_hit, dy_wall_top_top, dy_wall_top_bottom, tex,
							edge.top_brightness, edge.top_tiled, column_pixel_size,
							full_euclid_dist, game_pixels);
				}
			}
			if (!edge.middle_skip_texture) {
				Texture tex = ResourceManager.getImage(edge.middle_texture);
				int column_pixel_size = dy_wall_bottom_top - dy_wall_top_bottom;
				for (int y = dy_wall_top_bottom_clipped; y < dy_wall_bottom_top_clipped; y++) {
					drawVerticalTexture(x, y, perc_wall_hit, dy_wall_top_bottom, dy_wall_bottom_top, tex,
							edge.middle_brightness, edge.middle_tiled, column_pixel_size,
							full_euclid_dist, game_pixels);
				}
			}
			if (!edge.bottom_skip_texture) {
				Texture tex = ResourceManager.getImage(edge.bottom_texture);
				int column_pixel_size = dy_wall_bottom_bottom - dy_wall_bottom_top;
				for (int y = dy_wall_bottom_top_clipped; y < dy_wall_bottom_bottom_clipped; y++) {
					drawVerticalTexture(x, y, perc_wall_hit, dy_wall_bottom_top, dy_wall_bottom_bottom, tex,
							edge.bottom_brightness, edge.bottom_tiled, column_pixel_size,
							full_euclid_dist, game_pixels);
				}
			}
			if (!cur_sector.floor_skip_texture) {
				Texture tex = ResourceManager.getImage(cur_sector.floor_texture);
				for (int y = dy_wall_bottom_bottom_clipped; y < Main.GAME_HEI; y++) {
					drawHorizontalTexture(x, y, fl_h, y - horizon_line, ray_angle_correct,
							full_euclid_dist, start_x, start_z, tex, cur_sector.floor_brightness,
							cur_sector.floor_tiled, game_pixels, player_x, player_z);
				}
			}

			// EARLY-OUT.
			// Anything visible through this wall is bounded by the gap between
			// the top band and the bottom band. If that gap has closed - either
			// because the floor/ceiling met, or because the whole opening is off
			// the top or bottom of the screen - nothing further along this ray
			// can ever be seen, so stop marching. Without this the ray keeps
			// walking into sector after sector re-running the full draw loop for
			// pixels that are already covered.
			if (dy_wall_bottom_top_clipped <= dy_wall_top_bottom_clipped) {
				return;
			}
		}
	}

	public static int makeWallIndex(int x, int z, int is_vertical) {
		if (x >= map_width || z >= map_height || x < 0 || z < 0) {
			return -1;
		}
		return (z * map_width + x) * 2 + is_vertical;
	}

	public static double euclidDist(double x1, double z1, double x2, double z2) {
		return Math.sqrt((z2 - z1) * (z2 - z1) + (x2 - x1) * (x2 - x1));
	}

	private int clipColumn(int column_n) {
		return Math.max(0, Math.min(column_n, Main.GAME_HEI));
	}

	private int projectColumn(double wallhit_x, double wallhit_y, double wallhit_z,
			double ray_angle_correct, double player_x, double player_y, double player_z) {
		return (int) (((wallhit_y - player_y)
				/ (euclidDist(player_x, player_z, wallhit_x, wallhit_z) * Math.cos(ray_angle_correct)))
				* Camera.retina_dist);
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
			double player_dir) {
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
			int screen_sprite_y = Table.half_screen_height - (int) (screen_offset_y);
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
		int white = Table.SHADE_TABLE[bright][15];

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
