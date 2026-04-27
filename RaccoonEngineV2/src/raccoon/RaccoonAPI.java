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
	
	
}
