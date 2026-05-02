package raccoon;

import java.time.LocalDateTime;
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
    private static boolean debug_console = true;
	
	public static void runUserScripts() {
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
			System.out.println("[" + time + "] " + system_call + ": " + msg);
		}
		// TODO and all Display the text ALSO to the actual game, like garrys mod. Only output. My own Console.
    }
	
	public String systemWorldTime() {
		LocalDateTime now = LocalDateTime.now();
	    return String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano() / 1000000);
	}
	
	public int systemStartTime() {
		 return (int)((System.currentTimeMillis() - Main.start_time) / 1000);
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
				systemLog("Failed! Map not found.", "worldLoadMap");
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
                		Screen.sectors[sector_id] = new Sector(sector_id, floor_height, ceil_height, parts[3], Integer.parseInt(parts[4]), Double.parseDouble(parts[5]), Boolean.parseBoolean(parts[6]), parts[7], Integer.parseInt(parts[8]), Double.parseDouble(parts[9]), Boolean.parseBoolean(parts[10]));
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
                		int brightness = Integer.parseInt(parts[6]);
                		double tiled = Double.parseDouble(parts[7]);
                		boolean skip_texture = Boolean.parseBoolean(parts[8]);
                		int is_vertical;
                		if (z1 == z2) {
                			is_vertical = 0;
                			Screen.sectors[sector_id].updateSectorBoundary(z1, is_vertical);
                			int start_x = (int) Math.floor(x1);
                            int end_x = (int) Math.floor(x2);
                            for (int x = start_x; x < end_x; x++) {
                                int key = Screen.makeWallIndex(x, (int)z1, is_vertical);
                                Screen.verticals[key] = new Wall(x, z1, x2, z2, sector_id, wall_texture, brightness, tiled, skip_texture);
                            }
                        } else if (x1 == x2) {
                        	is_vertical = 1;
                        	Screen.sectors[sector_id].updateSectorBoundary(x1, is_vertical);
                            int start_z = (int) Math.floor(z1);
                            int end_z = (int) Math.floor(z2);
                            for (int z = start_z; z < end_z; z++) {
                            	int key = Screen.makeWallIndex((int)x1, z, is_vertical);
                                Screen.verticals[key] = new Wall(x1, z, x2, z2, sector_id, wall_texture, brightness, tiled, skip_texture);
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
                		int bottom_brightness = Integer.parseInt(parts[7]);
                		double bottom_tiled = Double.parseDouble(parts[8]);
                		boolean bottom_skip_texture = Boolean.parseBoolean(parts[9]);
                		String middle_tex = parts[10];
                		int middle_brightness = Integer.parseInt(parts[11]);
                		double middle_tiled = Double.parseDouble(parts[12]);
                		boolean middle_skip_texture = Boolean.parseBoolean(parts[13]);
                		String top_tex = parts[14];
                		int top_brightness = Integer.parseInt(parts[15]);
                		double top_tiled = Double.parseDouble(parts[16]);
                		boolean top_skip_texture = Boolean.parseBoolean(parts[17]);
                		boolean is_solid = Boolean.parseBoolean(parts[18]);
                		Screen.portal_collision_data[sector_a * Screen.sectors_count + sector_b] = is_solid;
                		Screen.portal_collision_data[sector_b * Screen.sectors_count + sector_a] = is_solid;
                		int is_vertical;
                		if (z1 == z2) {
                			is_vertical = 0;
                        	Screen.sectors[sector_a].updateSectorBoundary(z1, is_vertical);
                        	Screen.sectors[sector_b].updateSectorBoundary(z1, is_vertical);
                            int start_x = (int) Math.floor(x1);
                            int end_x = (int) Math.floor(x2);
                            for (int x = start_x; x < end_x; x++) {
                                int key = Screen.makeWallIndex(x, (int)z1, is_vertical);
                                Screen.verticals[key] = new Portal(x, z1, x2, z2, sector_a, sector_b, bottom_tex, bottom_brightness, bottom_tiled, bottom_skip_texture, middle_tex, middle_brightness, middle_tiled, middle_skip_texture, top_tex, top_brightness, top_tiled, top_skip_texture);
                            }
                        } else if (x1 == x2) {
                        	is_vertical = 1;
                        	Screen.sectors[sector_a].updateSectorBoundary(x1, is_vertical);
                        	Screen.sectors[sector_b].updateSectorBoundary(x1, is_vertical);
                            int start_z = (int) Math.floor(z1);
                            int end_z = (int) Math.floor(z2);
                            for (int z = start_z; z < end_z; z++) {
                                int key = Screen.makeWallIndex((int)x1, z, is_vertical);
                                Screen.verticals[key] = new Portal(x1, z, x2, z2, sector_a, sector_b, bottom_tex, bottom_brightness, bottom_tiled, bottom_skip_texture, middle_tex, middle_brightness, middle_tiled, middle_skip_texture, top_tex, top_brightness, top_tiled, top_skip_texture);
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
    	if (x<0 || y<0 || z<0 || dir<0) {
    		systemLog("Failed to set player at given position. Negative values.", "playerSetPosition");
    		return;
    	}
		systemLog("Setting player position to [" + x + ", " + y + ", " + z + "]. Direction=" + dir + ".", "playerSetPosition");
    	Camera.player_x = x;
    	Camera.player_y = y;
    	Camera.player_z = z;
    	Camera.direction_rad = dir % Table.pi2;
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
	
	public void inputSetMouseSensitivity(double sens) {
		systemLog("Setting mouse sensitivity to" + sens + ".", "inputSetMouseSensitivity");
		Camera.mouse_sens = sens;
	}
	
	public String saveGameState() {
		return "Implement Me!";
	}
	
	public String saveLoadGameState() {
		return "Implement Me!";
	}
	
	
	
}
