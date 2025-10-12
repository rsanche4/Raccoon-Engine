import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;

public class Camera implements KeyListener {
	public static boolean left, right, forward, back, enter, space, ctrl, strafeleft, straferight, first, second, third, pgup, pgdn, fourth, f4;
	public static boolean left_once, right_once, forward_once, back_once, enter_once, space_once, ctrl_once, strafeleft_once, straferight_once, first_once, second_once, third_once, pgup_once, pgdn_once, fourth_once, f4_once;
	private boolean left_once_flag, right_once_flag, forward_once_flag, back_once_flag, enter_once_flag, space_once_flag, ctrl_once_flag, strafeleft_once_flag, straferight_once_flag, first_once_flag, second_once_flag, third_once_flag, pgup_once_flag, pgdn_once_flag, fourth_once_flag, f4_once_flag;
	private boolean fullscreen = false;
	public static double direction_rad;
	public static double TURN_SPEED = 0.03;
	public static double MOVE_SPEED = 0.2;
	public static double JUMP_UP_SPEED = 0.2;
	public static double CROUCHING_SPEED = 0.5;
	public static double gravity_up_multiplier = 1;
	public static double gravity_down_multiplier = 1;
	public static double player_x, player_y, player_z;
	public static int player_sector;
	public static double retina_dist = 8;
	public static double player_height = 2;
	private boolean jumping_in_progress = false;
	private double crouch_diff_height = player_height/2;
	private double jump_diff_height = player_height/4;
	private boolean jumping_down_flag = false;
	private boolean crouching_in_progress = false;
	private double buffer_dist = 0.2;
	

	public Camera() {
		direction_rad = 0;
		player_x = player_y = player_z = 0;
		player_sector = 1;
	}

