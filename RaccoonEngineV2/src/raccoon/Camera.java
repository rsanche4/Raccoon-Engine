package raccoon;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;

import javax.swing.JFrame;

public class Camera implements KeyListener, MouseMotionListener, MouseListener {

	public static HashMap<String, Boolean> keyStates = new HashMap<>();
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
        keyStates.put("esc", false);
        keyStates.put("f1", false);
        keyStates.put("f2", false);
        keyStates.put("f3", false);
        keyStates.put("f4", false);
        keyStates.put("f5", false);
        keyStates.put("f6", false);
        keyStates.put("f7", false);
        keyStates.put("f8", false);
        keyStates.put("f9", false);
        keyStates.put("f10", false);
        keyStates.put("f11", false);
        keyStates.put("f12", false);
        keyStates.put("pgup", false);
        keyStates.put("pgdn", false);
        keyStates.put("home", false);
        keyStates.put("end", false);
        keyStates.put("insert", false);
        keyStates.put("delete", false);
        keyStates.put("arrow_up", false);
        keyStates.put("arrow_down", false);
        keyStates.put("arrow_right", false);
        keyStates.put("arrow_left", false);
        keyStates.put("backspace", false);
        keyStates.put("enter", false);
        keyStates.put("shift", false);
        keyStates.put("ctrl", false);
        keyStates.put("1", false);
        keyStates.put("2", false);
        keyStates.put("3", false);
        keyStates.put("4", false);
        keyStates.put("5", false);
        keyStates.put("6", false);
        keyStates.put("7", false);
        keyStates.put("8", false);
        keyStates.put("9", false);
        keyStates.put("0", false);
        keyStates.put("tab", false);
        keyStates.put("alt", false);
        keyStates.put("space", false);
        keyStates.put("tilde", false);
        String[] letters = {"q","w","e","r","t","y","u","i","o","p","a","s","d","f","g","h","j","k","l","z","x","c","v","b","n","m"};
        for (String letter : letters) {
            keyStates.put(letter, false);
        }
        keyStates.put("minus", false);
        keyStates.put("plus", false);
        keyStates.put("left_bracket", false);
        keyStates.put("right_bracket", false);
        keyStates.put("colon", false);
        keyStates.put("quote", false);
        keyStates.put("pipe", false);
        keyStates.put("comma", false);
        keyStates.put("dot", false);
        keyStates.put("slash", false);
        keyStates.put("mouse_left", false);
        keyStates.put("mouse_right", false);
        keyStates.put("mouse_wheel_up", false);
        keyStates.put("mouse_wheel_down", false);
    }
	
	public void update() {
		
	}
	
}
