package raccoon;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Screen {

	public static int MAX_NUM_SECTORS = 1024;
	public static int MAX_NUM_WALLS = 8192;
	public static Sector[] sectors=null;
	public static int sectors_length = 0;
	public static Wall[] walls;
	public static Portal[] portals;
	public static int map_width;
	public static int map_height;
	public static String skybox = null;
	public static int sky_offset = 0;
	public static int skybox_brightness = 31;
	
	private int[] phantom_rays;
	private int phantom_hunters = 10;
	private double[] depth_buffer;
	
	
	public Screen() {
		this.depth_buffer = new double[Main.GAME_WID * Main.GAME_HEI];
		phantom_rays = new int[phantom_hunters];
	}
	
	public static int updatePlayerSector(double player_x, double player_z) {
		for (int i=0; i<sectors_length; i++) {
	        Sector sector = sectors[i];
			if (player_x >= sector.boundary_coords[0] && player_x <= sector.boundary_coords[1] && player_z >= sector.boundary_coords[2] && player_z <= sector.boundary_coords[3]) {
	            return sector.ID;
	        }
	    }
		return -1;
	}
	
	public void update(int frame_num, int[] game_pixels) {
	    Arrays.fill(phantom_rays, -1);
	    Arrays.fill(game_pixels, -1);
	    Arrays.fill(depth_buffer, Table.MAX_DOUBLE_VAL);
	    if (sectors!=null) {
	    	double player_dir = Camera.direction_rad;
	        double player_x = Camera.player_x;
	        double player_y = Camera.player_y;
	        double player_z = Camera.player_z;
	    	Camera.player_sector = updatePlayerSector(Camera.player_x, Camera.player_z);
	        CountDownLatch latch = new CountDownLatch(Main.GAME_WID);      
	        for (int x = 0; x < Main.GAME_WID; x++) {
	            final int ray_num = x;
	            Main.executor_threads.submit(() -> {
	                try {
	                	castRayAndRenderScreenColumn(ray_num, game_pixels, player_dir, player_x, player_y, player_z, Camera.player_sector);
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
	        		int next_phd = phd+1;
	        		if (phd==Table.LAST_WID_INDEX) {
	        			next_phd = 0;
	        		}
	        		for (int y=0; y < Main.GAME_HEI; y++) {
	        			int cur_column = y * Main.GAME_WID + phd;
	        			int next_column = y * Main.GAME_WID + next_phd;
	        			game_pixels[cur_column] = game_pixels[next_column];
	        			depth_buffer[cur_column] = depth_buffer[next_column];
	        		}
	        	}
	        }
	        drawSky(player_dir, ResourceManager.images.get(skybox).pixels, game_pixels);
	        drawSprites(game_pixels, player_x, player_y, player_z, player_dir);
	    }
	    RaccoonAPI.runUserScripts();
	}
	
	private void castRayAndRenderScreenColumn(int x, int[] game_pixels, double player_dir, double player_x, double player_y, double player_z, int player_sector) {
		double ray_angle = player_dir + Table.ray_offset[x];
		double tan_ray = Math.tan(ray_angle);
		double dir_theta_x = Math.signum(Math.cos(ray_angle));
		double dir_theta_z = Math.signum(Math.sin(ray_angle));
		double start_x = player_x;
		double start_z = player_z;
		
		int ray_sector = player_sector;
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
				wall_index = makeWallIndex((int)start_x, (int)start_z, 0);
				perc_wall_hit = mod_z;
			} else {
				start_x = start_x + dir_theta_x*dx_2;
				start_z = start_z + dir_theta_z*dz_2;
				wall_index = makeWallIndex((int)start_x, (int)start_z, 1);
				perc_wall_hit = mod_x;
			}
			double full_euclid_dist = euclidDist(player_x, player_z, start_x, start_z);
			if (wall_index<0) {
				phantom_rays[(int)((double)x/Main.GAME_WID*phantom_hunters)] = x;
				return;
			}
			
			double ray_angle_correct = ray_angle-player_dir;
			
			if (walls[wall_index].sector_a>0) {
				
				Sector sector_info = sectors[walls[wall_index].sector_a]; 
				int dy_walltop = Table.half_screen_height - projectColumn(start_x, sector_info.ceil_height, start_z, ray_angle_correct, player_x, player_y, player_z);
				int dy_wallbottom = Table.half_screen_height - projectColumn(start_x, sector_info.floor_height, start_z, ray_angle_correct, player_x, player_y, player_z);
				
				double cl_h = sector_info.ceil_height-player_y;
				if (!sector_info.ceil_skip_texture) {
					for (int y=0; y < dy_walltop; y++) {
						drawHorizontalTexture(x, y, cl_h, Table.half_screen_height - y, ray_angle_correct, full_euclid_dist, start_x, start_z, sector_info.ceil_texture, sector_info.ceil_brightness, sector_info.ceil_tiled, game_pixels, player_x, player_z);
					}	
				}
				
				int dy_walltop_clipped = clipColumn(dy_walltop);
				int dy_wallbottom_clipped = clipColumn(dy_wallbottom);
				if (!walls[wall_index].skip_wall_texture) {
					int column_pixel_size = dy_wallbottom-dy_walltop;
					for (int y=dy_walltop_clipped; y < dy_wallbottom_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_walltop, dy_wallbottom, walls[wall_index].wall_texture, walls[wall_index].wall_brightness, walls[wall_index].wall_tiled, column_pixel_size, full_euclid_dist, game_pixels);
					}
				}
				
				double fl_h = player_y-sector_info.floor_height;				
				if (!sector_info.floor_skip_texture) {
					for (int y=dy_wallbottom_clipped; y < Main.GAME_HEI; y++) {
						drawHorizontalTexture(x, y, fl_h, y - Table.half_screen_height, ray_angle_correct, full_euclid_dist, start_x, start_z, sector_info.floor_texture, sector_info.floor_brightness, sector_info.floor_tiled, game_pixels, player_x, player_z);
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
					dy_wall_bottom_bottom = Table.half_screen_height - projectColumn(start_x, cur_sector.floor_height, start_z, ray_angle_correct, player_x, player_y, player_z);
					dy_wall_bottom_top = Table.half_screen_height - projectColumn(start_x, next_sector.floor_height, start_z, ray_angle_correct, player_x, player_y, player_z);
				} else {
					dy_wall_bottom_bottom = Table.half_screen_height - projectColumn(start_x, cur_sector.floor_height, start_z, ray_angle_correct, player_x, player_y, player_z);
					dy_wall_bottom_top = dy_wall_bottom_bottom;
				}
				if (cur_sector.ceil_height > next_sector.ceil_height) {
					dy_wall_top_bottom = Table.half_screen_height - projectColumn(start_x, next_sector.ceil_height, start_z, ray_angle_correct, player_x, player_y, player_z);
					dy_wall_top_top = Table.half_screen_height - projectColumn(start_x, cur_sector.ceil_height, start_z, ray_angle_correct, player_x, player_y, player_z);
				} else {
					dy_wall_top_bottom = Table.half_screen_height - projectColumn(start_x, cur_sector.ceil_height, start_z, ray_angle_correct, player_x, player_y, player_z);
					dy_wall_top_top = dy_wall_top_bottom;
				}
				
				int dy_wall_top_top_clipped = clipColumn(dy_wall_top_top);
				int dy_wall_top_bottom_clipped = clipColumn(dy_wall_top_bottom);
				int dy_wall_bottom_top_clipped = clipColumn(dy_wall_bottom_top);
				int dy_wall_bottom_bottom_clipped = clipColumn(dy_wall_bottom_bottom);
				
				
				double cl_h = cur_sector.ceil_height-player_y;
				if (!cur_sector.ceil_skip_texture) {
					for (int y=0; y < dy_wall_top_top_clipped; y++) {
						drawHorizontalTexture(x, y, cl_h, Table.half_screen_height - y, ray_angle_correct, full_euclid_dist, start_x, start_z, cur_sector.ceil_texture, cur_sector.ceil_brightness, cur_sector.ceil_tiled, game_pixels, player_x, player_z);
					}
				}
				
				if (!portals[wall_index].top_skip_texture) {
					int column_pixel_size = dy_wall_top_bottom-dy_wall_top_top;
					for (int y=dy_wall_top_top_clipped; y < dy_wall_top_bottom_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_wall_top_top, dy_wall_top_bottom, portals[wall_index].top_texture, portals[wall_index].top_brightness, portals[wall_index].top_tiled, column_pixel_size, full_euclid_dist, game_pixels);
					}
				}
				
				if (!portals[wall_index].middle_skip_texture) {
					int column_pixel_size = dy_wall_bottom_top-dy_wall_top_bottom;
					for (int y=dy_wall_top_bottom_clipped; y < dy_wall_bottom_top_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_wall_top_bottom, dy_wall_bottom_top, portals[wall_index].middle_texture, portals[wall_index].middle_brightness, portals[wall_index].middle_tiled, column_pixel_size, full_euclid_dist, game_pixels);
					}
				}
				
				if (!portals[wall_index].bottom_skip_texture) {
					int column_pixel_size = dy_wall_bottom_bottom-dy_wall_bottom_top;
					for (int y=dy_wall_bottom_top_clipped; y < dy_wall_bottom_bottom_clipped; y++) {
						drawVerticalTexture(x, y, perc_wall_hit, dy_wall_bottom_top, dy_wall_bottom_bottom, portals[wall_index].bottom_texture, portals[wall_index].bottom_brightness, portals[wall_index].bottom_tiled, column_pixel_size, full_euclid_dist, game_pixels);
					}	
				}

				double fl_h = player_y-cur_sector.floor_height;
				if (!cur_sector.floor_skip_texture) {
					for (int y=dy_wall_bottom_bottom_clipped; y < Main.GAME_HEI; y++) {
						drawHorizontalTexture(x, y, fl_h, y - Table.half_screen_height, ray_angle_correct, full_euclid_dist, start_x, start_z, cur_sector.floor_texture, cur_sector.floor_brightness, cur_sector.floor_tiled, game_pixels, player_x, player_z);
					}
				}

				continue;
			}
			
		}
		
	}
	
	private int makeWallIndex(int x, int z, int isVertical) {
		if (x>=map_width || z>=map_height) {
			return -1;
		}
	    return (z * map_width + x) * 2 + isVertical;
	}
	
	private double euclidDist(double x1, double z1, double x2, double z2) {
		return Math.sqrt((z2-z1)*(z2-z1)+(x2-x1)*(x2-x1));
	}
	
	private int clipColumn(int column_n) {
		return Math.max(0, Math.min(column_n, Main.GAME_HEI));
	}
	
	private int projectColumn(double wallhit_x, double wallhit_y, double wallhit_z, double ray_angle_correct, double player_x, double player_y, double player_z) {
		return (int)(((wallhit_y-player_y)/(euclidDist(player_x, player_z, wallhit_x, wallhit_z)*Math.cos(ray_angle_correct)))*Camera.retina_dist);
	}
	
	private double reverseProject(double fl_h, int screen_y_offset, double ray_angle_correct) {
		return ((fl_h*Camera.retina_dist)/screen_y_offset) / Math.cos(ray_angle_correct);
	}
	
	private double figureOutTile(double full_euclid_distance, double full_euclid_minus_perp_dist, double wallhit_x, double player_x) {
		return wallhit_x+(full_euclid_minus_perp_dist * (player_x - wallhit_x) / full_euclid_distance);
	}
	
	private int getTextureTileColor(double tilehit_x, double tilehit_z, String horizontal_texture, int horizontal_brightness) {
	    double local_x = tilehit_x - Math.floor(tilehit_x);
	    double local_z = tilehit_z - Math.floor(tilehit_z);
	    Texture texture_horizontal_obj = ResourceManager.images.get(horizontal_texture); 
	    int u = (int)(local_x * texture_horizontal_obj.IMG_WID);
	    int v = (int)(local_z * texture_horizontal_obj.IMG_HEI);
	    int texture_color = texture_horizontal_obj.pixels[v * texture_horizontal_obj.IMG_WID + u];
	    if (texture_color>=0) {
	    	return Table.SHADE_TABLE[horizontal_brightness][texture_color];
	    }
	    return -1;
	}
	
	private void drawHorizontalTexture(int x, int y, double height_offset, int screen_y_offset, double ray_angle_correct, double full_euclid_dist, double start_x, double start_z, String horizontal_texture, int horizontal_brightness, double horizontal_tiled, int[] game_pixels, double player_x, double player_z) {
		int i = y * Main.GAME_WID + x;
		if (game_pixels[i]<0) {
			double perp_dist = reverseProject(height_offset, screen_y_offset, ray_angle_correct);
			double full_euclid_minus_perp_dist = full_euclid_dist-perp_dist;
			double tile_scale = 1 + horizontal_tiled;
			double tile_x = figureOutTile(full_euclid_dist, full_euclid_minus_perp_dist, start_x, player_x) / tile_scale;
			double tile_z = figureOutTile(full_euclid_dist, full_euclid_minus_perp_dist, start_z, player_z) / tile_scale;
			depth_buffer[i] = perp_dist;
			game_pixels[i] = getTextureTileColor(tile_x, tile_z, horizontal_texture, horizontal_brightness);
		}	
	}
	
	private void drawVerticalTexture(int x, int y, double perc_wall_hit, int dy_walltop, int dy_wallbottom, String wall_texture, int wall_brightness, double wall_tiled, int wall_column_pixel_size, double full_euclid_dist, int[] game_pixels) {
		int i = y * Main.GAME_WID + x;
		if (game_pixels[i]<0) {
			Texture texture_wall_obj = ResourceManager.images.get(wall_texture);
			int u = (int)(perc_wall_hit*texture_wall_obj.IMG_WID);
			double perc_vert = ((double)(y - dy_walltop) / wall_column_pixel_size) * (1 + wall_tiled);
			int v = (int)((perc_vert - Math.floor(perc_vert)) * texture_wall_obj.IMG_HEI);
			int texture_color = texture_wall_obj.pixels[v * texture_wall_obj.IMG_WID + u];
			if (texture_color>=0) {
		    	depth_buffer[i] = full_euclid_dist;
		    	game_pixels[i] = Table.SHADE_TABLE[wall_brightness][texture_color];
		    }
		}
	}
	
	private void drawSky(double dir, int[] skybox_picture, int[] game_pixels) {
		if (skybox!=null) {
			int skybox_width = Table.SKYBOX_WID;
			int offset_x = ((int)((dir / Table.pi2) * skybox_width)+sky_offset) % skybox_width;
			
			for (int y = 0; y < Main.GAME_HEI; y++) {
				for (int x = 0; x < Main.GAME_WID; x++) {
					int i = y * Main.GAME_WID + x;
					if (game_pixels[i]<0) {
						int sky_x = (x - offset_x + skybox_width) % skybox_width;
						int sky_index = Table.sky_y[y] * skybox_width + sky_x;
						game_pixels[i] = Table.SHADE_TABLE[skybox_brightness][skybox_picture[sky_index]];
						depth_buffer[i] = Table.MAX_DOUBLE_VAL;
					}
				}
			}
		} else {
			for (int y = 0; y < Main.GAME_HEI; y++) {
				for (int x = 0; x < Main.GAME_WID; x++) { 
					int i = y * Main.GAME_WID + x;
					if (game_pixels[i]<0) {
						game_pixels[i] = 0;
						depth_buffer[i] = Table.MAX_DOUBLE_VAL;
					}
				}
			}
		}
	}
	
	private void drawSprites(int[] game_pixels, double player_x, double player_y, double player_z, double player_dir) {
		for (Map.Entry<String, Sprite> entry : ResourceManager.sprites.entrySet()) {
			Sprite entity = entry.getValue();
			double vectorx = entity.sprite_x_pos - player_x;
			double vectory = entity.sprite_y_pos - player_y;
			double vectorz = entity.sprite_z_pos - player_z;
			double cam_x = (double)(vectorx * Math.cos(-player_dir) - vectorz * Math.sin(-player_dir));
			if (cam_x <= 0) {
				continue;
			}
			double cam_z = (double)(vectorx * Math.sin(-player_dir) + vectorz * Math.cos(-player_dir));
			double screen_offset_x = (cam_z / cam_x) * Camera.retina_dist;
			double screen_offset_y = (vectory / cam_x) * Camera.retina_dist;
			int screen_sprite_x = Table.half_screen_width - (int)(screen_offset_x);
			int screen_sprite_y = Table.half_screen_height - (int)(screen_offset_y);
			int screen_sprite_length = (int)(entity.sprite_length / cam_x);
			int half = screen_sprite_length / 2;
			int startx = screen_sprite_x - half;
			int endx = screen_sprite_x + half;
			int starty = screen_sprite_y - half;
			int endy = screen_sprite_y + half;
			int start_x_og = startx;
			int end_x_og = endx;
			int start_y_og = starty;
			int end_y_og = endy;
			startx = Math.max(0, startx);
			starty = Math.max(0, starty);
			endx = Math.min(Main.GAME_WID - 1, endx);
			endy = Math.min(Main.GAME_HEI - 1, endy);
			for (int y = starty; y <= endy; y++) {
				for (int x = startx; x <= endx; x++) {
					int i = y * Main.GAME_WID + x;
					if (depth_buffer[i]>cam_x) {
						double spritex = (double)(x - start_x_og) / (end_x_og - start_x_og);
						double spritey = (double)(y - start_y_og) / (end_y_og - start_y_og);
			            int color = getTextureSpriteColor(spritex, spritey, entity, player_x, player_z);
			            if (color>=0) {
			            	depth_buffer[i] = cam_x;
			            	game_pixels[i] = Table.SHADE_TABLE[entity.sprite_brightness][color];
			            }
					}
				}
			}
        }
	}
	
	private int getTextureSpriteColor(double spritex, double spritey, Sprite entity, double player_x, double player_z) {
		double local_x = spritex - Math.floor(spritex);
	    double local_y = spritey - Math.floor(spritey);
	    Texture texture_sprite_obj = ResourceManager.images.get(entity.spritename);
	    double relative_angle = Math.atan2(player_z - entity.sprite_z_pos, player_x - entity.sprite_x_pos) - entity.direction_rad;
	    while (relative_angle < 0) relative_angle += Table.pi2;
	    while (relative_angle >= Table.pi2) relative_angle -= Table.pi2;
	    int directional_frame = (int)(relative_angle / Table.DIRECTIONAL_SLICE_ANGLE);
	    int sprite_size = texture_sprite_obj.IMG_HEI;
	    int u = (directional_frame * sprite_size) + (int)(local_x * sprite_size);
	    int v = (int)(local_y * sprite_size);
	    return texture_sprite_obj.pixels[v * texture_sprite_obj.IMG_WID + u];
	}
	
}
