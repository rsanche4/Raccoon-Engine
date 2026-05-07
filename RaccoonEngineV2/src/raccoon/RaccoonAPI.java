package raccoon;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

public class RaccoonAPI {

	private static final RaccoonAPI api_instance = new RaccoonAPI();
    private static HashMap<String, Object> user_variables = new HashMap<>();
    public static boolean debug_console = true;
    private String system_font = "system_font.ttf";
    private static final int CONSOLE_MAX_LINES = 20;
    private static final ArrayDeque<String> console_lines = new ArrayDeque<>();
    private static int[] api_game_pixels;
    
	public static void runUserScripts(int[] game_pixels) {
		api_game_pixels = game_pixels;
		ResourceManager.active_scripts.sort(Comparator.comparingInt(e -> e.priority));
		
		for (int i = 0; i < ResourceManager.active_scripts.size(); i++) {
			try {
                Globals globals = JsePlatform.standardGlobals();
                globals.set("RA", CoerceJavaToLua.coerce(api_instance));
                LuaValue chunk = globals.load(ResourceManager.level_data.get(ResourceManager.active_scripts.get(i).script_name), "");
                chunk.call(LuaValue.valueOf(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
		}
		
        for (Map.Entry<String, Sprite> entry : ResourceManager.sprites.entrySet()) {
            try {
            	Globals globals = JsePlatform.standardGlobals();
            	globals.set("RA", CoerceJavaToLua.coerce(api_instance));
            	Sprite entity = entry.getValue();
            	LuaValue chunk = globals.load(ResourceManager.level_data.get(entity.behavior_script), "");  
            	Varargs va = LuaValue.varargsOf(new LuaValue[] {
            			LuaValue.valueOf(entity.sprite_x_pos),
            			LuaValue.valueOf(entity.sprite_y_pos),
            			LuaValue.valueOf(entity.sprite_z_pos),
            			LuaValue.valueOf(entity.sprite_length),
            			LuaValue.valueOf(entity.sprite_brightness),
            			LuaValue.valueOf(entity.spritename),
            			LuaValue.valueOf(entity.ID),
            			LuaValue.valueOf(entity.collision_radius),
            			LuaValue.valueOf(entity.direction_rad)
            	});
            	chunk.invoke(va);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
	
	public void systemDebug(boolean is_debug) {
		debug_console = is_debug;
	}
	
	public void systemLog(String msg, String system_call) { 
		if (debug_console) {
			String time = systemWorldTime();
			String output = "[" + time + "] " + system_call + ": " + msg;
			System.out.println(output);
			systemConsole(output);
		}
    }
	
	private void systemConsole(String msg) {
		console_lines.addLast(msg);
	    if (console_lines.size() > CONSOLE_MAX_LINES) {
	        console_lines.removeFirst();
	    }
	}
	
	public static void systemDrawConsole() {
	    if (!debug_console) return;
	    int line_height = 14;
	    int x_start = 4;
	    int y = 4;
	    for (String line : console_lines) {
	    	systemDrawConsoleString(line, x_start, y, api_game_pixels);
	        y += line_height;
	    }
	}

	private static void systemDrawConsoleString(String text, int x, int y, int[] game_pixels) {
	    int cursor_x = x;
	    for (char c : text.toCharArray()) {
	        String key = c + "_" + api_instance.system_font;
	        Texture glyph = ResourceManager.fonts.get(key);
	        if (glyph == null) continue;
	        for (int gy = 0; gy < glyph.IMG_HEI; gy++) {
	            for (int gx = 0; gx < glyph.IMG_WID; gx++) {
	                int screen_x = cursor_x + gx;
	                int screen_y = y + gy;
	                if (screen_x < 0 || screen_x >= Main.GAME_WID) continue;
	                if (screen_y < 0 || screen_y >= Main.GAME_HEI) continue;
	                int color = glyph.pixels[gy * glyph.IMG_WID + gx];
	                if (color >= 0) {
	                    game_pixels[screen_y * Main.GAME_WID + screen_x] = color;
	                }
	            }
	        }
	        cursor_x += glyph.IMG_WID + 1;
	    }
	}
	
	public String systemWorldTime() {
		LocalDateTime now = LocalDateTime.now();
	    return String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano() / 1000000);
	}
	
	public int systemStartTime() {
		 return (int)((System.currentTimeMillis() - Main.start_time) / 1000);
	}
	
	public int systemGetMaxFPS() {
    	return Main.MAX_FPS;
    }
	
	public int systemGetFrameNumber() {
    	return Main.frame_num;
    }
	
	public void systemQuit() {
		System.exit(0);
    }
	
	public void scriptAdd(String script_name, int priority) {
		systemLog("Adding " + script_name + " to active scripts.", "scriptAdd");
		ResourceManager.active_scripts.add(new Event(script_name, priority));
	}
	
	public void scriptEnd(int script_index) {
		systemLog("Removing script #" + script_index + ".", "scriptEnd");
		ResourceManager.active_scripts.remove(script_index);
	}
	
	public void scriptEndByName(String script_name) {
		systemLog("Removing script " + script_name + ".", "scriptEndByName");
		for (int i = 0; i < ResourceManager.active_scripts.size(); i++) {
			if (ResourceManager.active_scripts.get(i).script_name.contentEquals(script_name)) {
				scriptEnd(i);
				return;
			}
		}
	}
	
	public String audioPlayBGM(String bgm_name, boolean loop, float volume) {
		return "Implement me!";
	}
    
    public String audioChangeBGMVol(float volume) {
    	return "Implement me!";
    }
	
	public String audioStopBGM() {
		return "Implement me!";
	}
	
	public String audioPlaySE(String se_name, boolean loop, float volume) {
		return "Implement me!";
	}
	
	public String audioChangeSEVol(String se_name, float volume) {
		return "Implement me!";
	}
	
	public String audioStopSE(String se_name) {
		return "Implement me!";
	}
	
	public void worldSetSkybox(String skyboxname, int brightness) {
    	systemLog("Setting skybox to " + skyboxname + ".", "worldSetSkybox");
		Screen.skybox = skyboxname;
    	Screen.skybox_brightness = brightness;
    }
	
	public void worldSetSkyboxOffset(int offset) {
		systemLog("Setting skybox offset by " + offset + ".", "worldSetSkyboxOffset");
		Screen.sky_offset = offset;
    }
	
	public int worldGetSectorCountLimit() {
		systemLog("Getting sector limit.", "worldGetSectorCountLimit");
		return Screen.MAX_NUM_SECTORS;
	}
	
	public void worldSetSectorCountLimit(int lim) {
		systemLog("Setting sector limit.", "worldSetSectorCountLimit");
		Screen.MAX_NUM_SECTORS = lim;
	}
	
	private double[] worldLoadMapHelperNormalize(double x1, double z1, double x2, double z2) {
		if (x1 > x2 || (x1 == x2 && z1 > z2)) {
            double tmpX = x1, tmpZ = z1;
            x1 = x2; z1 = z2;
            x2 = tmpX; z2 = tmpZ;
        }
		return new double[] {x1, z1, x2, z2};
	}
	
	public void worldLoadMap(String mapname) {
		systemLog("Loading map " + mapname + ".", "worldLoadMap");
		Screen.sectors = new Sector[Screen.MAX_NUM_SECTORS];
		Screen.map_width = 0;
		Screen.map_height = 0;
		Screen.sectors_count = 0;
		int selected = -1;
		try {
			systemLog("Reading level data.", "worldLoadMap");
			String maptxt = ResourceManager.level_data.get(mapname);
			if (maptxt==null) {
				systemLog("Not a map. Resetting sectors to 0.", "worldLoadMap");
				return;
			}
			String[] lines = maptxt.split("\n");
			for (String line : lines) {
				line = line.trim();
				if (line.equals("[SIZE]")) { 
                	systemLog("Reading map size and establishing verticals.", "worldLoadMap");
                	selected = 3; 
                	continue; 
                }
				if (line.equals("[SECTORS]")) { 
					systemLog("Reading sectors.", "worldLoadMap");
					selected = 0; 
					continue; 
				}
                if (line.equals("[WALLS]")) { 
                	systemLog("Reading walls.", "worldLoadMap");
                	selected = 1; 
                	continue; 
                }
                if (line.equals("[PORTALS]")) { 
                	systemLog("Reading portals and initializing collision.", "worldLoadMap");
                	Screen.portal_collision_data = new boolean[Screen.sectors_count*Screen.sectors_count];
                	selected = 2; 
                	continue; 
                }
                
                String[] parts = line.split("\\s+");
                switch (selected) {
                	case 0 -> {
                		if (Screen.sectors_count>=Screen.MAX_NUM_SECTORS) {
                			systemLog("Failed! Too many sectors. This means your map is too big. Try simplifying it with less sectors!", "worldLoadMap");
                			return;
                		}
                		int sector_id = Integer.parseInt(parts[0]);
                		double floor_height = Double.parseDouble(parts[1]);
                		double ceil_height = Double.parseDouble(parts[2]);
                		if (floor_height<0 || floor_height>Screen.LIMIT_MAP_COORD || ceil_height<0 || ceil_height>Screen.LIMIT_MAP_COORD) {
                			systemLog("Failed! Your height value is bigger than the world coordinate limit of " + Screen.LIMIT_MAP_COORD + ".", "worldLoadMap");
                			return;
                		}
                		double floor_brightness = Double.parseDouble(parts[4]);
                		double ceil_brightness = Double.parseDouble(parts[8]);
                		if (floor_brightness < 0 || floor_brightness > 1 || ceil_brightness < 0 || ceil_brightness > 1) {
                			systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
                			return;
                		}
                		int actual_floor_brightness = (int)(floor_brightness*(Table.NUM_LIGHT_LEVELS-1));
                		int actual_ceil_brightness = (int)(ceil_brightness*(Table.NUM_LIGHT_LEVELS-1));
                		Screen.sectors[sector_id] = new Sector(sector_id, floor_height, ceil_height, parts[3], actual_floor_brightness, Double.parseDouble(parts[5]), Boolean.parseBoolean(parts[6]), parts[7], actual_ceil_brightness, Double.parseDouble(parts[9]), Boolean.parseBoolean(parts[10]));
                		Screen.sectors_count++;
                	}
                	case 1 -> {
                		double[] xz = new double[4];
                		xz[0] = Double.parseDouble(parts[0]);
                		xz[1] = Double.parseDouble(parts[1]);
                		xz[2] = Double.parseDouble(parts[2]);
                		xz[3] = Double.parseDouble(parts[3]);
                		xz = worldLoadMapHelperNormalize(xz[0], xz[1], xz[2], xz[3]);
                		double x1 = xz[0];
                		double z1 = xz[1];
                		double x2 = xz[2];
                		double z2 = xz[3];
                		if (x1<0 || z1<0 || x2<0 || z2<0) {
                			systemLog("Failed! Your map has negative values! No bueno!", "worldLoadMap");
                			return;
                		}
                		int sector_id = Integer.parseInt(parts[4]);
                		String wall_texture = parts[5];
                		double brightness = Double.parseDouble(parts[6]);
                		if (brightness < 0 || brightness > 1) {
                			systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
                			return;
                		}
                		int actual_brightness = (int)(brightness*(Table.NUM_LIGHT_LEVELS-1));
                		double tiled = Double.parseDouble(parts[7]);
                		boolean skip_texture = Boolean.parseBoolean(parts[8]);
                		int is_vertical;
                		if (z1 == z2) {
                			is_vertical = 1;
                			Screen.sectors[sector_id].updateSectorBoundary(z1, is_vertical);
                			int start_x = (int) Math.floor(x1);
                            int end_x = (int) Math.floor(x2);
                            for (int x = start_x; x < end_x; x++) {
                                int key = Screen.makeWallIndex(x, (int)z1, is_vertical);
                                Screen.verticals[key] = new Wall(x, z1, x2, z2, sector_id, wall_texture, actual_brightness, tiled, skip_texture);
                            }
                        } else if (x1 == x2) {
                        	is_vertical = 0;
                        	Screen.sectors[sector_id].updateSectorBoundary(x1, is_vertical);
                            int start_z = (int) Math.floor(z1);
                            int end_z = (int) Math.floor(z2);
                            for (int z = start_z; z < end_z; z++) {
                            	int key = Screen.makeWallIndex((int)x1, z, is_vertical);
                                Screen.verticals[key] = new Wall(x1, z, x2, z2, sector_id, wall_texture, actual_brightness, tiled, skip_texture);
                            }
                        }
                	}
                	case 2 -> {
                		double[] xz = new double[4];
                		xz[0] = Double.parseDouble(parts[0]);
                		xz[1] = Double.parseDouble(parts[1]);
                		xz[2] = Double.parseDouble(parts[2]);
                		xz[3] = Double.parseDouble(parts[3]);
                		xz = worldLoadMapHelperNormalize(xz[0], xz[1], xz[2], xz[3]);
                		double x1 = xz[0];
                		double z1 = xz[1];
                		double x2 = xz[2];
                		double z2 = xz[3];
                		if (x1<0 || z1<0 || x2<0 || z2<0) {
                			systemLog("Failed! Your map has negative values! No bueno!", "worldLoadMap");
                			return;
                		}
                		int sector_a = Integer.parseInt(parts[4]);
                		int sector_b = Integer.parseInt(parts[5]);
                		String bottom_tex = parts[6];
                		double bottom_brightness = Double.parseDouble(parts[7]);
                		if (bottom_brightness < 0 || bottom_brightness > 1) {
                			systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
                			return;
                		}
                		int actual_bottom_brightness = (int)(bottom_brightness*(Table.NUM_LIGHT_LEVELS-1));
                		double bottom_tiled = Double.parseDouble(parts[8]);
                		boolean bottom_skip_texture = Boolean.parseBoolean(parts[9]);
                		String middle_tex = parts[10];
                		double middle_brightness = Double.parseDouble(parts[11]);
                		if (middle_brightness < 0 || middle_brightness > 1) {
                			systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
                			return;
                		}
                		int actual_middle_brightness = (int)(middle_brightness*(Table.NUM_LIGHT_LEVELS-1));
                		double middle_tiled = Double.parseDouble(parts[12]);
                		boolean middle_skip_texture = Boolean.parseBoolean(parts[13]);
                		String top_tex = parts[14];
                		double top_brightness = Double.parseDouble(parts[15]);
                		if (top_brightness < 0 || top_brightness > 1) {
                			systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
                			return;
                		}
                		int actual_top_brightness = (int)(top_brightness*(Table.NUM_LIGHT_LEVELS-1));
                		double top_tiled = Double.parseDouble(parts[16]);
                		boolean top_skip_texture = Boolean.parseBoolean(parts[17]);
                		boolean is_solid = Boolean.parseBoolean(parts[18]);
                		Screen.portal_collision_data[sector_a * Screen.sectors_count + sector_b] = is_solid;
                		Screen.portal_collision_data[sector_b * Screen.sectors_count + sector_a] = is_solid;
                		int is_vertical;
                		if (z1 == z2) {
                			is_vertical = 1;
                        	Screen.sectors[sector_a].updateSectorBoundary(z1, is_vertical);
                        	Screen.sectors[sector_b].updateSectorBoundary(z1, is_vertical);
                            int start_x = (int) Math.floor(x1);
                            int end_x = (int) Math.floor(x2);
                            for (int x = start_x; x < end_x; x++) {
                                int key = Screen.makeWallIndex(x, (int)z1, is_vertical);
                                Screen.verticals[key] = new Portal(x, z1, x2, z2, sector_a, sector_b, bottom_tex, actual_bottom_brightness, bottom_tiled, bottom_skip_texture, middle_tex, actual_middle_brightness, middle_tiled, middle_skip_texture, top_tex, actual_top_brightness, top_tiled, top_skip_texture);
                            }
                        } else if (x1 == x2) {
                        	is_vertical = 0;
                        	Screen.sectors[sector_a].updateSectorBoundary(x1, is_vertical);
                        	Screen.sectors[sector_b].updateSectorBoundary(x1, is_vertical);
                            int start_z = (int) Math.floor(z1);
                            int end_z = (int) Math.floor(z2);
                            for (int z = start_z; z < end_z; z++) {
                                int key = Screen.makeWallIndex((int)x1, z, is_vertical);
                                Screen.verticals[key] = new Portal(x1, z, x2, z2, sector_a, sector_b, bottom_tex, actual_bottom_brightness, bottom_tiled, bottom_skip_texture, middle_tex, actual_middle_brightness, middle_tiled, middle_skip_texture, top_tex, actual_top_brightness, top_tiled, top_skip_texture);
                            }
                        }
                	}
                	case 3 -> {
                		Screen.map_width = Integer.parseInt(parts[0]);
                		Screen.map_height = Integer.parseInt(parts[1]);
                		if (Screen.map_width>Screen.LIMIT_MAP_COORD || Screen.map_height>Screen.LIMIT_MAP_COORD) {
                			systemLog("Failed! Your world is bigger than the world coordinate limit of " + Screen.LIMIT_MAP_COORD + ".", "worldLoadMap");
                			return;
                		}
                		Screen.vertical_length = (Screen.map_width+1)*(Screen.map_height+1)*2;
                		Screen.verticals = new Object[Screen.vertical_length];
                	}
                }
			
			}
		} catch (Exception e) {
			systemLog("Failed to read map data. Exception " + e.getMessage(), "worldLoadMap");
		}
		
	}
	
	public void worldSetPortalCollision(int sector_a, int sector_b, boolean is_solid) {
		systemLog("Setting collision to " + is_solid + " from sector " + sector_a + " to " + sector_b + ".", "worldSetPortalCollision");
		Screen.portal_collision_data[sector_a * Screen.sectors_count + sector_b] = is_solid;
	}
	
	public void worldChangeSectorVals(boolean is_floor, int sector_id, String texture, double brightness, int tiled, boolean skip_texture) {
    	systemLog("Setting sector values.", "worldChangeSectorVals");
		if (sector_id<0 || sector_id >= Screen.sectors_count) {
			systemLog("Failed! Sector ID must be valid.", "worldChangeSectorVals");
			return;
		}
		if (brightness < 0 || brightness > 1) {
			systemLog("Failed! Invalid brightness.", "worldChangeSectorVals");
			return;
		}
    	if (is_floor) {
    		Screen.sectors[sector_id].floor_texture = texture;
    		Screen.sectors[sector_id].floor_tiled = tiled;
    		Screen.sectors[sector_id].floor_brightness = (int)(brightness*(Table.NUM_LIGHT_LEVELS-1));
    		Screen.sectors[sector_id].floor_skip_texture = skip_texture;
    	} else {
    		Screen.sectors[sector_id].ceil_texture = texture;
    		Screen.sectors[sector_id].ceil_tiled = tiled;
    		Screen.sectors[sector_id].ceil_brightness = (int)(brightness*(Table.NUM_LIGHT_LEVELS-1));
    		Screen.sectors[sector_id].ceil_skip_texture = skip_texture;
    	}
    }
	
	public void worldChangeVerticalVals(int x, int z, int is_vertical, String wall_texture, boolean skip_wall_texture, double brightness, int tiled, int portal_texture_type) {
		systemLog("Setting vertical values.", "worldChangeVerticalVals");
		int index = Screen.makeWallIndex(x, z, is_vertical);
		if (index < 0) {
			systemLog("Failed! Out of bounds index.", "worldChangeVerticalVals");
			return;
		}
		if (brightness < 0 || brightness > 1) {
			systemLog("Failed! Invalid brightness.", "worldChangeVerticalVals");
			return;
		}
		Object w = Screen.verticals[index];
		int actual_brightness = (int)(brightness*(Table.NUM_LIGHT_LEVELS-1));
		if (w instanceof Wall) {
			Wall wall = (Wall) w;
			wall.wall_texture = wall_texture;
			wall.skip_wall_texture = skip_wall_texture;
			wall.wall_brightness = actual_brightness;
			wall.wall_tiled = tiled;
		} else if (w instanceof Portal) {
			Portal portal = (Portal) w;
			if (portal_texture_type==0) {
				portal.bottom_texture = wall_texture;
				portal.bottom_skip_texture = skip_wall_texture;
				portal.bottom_brightness = actual_brightness;
				portal.bottom_tiled = tiled;
			} else if (portal_texture_type==1) {
				portal.middle_texture = wall_texture;
				portal.middle_skip_texture = skip_wall_texture;
				portal.middle_brightness = actual_brightness;
				portal.middle_tiled = tiled;
			} else if (portal_texture_type==2) {
				portal.top_texture = wall_texture;
				portal.top_skip_texture = skip_wall_texture;
				portal.top_brightness = actual_brightness;
				portal.top_tiled = tiled;
			} else {
				systemLog("Failed! Incorrect portal texture type.", "worldChangeVerticalVals");
				return;
			}
		}
    }
	
	public void entityUpsertSprite(String sprite_id, double sprite_x_pos, double sprite_y_pos, double sprite_z_pos, double sprite_length, double sprite_brightness, String spritename, String behavior_script, double collision_radius, double direction_rad) {
		systemLog("Upserting entity " + sprite_id + ".", "entityUpsertSprite");
		int actual_brightness = (int)(sprite_brightness*(Table.NUM_LIGHT_LEVELS-1));
		ResourceManager.sprites.put(sprite_id, new Sprite(sprite_id, sprite_x_pos, sprite_y_pos, sprite_z_pos, sprite_length, spritename, behavior_script, actual_brightness, collision_radius, direction_rad));
	}
		
	public void entityRemoveSprite(String sprite_id) {
		systemLog("Removing entity " + sprite_id + ".", "entityRemoveSprite");
		ResourceManager.sprites.remove(sprite_id);
	}
	
	public double playerGetPosition(int dimension_number) {
		systemLog("Getting player position at dimension " + dimension_number + ".", "playerGetPosition");
		switch (dimension_number) {
        	case 0: return Camera.player_x;
        	case 1: return Camera.player_y;
        	case 2: return Camera.player_z;
        	default: systemLog("Invalid dimension " + dimension_number, "playerGetPosition");
		}
		return -1;
	}
	
	public void playerSetPosition(double x, double y, double z, double dir) { 
    	if (x<0 || y<0 || z<0 || dir<0 || dir>=Table.pi2) {
    		systemLog("Failed to set player at given position. Invalid values.", "playerSetPosition");
    		return;
    	}
		systemLog("Setting player position to [" + x + ", " + y + ", " + z + "]. Direction=" + dir + ".", "playerSetPosition");
    	Camera.player_x = x;
    	Camera.player_y = y;
    	Camera.player_z = z;
    	Camera.direction_rad = dir;
    }
	
	public int playerGetSector() {
		systemLog("Getting player sector.", "playerGetSector");
		return Camera.player_sector; 
	}
	
	public void playerSetMoveSpeed(double move_speed) {
		systemLog("Setting move speed to" + move_speed + ".", "playerSetMoveSpeed");
		Camera.move_speed = move_speed;
	}
	
	public void playerSetTurnSpeed(double turn_speed) {
		systemLog("Setting turn speed to" + turn_speed + ".", "playerSetTurnSpeed");
		Camera.turn_speed = turn_speed;
	}
	
	public void playerSetPitchSpeed(double pitch_speed) {
		systemLog("Setting pitch speed to" + pitch_speed + ".", "playerSetPitchSpeed");
		Camera.pitch_speed = pitch_speed;
	}
	
	public void playerSetFly() {
		systemLog("Turning on jetpack.", "playerSetFly");
		Camera.jetpack = true;;
	}
	
	public void playerSetWalk(double floor_offset, double bob_speed, double bob_amount) {
		systemLog("Turning off jetpack. Setting walking variables.", "playerSetWalk");
	    Camera.jetpack = false;
	    Camera.FLOOR_OFFSET = floor_offset;
	    Camera.BOB_SPEED = bob_speed;
	    Camera.BOB_AMOUNT = bob_amount;
	}
	
	public void playerSetGravity(double grav) {
		systemLog("Setting player gravity.", "playerSetGravity");
		Camera.GRAVITY = grav;
	}
	
	public double playerGetGravity() {
		systemLog("Getting player gravity.", "playerGetGravity");
		return Camera.GRAVITY;
	}
	
	public void inputSetMouseSensitivity(double sens) {
		systemLog("Setting mouse sensitivity to" + sens + ".", "inputSetMouseSensitivity");
		Camera.mouse_sens = sens;
	}
	
	public boolean inputGetKeyStatus(boolean is_once, String keyname) {
		systemLog("Getting input key status.", "inputGetKeyStatus");
		Integer vk = Camera.KEY_MAP.get(keyname.toLowerCase());
	    if (vk == null) {
	        System.err.println("Unknown key: " + keyname);
	        return false;
	    }
	    return is_once ? Camera.isOnce(vk) : Camera.isHeld(vk);
	}
	
	public void storeSet(String key, Object val) {
		systemLog("Storing variable " + key + ".", "storeSet");
		user_variables.put(key, val);
	}
	
	public Object storeGet(String key) {
		systemLog("Getting variable " + key + ".", "storeGet");
		return user_variables.get(key);
	}
	
	public String storeSaveGameState() {
		return "Implement me!";
	}
	
	public String storeLoadGameState() {
		return "Implement me!";
	}
	
	public String uiDraw() {
		return "Implement me!";
	}
	
	public String uiText() {
		return "Implement me!";
	}
	
	public String userExampleFunc() {
		systemLog("Calling dummy user function.", "userExampleFunc");
		return "This is a dummy function. Create your wonderful user-specific functions like this! Feel free to add specific variables you need at the top of this file as well!";
	}
	
}
