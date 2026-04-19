package raccoon;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class Screen {

	public static double fog_start = 5;
	public static double fog_end = 25;
	public static Sector[] sectors=null;
	public static Wall[] walls;
	public static Portal[] portals;
	public static int map_width;
	public static int map_height;
	public static String skybox = "default_sky.png";
	
	private int[] phantom_rays;
	private int phantom_hunters = 10;
	private double[] depth_buffer;
	
	public Screen() {
		this.depth_buffer = new double[Main.GAME_WID * Main.GAME_HEI];
		phantom_rays = new int[phantom_hunters];
	}
	
	public static int updatePlayerSector(double player_x, double player_z) {
		for (int i=1; i<=sectors.length; i++) {
	        Sector sector = sectors[i];
			if (player_x >= sector.boundary_coords[0] && player_x <= sector.boundary_coords[1] && player_z >= sector.boundary_coords[2] && player_z <= sector.boundary_coords[3]) {
	            return sector.ID;
	        }
	    }
		return -1;
	}
	
	public int[] update(int frame_num, int[] game_pixels) {
	    Arrays.fill(phantom_rays, -1);
	    Arrays.fill(game_pixels, 0);
	    Arrays.fill(depth_buffer, fog_end);
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
	        for (int ph = 0; ph < phantom_hunters; ph++) {
	        	int phd = phantom_rays[ph];
	        	if (phd>=0) {
	        		for (int y=0; y < Main.GAME_HEI; y++) {
	        			int cur_column = y * Main.GAME_WID + phd;
	        			int next_column = y * Main.GAME_WID + ((phd+1)%Main.GAME_WID);
	        			game_pixels[cur_column] = game_pixels[next_column];
	        			depth_buffer[cur_column] = depth_buffer[next_column];
	        		}
	        	}
	        }
	        drawSky(Camera.direction_rad, ResourceManager.textures.get(skybox).pixels);
	        drawSprites();
	    }
	    RaccoonAPI.runUserScripts();
	    return game_pixels;
	}
	
	private void castRayAndRenderScreenColumn(int x) {
		double ray_angle = (Camera.direction_rad + Table.ray_offset[x]) % Table.pi2;
		int angle_index = (int) ((ray_angle/Table.pi2)*Table.ANGLE_COUNT);
		double tan_ray = Table.tans[angle_index];
		int dir_theta_x = Table.signs_coss[angle_index];
		int dir_theta_z = Table.signs_sins[angle_index];
		double start_x = Camera.player_x;
		double start_z = Camera.player_z;
		
		int ray_sector = Camera.player_sector;
		int dy_wall_bottom_bottom;
		int dy_wall_bottom_top;
		int dy_wall_top_bottom;
		int dy_wall_top_top;
		
		while (true) {
			double dx_1;
			double dz_1;
			double dx_2;
			double dz_2;
			double mod_x = start_x % 1;
			if (dir_theta_x>0) {
				dx_1 = 1 - mod_x;
			} else {
				dx_1 = mod_x;
			}
			dz_1 = dx_1*tan_ray;
			double dist_horizontal = dx_1 + dz_1;
			double mod_z = start_z % 1;
			if (dir_theta_z > 0) {
				dz_2 = 1 - mod_z;
			} else {
				dz_2 = mod_z;
			}
			dx_2 = dz_2 / tan_ray;
			double dist_vertical = dx_2 + dz_2;
			
			int wall_index;
			double perc_wall_hit;
			if (dist_horizontal < dist_vertical) {
				start_x = start_x + dir_theta_x*dx_1;
				start_z = start_z + dir_theta_z*dz_1;
				wall_index = makeWallIndex((int)start_x, (int)start_z, 1, map_width);
				perc_wall_hit = mod_z/1;
			} else {
				start_x = start_x + dir_theta_x*dx_2;
				start_z = start_z + dir_theta_z*dz_2;
				wall_index = makeWallIndex((int)start_x, (int)start_z, 0, map_width);
				perc_wall_hit = mod_x/1;
			}
			double full_euclid_dist = euclidDist(Camera.player_x, Camera.player_z, start_x, start_z);
			if (wall_index>=walls.length && wall_index>=portals.length) {
				phantom_rays[(int)((double)x/Main.GAME_WID*phantom_hunters)] = x;
				return;
			}
			
			if (walls[wall_index].sector_a>0) {
				
				Sector sector_info = sectors[walls[wall_index].sector_a]; 
				int dy_walltop = Table.half_screen_height - projectColumn(start_x, sector_info.ceil_height, start_z, angle_index);
				int dy_wallbottom = Table.half_screen_height - projectColumn(start_x, sector_info.floor_height, start_z, angle_index);
				
				double cl_h = sector_info.ceil_height-Camera.player_y;
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
						drawVerticalTexture(x, y, perc_wall_hit, dy_walltop, dy_wallbottom, walls[wall_index].wall_texture, walls[wall_index].wall_brightness, walls[wall_index].wall_tiled, column_pixel_size, full_euclid_dist);
					}
				}
				
				double fl_h = Camera.player_y-sector_info.floor_height;				
				if (!sector_info.floor_skip_texture) {
					for (int y=dy_wallbottom_clipped; y < Main.GAME_HEI; y++) {
						drawHorizontalTexture(x, y, fl_h, y - Table.half_screen_height, angle_index, full_euclid_dist, start_x, start_z, sector_info.floor_texture, sector_info.floor_brightness, sector_info.floor_tiled);
					}
				}
				
				return;
			}
			
			if (portals[wall_index].sector_a>0) {
				
				int prev_ray_sector = ray_sector;
				if (ray_sector==portals[wall_index].sector_a) {
					ray_sector = portals[wall_index].sector_b;
				} else {
					ray_sector = portals[wall_index].sector_a;
				}
				Sector cur_sector = sectors[prev_ray_sector];
				Sector next_sector = sectors[ray_sector];

				if (cur_sector.floor_height < next_sector.floor_height) {
					dy_wall_bottom_bottom = Table.half_screen_height - projectColumn(start_x, cur_sector.floor_height, start_z, angle_index);
					dy_wall_bottom_top = Table.half_screen_height - projectColumn(start_x, next_sector.floor_height, start_z, angle_index);
				} else {
					dy_wall_bottom_bottom = Table.half_screen_height - projectColumn(start_x, cur_sector.floor_height, start_z, angle_index);
					dy_wall_bottom_top = dy_wall_bottom_bottom;
				}
				if (cur_sector.ceil_height > next_sector.ceil_height) {
					dy_wall_top_bottom = Table.half_screen_height - projectColumn(start_x, next_sector.ceil_height, start_z, angle_index);
					dy_wall_top_top = Table.half_screen_height - projectColumn(start_x, cur_sector.ceil_height, start_z, angle_index);
				} else {
					dy_wall_top_bottom = Table.half_screen_height - projectColumn(start_x, cur_sector.ceil_height, start_z, angle_index);
					dy_wall_top_top = dy_wall_top_bottom;
				}
				
				int dy_wall_top_top_clipped = clipColumn(dy_wall_top_top);
				int dy_wall_top_bottom_clipped = clipColumn(dy_wall_top_bottom);
				int dy_wall_bottom_top_clipped = clipColumn(dy_wall_bottom_top);
				int dy_wall_bottom_bottom_clipped = clipColumn(dy_wall_bottom_bottom);
				
				
				double cl_h = cur_sector.ceil_height-Camera.player_y;
				if (!cur_sector.ceil_skip_texture) {
					for (int y=0; y < dy_wall_top_top_clipped; y++) {
						drawHorizontalTexture(x, y, cl_h, Table.half_screen_height - y, angle_index, full_euclid_dist, start_x, start_z, cur_sector.ceil_texture, cur_sector.ceil_brightness, cur_sector.ceil_tiled);
					}
				}
				
				if (!portals[wall_index].top_skip_texture) {
					int column_pixel_size = dy_wall_top_bottom-dy_wall_top_top;
					for (int y=dy_wall_top_top_clipped; y < dy_wall_top_bottom_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_wall_top_top, dy_wall_top_bottom, portals[wall_index].top_texture, portals[wall_index].top_brightness, portals[wall_index].top_tiled, column_pixel_size, full_euclid_dist);
					}
				}
				
				if (!portals[wall_index].middle_skip_texture) {
					int column_pixel_size = dy_wall_bottom_top-dy_wall_top_bottom;
					for (int y=dy_wall_top_bottom_clipped; y < dy_wall_bottom_top_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_wall_top_bottom, dy_wall_bottom_top, portals[wall_index].middle_texture, portals[wall_index].middle_brightness, portals[wall_index].middle_tiled, column_pixel_size, full_euclid_dist);
					}
				}
				
				if (!portals[wall_index].bottom_skip_texture) {
					int column_pixel_size = dy_wall_bottom_bottom-dy_wall_bottom_top;
					for (int y=dy_wall_bottom_top_clipped; y < dy_wall_bottom_bottom_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_wall_bottom_top, dy_wall_bottom_bottom, portals[wall_index].bottom_texture, portals[wall_index].bottom_brightness, portals[wall_index].bottom_tiled, column_pixel_size, full_euclid_dist);
					}	
				}

				double fl_h = Camera.player_y-cur_sector.floor_height;
				if (!cur_sector.floor_skip_texture) {
					for (int y=dy_wall_bottom_bottom_clipped; y < Main.GAME_HEI; y++) {
						drawHorizontalTexture(x, y, fl_h, y - Table.half_screen_height, angle_index, full_euclid_dist, start_x, start_z, cur_sector.floor_texture, cur_sector.floor_brightness, cur_sector.floor_tiled);
					}
				}

				continue;
			}
			
		}
		
	}
	
	private int makeWallIndex(int x, int z, int isHorizontal, int map_width) {
	    int pointIndex = z * map_width + x;
	    return pointIndex * 2 + isHorizontal;
	}
	
	private double euclidDist(double x1, double z1, double x2, double z2) {
		return Math.sqrt((z2-z1)*(z2-z1)+(x2-x1)*(x2-x1));
	}
	
	private int clipColumn(int column_n) {
		
	}
	
	private int projectColumn(double wallhit_x, double wallhit_y, double wallhit_z, int angle_index) {
		
	}
	
	private void drawHorizontalTexture(int x, int y, double height_offset, int screen_y_offset, int angle_index, double full_euclid_dist, double start_x, double start_z, String horizontal_texture, int horizontal_brightness, int horizontal_tiled) {
		
	}
	
	private void drawVerticalTexture(int x, int y, double perc_wall_hit, int dy_walltop, int dy_wallbottom, String wall_texture, int wall_brightness, int wall_tiled, int wall_column_pixel_size, double full_euclid_dist) {
		
	}
	
	private void drawSky(double dir, int[] skybox_picture) {
		
	}
	
	private void drawSprites() {
		
	}
	
}
