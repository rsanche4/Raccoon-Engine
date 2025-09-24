import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

public class ReApi {

    private static final ReApi apiInstance = new ReApi();

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
    }

    public void log(String msg) {
        System.out.println("[Lua] " + msg);
    }
    
    public void endme(int script_index) {
        Main.active_scripts.remove(script_index);
    }
    
    public void endit(String script_name) {
        Main.active_scripts.remove(script_name);
    }
    
    public void set_skybox(String skyboxname, float brightness) {
    	Screen.skybox = skyboxname;
    	Screen.skybox_brightness = brightness;
    }
    
    public void load_map(String mapname) {
    	mapname = mapname.toLowerCase();
    	if (mapname.contentEquals("menu")) {
    		Screen.is_menu = true;
    		return;
    	} else {
    		Screen.is_menu = false;
    	}
    	
        Screen.sectorMap = new HashMap<>();
        Screen.wallMap = new HashMap<>();
        Screen.portalMap = new HashMap<>();

        String path = "data/maps/" + mapname; // relative path

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
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            parts[3],
                            Double.parseDouble(parts[4]),
                            parts[5],
                            Double.parseDouble(parts[6])
                        );
                        
                        Screen.sectorMap.put(secid, curr_sector);
                    }
                    case 1 -> { // WALLS
                        double x1 = Double.parseDouble(parts[0]);
                        double z1 = Double.parseDouble(parts[1]);
                        double x2 = Double.parseDouble(parts[2]);
                        double z2 = Double.parseDouble(parts[3]);
                        int sectorId = Integer.parseInt(parts[4]);
                        String texture = parts[5];
                        double scale = Double.parseDouble(parts[6]);

                        // Normalize coordinates so start < end
                        if (x1 > x2 || (x1 == x2 && z1 > z2)) {
                            double tmpX = x1, tmpZ = z1;
                            x1 = x2; z1 = z2;
                            x2 = tmpX; z2 = tmpZ;
                        }

                        // Horizontal walls (z fixed)
                        if (z1 == z2) {
                        	Screen.sectorMap.get(sectorId).update_sector_boundary(z1, 0);
                            int startX = (int) Math.floor(x1);
                            int endX = (int) Math.floor(x2);
                            for (int x = startX; x < endX; x++) {
                                String key = Screen.makeWallKey((double)x, z1, (double)(x + 1), z1);
                                Wall w = new Wall((double)x, z1, (double)(x + 1), z1, sectorId, texture, scale);
                                Screen.wallMap.put(key, w);
                            }
                        }
                        // Vertical walls (x fixed)
                        else if (x1 == x2) {
                        	Screen.sectorMap.get(sectorId).update_sector_boundary(x1, 1);
                            int startZ = (int) Math.floor(z1);
                            int endZ = (int) Math.floor(z2);
                            for (int z = startZ; z < endZ; z++) {
                                String key = Screen.makeWallKey(x1, (double)z, x1, (double)(z + 1));
                                Wall w = new Wall(x1, (double)z, x1, (double)(z + 1), sectorId, texture, scale);
                                Screen.wallMap.put(key, w);
                            }
                        }
                    }

                    case 2 -> { // PORTALS
                        double x1 = Double.parseDouble(parts[0]);
                        double z1 = Double.parseDouble(parts[1]);
                        double x2 = Double.parseDouble(parts[2]);
                        double z2 = Double.parseDouble(parts[3]);
                        int sectorA = Integer.parseInt(parts[4]);
                        int sectorB = Integer.parseInt(parts[5]);

                        // Normalize coordinates so start < end
                        if (x1 > x2 || (x1 == x2 && z1 > z2)) {
                            double tmpX = x1, tmpZ = z1;
                            x1 = x2; z1 = z2;
                            x2 = tmpX; z2 = tmpZ;
                        }

                        // Optional: normalize sectors so sectorA <= sectorB
                        if (sectorA > sectorB) {
                            int tmp = sectorA;
                            sectorA = sectorB;
                            sectorB = tmp;
                        }

                        // Horizontal portals (z fixed)
                        if (z1 == z2) {
                        	Screen.sectorMap.get(sectorA).update_sector_boundary(z1, 0);
                        	Screen.sectorMap.get(sectorB).update_sector_boundary(z1, 0);
                            int startX = (int) Math.floor(x1);
                            int endX = (int) Math.ceil(x2);
                            for (int x = startX; x < endX; x++) {
                                String key = Screen.makeWallKey((double)x, z1, (double)(x + 1), z1);
                                Portal p = new Portal((double)x, z1, (double)(x + 1), z1, sectorA, sectorB);
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
                                String key = Screen.makeWallKey(x1, (double)z, x1, (double)(z + 1));
                                Portal p = new Portal(x1, (double)z, x1, (double)(z + 1), sectorA, sectorB);
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
    
    public void set_atomic_xz_unit(double u) {
    	Screen.atomic_xz_unit = u;
    }
    
    public double get_atomic_xz_unit() {
    	return Screen.atomic_xz_unit;
    }
    
    public double get_retina_dist() {
    	return Camera.retina_dist;
    }
    
    public void set_retina_dist(double retina_dist) {
    	Camera.retina_dist = retina_dist;
    }
    
    public void set_player_pos(double x, double y, double z) {
    	Camera.player_x = x;
    	Camera.player_y = y;
    	Camera.player_z = z;
    }
    
    public double get_player_pos_x() {
    	return Camera.player_x;
    }
    
    public double get_player_pos_y() {
    	return Camera.player_y;
    }
    
    public double get_player_pos_z() {
    	return Camera.player_z;
    }
    
    public int get_player_sector() {
    	return Camera.player_sector;
    }
    
    public double euclidean_distance(double x1, double y1, double x2, double y2) {
    	return Screen.euclid_dist(x1, y1, x2, y2);
    }
    
    public double get_move_speed() {
    	return Camera.MOVE_SPEED;
    }
    
    public void set_move_speed(double move_speed) {
    	Camera.MOVE_SPEED = move_speed;
    }
    
    public double get_turn_speed() {
    	return Camera.TURN_SPEED;
    }
    
    public void set_turn_speed(double turn_speed) {
    	Camera.TURN_SPEED = turn_speed;
    }
    
    public double get_move_up_speed() {
    	return Camera.MOVE_UP_SPEED;
    }
    
    public void set_move_up_speed(double move_up_speed) {
    	Camera.MOVE_UP_SPEED = move_up_speed;
    }
    
    public double get_dir_player() {
    	return Camera.direction_rad;
    }
    
    public void set_dir_player(double dir) {
    	Camera.direction_rad = dir;
    }
    
    public boolean is_key_pressed_once(String keyname) {
        keyname = keyname.toLowerCase();
        switch (keyname) {
            case "left": return Camera.left_once;
            case "right": return Camera.right_once;
            case "forward": return Camera.forward_once;
            case "back": return Camera.back_once;
            case "enter": return Camera.enter_once;
            case "space": return Camera.space_once;
            case "ctrl": return Camera.ctrl_once;
            case "strafeleft": return Camera.strafeleft_once;
            case "straferight": return Camera.straferight_once;
            case "alt": return Camera.alt_once;
            case "first": return Camera.first_once;
            case "second": return Camera.second_once;
            case "third": return Camera.third_once;
            case "pgup": return Camera.pgup_once;
            case "pgdn": return Camera.pgdn_once;
            default: return false;
        }
    }

    public boolean is_key_pressed(String keyname) {
        keyname = keyname.toLowerCase();
        switch (keyname) {
            case "left": return Camera.left;
            case "right": return Camera.right;
            case "forward": return Camera.forward;
            case "back": return Camera.back;
            case "enter": return Camera.enter;
            case "space": return Camera.space;
            case "ctrl": return Camera.ctrl;
            case "strafeleft": return Camera.strafeleft;
            case "straferight": return Camera.straferight;
            case "alt": return Camera.alt;
            case "first": return Camera.first;
            case "second": return Camera.second;
            case "third": return Camera.third;
            case "pgup": return Camera.pgup;
            case "pgdn": return Camera.pgdn;
            default: return false;
        }
    }

    public boolean is_key_released(String keyname) {
        keyname = keyname.toLowerCase();
        switch (keyname) {
            case "left": return !Camera.left;
            case "right": return !Camera.right;
            case "forward": return !Camera.forward;
            case "back": return !Camera.back;
            case "enter": return !Camera.enter;
            case "space": return !Camera.space;
            case "ctrl": return !Camera.ctrl;
            case "strafeleft": return !Camera.strafeleft;
            case "straferight": return !Camera.straferight;
            case "alt": return !Camera.alt;
            case "first": return !Camera.first;
            case "second": return !Camera.second;
            case "third": return !Camera.third;
            case "pgup": return !Camera.pgup;
            case "pgdn": return !Camera.pgdn;
            default: return true; // consider default as released
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