	@Override
	public void keyTyped(KeyEvent e) {
		return;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			left = true;
			if (!left_once_flag) {
				left_once = true;
				left_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			right = true;
			if (!right_once_flag) {
				right_once = true;
				right_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			forward = true;
			if (!forward_once_flag) {
				forward_once = true;
				forward_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			back = true;
			if (!back_once_flag) {
				back_once = true;
				back_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_ENTER) {
			enter = true;
			if (!enter_once_flag) {
				enter_once = true;
				enter_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			space = true;
			if (!space_once_flag) {
				space_once = true;
				space_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
			ctrl = true;
			if (!ctrl_once_flag) {
				ctrl_once = true;
				ctrl_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_COMMA) {
			strafeleft = true;
			if (!strafeleft_once_flag) {
				strafeleft_once = true;
				strafeleft_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_PERIOD) {
			straferight = true;
			if (!straferight_once_flag) {
				straferight_once = true;
				straferight_once_flag = true;
			}
		}
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
		if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
			pgup = true;
			if (!pgup_once_flag) {
				pgup_once = true;
				pgup_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
			pgdn = true;
			if (!pgdn_once_flag) {
				pgdn_once = true;
				pgdn_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_4) {
			fourth = true;
			if (!fourth_once_flag) {
				fourth_once = true;
				fourth_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_F4) {
			f4 = true;
			if (!f4_once_flag) {
				f4_once = true;
				f4_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			System.exit(0);
		}
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			left = false;
			left_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			right = false;
			right_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			forward = false;
			forward_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			back = false;
			back_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_ENTER) {
			enter = false;
			enter_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			space = false;
			space_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
			ctrl = false;
			ctrl_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_COMMA) {
			strafeleft = false;
			strafeleft_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_PERIOD) {
			straferight = false;
			straferight_once_flag = false;
		}
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
		if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
			pgup = false;
			pgup_once = false;
			pgup_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
			pgdn = false;
			pgdn_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_4) {
			fourth = false;
			fourth_once_flag = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_F4) {
			f4 = false;
			f4_once_flag = false;
		}
	}

	private boolean is_collision(Sector cur_sector, double val_to_add_x, double val_to_add_z) {
		double temp_x = player_x;
		double temp_z = player_z;
		
		temp_x += val_to_add_x;
		temp_z += val_to_add_z;
		
		int possible_sector = Screen.update_player_sector(temp_x, temp_z);
		
		if ((possible_sector<0) || (pgdn && Screen.sectorMap.get(possible_sector).floor_height>cur_sector.floor_height)) {
			return true;
		}
		if (possible_sector>=0 && !cur_sector.collision_data[possible_sector-1]) {
			return false;
		}
		return true;
	}
	
	public void update(JFrame frame) {
		if (f4_once) {
			fullscreen = !fullscreen;
			if (fullscreen) {
				Main.SCREEN_W = Main.USER_SCREEN_SIZE_W;
			    Main.SCREEN_H = Main.USER_SCREEN_SIZE_H;
			    frame.dispose(); // Remove current window
			    frame.setUndecorated(true); // Remove window borders
			    frame.setExtendedState(JFrame.MAXIMIZED_BOTH); // Maximize
			    Main.reinitializeBuffers(frame);
			    frame.setVisible(true);
			} else {
				Main.SCREEN_W = 800;
			    Main.SCREEN_H = 600;
			    frame.dispose();
			    frame.setUndecorated(false);
			    frame.setExtendedState(JFrame.NORMAL);
			    Main.reinitializeBuffers(frame);
			    frame.setVisible(true);
			}
		}
		
		Sector cur_sector = Screen.sectorMap.get(player_sector);

		if (right) {
			direction_rad = (direction_rad - TURN_SPEED) % (2*Math.PI);
			if (direction_rad < 0) {
				direction_rad += 2 * Math.PI;
			}
		}
		else if (left) {
			direction_rad = (direction_rad + TURN_SPEED) % (2*Math.PI);
		}
		if (forward) {			
			if (!is_collision(cur_sector, Math.cos(direction_rad) * (MOVE_SPEED + buffer_dist), 0)) {
				player_x += Math.cos(direction_rad) * MOVE_SPEED;
			}
			if (!is_collision(cur_sector, 0, Math.sin(direction_rad) * (MOVE_SPEED + buffer_dist))) {
				player_z += Math.sin(direction_rad) * MOVE_SPEED;
			}
		}
		else if (back) {
			if (!is_collision(cur_sector, -Math.cos(direction_rad) * (MOVE_SPEED + buffer_dist), 0)) {
				player_x -= Math.cos(direction_rad) * MOVE_SPEED;
			}
			if (!is_collision(cur_sector, 0, -Math.sin(direction_rad) * (MOVE_SPEED + buffer_dist))) {
				player_z -= Math.sin(direction_rad) * MOVE_SPEED;
			}
		}
		if (straferight) {
			if (!is_collision(cur_sector, Math.cos(direction_rad - Math.PI/2) * (MOVE_SPEED + buffer_dist), 0)) {
				player_x += Math.cos(direction_rad - Math.PI/2) * MOVE_SPEED;
			}
			if (!is_collision(cur_sector, 0, Math.sin(direction_rad - Math.PI/2) * (MOVE_SPEED + buffer_dist))) {
				player_z += Math.sin(direction_rad - Math.PI/2) * MOVE_SPEED;
			}
		}
		else if (strafeleft) {
			if (!is_collision(cur_sector, Math.cos(direction_rad + Math.PI/2) * (MOVE_SPEED + buffer_dist), 0)) {
				player_x += Math.cos(direction_rad + Math.PI/2) * MOVE_SPEED;
			}
			if (!is_collision(cur_sector, 0, Math.sin(direction_rad + Math.PI/2) * (MOVE_SPEED + buffer_dist))) {
				player_z += Math.sin(direction_rad + Math.PI/2) * MOVE_SPEED;
			}
		}

		double sector_h = cur_sector.floor_height;
		double player_h_limit = sector_h+player_height;
		double player_f_limit = sector_h+crouch_diff_height;
		double move_up_speed = JUMP_UP_SPEED*gravity_up_multiplier;
		double move_dn_speed = JUMP_UP_SPEED*gravity_down_multiplier;

		if (pgup_once) {
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
		} else if (!crouching_in_progress && pgdn) {
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
		

		// Reset all "once" flags after processing
		if (left_once_flag) {
			left_once = false;
		}
		if (right_once_flag) {
			right_once = false;
		}
		if (forward_once_flag) {
			forward_once = false;
		}
		if (back_once_flag) {
			back_once = false;
		}
		if (enter_once_flag) {
			enter_once = false;
		}
		if (space_once_flag) {
			space_once = false;
		}
		if (ctrl_once_flag) {
			ctrl_once = false;
		}
		if (strafeleft_once_flag) {
			strafeleft_once = false;
		}
		if (straferight_once_flag) {
			straferight_once = false;
		}
		if (first_once_flag) {
			first_once = false;
		}
		if (second_once_flag) {
			second_once = false;
		}
		if (third_once_flag) {
			third_once = false;
		}
		if (pgup_once_flag) {
			pgup_once = false;
		}
		if (pgdn_once_flag) {
			pgdn_once = false;
		}
		if (fourth_once_flag) {
			fourth_once = false;
		}
		if (f4_once_flag) {
			f4_once = false;
		}
	}
}