package raccoon;

import java.awt.Toolkit;

public class Table {
	
	public static int USER_SCREEN_SIZE_W = Toolkit.getDefaultToolkit().getScreenSize().width;
	public static int USER_SCREEN_SIZE_H = Toolkit.getDefaultToolkit().getScreenSize().height;
	public static int ANGLE_COUNT = 65536;
	public static double pi = 3.141592654;
	public static double pi2 = 2*pi;
	public static double[] screen_x;
	public static double[] ray_offset;
	public static double[] all_angles;
	public static double[] tans;
	public static double[] sins;
	public static double[] coss;
	public static int[] signs_sins;
	public static int[] signs_coss;
	public static int half_screen_height = Main.GAME_HEI/2;
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
	public static int NUM_COLORS = 256;
	public static int[] PALETTE = new int[NUM_COLORS];
	public static int NUM_LIGHT_LEVELS = 32;
	public static int[][] SHADE_TABLE = new int[NUM_LIGHT_LEVELS][NUM_COLORS];
	
    public static void init() {
    	all_angles = new double[ANGLE_COUNT];
    	tans = new double[ANGLE_COUNT];
    	sins = new double[ANGLE_COUNT];
    	coss = new double[ANGLE_COUNT];
    	signs_sins = new int[ANGLE_COUNT];
    	signs_coss = new int[ANGLE_COUNT];
    	double increment = pi2/ANGLE_COUNT;
    	for (int angle_val = 0; angle_val < ANGLE_COUNT; angle_val++) {
    		all_angles[angle_val] = increment*angle_val;
    		tans[angle_val] = Math.tan(all_angles[angle_val]);
    		sins[angle_val] = Math.sin(all_angles[angle_val]);
    		signs_sins[angle_val] = (int) Math.signum(sins[angle_val]);
    		coss[angle_val] = Math.cos(all_angles[angle_val]);
    		signs_coss[angle_val] = (int) Math.signum(coss[angle_val]);
    	}
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
    	
        int[] base = {0xFF000000, 0xFF800000, 0xFF008000, 0xFF808000, 0xFF000080, 0xFF800080, 0xFF008080, 0xFFC0C0C0, 0xFF808080, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF0000FF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF};
        System.arraycopy(base, 0, PALETTE, 0, 16);
        int[] steps = {0x00, 0x5F, 0x87, 0xAF, 0xD7, 0xFF};
        int idx = 16;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 6; b++) {
                	PALETTE[idx++] = (0xFF << 24) | (steps[r] << 16) | (steps[g] << 8) | steps[b];
                }
            }
        }
        for (int i = 0; i < 24; i++) {
            int v = 8 + i * 10;
            PALETTE[232 + i] = (0xFF << 24) | (v << 16) | (v << 8) | v;
        }
    	
        for (int light = 0; light < NUM_LIGHT_LEVELS; light++) {
            double factor = (double) light / (NUM_LIGHT_LEVELS - 1);
            for (int i = 0; i < NUM_COLORS; i++) {
                int color = PALETTE[i];
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int nr = (int)(r * factor);
                int ng = (int)(g * factor);
                int nb = (int)(b * factor);
                SHADE_TABLE[light][i] = findClosestColorIndex(nr, ng, nb);
            }
        }
    }
    
    public static int findClosestColorIndex(int r, int g, int b) {
        int best_index = 0;
        int best_dist = Integer.MAX_VALUE;
        for (int i = 0; i < NUM_COLORS; i++) {
            int c = PALETTE[i];
            int cr = (c >> 16) & 0xFF;
            int cg = (c >> 8) & 0xFF;
            int cb = c & 0xFF;
            int dr = r - cr;
            int dg = g - cg;
            int db = b - cb;
            int dist = dr * dr + dg * dg + db * db;
            if (dist < best_dist) {
                best_dist = dist;
                best_index = i;
            }
        }
        return best_index;
    }
}
