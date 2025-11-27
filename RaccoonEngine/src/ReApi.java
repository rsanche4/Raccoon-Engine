import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

public class ReApi {

    private static final ReApi apiInstance = new ReApi();
    private static HashMap<String, Object> user_temp_variables = new HashMap<>();

    public static void run_user_scripts() {
        for (int i = 0; i < Main.active_scripts.size(); i++) {
            try {
                Globals globals = JsePlatform.standardGlobals();
                globals.set("REAPI", CoerceJavaToLua.coerce(apiInstance));
                LuaValue chunk = globals.load(new FileReader("data/scripts/" + Main.active_scripts.get(i)), "");
                chunk.call(LuaValue.valueOf(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (Map.Entry<String, Sprite> entry : Main.allSprites.entrySet()) {
            try {
            	Globals globals = JsePlatform.standardGlobals();
            	globals.set("REAPI", CoerceJavaToLua.coerce(apiInstance));
            	Sprite entity = entry.getValue();
            	LuaValue chunk = globals.load(new FileReader("data/scripts/" + entity.behaviorScript), "");
            	Varargs va = LuaValue.varargsOf(new LuaValue[] {
            			LuaValue.valueOf(entity.spriteXPos),
            			LuaValue.valueOf(entity.spriteYPos),
            			LuaValue.valueOf(entity.spriteZPos),
            			LuaValue.valueOf(entity.sprite_length),
            			LuaValue.valueOf(entity.sprite_brightness),
            			LuaValue.valueOf(entity.spritename),
            			LuaValue.valueOf(entity.spriteId)
            	});

            	// Single call with all args
            	chunk.invoke(va);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void log(String msg) {
        System.out.println("[Lua] " + msg);
    }
    
    public void add_script(String script_name) {
    	Main.active_scripts.add(script_name);
    }
    
    public void endme(int script_index) {
        Main.active_scripts.remove(script_index);
    }
    
    public void endit(String script_name) {
        Main.active_scripts.remove(script_name);
    }
    
    public String get_skybox() {
    	return Screen.skybox;
    }
    
    public void set_skybox(String skyboxname, float brightness) {
    	Screen.skybox = skyboxname;
    	Screen.skybox_brightness = brightness;
    }
    
    public void set_skybox_offset(int offset) {
    	Screen.sky_offset = offset;
    }
    
    public int get_skybox_offset() {
    	return Screen.sky_offset;
    }
    
    public void set_max_ray_steps(int max_count) {
    	Screen.max_count = max_count;
    }
    
    public int get_max_ray_steps() {
    	return Screen.max_count;
    }
    
    public void load_map(String mapname) {
    	Screen.sectorMap = new HashMap<>();
        Screen.wallMap = new HashMap<>();
        Screen.portalMap = new HashMap<>();
        Screen.portalCollisionData = new HashMap<>();
    	mapname = mapname.toLowerCase();
    	if (mapname.contentEquals("menu")) {
    		Screen.is_menu = true;
    		return;
    	} else {
    		Screen.is_menu = false;
    	}

        String path = "data/maps/" + mapname;

        int selected = -1;
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            for (String line : lines) {
                line = line.trim();
                if (line.equals("[SECTORS]")) { selected = 0; continue; }
                if (line.equals("[WALLS]")) { selected = 1; continue; }
                if (line.equals("[PORTALS]")) { selected = 2; continue; }

                String[] parts = line.split("\\s+");
                
                switch (selected) {
                    case 0 -> { // SECTORS
                    	int secid = Integer.parseInt(parts[0]); 
                        
                    	Sector curr_sector = new Sector(
                        	secid,
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            parts[3],
                            Float.parseFloat(parts[4]),
                            parts[5],
                            Float.parseFloat(parts[6])
                        );
                        
                        Screen.sectorMap.put(secid, curr_sector);
                    }
                    case 1 -> { // WALLS                    	
                        float x1 = Float.parseFloat(parts[0]);
                        float z1 = Float.parseFloat(parts[1]);
                        float x2 = Float.parseFloat(parts[2]);
                        float z2 = Float.parseFloat(parts[3]);
                        int sectorId = Integer.parseInt(parts[4]);
                        String texture = parts[5];
                        float brightness = Float.parseFloat(parts[6]);

                        // Normalize coordinates so start < end
                        if (x1 > x2 || (x1 == x2 && z1 > z2)) {
                            float tmpX = x1, tmpZ = z1;
                            x1 = x2; z1 = z2;
                            x2 = tmpX; z2 = tmpZ;
                        }

                        // Horizontal walls (z fixed)
                        if (z1 == z2) {
                        	Screen.sectorMap.get(sectorId).update_sector_boundary(z1, 0);
                            int startX = (int) Math.floor(x1);
                            int endX = (int) Math.floor(x2);
                            for (int x = startX; x < endX; x++) {
                                String key = Screen.makeWallKey((float)x, z1, (float)(x + 1), z1);
                                Wall w = new Wall((float)x, z1, (float)(x + 1), z1, sectorId, texture, brightness);
                                Screen.wallMap.put(key, w);
                            }
                        }
                        // Vertical walls (x fixed)
                        else if (x1 == x2) {
                        	Screen.sectorMap.get(sectorId).update_sector_boundary(x1, 1);
                            int startZ = (int) Math.floor(z1);
                            int endZ = (int) Math.floor(z2);
                            for (int z = startZ; z < endZ; z++) {
                                String key = Screen.makeWallKey(x1, (float)z, x1, (float)(z + 1));
                                Wall w = new Wall(x1, (float)z, x1, (float)(z + 1), sectorId, texture, brightness);
                                Screen.wallMap.put(key, w);
                            }
                        }
                    }

                    case 2 -> { // PORTALS
                        float x1 = Float.parseFloat(parts[0]);
                        float z1 = Float.parseFloat(parts[1]);
                        float x2 = Float.parseFloat(parts[2]);
                        float z2 = Float.parseFloat(parts[3]);
                        int sectorA = Integer.parseInt(parts[4]);
                        int sectorB = Integer.parseInt(parts[5]);
                        String bottomtexture = parts[6];
                        float bottombrightness = Float.parseFloat(parts[7]);
                        String middleTexture = parts[8];
                        float middlebrightness = Float.parseFloat(parts[9]);
                        String topTexture = parts[10];
                        float topbrightness = Float.parseFloat(parts[11]);
                        boolean isSolid = Integer.parseInt(parts[12])==1 ? true : false;
                        
                        // Normalize coordinates so start < end
                        if (x1 > x2 || (x1 == x2 && z1 > z2)) {
                            float tmpX = x1, tmpZ = z1;
                            x1 = x2; z1 = z2;
                            x2 = tmpX; z2 = tmpZ;
                        }

                        // Optional: normalize sectors so sectorA <= sectorB
                        if (sectorA > sectorB) {
                            int tmp = sectorA;
                            sectorA = sectorB;
                            sectorB = tmp;
                        }
                        
                        Screen.portalCollisionData.put(Integer.parseInt(sectorA + "" + sectorB), isSolid);
                   
                        // Horizontal portals (z fixed)
                        if (z1 == z2) {
                        	Screen.sectorMap.get(sectorA).update_sector_boundary(z1, 0);
                        	Screen.sectorMap.get(sectorB).update_sector_boundary(z1, 0);
                            int startX = (int) Math.floor(x1);
                            int endX = (int) Math.ceil(x2);
                            for (int x = startX; x < endX; x++) {
                                String key = Screen.makeWallKey((float)x, z1, (float)(x + 1), z1);
                                Portal p = new Portal((float)x, z1, (float)(x + 1), z1, sectorA, sectorB, bottomtexture, bottombrightness, middleTexture, middlebrightness, topTexture, topbrightness);
                                Screen.portalMap.put(key, p);
                            }
                        }
                        // Vertical portals (x fixed)
                        else if (x1 == x2) {
                        	Screen.sectorMap.get(sectorA).update_sector_boundary(x1, 1);
                        	Screen.sectorMap.get(sectorB).update_sector_boundary(x1, 1);
                            int startZ = (int) Math.floor(z1);
                            int endZ = (int) Math.ceil(z2);
                            for (int z = startZ; z < endZ; z++) {
                                String key = Screen.makeWallKey(x1, (float)z, x1, (float)(z + 1));
                                Portal p = new Portal(x1, (float)z, x1, (float)(z + 1), sectorA, sectorB, bottomtexture, bottombrightness, middleTexture, middlebrightness, topTexture, topbrightness);
                                Screen.portalMap.put(key, p);
                            }
                        }
                    }
                    
                }
                
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void set_player_pos(float x, float y, float z) {
    	Camera.player_x = x;
    	Camera.player_y = y;
    	Camera.player_z = z;
    }
    
    public float get_player_pos_x() {
    	return Camera.player_x;
    }
    
    public float get_player_pos_y() {
    	return Camera.player_y;
    }
    
    public float get_player_pos_z() {
    	return Camera.player_z;
    }
    
    public int get_player_sector() {
    	return Camera.player_sector;
    }
    
    public float euclidean_distance_2D(float x1, float y1, float x2, float y2) {
    	return Screen.euclid_dist(x1, y1, x2, y2);
    }
    
    public float manhattan_distance_2D(float x1, float y1, float x2, float y2) {
    	return Screen.manhattan_dist(x1, y1, x2, y2);
    }
    
    public float get_move_speed() {
    	return Camera.MOVE_SPEED;
    }
    
    public void set_move_speed(float move_speed) {
    	Camera.MOVE_SPEED = move_speed;
    }
    
    public float get_turn_speed() {
    	return Camera.TURN_SPEED;
    }
    
    public void set_turn_speed(float turn_speed) {
    	Camera.TURN_SPEED = turn_speed;
    }
    
    public void set_pitch_speed(int pitch_speed) {
    	Camera.PITCH_SPEED = pitch_speed;
    }
    
    public int get_pitch_speed() {
    	return Camera.PITCH_SPEED;
    }
    
    public float get_jump_up_speed() {
    	return Camera.JUMP_UP_SPEED;
    }
    
    public void set_jump_up_speed(float move_up_speed) {
    	Camera.JUMP_UP_SPEED = move_up_speed;
    }
    
    public void set_mouse_sensitivity(float sens_speed) {
    	Camera.MOUSE_SENSITIVITY = sens_speed;
    }
    
    public float get_mouse_sensitivity() {
    	return Camera.MOUSE_SENSITIVITY;
    }
    
    public void set_gravity_up_multiplier(float grav_up_mult) {
    	Camera.gravity_up_multiplier = grav_up_mult;
    }
    
    public void set_gravity_down_multiplier(float grav_dn_mult) {
    	Camera.gravity_down_multiplier = grav_dn_mult;
    }
    
   public float get_gravity_up_multiplier() {
    	return Camera.gravity_up_multiplier;
    }
    
    public float get_gravity_down_multiplier() {
    	return Camera.gravity_down_multiplier;
    }
    
    public float get_crouching_speed() {
    	return Camera.CROUCHING_SPEED;
    }
    
    public void set_crouching_speed(float crouch_speed) {
    	Camera.CROUCHING_SPEED = crouch_speed;
    }
    
    public float get_dir_player() {
    	return Camera.direction_rad;
    }
    
    public void set_dir_player(float dir) {
    	Camera.direction_rad = dir;
    }
    
    public void set_player_height (float player_height) {
    	Camera.player_height = player_height;
    }
    
    public float get_player_height() {
    	return Camera.player_height;
    }
    
    public void set_fog_settings(int r, int g, int b, float start, float end) {
    	Screen.fog_r = r;
    	Screen.fog_g = g;
    	Screen.fog_b = b;
    	Screen.fog_start = start;
    	Screen.fog_end = end;
    }
    
    public int fog_color() {
    	return (Screen.fog_r << 16) | (Screen.fog_g << 8) | Screen.fog_b;
    }
    
    public float get_fog_start() {
    	return Screen.fog_start;
    }
    
    public float get_fog_end() {
    	return Screen.fog_end;
    }
    
    public float get_retina_dist() {
    	return Camera.retina_dist;
    }
    
    public void set_retina_dist(float ret_dist) {
    	Camera.retina_dist = ret_dist;
    }
    
    public void toggle_plane_texture(boolean planeToggle) {
    	Screen.plane_texture = planeToggle;
    }
    
    public boolean get_toggle_plane_texture() {
    	return Screen.plane_texture;
    }
    
    public void toggle_sky_texture(boolean skyToggle) {
    	Screen.sky_texture_bool = skyToggle;
    }
    
    public boolean get_toggle_sky_texture() {
    	return Screen.sky_texture_bool;
    }
    
    public boolean is_key_pressed_once(String keyname) {
        keyname = keyname.toLowerCase();
        switch (keyname) {
            // Menu navigation
            case "left":
            case "menuleft": return Camera.menuleft_once;
            case "right":
            case "menuright": return Camera.menuright_once;
            case "up":
            case "menuup": return Camera.menuup_once;
            case "down":
            case "menudown": return Camera.menudown_once;
            
            // Movement
            case "forward":
            case "w": return Camera.forward_once;
            case "back":
            case "s": return Camera.back_once;
            case "strafeleft":
            case "a": return Camera.strafeleft_once;
            case "straferight":
            case "d": return Camera.straferight_once;
            
            // Actions
            case "enter": return Camera.enter_once;
            case "space":
            case "jump": return Camera.space_once;
            case "ctrl":
            case "crouch": return Camera.ctrl_once;
            case "interact":
            case "e": return Camera.interact_once;
            case "reload":
            case "r": return Camera.reload_once;
            
            // Weapon slots
            case "first":
            case "1": return Camera.first_once;
            case "second":
            case "2": return Camera.second_once;
            case "third":
            case "3": return Camera.third_once;
            case "fourth":
            case "4": return Camera.fourth_once;
            
            // Mouse
            case "leftclick":
            case "shoot":
            case "attack": return Camera.leftclick_once;
            
            // General
            case "esc":
            case "escape": return Camera.esc_once;
            case "f4":
            case "fullscreen": return Camera.f4_once;
            
            default: return false;
        }
    }

    public boolean is_key_pressed(String keyname) {
        keyname = keyname.toLowerCase();
        switch (keyname) {
            // Menu navigation
            case "left":
            case "menuleft": return Camera.menuleft;
            case "right":
            case "menuright": return Camera.menuright;
            case "up":
            case "menuup": return Camera.menuup;
            case "down":
            case "menudown": return Camera.menudown;
            
            // Movement
            case "forward":
            case "w": return Camera.forward;
            case "back":
            case "s": return Camera.back;
            case "strafeleft":
            case "a": return Camera.strafeleft;
            case "straferight":
            case "d": return Camera.straferight;
            
            // Actions
            case "enter": return Camera.enter;
            case "space":
            case "jump": return Camera.space;
            case "ctrl":
            case "crouch": return Camera.ctrl;
            case "interact":
            case "e": return Camera.interact;
            case "reload":
            case "r": return Camera.reload;
            
            // Weapon slots
            case "first":
            case "1": return Camera.first;
            case "second":
            case "2": return Camera.second;
            case "third":
            case "3": return Camera.third;
            case "fourth":
            case "4": return Camera.fourth;
            
            // Mouse
            case "leftclick":
            case "shoot":
            case "attack": return Camera.leftclick;
            
            // General
            case "esc":
            case "escape": return Camera.esc;
            case "f4":
            case "fullscreen": return Camera.f4;
            
            default: return false;
        }
    }

    public boolean is_key_released(String keyname) {
        keyname = keyname.toLowerCase();
        switch (keyname) {
            // Menu navigation
            case "left":
            case "menuleft": return !Camera.menuleft;
            case "right":
            case "menuright": return !Camera.menuright;
            case "up":
            case "menuup": return !Camera.menuup;
            case "down":
            case "menudown": return !Camera.menudown;
            
            // Movement
            case "forward":
            case "w": return !Camera.forward;
            case "back":
            case "s": return !Camera.back;
            case "strafeleft":
            case "a": return !Camera.strafeleft;
            case "straferight":
            case "d": return !Camera.straferight;
            
            // Actions
            case "enter": return !Camera.enter;
            case "space":
            case "jump": return !Camera.space;
            case "ctrl":
            case "crouch": return !Camera.ctrl;
            case "interact":
            case "e": return !Camera.interact;
            case "reload":
            case "r": return !Camera.reload;
            
            // Weapon slots
            case "first":
            case "1": return !Camera.first;
            case "second":
            case "2": return !Camera.second;
            case "third":
            case "3": return !Camera.third;
            case "fourth":
            case "4": return !Camera.fourth;
            
            // Mouse
            case "leftclick":
            case "shoot":
            case "attack": return !Camera.leftclick;
            
            // General
            case "esc":
            case "escape": return !Camera.esc;
            case "f4":
            case "fullscreen": return !Camera.f4;
            
            default: return true; // consider default as released
        }
    }
    
    public void set_max_fps(float max_fps) {
    	Main.MAX_FPS = max_fps;
    }
    
    public void playBGM(String bgm_path, boolean loop, float volume) {
		Screen.current_bgm = new Sound("data/bgm/"+bgm_path, loop, volume);
	}
    
    public void changeBGMVol(float volume) {
    	Screen.current_bgm.setVolume(volume);
    }
	
	public void stopBGM() {
		Screen.current_bgm.stopSound();
	}
	
	public void playSE(String bgm_path, boolean loop, float volume) {
		Screen.current_sfe = new Sound("data/se/"+bgm_path, loop, volume);
	}
	
	public void stopSE() {
		Screen.current_sfe.stopSound();
	}
	
	public void upsertSprite(float sx, float sy, float sz, float sprite_length, float sprite_brightness, String spriteTextureName, String spriteId, String behavior_script) {
		Main.allSprites.put(spriteId, new Sprite(sx, sy, sz, sprite_length, sprite_brightness, spriteTextureName, spriteId, behavior_script));
	}
		
	public void removeSprite(String spriteId) {
		Main.allSprites.remove(spriteId);
	}
	
	public long debug_stats(String stat) {
	    Runtime runtime = Runtime.getRuntime();
	    // Memory stats (in MB)
	    long maxMemory = runtime.maxMemory() / (1024 * 1024);
	    long allocatedMemory = runtime.totalMemory() / (1024 * 1024);
	    long freeMemory = runtime.freeMemory() / (1024 * 1024);
	    long usedMemory = allocatedMemory - freeMemory;
	    if (stat.contentEquals("used_mem")) {
	        return usedMemory;
	    } else if (stat.contentEquals("total_mem")) {
	        return allocatedMemory;
	    } else if (stat.contentEquals("max_mem")) {
	        return maxMemory;
	    } else if (stat.contentEquals("free_mem")) {
	        return freeMemory;
	    } else if (stat.contentEquals("active_threads")) {
	        return Thread.activeCount();
	    }
	    // CPU & System stats
	    else if (stat.contentEquals("cpu_cores")) {
	        return runtime.availableProcessors();
	    } else if (stat.contentEquals("fps")) {
	        return (long) Main.currentFPS;
	    } else if (stat.contentEquals("frame_num")) {
	    	return Main.frame_num;
	    } else if (stat.contentEquals("max_fps")) {
	    	return (long) Main.MAX_FPS;
	    }
	    // Texture & Resource stats
	    else if (stat.contentEquals("texture_count")) {
	        return Main.allTextures.size();
	    } else if (stat.contentEquals("sprite_count")) {
	        return Main.allSprites.size();
	    } else if (stat.contentEquals("sector_count")) {
	    	return Screen.sectorMap.size();	    
	    } else if (stat.contentEquals("wall_count")) {
	    	return Screen.wallMap.size();
	    } else if (stat.contentEquals("portal_count")) {
	    	return Screen.portalMap.size();	   
	    }
	    // Screen/Display stats
	    else if (stat.contentEquals("game_width")) {
	        return Main.game_width;
	    } else if (stat.contentEquals("game_height")) {
	        return Main.game_height;
	    } else if (stat.contentEquals("screen_width")) {
	        return Main.SCREEN_W;
	    } else if (stat.contentEquals("screen_height")) {
	        return Main.SCREEN_H;
	    } else if (stat.contentEquals("total_pixels")) {
	        return (long) Main.SCREEN_W * Main.SCREEN_H;
	    }
	    // Memory percentage stats
	    else if (stat.contentEquals("mem_usage_percent")) {
	        return (usedMemory * 100) / maxMemory;
	    } else if (stat.contentEquals("mem_allocated_percent")) {
	        return (allocatedMemory * 100) / maxMemory;
	    }
	    // Script stats
	    else if (stat.contentEquals("active_scripts")) {
	        return Main.active_scripts.size();
	    }
	    // Advanced stats
	    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
	    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
	    ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
	    if (stat.contentEquals("heap_memory_used")) {
	        return (long) (memoryBean.getHeapMemoryUsage().getUsed() / (1024.0 * 1024.0)); // MB
	    } else if (stat.contentEquals("heap_memory_max")) {
	        return (long) (memoryBean.getHeapMemoryUsage().getMax() / (1024.0 * 1024.0)); // MB
	    } else if (stat.contentEquals("non_heap_memory_used")) {
	        return (long) (memoryBean.getNonHeapMemoryUsage().getUsed() / (1024.0 * 1024.0)); // MB
	    } else if (stat.contentEquals("thread_peak_count")) {
	        return threadBean.getPeakThreadCount();
	    } else if (stat.contentEquals("thread_total_started")) {
	        return threadBean.getTotalStartedThreadCount();
	    } else if (stat.contentEquals("classes_loaded")) {
	        return classBean.getLoadedClassCount();
	    } else if (stat.contentEquals("classes_total_loaded")) {
	        return classBean.getTotalLoadedClassCount();
	    } else if (stat.contentEquals("classes_unloaded")) {
	        return classBean.getUnloadedClassCount();
	    }
		return -1;
	}
	
	public void writeVar(String key, Object val) {
		user_temp_variables.put(key, val);
	}
	
	public Object readVar(String key) {
		return user_temp_variables.get(key);
	}
    
	public float decodeCoordinatefromPathString(String path_comma_sep, int axis) {
	    String[] parts = path_comma_sep.split(",");
	    return Float.parseFloat(parts[axis]);
	}
	
	public String gbfs_pathfindToward(float source_x, float source_y, float source_z, float targetx, float targety, float targetz, float speed) {
		// TODO
		return "Implement";
	}
	
	public String direct_PathfindToward(float source_x, float source_y, float source_z, float targetx, float targety, float targetz, float speed) {
		float z_dif = Math.abs(targetz-source_z);
		float y_dif = Math.abs(targety-source_y);
		float x_dif = Math.abs(targetx-source_x);
		if (z_dif<Camera.buffer_dist && y_dif<Camera.buffer_dist && x_dif<Camera.buffer_dist) {
			return source_x + "," + source_y + "," + source_z;
		}
		int z_dir = 0;
		if (z_dif<Camera.buffer_dist) {
			z_dir = 0;
		} else if (targetz > source_z) {
			z_dir = 1;
		} else if (targetz < source_z) {
			z_dir = -1;
		}
		int y_dir = 0;
		if (y_dif<Camera.buffer_dist) {
			y_dir = 0;
		} else if (targety > source_y) {
			y_dir = 1;
		} else if (targety < source_y) {
			y_dir = -1;
		}
		int x_dir = 0;
		if (x_dif<Camera.buffer_dist) {
			x_dir = 0;
		} else if (targetx > source_x) {
			x_dir = 1;
		} else if (targetx < source_x) {
			x_dir = -1;
		}
		float newx = source_x + (x_dir*speed);
        float newy = source_y + (y_dir*speed);
        float newz = source_z + (z_dir*speed);
        return newx + "," + newy + "," + newz;
	}
	
    public void addUIToScreen(String textureName, int pos_x, int pos_y, int opacity) {
	    Texture texture = Main.allTextures.get(textureName);
	    if (texture == null) return;
	    float opacityFactor = opacity / 255.0f;
	    for (int y = 0; y < texture.IMG_HEI; y++) {
	        for (int x = 0; x < texture.IMG_WID; x++) {
	            int screenX = pos_x + x;
	            int screenY = pos_y + y;
	            if (screenX >= 0 && screenX < Main.game_width && screenY >= 0 && screenY < Main.game_height) {
	                int srcPixel = texture.pixels[y * texture.IMG_WID + x];
	                if (srcPixel==0xFF000000) {
	                	continue;
	                } else {
	                    int screenIndex = screenY * Main.game_width + screenX;
	                    int dstPixel = Screen.gamepixels[screenIndex];
	                    int Rb = (dstPixel >> 16) & 0xFF;
	                    int Gb = (dstPixel >> 8) & 0xFF;
	                    int Bb = dstPixel & 0xFF;
	                    
	                    int Rf = (srcPixel >> 16) & 0xFF;
	                    int Gf = (srcPixel >> 8) & 0xFF;
	                    int Bf = srcPixel & 0xFF;
	                    int Rr = Math.min(255, (int)(Rf * opacityFactor + Rb * (1 - opacityFactor)));
	                    int Gr = Math.min(255, (int)(Gf * opacityFactor + Gb * (1 - opacityFactor)));
	                    int Br = Math.min(255, (int)(Bf * opacityFactor + Bb * (1 - opacityFactor)));
	                    Screen.gamepixels[screenIndex] = 0xFF000000 | (Rr << 16) | (Gr << 8) | Br;
	                }
	            }
	        }
	    }
	}
    
    public void displayText(String text, int pos_x, int pos_y, String fontfile) {
	    text = text.toLowerCase();
	    int[] font_pixels = Main.allTextures.get(fontfile).pixels;
	    int cursor = pos_x;
	    int font_original_pixel_size = Main.allTextures.get(fontfile).IMG_WID;
	    for (int i = 0; i < text.length(); i++) {
	        int letter_location_in_fontpng = -1;
	        switch (text.charAt(i)) {
	            case 'a': letter_location_in_fontpng = 0; break;
	            case 'b': letter_location_in_fontpng = 1; break;
	            case 'c': letter_location_in_fontpng = 2; break;
	            case 'd': letter_location_in_fontpng = 3; break;
	            case 'e': letter_location_in_fontpng = 4; break;
	            case 'f': letter_location_in_fontpng = 5; break;
	            case 'g': letter_location_in_fontpng = 6; break;
	            case 'h': letter_location_in_fontpng = 7; break;
	            case 'i': letter_location_in_fontpng = 8; break;
	            case 'j': letter_location_in_fontpng = 9; break;
	            case 'k': letter_location_in_fontpng = 10; break;
	            case 'l': letter_location_in_fontpng = 11; break;
	            case 'm': letter_location_in_fontpng = 12; break;
	            case 'n': letter_location_in_fontpng = 13; break;
	            case 'o': letter_location_in_fontpng = 14; break;
	            case 'p': letter_location_in_fontpng = 15; break;
	            case 'q': letter_location_in_fontpng = 16; break;
	            case 'r': letter_location_in_fontpng = 17; break;
	            case 's': letter_location_in_fontpng = 18; break;
	            case 't': letter_location_in_fontpng = 19; break;
	            case 'u': letter_location_in_fontpng = 20; break;
	            case 'v': letter_location_in_fontpng = 21; break;
	            case 'w': letter_location_in_fontpng = 22; break;
	            case 'x': letter_location_in_fontpng = 23; break;
	            case 'y': letter_location_in_fontpng = 24; break;
	            case 'z': letter_location_in_fontpng = 25; break;
	            case '0': letter_location_in_fontpng = 26; break;
	            case '1': letter_location_in_fontpng = 27; break;
	            case '2': letter_location_in_fontpng = 28; break;
	            case '3': letter_location_in_fontpng = 29; break;
	            case '4': letter_location_in_fontpng = 30; break;
	            case '5': letter_location_in_fontpng = 31; break;
	            case '6': letter_location_in_fontpng = 32; break;
	            case '7': letter_location_in_fontpng = 33; break;
	            case '8': letter_location_in_fontpng = 34; break;
	            case '9': letter_location_in_fontpng = 35; break;
	            case '.': letter_location_in_fontpng = 36; break;
	            case '\'': letter_location_in_fontpng = 37; break;
	            case '!': letter_location_in_fontpng = 38; break;
	            case '?': letter_location_in_fontpng = 39; break;
	            case ':': letter_location_in_fontpng = 40; break;
	            case '-': letter_location_in_fontpng = 41; break;
	            case ',': letter_location_in_fontpng = 42; break;
	            case '/': letter_location_in_fontpng = 43; break;
	            case '(': letter_location_in_fontpng = 44; break;
	            case ')': letter_location_in_fontpng = 45; break;
	            case '+': letter_location_in_fontpng = 46; break;
	            case '*': letter_location_in_fontpng = 47; break;
	            case '"': letter_location_in_fontpng = 48; break;
	            case '#': letter_location_in_fontpng = 49; break;
	            case '=': letter_location_in_fontpng = 50; break;
	            case ';': letter_location_in_fontpng = 51; break;
	            default: letter_location_in_fontpng = -1; break;
	        }
	        if (letter_location_in_fontpng > -1) {
	        	if (cursor + font_original_pixel_size > Main.game_width) {
	                break;
	            }
	            for (int j = 0; j < font_original_pixel_size; j++) {
	                for (int k = 0; k < font_original_pixel_size; k++) {
	                    int font_index = (letter_location_in_fontpng * font_original_pixel_size + j) * font_original_pixel_size + k;
	                    int ind = (pos_y + j) * Main.game_width + (cursor + k); 
	                    if (ind < Main.game_width * Main.game_height && font_pixels[font_index]!=0x000000) {
	                        Screen.gamepixels[ind] = font_pixels[font_index];
	                    }
	                }
	            }
	        }
	        cursor += font_original_pixel_size;
	    }
	}
    
}
