package raccoon;

public class Table {

	public static int ANGLE_COUNT = 65536;
	public static double pi = 3.141592654;
	public static double pi2 = 2*pi;
	public static int world_position_base_unit = 1000;
	public static double[] screen_x;
	public static double[] ray_offset;
	public static double[] all_angles;
	public static double[] tans;
	public static double[] sins;
	public static double[] coss;
	public static int[] signs_sins;
	public static int[] signs_coss;
	public static int half_screen_height = Main.GAME_HEI/2;
	
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
    	
    	
    	
    }
}
