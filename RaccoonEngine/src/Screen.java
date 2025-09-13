import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
	public static int atomic_xz_unit = 100;
	
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
		
		
		
		if (skybox!=null) {
			draw_sky(Camera.direction_rad, Main.allTextures.get(skybox).pixels);
		}
		
		ReApi.run_user_scripts();
		up_res();
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
