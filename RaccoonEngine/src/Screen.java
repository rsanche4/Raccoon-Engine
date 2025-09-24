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

	public Screen(int[] pixels, int[] gamepixels) {
		this.pixels = pixels;
		this.gamepixels = gamepixels;
	}

	public void update(int frame_num) {
		Arrays.fill(gamepixels, 0x000000);
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
				// DEBUG shading
				int[] sector_colors_ceil= new int[] {0, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFF000};
				int[] sector_colors_floor= new int[] {0, 0xF0F000, 0x0FF000, 0xF0000F, 0x0F0F00};
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
					if (dist_horizontal < dist_vertical) {
						startx = startx + dx_1;
						startz = startz + dz_1;
						wallkey = makeWallKey(startx, Math.floor(startz), startx, Math.floor(startz+1));
					} else {
						startx = startx + dx_2;
						startz = startz + dz_2;
						wallkey = makeWallKey(Math.floor(startx), startz, Math.floor(startx+1), startz);
					}

					if (wallMap.containsKey(wallkey)) {

						Wall wallhit = wallMap.get(wallkey);
						Sector sector_info = sectorMap.get(wallhit.sectorid); 
						int dy_walltop = Main.game_height/2 - project_column(startx, sector_info.ceil_height, startz, Main.game_height, ray_angle);
						int dy_wallbottom = Main.game_height/2 - project_column(startx, sector_info.floor_height, startz, Main.game_height, ray_angle);

						// Draw depending on the sector
						for (int y=0; y < dy_walltop; y++) {
							draw_renderpixel(x, y, sector_colors_ceil[sector_info.sectorId]);
						}

						for (int y=dy_walltop; y < dy_wallbottom; y++) {
							draw_renderpixel(x, y, 0xFF00FF);
						}

						for (int y=dy_wallbottom; y < Main.game_height; y++) {
							draw_renderpixel(x, y, sector_colors_floor[sector_info.sectorId]);
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
							dy_wall_bottom_bottom = Main.game_height/2 - project_column(startx, cur_sector.floor_height, startz, Main.game_height, ray_angle);
							dy_wall_bottom_top = Main.game_height/2 - project_column(startx, next_sector.floor_height, startz, Main.game_height, ray_angle);
						} else {
							dy_wall_bottom_bottom = Main.game_height/2 - project_column(startx, cur_sector.floor_height, startz, Main.game_height, ray_angle);
							dy_wall_bottom_top = dy_wall_bottom_bottom;
						}
						if (cur_sector.ceil_height > next_sector.ceil_height) {
							dy_wall_top_bottom = Main.game_height/2 - project_column(startx, next_sector.ceil_height, startz, Main.game_height, ray_angle);
							dy_wall_top_top = Main.game_height/2 - project_column(startx, cur_sector.ceil_height, startz, Main.game_height, ray_angle);
						} else {
							dy_wall_top_bottom = Main.game_height/2 - project_column(startx, cur_sector.ceil_height, startz, Main.game_height, ray_angle);
							dy_wall_top_top = dy_wall_top_bottom;
						}

						for (int y=0; y < dy_wall_top_top; y++) {
							draw_renderpixel(x, y, sector_colors_ceil[cur_sector.sectorId]);
						}

						for (int y=dy_wall_top_top; y < dy_wall_top_bottom; y++) {
							draw_renderpixel(x, y, 0xFFFFFF);
						}

						for (int y=dy_wall_bottom_top; y < dy_wall_bottom_bottom; y++) {
							draw_renderpixel(x, y, 0xFFFFFF);
						}

						for (int y=dy_wall_bottom_bottom; y < Main.game_height; y++) {
							draw_renderpixel(x, y, sector_colors_floor[cur_sector.sectorId]);
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
				if (gamepixels[y * Main.game_width + x]!=0x000000) {
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
		int column_size_half = column_size/2;
		if (dy_from_middle < -column_size_half) {
			return -column_size_half;
		}
		if (dy_from_middle > column_size_half) {
			return column_size_half;
		}
		return dy_from_middle;
	}

	private void draw_renderpixel(int x, int y, int color) {
		if (gamepixels[y * Main.game_width + x]==0x000000) {
			gamepixels[y * Main.game_width + x] = color;
		}
	}

}
