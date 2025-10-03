import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

public class Screen {

	private int[] pixels;
	public static int[] gamepixels;
	public static String skybox;
	public static float skybox_brightness;
	public static double atomic_xz_unit = 100;
	public static Map<Integer, Sector> sectorMap;
	public static Map<String, Wall> wallMap;
	public static Map<String, Portal> portalMap;
	public static boolean is_menu = true;
	private int half_screen_height = Main.game_height/2;
	public static boolean fog_occlusion = true;
	private double[] depth_buffer;
	public static int fog_r = 0x00;
	public static int fog_g = 0x00;    
	public static int fog_b = 0x01;
	public static double fog_start = 5.0;
	public static double fog_end = 25.0;
	private int skybox_refresh_val = 0x1000000;

	public Screen(int[] pixels, int[] gamepixels) {
		this.pixels = pixels;
		this.gamepixels = gamepixels;
		this.depth_buffer = new double[gamepixels.length];
	}

	public void update(int frame_num) {
		Arrays.fill(gamepixels, 0x000000);
		Arrays.fill(depth_buffer, Integer.MAX_VALUE);
		if (!is_menu) {
			double camera_mid_side_a = Camera.retina_dist;
			double camera_mid_side_b = Main.game_width / 2.0 / atomic_xz_unit;

			double total_fov = 2 * Math.atan(camera_mid_side_b / camera_mid_side_a);
			double deltatheta = total_fov / Main.game_width;

			// kinda terrible, can optimize but im too lazy
			for (int i=1; i<=Screen.sectorMap.size(); i++) {
		        Sector sector = Screen.sectorMap.get(i);
				if (Camera.player_x >= sector.boundary_coords[0] && Camera.player_x <= sector.boundary_coords[1] &&
		            Camera.player_z >= sector.boundary_coords[2] && Camera.player_z <= sector.boundary_coords[3]) {
		            Camera.player_sector = sector.sectorId;
		            break;
		        }
		    }
			
			for (int x = 0; x < Main.game_width; x++) {
				int ray_num = (Main.game_width/2) - x;

				double ray_angle = Camera.direction_rad + ray_num * deltatheta;

				double startx = Camera.player_x;
				double startz = Camera.player_z;

				double dirThetaX = Math.signum(Math.cos(ray_angle));
				double dirThetaZ = Math.signum(Math.sin(ray_angle));

				int counter = 0;
				int max_count = 100;
				int ray_sector = Camera.player_sector;
				int dy_wall_bottom_bottom;
				int dy_wall_bottom_top;
				int dy_wall_top_bottom;
				int dy_wall_top_top;
				
				while (counter<max_count) {
					counter+=1;
					double dx_1;
					double dz_1;
					double dx_2;
					double dz_2;
					if (dirThetaX>0) {
						dx_1 = Math.floor(startx+1)-startx;
					} else {
						dx_1 = Math.ceil(startx-1)-startx;
					}

					dz_1 = dirThetaZ*Math.abs(dx_1*Math.tan(ray_angle));

					double dist_horizontal = euclid_dist(startx, startz, startx+dx_1, startz+dz_1);

					if (dirThetaZ > 0) {
						dz_2 = Math.floor(startz + 1) - startz;
					} else {
						dz_2 = Math.ceil(startz - 1) - startz;
					}
					dx_2 = dirThetaX * Math.abs(dz_2 / Math.tan(ray_angle));

					double dist_vertical = euclid_dist(startx, startz, startx + dx_2, startz + dz_2);

					String wallkey;
					// Pick the closer intersection
					double decimal_value_wall_hit;
					if (dist_horizontal < dist_vertical) {
						startx = startx + dx_1;
						startz = startz + dz_1;
						wallkey = makeWallKey(startx, Math.floor(startz), startx, Math.floor(startz+1));
						double abs_startz = Math.abs(startz);
						decimal_value_wall_hit = abs_startz-Math.floor(abs_startz);
					} else {
						startx = startx + dx_2;
						startz = startz + dz_2;
						wallkey = makeWallKey(Math.floor(startx), startz, Math.floor(startx+1), startz);
						double abs_startx = Math.abs(startx);
						decimal_value_wall_hit = abs_startx-Math.floor(abs_startx);
					}

					double full_euclid_dist = euclid_dist(Camera.player_x, Camera.player_z, startx, startz);
					if (wallMap.containsKey(wallkey)) {

						Wall wallhit = wallMap.get(wallkey);
						Sector sector_info = sectorMap.get(wallhit.sectorid); 
						int dy_walltop = half_screen_height - project_column(startx, sector_info.ceil_height, startz, Main.game_height, ray_angle);
						int dy_wallbottom = half_screen_height - project_column(startx, sector_info.floor_height, startz, Main.game_height, ray_angle);

						int dy_walltop_clipped = clip_column(dy_walltop);
						int dy_wallbottom_clipped = clip_column(dy_wallbottom);
						
						double fl_h = Camera.player_y-sector_info.floor_height;
						double cl_h = sector_info.ceil_height-Camera.player_y;
						// Draw depending on the sector
						for (int y=0; y < dy_walltop_clipped; y++) {
							draw_horizontal_plane(x, y, cl_h, half_screen_height - y, ray_angle, full_euclid_dist, startx, startz, sector_info.ceilTexture, sector_info.ceilBrightness);
						}
						
						int column_pixel_size = dy_wallbottom-dy_walltop;
						for (int y=dy_walltop_clipped; y < dy_wallbottom_clipped; y++) {
							draw_wall_texture(x, y, decimal_value_wall_hit, dy_walltop, dy_wallbottom, wallhit.wallTexture, wallhit.wallBrightness, column_pixel_size, full_euclid_dist);
						}

						for (int y=dy_wallbottom_clipped; y < Main.game_height; y++) {
							draw_horizontal_plane(x, y, fl_h, y - half_screen_height, ray_angle, full_euclid_dist, startx, startz, sector_info.floorTexture, sector_info.floorBrightness);
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
							dy_wall_bottom_bottom = half_screen_height - project_column(startx, cur_sector.floor_height, startz, Main.game_height, ray_angle);
							dy_wall_bottom_top = half_screen_height - project_column(startx, next_sector.floor_height, startz, Main.game_height, ray_angle);
						} else {
							dy_wall_bottom_bottom = half_screen_height - project_column(startx, cur_sector.floor_height, startz, Main.game_height, ray_angle);
							dy_wall_bottom_top = dy_wall_bottom_bottom;
						}
						if (cur_sector.ceil_height > next_sector.ceil_height) {
							dy_wall_top_bottom = half_screen_height - project_column(startx, next_sector.ceil_height, startz, Main.game_height, ray_angle);
							dy_wall_top_top = half_screen_height - project_column(startx, cur_sector.ceil_height, startz, Main.game_height, ray_angle);
						} else {
							dy_wall_top_bottom = half_screen_height - project_column(startx, cur_sector.ceil_height, startz, Main.game_height, ray_angle);
							dy_wall_top_top = dy_wall_top_bottom;
						}
						
						int dy_wall_top_top_clipped = clip_column(dy_wall_top_top);
						int dy_wall_top_bottom_clipped = clip_column(dy_wall_top_bottom);
						int dy_wall_bottom_top_clipped = clip_column(dy_wall_bottom_top);
						int dy_wall_bottom_bottom_clipped = clip_column(dy_wall_bottom_bottom);
						
						double fl_h = Camera.player_y-cur_sector.floor_height;
						double cl_h = cur_sector.ceil_height-Camera.player_y;
						
						for (int y=0; y < dy_wall_top_top_clipped; y++) {
							draw_horizontal_plane(x, y, cl_h, half_screen_height - y, ray_angle, full_euclid_dist, startx, startz, cur_sector.ceilTexture, cur_sector.ceilBrightness);
						}
						
						int column_pixel_size = dy_wall_top_bottom-dy_wall_top_top;
						for (int y=dy_wall_top_top_clipped; y < dy_wall_top_bottom_clipped; y++) {
							draw_wall_texture(x, y, decimal_value_wall_hit, dy_wall_top_top, dy_wall_top_bottom, portalhit.portalTopTexture, portalhit.portalTopBrightness, column_pixel_size, full_euclid_dist);
						}
						
						if (!portalhit.portalMiddleTexture.contentEquals("black.png")) {
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
							draw_horizontal_plane(x, y, fl_h, y - half_screen_height, ray_angle, full_euclid_dist, startx, startz, cur_sector.floorTexture, cur_sector.floorBrightness);
						}

						continue;

					}


				}




			}

			if (skybox!=null) {
				draw_sky(Camera.direction_rad, Main.allTextures.get(skybox).pixels);
			}
		}

		ReApi.run_user_scripts();
		up_res();
	}

