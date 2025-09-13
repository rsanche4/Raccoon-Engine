import java.io.FileReader;

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
        if (Main.active_scripts != null && script_index >= 0 && script_index < Main.active_scripts.size()) {
            Main.active_scripts.remove(script_index);
        }
    }
    
    public void set_skybox(String skyboxname, float brightness) {
    	Screen.skybox = skyboxname;
    	Screen.skybox_brightness = brightness;
    }
    
    public void load_map(String mapname) {
    	// Clean the structures of the screen so they are blank
    	// Read the mapname file and parse it so that you save the stuff into some sort of structure of Screen
    	// Now screen will use that strucutre to render
    }
    
    public void set_atomic_xz_unit(int u) {
    	Screen.atomic_xz_unit = u;
    }
    
    public int get_atomic_xz_unit() {
    	return Screen.atomic_xz_unit;
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
