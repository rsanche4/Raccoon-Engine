import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Map;
import java.awt.Robot;
import java.awt.AWTException;
import java.awt.Point;

import javax.swing.JFrame;

public class Camera implements KeyListener, MouseMotionListener, MouseListener {
	// General controls
	public static boolean esc, f4;
	public static boolean esc_once, f4_once;
	private boolean esc_once_flag, f4_once_flag;
	
	// Weapon/Item slots
	public static boolean first, second, third, fourth;
	public static boolean first_once, second_once, third_once, fourth_once;
	private boolean first_once_flag, second_once_flag, third_once_flag, fourth_once_flag;
	
	// Movement & Actions
	public static boolean forward, strafeleft, back, straferight;
	public static boolean interact, reload, ctrl, space;
	public static boolean forward_once, strafeleft_once, back_once, straferight_once;
	public static boolean interact_once, reload_once, ctrl_once, space_once;
	private boolean forward_once_flag, strafeleft_once_flag, back_once_flag, straferight_once_flag;
	private boolean interact_once_flag, reload_once_flag, ctrl_once_flag, space_once_flag;
	
	// Menu controls
	public static boolean enter, menuup, menudown, menuleft, menuright;
	public static boolean enter_once, menuup_once, menudown_once, menuleft_once, menuright_once;
	private boolean enter_once_flag, menuup_once_flag, menudown_once_flag, menuleft_once_flag, menuright_once_flag;
	
	// Mouse controls
	public static boolean leftclick;
	public static boolean leftclick_once;
	private boolean leftclick_once_flag;
	
	private boolean fullscreen = false;
	public static float direction_rad;
	public static float TURN_SPEED = 0.02f;
	public static int PITCH_SPEED = 8;
	public static float MOUSE_SENSITIVITY = 0.001f;
	public static float MOVE_SPEED = 0.15f;
	public static float JUMP_UP_SPEED = 0.2f;
	public static float CROUCHING_SPEED = 0.5f;
	public static float gravity_up_multiplier = 1;
	public static float gravity_down_multiplier = 1;
	public static float player_x, player_y, player_z;
	public static int player_sector;
	public static float retina_dist = 800;
	public static float player_height = 2;
	private boolean jumping_in_progress = false;
	private float crouch_diff_height = player_height/2;
	private float jump_diff_height = player_height/4;
	private boolean jumping_down_flag = false;
	private boolean crouching_in_progress = false;
	public static float buffer_dist = 0.2f;
	private int pitch = 0;
	private int max_pitch = 400;
	private int min_pitch = -max_pitch;
	private int half_screen_h = Screen.half_screen_height;
	
	// Mouse tracking
	private Point lastMousePos;
	private Robot robot;
	private JFrame frame;
	private boolean mouseCaptured = false;
	private java.awt.Cursor invisibleCursor;
	
	public Camera() {
		direction_rad = 0;
		player_x = player_y = player_z = 0;
		player_sector = 1;
		try {
			robot = new Robot();
			
			// Create invisible cursor
			java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
			java.awt.image.BufferedImage cursorImage = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			invisibleCursor = toolkit.createCustomCursor(cursorImage, new java.awt.Point(0, 0), "InvisibleCursor");
		} catch (AWTException e) {
			e.printStackTrace();
		}
	}
	