	public static double euclid_dist(double x1, double z1, double x2, double z2) {
		return Math.sqrt((z2-z1)*(z2-z1)+(x2-x1)*(x2-x1));
	} 

	public static String makeWallKey(double x1, double z1, double x2, double z2) {
		// Normalize so the smaller point comes first
		if (x1 > x2 || (x1 == x2 && z1 > z2)) {
			double tmpX = x1, tmpZ = z1;
			x1 = x2; z1 = z2;
			x2 = tmpX; z2 = tmpZ;
		}
		return x1 + "," + z1 + "," + x2 + "," + z2;
	}

	private void draw_sky(double dir, int[] skybox_picture) {
		int skybox_width = Main.game_width * 4;  
		int skybox_height = Main.game_height; 

		// dir is in radians (0..2PI), convert to horizontal offset in the skybox
		int offsetX = (int)((dir / (2 * Math.PI)) * skybox_width) % skybox_width;

		for (int y = 0; y < Main.game_height; y++) {
			for (int x = 0; x < Main.game_width; x++) {
				// subtract offsetX instead of adding to reverse direction
				if (gamepixels[y * Main.game_width + x]!=skybox_refresh_val && gamepixels[y * Main.game_width + x]!=0x000000) {
					continue;
				}

				int skyX = (x - offsetX + skybox_width) % skybox_width; // add skybox_width to prevent negative wrap
				int skyY = y * skybox_height / Main.game_height; 

				int skyIndex = skyY * skybox_width + skyX;
				int color = skybox_picture[skyIndex];

				// apply brightness
				int r = (int)(((color >> 16) & 0xFF) * skybox_brightness);
				int g = (int)(((color >> 8) & 0xFF) * skybox_brightness);
				int b = (int)((color & 0xFF) * skybox_brightness);
				gamepixels[y * Main.game_width + x] = (r << 16) | (g << 8) | b;
			}
		}
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

	private int project_column(double wallhit_x, double wallhit_y, double wallhit_z, int column_size, double ray_angle) {
		double dy = ((wallhit_y-Camera.player_y)/(euclid_dist(Camera.player_x, Camera.player_z, wallhit_x, wallhit_z)*Math.cos(ray_angle-Camera.direction_rad)))*Camera.retina_dist;
		int dy_from_middle = (int)(dy*atomic_xz_unit);
		return dy_from_middle;
	}
	
	private double reverse_project(double fl_h, int screen_y_offset, double ray_angle) {
		double dy = screen_y_offset/atomic_xz_unit;
		double perp_dist = (fl_h*Camera.retina_dist)/dy;
		return perp_dist / Math.cos(ray_angle - Camera.direction_rad);
	}
	
	private double figure_out_x_tile(double full_euclid_distance, double full_euclid_minus_perp_dist, double wallhit_x) {
		double x2 = Camera.player_x - wallhit_x;
		double x_delta = full_euclid_minus_perp_dist * x2 / full_euclid_distance;
		return wallhit_x+x_delta;
	}
	
	private double figure_out_z_tile(double full_euclid_distance, double full_euclid_minus_perp_dist, double wallhit_z) {
		double z2 = Camera.player_z - wallhit_z;
		double z_delta = full_euclid_minus_perp_dist * z2 / full_euclid_distance;
		return wallhit_z+z_delta;
	}
	
	private int get_texture_tile_color(double tilehit_x, double tilehit_z, String floorTexture, double floorBrightness, int x, int y) {
	    double localX = tilehit_x - Math.floor(tilehit_x);
	    double localZ = tilehit_z - Math.floor(tilehit_z);
	    Texture texture_floor_obj = Main.allTextures.get(floorTexture);
	    int u = (int)(localX * texture_floor_obj.IMG_WID) % texture_floor_obj.IMG_WID;
	    int v = (int)(localZ * texture_floor_obj.IMG_HEI) % texture_floor_obj.IMG_HEI;
	    return adjustBrightness(texture_floor_obj.pixels[v * texture_floor_obj.IMG_WID + u], floorBrightness, x, y);
	}

	private void draw_horizontal_plane(int x, int y, double height_offset, int screen_y_offset, double ray_angle, double full_euclid_dist, double startx, double startz, String planeTexture, double planeBrightness) {
		if (gamepixels[y * Main.game_width + x]==0x000000) {
			if (planeTexture.contentEquals("black.png")) {
				gamepixels[y * Main.game_width + x] = skybox_refresh_val;
				return;
			}
			double perp_dist = reverse_project(height_offset, screen_y_offset, ray_angle);							
			double full_euclid_minus_perp_dist = full_euclid_dist-perp_dist;
			double tilex = figure_out_x_tile(full_euclid_dist, full_euclid_minus_perp_dist, startx);
			double tilez = figure_out_z_tile(full_euclid_dist, full_euclid_minus_perp_dist, startz);
			depth_buffer[y * Main.game_width + x] = perp_dist;
			gamepixels[y * Main.game_width + x] = get_texture_tile_color(tilex, tilez, planeTexture, planeBrightness, x, y);
		}
	}
	
	private void draw_wall_texture(int x, int y, double decimal_value_wall_hit, int dy_walltop, int dy_wallbottom, String wallTexture, double wallBrightness, int wall_column_pixel_size, double full_euclid_dist) {
		if (gamepixels[y * Main.game_width + x]==0x000000) {
			Texture texture_wall_obj = Main.allTextures.get(wallTexture);
			int u = (int)Math.round(decimal_value_wall_hit*texture_wall_obj.IMG_WID); // similar to u v mapping so the idea is u is the x along texture where, and v is going to be the y of that texture where
			int v = (int)Math.round((((double)y-(double)dy_walltop)/((double)wall_column_pixel_size))*texture_wall_obj.IMG_HEI);
			u = Math.max(0, Math.min(texture_wall_obj.IMG_WID - 1, u));
		    v = Math.max(0, Math.min(texture_wall_obj.IMG_HEI - 1, v));
		    int texture_color = texture_wall_obj.pixels[v * texture_wall_obj.IMG_WID + u];
		    if (texture_color!=0x000000) {
		    	depth_buffer[y * Main.game_width + x] = full_euclid_dist;
		    	gamepixels[y * Main.game_width + x] = adjustBrightness(texture_color, wallBrightness, x, y);
		    }
		}
	}
	
	private int adjustBrightness(int color, double brightness, int x, int y) {
	    int r = Math.min(255, (int)(((color >> 16) & 0xFF) * brightness));
	    int g = Math.min(255, (int)(((color >> 8) & 0xFF) * brightness));
	    int b = Math.min(255, (int)((color & 0xFF) * brightness));
	    
	    if (fog_occlusion) {
	        double distance = depth_buffer[y * Main.game_width + x];
	        double fog_factor = Math.min(1.0, Math.max(0.0, (distance - fog_start) / (fog_end - fog_start)));
	        r = (int)(r * (1 - fog_factor) + fog_r * fog_factor);
	        g = (int)(g * (1 - fog_factor) + fog_g * fog_factor);
	        b = (int)(b * (1 - fog_factor) + fog_b * fog_factor);
	    }
	    
	    // Ensure not total black pixel
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
