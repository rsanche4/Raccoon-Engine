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
	public static int max_count = 1000;
	
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
			int mod_x = start_x % Table.world_position_base_unit;
			if (dir_theta_x>0) {
				dx_1 = Table.world_position_base_unit - mod_x;
			} else {
				dx_1 = mod_x;
			}
			dz_1 = (int) Math.round(dx_1*tan_ray);
			int dist_horizontal = dx_1 + dz_1;
			int mod_z = start_z % Table.world_position_base_unit;
			if (dir_theta_z > 0) {
				dz_2 = Table.world_position_base_unit - mod_z;
			} else {
				dz_2 = mod_z;
			}
			dx_2 = (int) (dz_2 / tan_ray);
			int dist_vertical = dx_2 + dz_2;
			
			int wall_index;
			double perc_wall_hit;
			if (dist_horizontal < dist_vertical) {
				start_x = start_x + dir_theta_x*dx_1;
				start_z = start_z + dir_theta_z*dz_1;
				wall_index = makeWallIndex(start_x, start_z, 1, map_width);
				perc_wall_hit = mod_z/Table.world_position_base_unit;
			} else {
				start_x = start_x + dir_theta_x*dx_2;
				start_z = start_z + dir_theta_z*dz_2;
				wall_index = makeWallIndex(start_x, start_z, 0, map_width);
				perc_wall_hit = mod_x/Table.world_position_base_unit;
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
						drawVerticalTexture(x, y, perc_wall_hit, dy_walltop, dy_wallbottom, walls[wall_index].wall_texture, walls[wall_index].wall_brightness, walls[wall_index].wall_tiled, column_pixel_size, full_euclid_dist);
					}
				}
				
				int fl_h = Camera.player_y-sector_info.floor_height;				
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
				
				
				int cl_h = cur_sector.ceil_height-Camera.player_y;
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

				int fl_h = Camera.player_y-cur_sector.floor_height;
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
	
	private int euclidDist(int x1, int z1, int x2, int z2) {
		return (int) Math.sqrt((z2-z1)*(z2-z1)+(x2-x1)*(x2-x1));
	}
	
	private int clipColumn(int column_n) {
		
	}
	
	private int projectColumn(int wallhit_x, int wallhit_y, int wallhit_z, int angle_index) {
		
	}
	
	private void drawHorizontalTexture(int x, int y, int height_offset, int screen_y_offset, int angle_index, int full_euclid_dist, int start_x, int start_z, String horizontal_texture, int horizontal_brightness, int horizontal_tiled) {
		
	}
	
	private void drawVerticalTexture(int x, int y, double perc_wall_hit, int dy_walltop, int dy_wallbottom, String wall_texture, int wall_brightness, int wall_tiled, int wall_column_pixel_size, float full_euclid_dist) {
		
	}
	
	private void drawSky(int dir, int[] skybox_picture) {
		
	}
	
	private void drawSprites() {
		
	}
	
	private void upRes() {
		for (int y = 0; y < Table.render_h; y++) {
			for (int x = 0; x < Table.render_w; x++) {
				pixels[Table.screen_y[y] + x] = game_pixels[Table.src_offset[y] + Table.src_x[x]];
			}
		}
	}
}
