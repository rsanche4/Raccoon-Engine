package raccoon;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Camera implements KeyListener, MouseMotionListener, MouseListener, MouseWheelListener {

    public static int    player_sector = 0;
    public static double player_x = 0;
    public static double player_y = 0;
    public static double player_z = 0;
    public static double direction_rad = 0;
    public static double retina_dist = Main.GAME_WID / 2.0;
    public static boolean jetpack = false;
    public static double move_speed = 0.08;
    public static double turn_speed = 0.04;
    public static double pitch_speed = 1;
    public static double mouse_sens = 0.005;

    private double velocity_y = 0;
    private double walk_time = 0;
    
    public static double GRAVITY = 0.008;
    public static double FLOOR_OFFSET = 1.0;
    public static double BOB_SPEED = 0.15;
    public static double BOB_AMOUNT = 0.03;
    
    private final Set<Integer> DOWN_SET = new HashSet<>(); 
    private static Set<Integer> curr = new HashSet<>();  
    private static Set<Integer> prev = new HashSet<>();

    private volatile int mouseDX = 0;
    private volatile int mouseDY = 0;

    private static final int VK_MOUSE_LEFT = 10_001;
    private static final int VK_MOUSE_RIGHT = 10_002;
    private static final int VK_MOUSE_WHEEL_UP = 10_003;
    private static final int VK_MOUSE_WHEEL_DOWN = 10_004;

    private final double buffer_dist = 0.2;
    public static double pitch = 0;
    private final int max_pitch = 200;
    private final int min_pitch = -max_pitch;

    private Point lastMousePos;
    private Robot robot;
    private boolean mouseCaptured = false;
    private Cursor invisibleCursor;

    public static final HashMap<String, Integer> KEY_MAP = new HashMap<>();
    static {
        KEY_MAP.put("esc",           KeyEvent.VK_ESCAPE);
        KEY_MAP.put("space",         KeyEvent.VK_SPACE);
        KEY_MAP.put("enter",         KeyEvent.VK_ENTER);
        KEY_MAP.put("shift",         KeyEvent.VK_SHIFT);
        KEY_MAP.put("ctrl",          KeyEvent.VK_CONTROL);
        KEY_MAP.put("alt",           KeyEvent.VK_ALT);
        KEY_MAP.put("tab",           KeyEvent.VK_TAB);
        KEY_MAP.put("backspace",     KeyEvent.VK_BACK_SPACE);
        KEY_MAP.put("delete",        KeyEvent.VK_DELETE);
        KEY_MAP.put("insert",        KeyEvent.VK_INSERT);
        KEY_MAP.put("home",          KeyEvent.VK_HOME);
        KEY_MAP.put("end",           KeyEvent.VK_END);
        KEY_MAP.put("pgup",          KeyEvent.VK_PAGE_UP);
        KEY_MAP.put("pgdn",          KeyEvent.VK_PAGE_DOWN);
        KEY_MAP.put("arrow_up",      KeyEvent.VK_UP);
        KEY_MAP.put("arrow_down",    KeyEvent.VK_DOWN);
        KEY_MAP.put("arrow_left",    KeyEvent.VK_LEFT);
        KEY_MAP.put("arrow_right",   KeyEvent.VK_RIGHT);
        KEY_MAP.put("f1",  KeyEvent.VK_F1);  KEY_MAP.put("f2",  KeyEvent.VK_F2);
        KEY_MAP.put("f3",  KeyEvent.VK_F3);  KEY_MAP.put("f4",  KeyEvent.VK_F4);
        KEY_MAP.put("f5",  KeyEvent.VK_F5);  KEY_MAP.put("f6",  KeyEvent.VK_F6);
        KEY_MAP.put("f7",  KeyEvent.VK_F7);  KEY_MAP.put("f8",  KeyEvent.VK_F8);
        KEY_MAP.put("f9",  KeyEvent.VK_F9);  KEY_MAP.put("f10", KeyEvent.VK_F10);
        KEY_MAP.put("f11", KeyEvent.VK_F11); KEY_MAP.put("f12", KeyEvent.VK_F12);
        for (char c = 'a'; c <= 'z'; c++) {
            KEY_MAP.put(String.valueOf(c), KeyEvent.getExtendedKeyCodeForChar(c));
        }
        // digits
        for (char c = '0'; c <= '9'; c++) {
            KEY_MAP.put(String.valueOf(c), KeyEvent.getExtendedKeyCodeForChar(c));
        }
        KEY_MAP.put("minus",         KeyEvent.VK_MINUS);
        KEY_MAP.put("plus",          KeyEvent.VK_EQUALS);
        KEY_MAP.put("left_bracket",  KeyEvent.VK_OPEN_BRACKET);
        KEY_MAP.put("right_bracket", KeyEvent.VK_CLOSE_BRACKET);
        KEY_MAP.put("colon",         KeyEvent.VK_SEMICOLON);
        KEY_MAP.put("quote",         KeyEvent.VK_QUOTE);
        KEY_MAP.put("pipe",          KeyEvent.VK_BACK_SLASH);
        KEY_MAP.put("comma",         KeyEvent.VK_COMMA);
        KEY_MAP.put("dot",           KeyEvent.VK_PERIOD);
        KEY_MAP.put("slash",         KeyEvent.VK_SLASH);
        KEY_MAP.put("tilde",         KeyEvent.VK_BACK_QUOTE);
        KEY_MAP.put("mouse_left",       VK_MOUSE_LEFT);
        KEY_MAP.put("mouse_right",      VK_MOUSE_RIGHT);
        KEY_MAP.put("mouse_wheel_up",   VK_MOUSE_WHEEL_UP);
        KEY_MAP.put("mouse_wheel_down", VK_MOUSE_WHEEL_DOWN);
    }
    
    public Camera() {
        try {
            robot = new Robot();
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            BufferedImage cursorImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            invisibleCursor = toolkit.createCustomCursor(cursorImage, new Point(0, 0), "InvisibleCursor");
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void tickInput() {
        prev = curr;
        synchronized (DOWN_SET) {
            curr = new HashSet<>(DOWN_SET);
        }
        synchronized (DOWN_SET) {
            DOWN_SET.remove(VK_MOUSE_WHEEL_UP);
            DOWN_SET.remove(VK_MOUSE_WHEEL_DOWN);
        }
    }

    public static boolean isHeld(int vk) {
        return curr.contains(vk);
    }

    public static boolean isOnce(int vk) {
        return curr.contains(vk) && !prev.contains(vk);
    }

    public boolean isUp(int vk) {
        return !curr.contains(vk);
    }

    public boolean mouseLeftHeld()       { return isHeld(VK_MOUSE_LEFT); }
    public boolean mouseLeftOnce()       { return isOnce(VK_MOUSE_LEFT); }
    public boolean mouseRightHeld()      { return isHeld(VK_MOUSE_RIGHT); }
    public boolean mouseRightOnce()      { return isOnce(VK_MOUSE_RIGHT); }
    public boolean mouseWheelUpOnce()    { return isOnce(VK_MOUSE_WHEEL_UP); }
    public boolean mouseWheelDownOnce()  { return isOnce(VK_MOUSE_WHEEL_DOWN); }

    public int consumeMouseDX() {
        int v = mouseDX; mouseDX = 0; return v;
    }

    public int consumeMouseDY() {
        int v = mouseDY; mouseDY = 0; return v;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        synchronized (DOWN_SET) {
            DOWN_SET.add(e.getKeyCode());
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        synchronized (DOWN_SET) {
            DOWN_SET.remove(e.getKeyCode());
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { /* unused */ }

    @Override
    public void mousePressed(MouseEvent e) {
        synchronized (DOWN_SET) {
            if (e.getButton() == MouseEvent.BUTTON1) DOWN_SET.add(VK_MOUSE_LEFT);
            if (e.getButton() == MouseEvent.BUTTON3) DOWN_SET.add(VK_MOUSE_RIGHT);
        }
        if (!mouseCaptured) captureMouse(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        synchronized (DOWN_SET) {
            if (e.getButton() == MouseEvent.BUTTON1) DOWN_SET.remove(VK_MOUSE_LEFT);
            if (e.getButton() == MouseEvent.BUTTON3) DOWN_SET.remove(VK_MOUSE_RIGHT);
        }
    }

    @Override public void mouseClicked(MouseEvent e)  { }
    @Override public void mouseEntered(MouseEvent e)  { }
    @Override public void mouseExited(MouseEvent e)   { }

    @Override
    public void mouseMoved(MouseEvent e) {
        handleMouseMove(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        handleMouseMove(e);
    }

    private void handleMouseMove(MouseEvent e) {
        if (!mouseCaptured || lastMousePos == null || robot == null) return;

        int dx = e.getXOnScreen() - lastMousePos.x;
        int dy = e.getYOnScreen() - lastMousePos.y;

        if (dx == 0 && dy == 0) return;

        mouseDX += dx;
        mouseDY += dy;
        robot.mouseMove(lastMousePos.x, lastMousePos.y);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        synchronized (DOWN_SET) {
            if (e.getWheelRotation() < 0) DOWN_SET.add(VK_MOUSE_WHEEL_UP);
            else                          DOWN_SET.add(VK_MOUSE_WHEEL_DOWN);
        }
    }

    private void captureMouse(MouseEvent e) {
        mouseCaptured = true;
        lastMousePos = new Point(e.getXOnScreen(), e.getYOnScreen());
        e.getComponent().setCursor(invisibleCursor);
    }

    public void releaseMouse(Component comp) {
        mouseCaptured = false;
        lastMousePos = null;
        comp.setCursor(Cursor.getDefaultCursor());
    }

    private boolean isPortalSolid(int sector_a, int sector_b) {
    	return Screen.portal_collision_data[sector_a * Screen.sectors_count + sector_b];
    }

    private boolean collisionWithSprites(double srcx, double srcz) {
    	for (Map.Entry<String, Sprite> entry : ResourceManager.sprites.entrySet()) {
			Sprite entity = entry.getValue();
			if (entity.collision_radius==0) {
				continue;
			}
			double dist = Screen.euclidDist(srcx, srcz, entity.sprite_x_pos, entity.sprite_z_pos);
			if (dist < entity.collision_radius) {
				return true;
			}
		}
		return false;
    }

    private boolean isCollisionVertical(double temp_y) {
    	return (temp_y>=Screen.sectors[player_sector].ceil_height) || (temp_y<=Screen.sectors[player_sector].floor_height);
    }
    
    private boolean isCollisionHorizontal(double val_to_add_x, double val_to_add_z) {
    	double temp_x = player_x;
		double temp_z = player_z;
		temp_x += val_to_add_x;
		temp_z += val_to_add_z;
		int possible_sector = Screen.updatePlayerSector(temp_x, temp_z);
		int sector_a = player_sector;
		int sector_b = possible_sector;
        return (possible_sector<0) || (sector_a!=sector_b && isPortalSolid(sector_a, sector_b)) || (Screen.sectors[possible_sector].floor_height>=player_y) || (player_y>=Screen.sectors[possible_sector].ceil_height) || (collisionWithSprites(temp_x, temp_z));
    }

    public void update() {
    	tickInput();
        direction_rad -= consumeMouseDX() * mouse_sens;
        direction_rad = ((direction_rad % Table.pi2) + Table.pi2) % Table.pi2;
        pitch -= consumeMouseDY() * pitch_speed;
        if (isHeld(KeyEvent.VK_PAGE_DOWN)) {
        	pitch -= pitch_speed*2;
        } else if (isHeld(KeyEvent.VK_PAGE_UP)) {
        	pitch += pitch_speed*2;
        }
        if (pitch > max_pitch) pitch = max_pitch;
        if (pitch < min_pitch) pitch = min_pitch;
        
        if (isHeld(KeyEvent.VK_RIGHT)) {
            direction_rad = ((direction_rad - turn_speed) % Table.pi2 + Table.pi2) % Table.pi2;
        } else if (isHeld(KeyEvent.VK_LEFT)) {
            direction_rad = ((direction_rad + turn_speed) % Table.pi2 + Table.pi2) % Table.pi2;
        }
        
    	if (Screen.sectors_count!=0) {
    		if (isHeld(KeyEvent.VK_W)) {	
    			double step = move_speed + buffer_dist;
				if (!isCollisionHorizontal(Math.cos(direction_rad) * step, 0)) {
					player_x += Math.cos(direction_rad) * move_speed;
				}
				if (!isCollisionHorizontal(0, Math.sin(direction_rad) * step)) {
					player_z += Math.sin(direction_rad) * move_speed;
				}
			} else if (isHeld(KeyEvent.VK_S)) {
				double step = move_speed + buffer_dist;
				if (!isCollisionHorizontal(-Math.cos(direction_rad) * step, 0)) {
					player_x -= Math.cos(direction_rad) * move_speed;
				}
				if (!isCollisionHorizontal(0, -Math.sin(direction_rad) * step)) {
					player_z -= Math.sin(direction_rad) * move_speed;
				}
			}
			if (isHeld(KeyEvent.VK_D)) {
				double step = move_speed + buffer_dist;
				double theta = direction_rad - Table.pi_half_2;
				if (!isCollisionHorizontal(Math.cos(theta) * step, 0)) {
					player_x += Math.cos(theta) * move_speed;
				}
				if (!isCollisionHorizontal(0, Math.sin(theta) * step)) {
					player_z += Math.sin(theta) * move_speed;
				}
			} else if (isHeld(KeyEvent.VK_A)) {
				double step = move_speed + buffer_dist;
				double theta = direction_rad + Table.pi_half_2;
				if (!isCollisionHorizontal(Math.cos(theta) * step, 0)) {
					player_x += Math.cos(theta) * move_speed;
				}
				if (!isCollisionHorizontal(0, Math.sin(theta) * step)) {
					player_z += Math.sin(theta) * move_speed;
				}
			}
		}
		
		if (jetpack) {
			double temp_y=player_y;
			if (isHeld(KeyEvent.VK_SPACE)) { temp_y += move_speed; }
			else if (isHeld(KeyEvent.VK_CONTROL)) { temp_y -= move_speed; }
			if (!isCollisionVertical(temp_y)) {	player_y = temp_y; }
		} else {
		    // gravity
		    velocity_y -= GRAVITY;
		    double temp_y = player_y + velocity_y;
		    double floor = Screen.sectors[player_sector].floor_height + FLOOR_OFFSET;

		    if (temp_y <= floor) {
		        temp_y = floor;
		        velocity_y = 0;
		    }

		    // jump
		    if (isOnce(KeyEvent.VK_SPACE) && Math.abs(player_y - floor) < 0.05) {
		        velocity_y = 0.15;
		    }

		    player_y = temp_y;

		    // bob only when moving and on the floor
		    boolean on_floor = Math.abs(player_y - floor) < 0.05;
		    boolean moving = isHeld(KeyEvent.VK_W) || isHeld(KeyEvent.VK_S) || isHeld(KeyEvent.VK_A) || isHeld(KeyEvent.VK_D);
		    if (on_floor && moving) {
		        walk_time += BOB_SPEED;
		        player_y = temp_y + Math.sin(walk_time) * BOB_AMOUNT;
		    } else {
		        walk_time = 0;
		    }
		}
    	
    }
}