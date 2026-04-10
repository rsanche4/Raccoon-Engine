package raccoon;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JFrame;

public class Camera implements KeyListener, MouseMotionListener, MouseListener {

	public static int player_sector = 0;
	public static int player_x = 0;
	public static int player_y = 0; 
	public static int player_z = 0; 
	public static int direction_value = 0;
	public static int retina_dist = Main.GAME_WID/2;
	
	public Camera() {
		
	}
	
	public void update(JFrame frame) {
		
	}
	
	public void setFrame(JFrame frame) {
		
	}
	
}