	public void setFrame(JFrame frame) {
		this.frame = frame;
		// Hide the cursor when frame is set
		if (frame != null && invisibleCursor != null) {
			frame.setCursor(invisibleCursor);
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {
		return;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		// General
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			esc = true;
			if (!esc_once_flag) {
				esc_once = true;
				esc_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_F4) {
			f4 = true;
			if (!f4_once_flag) {
				f4_once = true;
				f4_once_flag = true;
			}
		}
		
		// Weapon/Item slots
		if (e.getKeyCode() == KeyEvent.VK_1) {
			first = true;
			if (!first_once_flag) {
				first_once = true;
				first_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_2) {
			second = true;
			if (!second_once_flag) {
				second_once = true;
				second_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_3) {
			third = true;
			if (!third_once_flag) {
				third_once = true;
				third_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_4) {
			fourth = true;
			if (!fourth_once_flag) {
				fourth_once = true;
				fourth_once_flag = true;
			}
		}
		
		// Movement & Actions
		if (e.getKeyCode() == KeyEvent.VK_W) {
			forward = true;
			if (!forward_once_flag) {
				forward_once = true;
				forward_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_A) {
			strafeleft = true;
			if (!strafeleft_once_flag) {
				strafeleft_once = true;
				strafeleft_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_S) {
			back = true;
			if (!back_once_flag) {
				back_once = true;
				back_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_D) {
			straferight = true;
			if (!straferight_once_flag) {
				straferight_once = true;
				straferight_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_E) {
			interact = true;
			if (!interact_once_flag) {
				interact_once = true;
				interact_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_R) {
			reload = true;
			if (!reload_once_flag) {
				reload_once = true;
				reload_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
			ctrl = true;
			if (!ctrl_once_flag) {
				ctrl_once = true;
				ctrl_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			space = true;
			if (!space_once_flag) {
				space_once = true;
				space_once_flag = true;
			}
		}
		
		// Menu controls
		if (e.getKeyCode() == KeyEvent.VK_ENTER) {
			enter = true;
			if (!enter_once_flag) {
				enter_once = true;
				enter_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			menuup = true;
			if (!menuup_once_flag) {
				menuup_once = true;
				menuup_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			menudown = true;
			if (!menudown_once_flag) {
				menudown_once = true;
				menudown_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			menuleft = true;
			if (!menuleft_once_flag) {
				menuleft_once = true;
				menuleft_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			menuright = true;
			if (!menuright_once_flag) {
				menuright_once = true;
				menuright_once_flag = true;
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// General
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			esc = false;
			esc_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_F4) {
			f4 = false;
			f4_once_flag = false;
		}
		
		// Weapon/Item slots
		if (e.getKeyCode() == KeyEvent.VK_1) {
			first = false;
			first_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_2) {
			second = false;
			second_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_3) {
			third = false;
			third_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_4) {
			fourth = false;
			fourth_once_flag = false;
		}
		
		// Movement & Actions
		if (e.getKeyCode() == KeyEvent.VK_W) {
			forward = false;
			forward_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_A) {
			strafeleft = false;
			strafeleft_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_S) {
			back = false;
			back_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_D) {
			straferight = false;
			straferight_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_E) {
			interact = false;
			interact_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_R) {
			reload = false;
			reload_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
			ctrl = false;
			ctrl_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			space = false;
			space_once_flag = false;
		}
		
		// Menu controls
		if (e.getKeyCode() == KeyEvent.VK_ENTER) {
			enter = false;
			enter_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			menuup = false;
			menuup_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			menudown = false;
			menudown_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			menuleft = false;
			menuleft_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			menuright = false;
			menuright_once_flag = false;
		}
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		mouseMoved(e);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// Always try to capture mouse on movement for easier control
		if (frame == null) return;
		
		if (!mouseCaptured) {
			mouseCaptured = true;
			Point frameLocation = frame.getLocationOnScreen();
			int centerX = frameLocation.x + frame.getWidth() / 2;
			int centerY = frameLocation.y + frame.getHeight() / 2;
			lastMousePos = new Point(centerX, centerY);
			if (robot != null) {
				robot.mouseMove(centerX, centerY);
			}
			return;
		}
		
		Point currentPos = e.getLocationOnScreen();
		
		if (lastMousePos != null) {
			int deltaX = currentPos.x - lastMousePos.x;
			int deltaY = currentPos.y - lastMousePos.y;
			
			// Only process if there's actual movement
			if (deltaX != 0 || deltaY != 0) {
				// Horizontal mouse movement for turning (left/right)
				direction_rad -= deltaX * MOUSE_SENSITIVITY;
				if (direction_rad < 0) {
					direction_rad += 2 * Math.PI;
				}
				direction_rad = (float) (direction_rad % (2 * Math.PI));
				
				// Vertical mouse movement for pitch (up/down)
				pitch -= deltaY;
				pitch = Math.max(min_pitch, Math.min(max_pitch, pitch));
				Screen.half_screen_height = half_screen_h + pitch;
			}
		}
		
		// Re-center mouse
		Point frameLocation = frame.getLocationOnScreen();
		int centerX = frameLocation.x + frame.getWidth() / 2;
		int centerY = frameLocation.y + frame.getHeight() / 2;
		
		if (robot != null) {
			robot.mouseMove(centerX, centerY);
		}
		
		lastMousePos = new Point(centerX, centerY);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			leftclick = true;
			if (!leftclick_once_flag) {
				leftclick_once = true;
				leftclick_once_flag = true;
			}
		}
		
		// Capture mouse on first click
		if (!mouseCaptured && frame != null) {
			mouseCaptured = true;
			Point frameLocation = frame.getLocationOnScreen();
			int centerX = frameLocation.x + frame.getWidth() / 2;
			int centerY = frameLocation.y + frame.getHeight() / 2;
			lastMousePos = new Point(centerX, centerY);
			if (robot != null) {
				robot.mouseMove(centerX, centerY);
			}
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			leftclick = false;
			leftclick_once_flag = false;
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	private boolean isPortalSolid(int sectorA, int sectorB) {
		int key = Integer.parseInt(sectorA+""+sectorB);
		if (Screen.portalCollisionData.containsKey(key)) {
			return Screen.portalCollisionData.get(key);
		}
		return false;
	}
	
	private boolean collisionWithSprites(float srcx, float srcz) {
		for (Map.Entry<String, Sprite> entry : Main.allSprites.entrySet()) {
			Sprite entity = entry.getValue();
			float dist = Screen.euclid_dist(srcx, srcz, entity.spriteXPos, entity.spriteZPos);
			if (dist < entity.collision_radius) {
				return true;
			}
		}
		return false;
	}
	
	private boolean is_collision(float val_to_add_x, float val_to_add_z) {
		float temp_x = player_x;
		float temp_z = player_z;
		
		temp_x += val_to_add_x;
		temp_z += val_to_add_z;
		
		int possible_sector = Screen.update_player_sector(temp_x, temp_z);
		int sectorA = Math.min(possible_sector, player_sector);
		int sectorB = Math.max(possible_sector, player_sector);
		if ((possible_sector<0) || (sectorA!=sectorB && isPortalSolid(sectorA, sectorB)) || 
				(Screen.sectorMap.get(possible_sector).floor_height>=player_y) || (player_y>=Screen.sectorMap.get(possible_sector).ceil_height) ||
				(collisionWithSprites(temp_x, temp_z))) {
			return true;
		}
		if (possible_sector>0) {
			return false;
		}
		return true;
	}
	
	public void update(JFrame frame) {
		// Handle ESC to quit
		if (esc_once) {
			System.exit(0);
		}
		
		// Handle F4 for fullscreen
		if (f4_once) {
			fullscreen = !fullscreen;
			if (fullscreen) {
				Main.SCREEN_W = Main.USER_SCREEN_SIZE_W;
			    Main.SCREEN_H = Main.USER_SCREEN_SIZE_H;
			    frame.dispose();
			    frame.setUndecorated(true);
			    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
			    Main.reinitializeBuffers(frame);
			    frame.setVisible(true);
			} else {
				Main.SCREEN_W = 800;
			    Main.SCREEN_H = 600;
			    frame.dispose();
			    frame.setUndecorated(false);
			    frame.setExtendedState(JFrame.NORMAL);
			    Main.reinitializeBuffers(frame);
			    frame.setLocationRelativeTo(null);
			    frame.setVisible(true);
			}
		}
		
		if (Screen.sectorMap!=null) {
			// Movement (WASD)
			if (forward) {			
				if (!is_collision((float)Math.cos(direction_rad) * (MOVE_SPEED + buffer_dist), 0)) {
					player_x += Math.cos(direction_rad) * MOVE_SPEED;
				}
				if (!is_collision(0, (float)Math.sin(direction_rad) * (MOVE_SPEED + buffer_dist))) {
					player_z += Math.sin(direction_rad) * MOVE_SPEED;
				}
			}
			else if (back) {
				if (!is_collision((float) -Math.cos(direction_rad) * (MOVE_SPEED + buffer_dist), 0)) {
					player_x -= Math.cos(direction_rad) * MOVE_SPEED;
				}
				if (!is_collision(0, (float) -Math.sin(direction_rad) * (MOVE_SPEED + buffer_dist))) {
					player_z -= Math.sin(direction_rad) * MOVE_SPEED;
				}
			}
			if (straferight) {
				if (!is_collision((float)Math.cos(direction_rad - Math.PI/2) * (MOVE_SPEED + buffer_dist), 0)) {
					player_x += Math.cos(direction_rad - Math.PI/2) * MOVE_SPEED;
				}
				if (!is_collision(0, (float)Math.sin(direction_rad - Math.PI/2) * (MOVE_SPEED + buffer_dist))) {
					player_z += Math.sin(direction_rad - Math.PI/2) * MOVE_SPEED;
				}
			}
			else if (strafeleft) {
				if (!is_collision((float)Math.cos(direction_rad + Math.PI/2) * (MOVE_SPEED + buffer_dist), 0)) {
					player_x += Math.cos(direction_rad + Math.PI/2) * MOVE_SPEED;
				}
				if (!is_collision(0, (float)Math.sin(direction_rad + Math.PI/2) * (MOVE_SPEED + buffer_dist))) {
					player_z += Math.sin(direction_rad + Math.PI/2) * MOVE_SPEED;
				}
			}

			// Jumping and crouching logic (unchanged)
			float sector_h = Screen.sectorMap.get(player_sector).floor_height;
			float player_h_limit = sector_h+player_height;
			float player_f_limit = sector_h+crouch_diff_height;
			float move_up_speed = JUMP_UP_SPEED*gravity_up_multiplier;
			float move_dn_speed = JUMP_UP_SPEED*gravity_down_multiplier;

			if (space_once) {
				jumping_in_progress = true;
			} else if (jumping_in_progress) {
				if (!jumping_down_flag && player_y<player_h_limit+jump_diff_height) {
					player_y += move_up_speed;
				} else if (player_y > player_h_limit) {
					player_y -= move_dn_speed;
					jumping_down_flag = true;
				} else {
					jumping_in_progress = false;
					jumping_down_flag = false;
				}
			} else if (!crouching_in_progress && ctrl) {
				crouching_in_progress = true;
			} else if (crouching_in_progress) {
				if (player_y > player_f_limit) {
					player_y -= CROUCHING_SPEED;
				} else {
					crouching_in_progress = false;
				}
			} else {
				if (Math.abs(player_y-player_h_limit)<move_up_speed || Math.abs(player_y-player_h_limit)<move_dn_speed) {
					player_y = player_h_limit;
				}
				else if (player_y<player_h_limit) {
					player_y += move_up_speed;
				}
				else if (player_y > player_h_limit) {
					player_y -= move_dn_speed;
				}
			}
		}

		// Reset all "once" flags after processing
		if (esc_once_flag) esc_once = false;
		if (f4_once_flag) f4_once = false;
		if (first_once_flag) first_once = false;
		if (second_once_flag) second_once = false;
		if (third_once_flag) third_once = false;
		if (fourth_once_flag) fourth_once = false;
		if (forward_once_flag) forward_once = false;
		if (strafeleft_once_flag) strafeleft_once = false;
		if (back_once_flag) back_once = false;
		if (straferight_once_flag) straferight_once = false;
		if (interact_once_flag) interact_once = false;
		if (reload_once_flag) reload_once = false;
		if (ctrl_once_flag) ctrl_once = false;
		if (space_once_flag) space_once = false;
		if (enter_once_flag) enter_once = false;
		if (menuup_once_flag) menuup_once = false;
		if (menudown_once_flag) menudown_once = false;
		if (menuleft_once_flag) menuleft_once = false;
		if (menuright_once_flag) menuright_once = false;
		if (leftclick_once_flag) leftclick_once = false;
	}
}