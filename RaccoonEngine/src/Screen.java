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
		//		for (int x = 0; x < Main.game_width; x++) {
		//			for (int y = 0; y < Main.game_height; y++) {
		//				gamepixels[y * Main.game_width + x] = 0xFF0000;
		//			}
		//		}
		if (!is_menu) {
			double camera_mid_side_a = Camera.retina_dist;
			double camera_mid_side_b = Main.game_width / 2.0 / atomic_xz_unit;

			// Calculate total field of view, then divide by screen width for angular step per pixel
			double total_fov = 2 * Math.atan(camera_mid_side_b / camera_mid_side_a);
			double deltatheta = total_fov / Main.game_width;

			// So now basically go through each ray and REDO TODO all of this loop and logic. DDA needs to be actually carried out manually by me step by step so its al concise. We should go through an example
			// I belive one of the problems is the edges and corners, another is just this logic seems to suck so go over it
//			for (int x = 0; x < Main.game_width; x++) {
//			    int ray_num = (Main.game_width/2) - x;
//			    
//			    double ray_angle = Camera.direction_rad + ray_num * deltatheta;
//			    
//			    double rayX = Camera.player_x;
//			    double rayZ = Camera.player_z;
//			    double rayDirX = Math.cos(ray_angle);
//			    double rayDirZ = Math.sin(ray_angle);
//			    
//			    // Use floating point for precise tracking
//			    double currentX = rayX;
//			    double currentZ = rayZ;
//			    
//			    double deltaDistX = Math.abs(1 / rayDirX);
//			    double deltaDistZ = Math.abs(1 / rayDirZ);
//			    
//			    int stepX = (rayDirX < 0) ? -1 : 1;
//			    int stepZ = (rayDirZ < 0) ? -1 : 1;
//			    
//			    // Calculate distance to next grid line in each direction
//			    double sideDistX = (rayDirX < 0) ? (currentX - Math.floor(currentX)) * deltaDistX : (Math.ceil(currentX) - currentX) * deltaDistX;
//			    double sideDistZ = (rayDirZ < 0) ? (currentZ - Math.floor(currentZ)) * deltaDistZ : (Math.ceil(currentZ) - currentZ) * deltaDistZ;
//			    
//			    // Handle edge case where we start exactly on a grid line
//			    if (sideDistX == 0) sideDistX = deltaDistX;
//			    if (sideDistZ == 0) sideDistZ = deltaDistZ;
//			    
//			    boolean hit = false;
//			    int stepsMax = 100;
//			    int stepscount = 0;
//			    
//			    while (!hit && stepscount < stepsMax) {
//			        stepscount++;
//			        
//			        int prevMapX = (int) Math.floor(currentX);
//			        int prevMapZ = (int) Math.floor(currentZ);
//			        
//			        boolean crossedX = false;
//			        boolean crossedZ = false;
//			        
//			        // Step along the smallest distance
//			        if (sideDistX < sideDistZ) {
//			            currentX += stepX * (sideDistX / deltaDistX);
//			            sideDistX += deltaDistX;
//			            crossedX = true;
//			        } else {
//			            currentZ += stepZ * (sideDistZ / deltaDistZ);
//			            sideDistZ += deltaDistZ;
//			            crossedZ = true;
//			        }
//			        
//			        int mapX = (int) Math.floor(currentX);
//			        int mapZ = (int) Math.floor(currentZ);
//			        
//			        // Check if we've crossed into a corner (both coordinates changed grid cells)
//			        boolean isCorner = (mapX != prevMapX && mapZ != prevMapZ);
//			        
//			        if (isCorner) {
//			            // We've moved diagonally across a corner - check all adjacent edges
//			            int x0 = Math.min(prevMapX, mapX);
//			            int z0 = Math.min(prevMapZ, mapZ);
//			            
//			            String[] cornerKeys = new String[] {
//			                makeWallKey(x0, z0, x0 + 1, z0),         // horizontal edge (bottom)
//			                makeWallKey(x0 + 1, z0, x0 + 1, z0 + 1), // vertical edge (right)
//			                makeWallKey(x0 + 1, z0 + 1, x0, z0 + 1), // horizontal edge (top)
//			                makeWallKey(x0, z0 + 1, x0, z0)          // vertical edge (left)
//			            };
//			            
//			            for (String key : cornerKeys) {
//			                if (wallMap.containsKey(key) || portalMap.containsKey(key)) {
//			                    hit = true;
//			                    // Remove debug print for performance - uncomment if needed
//			                    // System.out.println("Hit wall at corner edge: " + key + " Ray: " + x);
//			                    for (int y = 0; y < Main.game_height; y++) {
//			                    	gamepixels[y * Main.game_width + x] = 0xFF0000;
//			                    }
//			                    break;
//			                }
//			            }
//			        } else {
//			            // Regular edge crossing - check the specific edge we crossed
//			            String key = null;
//			            
//			            if (crossedX) {
//			                // We crossed a vertical grid line
//			                int edgeX = (stepX > 0) ? mapX : prevMapX;
//			                key = makeWallKey(edgeX, prevMapZ, edgeX, prevMapZ + 1);
//			            } else {
//			                // We crossed a horizontal grid line  
//			                int edgeZ = (stepZ > 0) ? mapZ : prevMapZ;
//			                key = makeWallKey(prevMapX, edgeZ, prevMapX + 1, edgeZ);
//			            }
//			            
//			            if (key != null && (wallMap.containsKey(key) || portalMap.containsKey(key))) {
//			                hit = true;
//			                // Remove debug print for performance - uncomment if needed
//			                //System.out.println("Hit wall at edge: " + key + " Ray: " + x);
//			                for (int y = 0; y < Main.game_height; y++) {
//		                    	gamepixels[y * Main.game_width + x] = 0xFF0000;
//		                    }
//			            }
//			        }
//			    }
//			}

			if (skybox!=null) {
				draw_sky(Camera.direction_rad, Main.allTextures.get(skybox).pixels);
			}
		}
		
		ReApi.run_user_scripts();
		up_res();
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

}
