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
	
	private double[] worldLoadMapHelper(double x1, double z1, double x2, double z2) {
		if (x1 > x2 || (x1 == x2 && z1 > z2)) {
            double tmpX = x1, tmpZ = z1;
            x1 = x2; z1 = z2;
            x2 = tmpX; z2 = tmpZ;
        }
		return new double[] {x1, z1, x2, z2};
	}
	
	private 
	
	public void worldLoadMap(String mapname) {
		systemLog("Loading map " + mapname + ".", "worldLoadMap");
		Screen.sectors = new Sector[Screen.MAX_NUM_SECTORS];
		Screen.walls = new Wall[Screen.MAX_NUM_WALLS];
		Screen.portals = new Portal[Screen.MAX_NUM_WALLS];
		Screen.map_width = 0;
		Screen.map_height = 0;
		Screen.sectors_length = 0;
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
                	systemLog("Reading portals.", "worldLoadMap");
                	selected = 2; 
                	continue; 
                }
                if (line.equals("[SIZE]")) { 
                	systemLog("Reading map size.", "worldLoadMap");
                	selected = 3; 
                	continue; 
                }
                
                String[] parts = line.split("\\s+");
                switch (selected) {
                	case 0 -> {
                		Screen.sectors[Screen.sectors_length] = new Sector(Integer.parseInt(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), parts[3], Integer.parseInt(parts[4]), Double.parseDouble(parts[5]), Boolean.parseBoolean(parts[6]), parts[7], Integer.parseInt(parts[8]), Double.parseDouble(parts[9]), Boolean.parseBoolean(parts[10]));
                		Screen.sectors_length++;
                	}
                	case 1 -> {
                		double[] xz = new double[4];
                		xz[0] = Double.parseDouble(parts[0]);
                		xz[1] = Double.parseDouble(parts[1]);
                		xz[2] = Double.parseDouble(parts[2]);
                		xz[3] = Double.parseDouble(parts[3]);
                		xz = worldLoadMapHelper(xz[0], xz[1], xz[2], xz[3]);
                		double x1 = xz[0];
                		double z1 = xz[1];
                		double x2 = xz[2];
                		double z2 = xz[3];
                		int sector_id = Integer.parseInt(parts[4]);
                		if (z1 == z2) { // TODO trying to figure this out. left here
                        	Screen.sectors[sector_id].update_sector_boundary(z1, 0);
                            int startX = (int) Math.floor(x1);
                            int endX = (int) Math.floor(x2);
                            for (int x = startX; x < endX; x++) {
                                String key = Screen.makeWallKey((float)x, z1, (float)(x + 1), z1);
                                Wall w = new Wall((float)x, z1, (float)(x + 1), z1, sectorId, texture, brightness);
                                Screen.wallMap.put(key, w);
                            }
                        }
                		
                	}
                	case 2 -> {
                    	
                	}
                	case 3 -> {
                		Screen.map_width = Integer.parseInt(parts[0]);
                		Screen.map_height = Integer.parseInt(parts[1]);
                	}
                }
			
			}
		} catch (Exception e) {
			systemLog("Failed to read map data.", "worldLoadMap");
			e.printStackTrace();
		}
		
	}
	
}
