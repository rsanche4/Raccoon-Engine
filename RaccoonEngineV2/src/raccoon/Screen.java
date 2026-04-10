package raccoon;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class Screen {

	public static int fog_start = 5;
	public static int fog_end = 25;
	public static Sector[] sectors=null;
	public static Wall[] walls;
	public static Portal[] portals;
	public static int map_width;
	public static int map_height;
	public static String skybox = "default_sky.png";
	public static int max_count = 100;
	
	private int[] pixels;
	private int[] game_pixels;
	private int[] depth_buffer;
	private int[] ZEROS;
	private int[] MAX_DEPTHS;
	
	public Screen(int[] pixels, int[] game_pixels) {
		this.pixels = pixels;
		this.game_pixels = game_pixels;
		this.depth_buffer = new int[game_pixels.length];
		ZEROS = new int[game_pixels.length];
		MAX_DEPTHS = new int[depth_buffer.length];
		Arrays.fill(ZEROS, 0x000000);
		Arrays.fill(MAX_DEPTHS, fog_end);
	}
	
	public static int updatePlayerSector(int player_x, int player_z) {
		for (int i=1; i<=sectors.length; i++) {
	        Sector sector = sectors[i];
			if (player_x >= sector.boundary_coords[0] && player_x <= sector.boundary_coords[1] && player_z >= sector.boundary_coords[2] && player_z <= sector.boundary_coords[3]) {
	            return sector.ID;
	        }
	    }
		return -1;
	}
	
	public void update(int frame_num) {
		System.arraycopy(ZEROS, 0, game_pixels, 0, game_pixels.length);
	    System.arraycopy(MAX_DEPTHS, 0, depth_buffer, 0, depth_buffer.length);
	    if (sectors!=null) {
	        Camera.player_sector = updatePlayerSector(Camera.player_x, Camera.player_z);
	        CountDownLatch latch = new CountDownLatch(Main.GAME_WID);      
	        for (int x = 0; x < Main.GAME_WID; x++) {
	            final int ray_num = x;
	            Main.executor_threads.submit(() -> {
	                try {
	                	castRayAndRenderScreenColumn(ray_num);
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
	        drawSky(Camera.direction_value, Main.textures.get(skybox).pixels);
	        drawSprites();
	    }
	    RaccoonAPI.runUserScripts();
	    upRes();
	}
	
	private void castRayAndRenderScreenColumn(int x) {
		double ray_angle = (Table.all_angles[Camera.direction_value] + Table.ray_offset[x]) % Table.pi2;
		int angle_index = (int) ((ray_angle/Table.pi2)*Table.ANGLE_COUNT);
		double tan_ray = Table.tans[angle_index];
		int dir_theta_x = Table.signs_coss[angle_index];
		int dir_theta_z = Table.signs_sins[angle_index];
		int start_x = Camera.player_x;
		int start_z = Camera.player_z;
		
		int counter = 0;
		int ray_sector = Camera.player_sector;
		int dy_wall_bottom_bottom;
		int dy_wall_bottom_top;
		int dy_wall_top_bottom;
		int dy_wall_top_top;
		
		while (counter<max_count) {
			counter++;
			int dx_1;
			int dz_1;
			int dx_2;
			int dz_2;
			int mod = start_x % Table.world_position_base_unit;
			if (dir_theta_x>0) {
				dx_1 = Table.world_position_base_unit - mod;
			} else {
				dx_1 = mod;
			}
			dz_1 = (int) Math.round(dx_1*tan_ray);
			int dist_horizontal = dx_1 + dz_1;
			mod = start_z % Table.world_position_base_unit;
			if (dir_theta_z > 0) {
				dz_2 = Table.world_position_base_unit - mod;
			} else {
				dz_2 = mod;
			}
			dx_2 = (int) (dz_2 / tan_ray);
			int dist_vertical = dx_2 + dz_2;
			
			int wall_index;
			double perc_wall_hit; // TODO Over here we need to calculate this one
			if (dist_horizontal < dist_vertical) {
				start_x = start_x + dir_theta_x*dx_1;
				start_z = start_z + dir_theta_z*dz_1;
				wall_index = makeWallIndex(start_x, start_z, 1, map_width);
				
			} else {
				start_x = start_x + dir_theta_x*dx_2;
				start_z = start_z + dir_theta_z*dz_2;
				wall_index = makeWallIndex(start_x, start_z, 0, map_width);
			}
			int full_euclid_dist = euclidDist(Camera.player_x, Camera.player_z, start_x, start_z);
			if (wall_index>=walls.length && wall_index>=portals.length) {
				// TODO PHANTOM RAY
				return;
			}
			
			if (walls[wall_index].sector_a>0) {
				
				Sector sector_info = sectors[walls[wall_index].sector_a]; 
				int dy_walltop = Table.half_screen_height - projectColumn(start_x, sector_info.ceil_height, start_z, angle_index);
				int dy_wallbottom = Table.half_screen_height - projectColumn(start_x, sector_info.floor_height, start_z, angle_index);
				
				int fl_h = Camera.player_y-sector_info.floor_height;
				int cl_h = sector_info.ceil_height-Camera.player_y;
				if (!sector_info.ceil_skip_texture) {
					for (int y=0; y < dy_walltop; y++) {
						drawHorizontalTexture(x, y, cl_h, Table.half_screen_height - y, angle_index, full_euclid_dist, start_x, start_z, sector_info.ceil_texture, sector_info.ceil_brightness, sector_info.ceil_tiled);
					}	
				}
				
				int dy_walltop_clipped = clipColumn(dy_walltop);
				int dy_wallbottom_clipped = clipColumn(dy_wallbottom);
				if (!walls[wall_index].skip_wall_texture) {
					int column_pixel_size = dy_wallbottom-dy_walltop;
					for (int y=dy_walltop_clipped; y < dy_wallbottom_clipped; y++) {
						
						draw_wall_texture(x, y, decimal_value_wall_hit this is the percentage for me, dy_walltop, dy_wallbottom, walls[wall_index].wall_texture, walls[wall_index].wall_brightness, walls[wall_index].wall_tiled, column_pixel_size, full_euclid_dist);
					}
				}
				
				
				for (int y=dy_wallbottom_clipped; y < Main.game_height; y++) {
					draw_plane_texture(x, y, fl_h, y - half_screen_height, ray_angle, full_euclid_dist, startx, startz, sector_info.floorTexture, sector_info.floorBrightness);
				}
				
				
				
				return;
			}
			
			if (portals[wall_index].sector_a>0) {
				
				return;
			}
			
		}
		
	}
	
	private int makeWallIndex(int x, int z, int isHorizontal, int map_width) {
	    int pointIndex = z * map_width + x;
	    return pointIndex * 2 + isHorizontal;
	}
	
	private int euclidDist(int x1, int z1, int x2, int z2) {
		return (int) Math.sqrt((z2-z1)*(z2-z1)+(x2-x1)*(x2-x1));
	}
	
	private int clipColumn(int column_n) {
		
	}
	
	private int projectColumn(int wallhit_x, int wallhit_y, int wallhit_z, int angle_index) {
		// clip the stuff from here
	}
	
	private void drawHorizontalTexture(int x, int y, int height_offset, int screen_y_offset, int angle_index, int full_euclid_dist, int start_x, int start_z, String horizontal_texture, int horizontal_brightness, boolean horizontal_tiled) {
		
	}
	
	private void drawSky(int dir, int[] skybox_picture) {
		
	}
	
	private void drawSprites() {
		
	}
	
	private void upRes() {
		
	}
}
