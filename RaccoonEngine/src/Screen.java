import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Screen {

	private static int[] pixels;
	public static int[] gamepixels;
	public static String skybox = "default_sky.png";
	public static float skybox_brightness = 1.0f;
	public static Map<Integer, Sector> sectorMap;
	public static Map<String, Wall> wallMap;
	public static Map<String, Portal> portalMap;
	public static Map<Integer, Boolean> portalCollisionData;
	public static int half_screen_height = Main.game_height/2;
	private int half_screen_width = Main.game_width/2;
	private float[] depth_buffer;
	public static int fog_r = 0x10;
	public static int fog_g = 0x10;
	public static int fog_b = 0x10;
	public static float fog_start = 5.0f;
	public static float fog_end = 25.0f;
	private int skybox_refresh_val = 16777216;
	public static Sound current_bgm;
	public static Sound current_sfe;
	public static int sky_offset = 0;
	float camera_mid_side_a = Camera.retina_dist;
	float camera_mid_side_b = Main.game_width / 2.0f;
	float total_fov = (float) (2 * Math.atan(camera_mid_side_b / camera_mid_side_a));
	float deltatheta = total_fov / Main.game_width;
	public static int max_count = 100;
	public static boolean plane_texture = true;
	public static boolean sky_texture_bool = true;
	public static boolean wall_texture_bool = true;
	private final int[] ZEROS; // allocate once at init
	private final float[] MAX_DEPTHS;
	private String transparentTex = "black.png";

	public Screen(int[] pixels_arg, int[] gamepixels_arg) {
		pixels = pixels_arg;
		gamepixels = gamepixels_arg;
		this.depth_buffer = new float[gamepixels.length];
		ZEROS = new int[gamepixels.length];
		MAX_DEPTHS = new float[depth_buffer.length];
		Arrays.fill(ZEROS, 0x000000); // redundant but I'm paranoid
		Arrays.fill(MAX_DEPTHS, fog_end);
	}
	
	public static int update_player_sector(float player_x, float player_z) {
		// kinda terrible, can optimize but im too lazy
		for (int i=1; i<=Screen.sectorMap.size(); i++) {
	        Sector sector = Screen.sectorMap.get(i);
			if (player_x >= sector.boundary_coords[0] && player_x <= sector.boundary_coords[1] && player_z >= sector.boundary_coords[2] && player_z <= sector.boundary_coords[3]) {
	            return sector.sectorId;
	        }
	    }
		return -1;
	}

	public void update(long frame_num) {
	    System.arraycopy(ZEROS, 0, gamepixels, 0, gamepixels.length); // About 20% faster than doing Arrays.fill
	    System.arraycopy(MAX_DEPTHS, 0, depth_buffer, 0, depth_buffer.length);
	    if (sectorMap!=null) {
	        Camera.player_sector = update_player_sector(Camera.player_x, Camera.player_z);
	        CountDownLatch latch = new CountDownLatch(Main.game_width);	        
	        for (int x = 0; x < Main.game_width; x++) {
	            final int rayNum = x;
	            Main.executorThreads.submit(() -> {
	                try {
	                    cast_ray_and_render_screen_column(rayNum);
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
	        draw_sky(Camera.direction_rad, Main.allTextures.get(skybox).pixels);
	        draw_sprites();
	    }
	    ReApi.run_user_scripts();
	    up_res();
	}
	
	private void draw_sprites() {        
		for (Map.Entry<String, Sprite> entry : Main.allSprites.entrySet()) {
			Sprite entity = entry.getValue();

			// Vectorize
			float vectorx = entity.spriteXPos - Camera.player_x;
			float vectorz = entity.spriteZPos - Camera.player_z;
			float vectory = entity.spriteYPos - Camera.player_y;

			// Rotate to camera space
			float cam_x = (float)(vectorx * Math.cos(-Camera.direction_rad) - vectorz * Math.sin(-Camera.direction_rad));
			// Behind camera check
			if (cam_x <= 0) {
				continue;
			}
			float cam_z = (float)(vectorx * Math.sin(-Camera.direction_rad) + vectorz * Math.cos(-Camera.direction_rad));

			// Project to screen
			float screen_offset_x = (cam_z / cam_x) * Camera.retina_dist;
			float screen_offset_y = (vectory / cam_x) * Camera.retina_dist;

			int screen_sprite_x = half_screen_width - (int)(screen_offset_x);
			int screen_sprite_y = half_screen_height - (int)(screen_offset_y);

			// Get sprite size
			int screen_sprite_length = (int)(entity.sprite_length / cam_x);

			int half = screen_sprite_length / 2;
			int startX = screen_sprite_x - half;
			int endX = screen_sprite_x + half;
			int startY = screen_sprite_y - half;
			int endY = screen_sprite_y + half;
			
			int startXog = startX;
			int endXog = endX;
			int startYog = startY;
			int endYog = endY;
			
			// Clamp and draw
			startX = Math.max(0, startX);
			startY = Math.max(0, startY);
			endX = Math.min(Main.game_width - 1, endX);
			endY = Math.min(Main.game_height - 1, endY);

			for (int y = startY; y <= endY; y++) {
				for (int x = startX; x <= endX; x++) {
					if (depth_buffer[y * Main.game_width + x]>cam_x) {
						// Adjust brightness and draw sprite
						float u = (float)(x - startXog) / (endXog - startXog);
			            float v = (float)(y - startYog) / (endYog - startYog);
			            int color = get_texture_sprite_color(u, v, entity.spritename);
			            if (color!=0x000000) {
			            	depth_buffer[y * Main.game_width + x] = cam_x;
			            	gamepixels[y * Main.game_width + x] = adjustBrightness(color, entity.sprite_brightness, x, y);
			            }
					}
				}
			}
        }
	}
	
	private void cast_ray_and_render_screen_column(int x) {
		int ray_num = half_screen_width - x;

		float ray_angle = Camera.direction_rad + ray_num * deltatheta;
		float tan_ray = (float) Math.tan(ray_angle);
		float startx = Camera.player_x;
		float startz = Camera.player_z;

		float dirThetaX = (float) Math.signum(Math.cos(ray_angle));
		float dirThetaZ = (float) Math.signum(Math.sin(ray_angle));

		int counter = 0;
		int ray_sector = Camera.player_sector;
		int dy_wall_bottom_bottom;
		int dy_wall_bottom_top;
		int dy_wall_top_bottom;
		int dy_wall_top_top;
		
		while (counter<max_count) {
			counter++;
			float dx_1;
			float dz_1;
			float dx_2;
			float dz_2;
			if (dirThetaX>0) {
				dx_1 = (float) (Math.floor(startx+1)-startx);
			} else {
				dx_1 = (float) (Math.ceil(startx-1)-startx);
			}

			dz_1 = (float) (dirThetaZ*Math.abs(dx_1*tan_ray));
			
			// manhattan_dist because of Clarity! And it doesn't actually cause that much of a bottleneck for our small levels
			float dist_horizontal = manhattan_dist(startx, startz, startx+dx_1, startz+dz_1);

			if (dirThetaZ > 0) {
				dz_2 = (float) (Math.floor(startz + 1) - startz);
			} else {
				dz_2 = (float) (Math.ceil(startz - 1) - startz);
			}
			dx_2 = (float) (dirThetaX * Math.abs(dz_2 / tan_ray));

			float dist_vertical = manhattan_dist(startx, startz, startx+dx_2, startz+dz_2);

			String wallkey;
			float decimal_value_wall_hit;
			if (dist_horizontal < dist_vertical) {
				startx = startx + dx_1;
				startz = startz + dz_1;
				float[] new_starts = fixPhantomRay(startx, startz);
				startx = new_starts[0];
				startz = new_starts[1];
			} else {
				startx = startx + dx_2;
				startz = startz + dz_2;
				float[] new_starts = fixPhantomRay(startx, startz);
				startx = new_starts[0];
				startz = new_starts[1];
			}
			if (isWholeNumber(startx) && isWholeNumber(startz)) {
				continue;
			} else if (isWholeNumber(startx)) {
				wallkey = makeWallKey(startx, (float)Math.floor(startz), startx, (float)Math.floor(startz+1));
				float abs_startz = Math.abs(startz);
				decimal_value_wall_hit = (float) (abs_startz-Math.floor(abs_startz));
			} else {
				wallkey = makeWallKey((float)Math.floor(startx), startz, (float)Math.floor(startx+1), startz);
				float abs_startx = Math.abs(startx);
				decimal_value_wall_hit = (float) (abs_startx-Math.floor(abs_startx));
			}
			float full_euclid_dist = euclid_dist(Camera.player_x, Camera.player_z, startx, startz);
			if (wallMap.containsKey(wallkey)) {

				Wall wallhit = wallMap.get(wallkey);
				Sector sector_info = sectorMap.get(wallhit.sectorid); 
				int dy_walltop = half_screen_height - project_column(startx, sector_info.ceil_height, startz, ray_angle);
				int dy_wallbottom = half_screen_height - project_column(startx, sector_info.floor_height, startz, ray_angle);

				int dy_walltop_clipped = clip_column(dy_walltop);
				int dy_wallbottom_clipped = clip_column(dy_wallbottom);
				
				float fl_h = Camera.player_y-sector_info.floor_height;
				float cl_h = sector_info.ceil_height-Camera.player_y;
				
				for (int y=0; y < dy_walltop_clipped; y++) {
					draw_plane_texture(x, y, cl_h, half_screen_height - y, ray_angle, full_euclid_dist, startx, startz, sector_info.ceilTexture, sector_info.ceilBrightness);
				}	
				
				if (!wallhit.wallTexture.contentEquals(transparentTex)) {
					int column_pixel_size = dy_wallbottom-dy_walltop;
					for (int y=dy_walltop_clipped; y < dy_wallbottom_clipped; y++) {
						draw_wall_texture(x, y, decimal_value_wall_hit, dy_walltop, dy_wallbottom, wallhit.wallTexture, wallhit.wallBrightness, column_pixel_size, full_euclid_dist);
					}
				}
				
				for (int y=dy_wallbottom_clipped; y < Main.game_height; y++) {
					draw_plane_texture(x, y, fl_h, y - half_screen_height, ray_angle, full_euclid_dist, startx, startz, sector_info.floorTexture, sector_info.floorBrightness);
				}
				
				break;
			}

			if (portalMap.containsKey(wallkey)) {
				Portal portalhit = portalMap.get(wallkey);
				int prev_ray_sector = ray_sector; 
				if (ray_sector==portalhit.sectorA) {
					ray_sector = portalhit.sectorB;
				} else {
					ray_sector = portalhit.sectorA;
				}
				Sector cur_sector = sectorMap.get(prev_ray_sector);
				Sector next_sector = sectorMap.get(ray_sector);

				if (cur_sector.floor_height < next_sector.floor_height) {
					dy_wall_bottom_bottom = half_screen_height - project_column(startx, cur_sector.floor_height, startz, ray_angle);
					dy_wall_bottom_top = half_screen_height - project_column(startx, next_sector.floor_height, startz, ray_angle);
				} else {
					dy_wall_bottom_bottom = half_screen_height - project_column(startx, cur_sector.floor_height, startz, ray_angle);
					dy_wall_bottom_top = dy_wall_bottom_bottom;
				}
				if (cur_sector.ceil_height > next_sector.ceil_height) {
					dy_wall_top_bottom = half_screen_height - project_column(startx, next_sector.ceil_height, startz, ray_angle);
					dy_wall_top_top = half_screen_height - project_column(startx, cur_sector.ceil_height, startz, ray_angle);
				} else {
					dy_wall_top_bottom = half_screen_height - project_column(startx, cur_sector.ceil_height, startz, ray_angle);
					dy_wall_top_top = dy_wall_top_bottom;
				}
				
				int dy_wall_top_top_clipped = clip_column(dy_wall_top_top);
				int dy_wall_top_bottom_clipped = clip_column(dy_wall_top_bottom);
				int dy_wall_bottom_top_clipped = clip_column(dy_wall_bottom_top);
				int dy_wall_bottom_bottom_clipped = clip_column(dy_wall_bottom_bottom);
				
				float fl_h = Camera.player_y-cur_sector.floor_height;
				float cl_h = cur_sector.ceil_height-Camera.player_y;
				
				for (int y=0; y < dy_wall_top_top_clipped; y++) {
					draw_plane_texture(x, y, cl_h, half_screen_height - y, ray_angle, full_euclid_dist, startx, startz, cur_sector.ceilTexture, cur_sector.ceilBrightness);
				}
				
				int column_pixel_size = dy_wall_top_bottom-dy_wall_top_top;
				for (int y=dy_wall_top_top_clipped; y < dy_wall_top_bottom_clipped; y++) {
					draw_wall_texture(x, y, decimal_value_wall_hit, dy_wall_top_top, dy_wall_top_bottom, portalhit.portalTopTexture, portalhit.portalTopBrightness, column_pixel_size, full_euclid_dist);
				}
				
				if (!portalhit.portalMiddleTexture.contentEquals(transparentTex)) {
					column_pixel_size = dy_wall_bottom_top-dy_wall_top_bottom;
					for (int y=dy_wall_top_bottom_clipped; y < dy_wall_bottom_top_clipped; y++) {
						draw_wall_texture(x, y, decimal_value_wall_hit, dy_wall_top_bottom, dy_wall_bottom_top, portalhit.portalMiddleTexture, portalhit.portalMiddleBrightness, column_pixel_size, full_euclid_dist);
					}	
				}
				
				column_pixel_size = dy_wall_bottom_bottom-dy_wall_bottom_top;
				for (int y=dy_wall_bottom_top_clipped; y < dy_wall_bottom_bottom_clipped; y++) {
					draw_wall_texture(x, y, decimal_value_wall_hit, dy_wall_bottom_top, dy_wall_bottom_bottom, portalhit.portalBottomTexture, portalhit.portalBottomBrightness, column_pixel_size, full_euclid_dist);
				}

				for (int y=dy_wall_bottom_bottom_clipped; y < Main.game_height; y++) {
					draw_plane_texture(x, y, fl_h, y - half_screen_height, ray_angle, full_euclid_dist, startx, startz, cur_sector.floorTexture, cur_sector.floorBrightness);
				}

				continue;
			}
		}
	}

	public static float euclid_dist(float x1, float z1, float x2, float z2) {
		return (float) Math.sqrt((z2-z1)*(z2-z1)+(x2-x1)*(x2-x1));
	} 
	
	public static float manhattan_dist(float x1, float z1, float x2, float z2) {
		return Math.abs(z2-z1)+Math.abs(x2-x1);
	}
	
	public static float euclidean_dist_3D(float x1, float y1, float z1, float x2, float y2, float z2) {
		return (float) Math.sqrt((z2-z1)*(z2-z1)+(y2-y1)*(y2-y1)+(x2-x1)*(x2-x1));
	}

	public static String makeWallKey(float x1, float z1, float x2, float z2) {
		// Normalize so the smaller point comes first
		if (x1 > x2 || (x1 == x2 && z1 > z2)) {
			float tmpX = x1, tmpZ = z1;
			x1 = x2; z1 = z2;
			x2 = tmpX; z2 = tmpZ;
		}
		return x1 + "," + z1 + "," + x2 + "," + z2;
	}
	
	private boolean isWholeNumber(float number) {
	    return number % 1 == 0;
	}
	
	private float[] fixPhantomRay(float startx, float startz) {
		float[] coords = new float[] {startx, startz};
		if (isWholeNumber(startx) && isWholeNumber(startz)) {
			// exact coordinates not allowed, check 4 walls to figure out where is the wallkey we want
			coords[0] = fixPhantomRay_x(startx, startz, 0.001f);
			if (coords[0]==startx) {
				coords[1] = fixPhantomRay_z(startx, startz, 0.001f);
			}
		}
		return coords;
	}
	
	private float fixPhantomRay_x(float startx, float startz, float small_eps) {
		float original_startx = startx;
		startx = startx+small_eps;
		String wallkey_candidateXp = makeWallKey((float)Math.floor(startx), startz, (float)Math.floor(startx+1), startz);
		if (wallMap.containsKey(wallkey_candidateXp) || portalMap.containsKey(wallkey_candidateXp)) {
			return startx;
		}
		startx = startx-2*small_eps;
		String wallkey_candidateXm = makeWallKey((float)Math.floor(startx), startz, (float)Math.floor(startx+1), startz);
		if (wallMap.containsKey(wallkey_candidateXm) || portalMap.containsKey(wallkey_candidateXm)) {
			return startx;
		}
		return original_startx;
	}
	
	private float fixPhantomRay_z(float startx, float startz, float small_eps) {
		float original_startz = startz;
		startz = startz+small_eps;
		String wallkey_candidateZp = makeWallKey(startx, (float)Math.floor(startz), startx, (float)Math.floor(startz+1));
		if (wallMap.containsKey(wallkey_candidateZp) || portalMap.containsKey(wallkey_candidateZp)) {
			return startz;
		}
		startz = startz-2*small_eps;
		String wallkey_candidateZm = makeWallKey(startx, (float)Math.floor(startz), startx, (float)Math.floor(startz+1));
		if (wallMap.containsKey(wallkey_candidateZm) || portalMap.containsKey(wallkey_candidateZm)) {
			return startz;
		}
		return original_startz;
	}
	
	private void draw_sky(float dir, int[] skybox_picture) {
		if (!sky_texture_bool) {
			for (int y = 0; y < Main.game_height; y++) {
				for (int x = 0; x < Main.game_width; x++) { 
					if (gamepixels[y * Main.game_width + x]!=skybox_refresh_val && gamepixels[y * Main.game_width + x]!=0x000000) {
						continue;
					}
					gamepixels[y * Main.game_width + x] = skybox_picture[0];
					depth_buffer[y * Main.game_width + x] = fog_end;
				}
			}
			return;
		}
		
		int skybox_width = Main.game_width * 4;  
		int skybox_height = Main.game_height; 

		// dir is in radians (0..2PI), convert to horizontal offset in the skybox
		int offsetX = ((int)((dir / (2 * Math.PI)) * skybox_width)+sky_offset) % skybox_width;
		
		for (int y = 0; y < Main.game_height; y++) {
			for (int x = 0; x < Main.game_width; x++) {
				if (gamepixels[y * Main.game_width + x]!=skybox_refresh_val && gamepixels[y * Main.game_width + x]!=0x000000) {
					continue;
				}

				int skyX = (x - offsetX + skybox_width) % skybox_width;
				int skyY = y * skybox_height / Main.game_height; 

				int skyIndex = skyY * skybox_width + skyX;
				int color = skybox_picture[skyIndex];
				int r = (int)(((color >> 16) & 0xFF) * skybox_brightness);
				int g = (int)(((color >> 8) & 0xFF) * skybox_brightness);
				int b = (int)((color & 0xFF) * skybox_brightness);
				gamepixels[y * Main.game_width + x] = (r << 16) | (g << 8) | b;
				depth_buffer[y * Main.game_width + x] = fog_end;
			}
		}
	}

	public static void updatePixelArrays(int[] newPixels) {
	    pixels = newPixels;
	}
	
	private void up_res() {
		final float scale = Math.min(Main.SCREEN_W / (float)Main.game_width, Main.SCREEN_H / (float)Main.game_height);
		final int renderW = (int)(Main.game_width * scale);
		final int renderH = (int)(Main.game_height * scale);
		final int startX = (Main.SCREEN_W - renderW) >> 1;
				final int startY = (Main.SCREEN_H - renderH) >> 1;
				final float invScale = 1.0f / scale;

				for (int y = 0; y < renderH; y++) {
					int srcY = (int)(y * invScale);
					int screenY = (startY + y) * Main.SCREEN_W + startX;
					int srcOffset = srcY * Main.game_width;
					for (int x = 0; x < renderW; x++) {
						int srcX = (int)(x * invScale);
						pixels[screenY + x] = gamepixels[srcOffset + srcX];
					}
				}
	}

	private int project_column(float wallhit_x, float wallhit_y, float wallhit_z, float ray_angle) {
		float dy = (float) (((wallhit_y-Camera.player_y)/(euclid_dist(Camera.player_x, Camera.player_z, wallhit_x, wallhit_z)*Math.cos(ray_angle-Camera.direction_rad)))*Camera.retina_dist);
		int dy_from_middle = (int)(dy);
		return dy_from_middle;
	}
	
	private float reverse_project(float fl_h, int screen_y_offset, float ray_angle) {
		float dy = screen_y_offset;
		float perp_dist = (fl_h*Camera.retina_dist)/dy;
		return (float) (perp_dist / Math.cos(ray_angle - Camera.direction_rad));
	}
	
	private float figure_out_tile(float full_euclid_distance, float full_euclid_minus_perp_dist, float wallhit_x, float player_x) {
		float x2 = player_x - wallhit_x;
		float x_delta = full_euclid_minus_perp_dist * x2 / full_euclid_distance;
		return wallhit_x+x_delta;
	}
	
	private int get_texture_sprite_color(float spritex, float spritey, String spriteTexture) {
		float localX = (float) (spritex - Math.floor(spritex));
	    float localY = (float) (spritey - Math.floor(spritey));
	    Texture texture_sprite_obj = Main.allTextures.get(spriteTexture);
	    int u = (int)(localX * texture_sprite_obj.IMG_WID) % texture_sprite_obj.IMG_WID;
	    int v = (int)(localY * texture_sprite_obj.IMG_HEI) % texture_sprite_obj.IMG_HEI;
	    return texture_sprite_obj.pixels[v * texture_sprite_obj.IMG_WID + u];
	}
	
	private int get_texture_tile_color(float tilehit_x, float tilehit_z, String floorTexture, float floorBrightness, int x, int y) {
	    float localX = (float) (tilehit_x - Math.floor(tilehit_x));
	    float localZ = (float) (tilehit_z - Math.floor(tilehit_z));
	    Texture texture_floor_obj = Main.allTextures.get(floorTexture);
	    int u = (int)(localX * texture_floor_obj.IMG_WID) % texture_floor_obj.IMG_WID;
	    int v = (int)(localZ * texture_floor_obj.IMG_HEI) % texture_floor_obj.IMG_HEI;
	    return adjustBrightness(texture_floor_obj.pixels[v * texture_floor_obj.IMG_WID + u], floorBrightness, x, y);
	}

	private void draw_plane_texture(int x, int y, float height_offset, int screen_y_offset, float ray_angle, float full_euclid_dist, float startx, float startz, String planeTexture, float planeBrightness) {
		if (gamepixels[y * Main.game_width + x]==0x000000) {
			if (planeTexture.contentEquals(transparentTex)) { // String comparisons are a bit slower, but again, clarity!
				gamepixels[y * Main.game_width + x] = skybox_refresh_val;
				return;
			}
			// Note: This is slow, but intuitive and at 320x240 res which is what I want doesn't cause much of a bottleneck at all. Also it is easy to set the depth buffer info killing two birds with one stone!
			float perp_dist = reverse_project(height_offset, screen_y_offset, ray_angle);
			float full_euclid_minus_perp_dist = full_euclid_dist-perp_dist;
			float tilex = figure_out_tile(full_euclid_dist, full_euclid_minus_perp_dist, startx, Camera.player_x);
			float tilez = figure_out_tile(full_euclid_dist, full_euclid_minus_perp_dist, startz, Camera.player_z);
			depth_buffer[y * Main.game_width + x] = perp_dist;
			if (plane_texture) {
				gamepixels[y * Main.game_width + x] = get_texture_tile_color(tilex, tilez, planeTexture, planeBrightness, x, y);
			} else {
				gamepixels[y * Main.game_width + x] = adjustBrightness(Main.allTextures.get(planeTexture).pixels[0], planeBrightness, x, y); // Get the first pixel, simplifying
			}
		}
	}
	
	private void draw_wall_texture(int x, int y, float decimal_value_wall_hit, int dy_walltop, int dy_wallbottom, String wallTexture, float wallBrightness, int wall_column_pixel_size, float full_euclid_dist) {
		if (gamepixels[y * Main.game_width + x]==0x000000) {
			Texture texture_wall_obj = Main.allTextures.get(wallTexture);
			int texture_color = 0;
			if (wall_texture_bool) {
				int u = (int)Math.round(decimal_value_wall_hit*texture_wall_obj.IMG_WID); // similar to u v mapping so the idea is u is the x along texture where, and v is going to be the y of that texture where
				int v = (int)Math.round((((float)y-(float)dy_walltop)/((float)wall_column_pixel_size))*texture_wall_obj.IMG_HEI);
				u = Math.max(0, Math.min(texture_wall_obj.IMG_WID - 1, u));
			    v = Math.max(0, Math.min(texture_wall_obj.IMG_HEI - 1, v));
			    texture_color = texture_wall_obj.pixels[v * texture_wall_obj.IMG_WID + u];
			} else {
				texture_color = texture_wall_obj.pixels[0];
			}
		    if (texture_color!=0x000000) {
		    	depth_buffer[y * Main.game_width + x] = full_euclid_dist;
		    	gamepixels[y * Main.game_width + x] = adjustBrightness(texture_color, wallBrightness, x, y);
		    }
		}
	}
	
	private int adjustBrightness(int color, float brightness, int x, int y) {
	    int r = Math.min(255, (int)(((color >> 16) & 0xFF) * brightness));
	    int g = Math.min(255, (int)(((color >> 8) & 0xFF) * brightness));
	    int b = Math.min(255, (int)((color & 0xFF) * brightness));
	    
	    float distance = depth_buffer[y * Main.game_width + x];
	    float fog_factor = Math.min(1.0f, Math.max(0.0f, (distance - fog_start) / (fog_end - fog_start)));
	    r = (int)(r * (1 - fog_factor) + fog_r * fog_factor);
	    g = (int)(g * (1 - fog_factor) + fog_g * fog_factor);
	    b = (int)(b * (1 - fog_factor) + fog_b * fog_factor);

	    int pixel_color = (r << 16) | (g << 8) | b;
	    if (pixel_color==0x000000) {
	    	return 0x000001;
	    }
	    return pixel_color;
	}

	private int clip_column(int column_n) {
		if (column_n < 0) {
			return 0;
		} else if (column_n > Main.game_height) {
			return Main.game_height;
		}
		return column_n;
	}
}
