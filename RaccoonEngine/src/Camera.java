import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;


// Camera Class
// Description: The class that allows for movement of the player when inputting keys. Main keys are detected here.
public class Camera implements KeyListener {
	public static boolean left, right, forward, back, enter, space, ctrl, strafeleft, straferight, alt, first, second, third;
	public static boolean left_once, right_once, forward_once, back_once, enter_once, space_once, ctrl_once, strafeleft_once, straferight_once, alt_once, first_once, second_once, third_once;
	private boolean left_once_flag, right_once_flag, forward_once_flag, back_once_flag, enter_once_flag, space_once_flag, ctrl_once_flag, strafeleft_once_flag, straferight_once_flag, alt_once_flag, first_once_flag, second_once_flag, third_once_flag;
	public static double direction_rad;
	public static double TURN_SPEED = 0.04;
	public static double MOVE_SPEED = 0.08;
	public static double player_x, player_y, player_z;
	public static double retina_dist = 4;
	
	public Camera() {
		direction_rad = 0;
		player_x = player_y = player_z = 0;
	}

	@Override
	public void keyTyped(KeyEvent e) {
		return;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_LEFT) {
			left = true;
			if (left_once_flag) {
				left_once = false;
			} else {
				left_once = true;
				left_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
			right = true;
			if (right_once_flag) {
				right_once = false;
			} else {
				right_once = true;
				right_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_UP) {
			forward = true;
			if (forward_once_flag) {
				forward_once = false;
			} else {
				forward_once = true;
				forward_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_DOWN) {
			back = true;
			if (back_once_flag) {
				back_once = false;
			} else {
				back_once = true;
				back_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_ENTER) {
			enter = true;
			if (enter_once_flag) {
				enter_once = false;
			} else {
				enter_once = true;
				enter_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			space = true;
			if (space_once_flag) {
				space_once = false;
			} else {
				space_once = true;
				space_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
			ctrl = true;
			if (ctrl_once_flag) {
				ctrl_once = false;
			} else {
				ctrl_once = true;
				ctrl_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_COMMA) {
			strafeleft = true;
			if (strafeleft_once_flag) {
				strafeleft_once = false;
			} else {
				strafeleft_once = true;
				strafeleft_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_PERIOD) {
			straferight = true;
			if (straferight_once_flag) {
				straferight_once = false;
			} else {
				straferight_once = true;
				straferight_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_ALT) {
			alt = true;
			if (alt_once_flag) {
				alt_once = false;
			} else {
				alt_once = true;
				alt_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_1) {
			first = true;
			if (first_once_flag) {
				first_once = false;
			} else {
				first_once = true;
				first_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_2) {
			second = true;
			if (second_once_flag) {
				second_once = false;
			} else {
				second_once = true;
				second_once_flag = true;
			}
		}
		if (e.getKeyCode() == KeyEvent.VK_3) {
			third = true;
			if (third_once_flag) {
				third_once = false;
			} else {
				third_once = true;
				third_once_flag = true;
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
        if (e.getKeyCode() == KeyEvent.VK_ALT) {
            alt = false;
            alt_once_flag = false;
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
	}

	public void update() {
		if (right) {
			direction_rad = (direction_rad - TURN_SPEED) % (2*Math.PI);
		    if (direction_rad < 0) {
		        direction_rad += 2 * Math.PI;
		    }
		}
		if (left) {
			direction_rad = (direction_rad + TURN_SPEED) % (2*Math.PI);
		}
	}
}