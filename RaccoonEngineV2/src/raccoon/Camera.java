package raccoon;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;

import javax.swing.JFrame;

public class Camera implements KeyListener, MouseMotionListener, MouseListener {

	public static HashMap<String, Boolean> key_states = new HashMap<>();
	public static int player_sector = 0;
	public static double player_x = 0;
	public static double player_y = 0; 
	public static double player_z = 0; 
	public static double direction_rad = 0; // TODO ensure angles are non-negative, do not overflow over pi2
	public static double retina_dist = Main.GAME_WID/2;
	public static boolean jetpack = false;
	public static double move_speed = 0;
	public static double turn_speed = 0;
	public static double pitch_speed = 0;
	public static double mouse_sens = 0;
	
	public Camera() {
		initializeKeyMap();
	}
	
	public void initializeKeyMap() {
        key_states.put("esc", false);
        key_states.put("f1", false);
        key_states.put("f2", false);
        key_states.put("f3", false);
        key_states.put("f4", false);
        key_states.put("f5", false);
        key_states.put("f6", false);
        key_states.put("f7", false);
        key_states.put("f8", false);
        key_states.put("f9", false);
        key_states.put("f10", false);
        key_states.put("f11", false);
        key_states.put("f12", false);
        key_states.put("pgup", false);
        key_states.put("pgdn", false);
        key_states.put("home", false);
        key_states.put("end", false);
        key_states.put("insert", false);
        key_states.put("delete", false);
        key_states.put("arrow_up", false);
        key_states.put("arrow_down", false);
        key_states.put("arrow_right", false);
        key_states.put("arrow_left", false);
        key_states.put("backspace", false);
        key_states.put("enter", false);
        key_states.put("shift", false);
        key_states.put("ctrl", false);
        key_states.put("1", false);
        key_states.put("2", false);
        key_states.put("3", false);
        key_states.put("4", false);
        key_states.put("5", false);
        key_states.put("6", false);
        key_states.put("7", false);
        key_states.put("8", false);
        key_states.put("9", false);
        key_states.put("0", false);
        key_states.put("tab", false);
        key_states.put("alt", false);
        key_states.put("space", false);
        key_states.put("tilde", false);
        String[] letters = {"q","w","e","r","t","y","u","i","o","p","a","s","d","f","g","h","j","k","l","z","x","c","v","b","n","m"};
        for (String letter : letters) {
            key_states.put(letter, false);
        }
        key_states.put("minus", false);
        key_states.put("plus", false);
        key_states.put("left_bracket", false);
        key_states.put("right_bracket", false);
        key_states.put("colon", false);
        key_states.put("quote", false);
        key_states.put("pipe", false);
        key_states.put("comma", false);
        key_states.put("dot", false);
        key_states.put("slash", false);
        key_states.put("mouse_left", false);
        key_states.put("mouse_right", false);
        key_states.put("mouse_wheel_up", false);
        key_states.put("mouse_wheel_down", false);
    }
	
	public void update() {
		
	}
	
}
