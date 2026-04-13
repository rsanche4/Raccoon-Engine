package raccoon;

public class Table {

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
	public static double scale = Math.min(Main.USER_SCREEN_SIZE_W / Main.GAME_WID, Main.USER_SCREEN_SIZE_H / Main.GAME_HEI);
	public static int render_w = (int) (Main.GAME_WID * scale);
	public static int render_h = (int) (Main.GAME_HEI * scale);
	public static int render_start_x = (Main.USER_SCREEN_SIZE_W - render_w) >> 1;
	public static int render_start_y = (Main.USER_SCREEN_SIZE_H - render_h) >> 1;
	public static double inv_scale = 1 / scale;
	public static int[] src_y = new int[render_h];
	public static int[] screen_y = new int[render_h];
	public static int[] src_offset = new int[render_h];
	public static int[] src_x = new int[render_w];
	
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
			screen_y[y] = (render_start_y + y) * Main.USER_SCREEN_SIZE_W + render_start_x;
			src_offset[y] = src_y[y] * Main.GAME_WID;
		}
    	
    	for (int x = 0; x < render_w; x++) {
    		src_x[x] = (int) (x * inv_scale);
    	}
    	
    }
}
