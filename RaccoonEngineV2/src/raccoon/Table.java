package raccoon;

import java.awt.Toolkit;

public class Table {
	
	public static int USER_SCREEN_SIZE_W = Toolkit.getDefaultToolkit().getScreenSize().width;
	public static int USER_SCREEN_SIZE_H = Toolkit.getDefaultToolkit().getScreenSize().height;
	public static double pi = 3.141592654;
	public static double pi2 = 2*pi;
	public static double pi_half_2 = pi/2;
	public static double MAX_DOUBLE_VAL = 9999999999.99;
	public static int SKYBOX_WID = Main.GAME_WID * 4;
	/** How far up/down the view can tilt. Camera clamps pitch to +/- this. */
	public static int MAX_PITCH = 200;
	/**
	 * Recommended skybox height. The extra 2*MAX_PITCH rows are the headroom
	 * that lets the sky slide vertically as you look up and down. A skybox that
	 * is exactly GAME_HEI tall still works - it just stays vertically locked,
	 * exactly like it did before.
	 */
	public static int SKYBOX_HEI = Main.GAME_HEI + (2 * MAX_PITCH);
	public static int LAST_WID_INDEX = Main.GAME_WID-1;
	public static double[] screen_x;
	public static double[] ray_offset;
	public static int half_screen_height = Main.GAME_HEI/2;
	public static int half_screen_width = Main.GAME_WID/2;
	public static double scale = Math.min(USER_SCREEN_SIZE_W / Main.GAME_WID, USER_SCREEN_SIZE_H / Main.GAME_HEI);
	public static int render_w = (int) (Main.GAME_WID * scale);
	public static int render_h = (int) (Main.GAME_HEI * scale);
	public static int render_start_x = (USER_SCREEN_SIZE_W - render_w) >> 1;
	public static int render_start_y = (USER_SCREEN_SIZE_H - render_h) >> 1;
	public static double inv_scale = 1 / scale;
	public static int[] src_y = new int[render_h];
	public static int[] screen_y = new int[render_h];
	public static int[] src_offset = new int[render_h];
	public static int[] src_x = new int[render_w];
	public static int NUM_COLORS = 4096;
	public static int[] PALETTE = new int[NUM_COLORS];
	public static int NUM_LIGHT_LEVELS = 32;
	public static int[][] SHADE_TABLE = new int[NUM_LIGHT_LEVELS][NUM_COLORS];
	public static int SPRITE_NUM_DIRECTIONS = 8;
	public static double DIRECTIONAL_SLICE_ANGLE = pi2 / SPRITE_NUM_DIRECTIONS;
	
    public static void init() {
    	screen_x = new double[Main.GAME_WID];
    	ray_offset = new double[Main.GAME_WID];
    	for (int x = 0; x < Main.GAME_WID; x++) {
    		screen_x[x] = (x + 0.5) - (Main.GAME_WID / 2.0);
    		ray_offset[x] = Math.atan(-screen_x[x] / Camera.retina_dist);
    	}
    	
    	for (int y = 0; y < render_h; y++) {
			src_y[y] = (int) (y * inv_scale);
			screen_y[y] = (render_start_y + y) * USER_SCREEN_SIZE_W + render_start_x;
			src_offset[y] = src_y[y] * Main.GAME_WID;
		}
    	
    	for (int x = 0; x < render_w; x++) {
    		src_x[x] = (int) (x * inv_scale);
    	}
    	
        // Palette def
        // 4096 colours: 16 evenly spaced levels per channel (0, 17, 34, ... 255).
        // Index = r_level * 256 + g_level * 16 + b_level
        int idx = 0;
        for (int r = 0; r < 16; r++) {
            for (int g = 0; g < 16; g++) {
                for (int b = 0; b < 16; b++) {
                    PALETTE[idx++] = 0xFF000000 | ((r * 17) << 16) | ((g * 17) << 8) | (b * 17);
                }
            }
        }
    	
        int middleindex = (NUM_LIGHT_LEVELS/2)-1;
        for (int light = 0; light < NUM_LIGHT_LEVELS; light++) {
            for (int i = 0; i < NUM_COLORS; i++) {
                int color = PALETTE[i];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8)  & 0xFF;
                int b =  color        & 0xFF;

                int nr, ng, nb;

                if (light == middleindex) {
                    nr = r; ng = g; nb = b;
                } else if (light < 15) {
                    // black → color
                    double t = (double) light / 15;
                    nr = (int)(r * t);
                    ng = (int)(g * t);
                    nb = (int)(b * t);
                } else {
                    // color → white
                    double t = (double)(light - 15) / 15;
                    nr = (int)(r + (255 - r) * t);
                    ng = (int)(g + (255 - g) * t);
                    nb = (int)(b + (255 - b) * t);
                }

                SHADE_TABLE[light][i] = findClosestColorIndex(nr, ng, nb);
            }
        }
    }
    
    public static int findClosestColorIndex(int r, int g, int b) {
    	r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return ((r + 8) / 17) * 256 + ((g + 8) / 17) * 16 + ((b + 8) / 17);
    }
}
