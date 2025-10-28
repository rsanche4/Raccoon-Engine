import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;

public class Main extends JFrame implements Runnable {
	private static final long serialVersionUID = 1L;
	public static float MAX_FPS = 30.0f;
	public static float currentFPS = 0;
	private static String game_title;
	private static String game_version;
	public static int SCREEN_W = 800;
	public static int SCREEN_H = 600;
	public static int game_width = 640;
	public static int game_height = 480;
	public static ArrayList<String> active_scripts = new ArrayList<>();
	public static int USER_SCREEN_SIZE_W;
	public static int USER_SCREEN_SIZE_H;
	private Thread thread;
	private static BufferedImage image;
	private static int[] pixels;
	private Camera camera;
	private Screen screen;
	private boolean running;
	private int[] gamepixels = new int[game_width * game_height];
	public static HashMap<String, Texture> allTextures = new HashMap<>();
	public static HashMap<String, Sprite> allSprites = new HashMap<>();
	public static long frame_num = 0;
	
	// Canvas for rendering
	private Canvas canvas;

	public Main() {
		thread = new Thread(this);
		image = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_INT_RGB);
		pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

		List<Path> dataFolders = List.of(
			Paths.get("data/tex"),
			Paths.get("data/sprites"),
			Paths.get("data/skybox"),
			Paths.get("data/pics"),
			Paths.get("data/fonts")
		);

		try {
			for (Path folder : dataFolders) {
				Files.walkFileTree(folder, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (file.toString().endsWith(".png")) {
							allTextures.put(file.getFileName().toString(), new Texture(file.toString()));
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}

			camera = new Camera();
			screen = new Screen(pixels, gamepixels);
			addKeyListener(camera);

			// Setup Canvas
			canvas = new Canvas();
			canvas.setPreferredSize(new Dimension(SCREEN_W, SCREEN_H));
			canvas.setMaximumSize(new Dimension(SCREEN_W, SCREEN_H));
			canvas.setMinimumSize(new Dimension(SCREEN_W, SCREEN_H));
			canvas.addKeyListener(camera);
			
			// Setup JFrame properties BEFORE making it visible
			setTitle(game_title + " " + game_version.toString() + " | F4: Toggle Fullscreen | ESC: Quit");
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setBackground(Color.black);
			setResizable(false);
			setUndecorated(false);
			
			add(canvas);
			pack();
			
			setLocationRelativeTo(null);
			setVisible(true);
			
			// Create buffer strategy after window is visible
			canvas.createBufferStrategy(2);
			
			start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private synchronized void start() {
		running = true;
		thread.start();
	}

	public synchronized void stop() {
		running = false;
		try {
			thread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void render() {
		BufferStrategy bs = canvas.getBufferStrategy();
		if (bs == null) {
			return;
		}

		Graphics g = bs.getDrawGraphics();
		
		// Draw the image to the canvas
		g.drawImage(image, 0, 0, SCREEN_W, SCREEN_H, null);
		
		g.dispose();
		bs.show();
	}

	public static void reinitializeBuffers(JFrame frame) {
		// Recreate the BufferedImage with new dimensions
		image = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_INT_RGB);
		pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

		// Update the screen object with new pixel array
		Screen.updatePixelArrays(pixels);

		// Update the JFrame size
		frame.setSize(SCREEN_W, SCREEN_H);
		frame.revalidate();
	}

	public void run() {
		long lastTime = System.nanoTime();
		final float ns = 1000000000.0f / MAX_FPS;
		float delta = 0;
		requestFocus();

		int frames = 0;
		long lastFpsTime = System.nanoTime();

		while (running) {
			long now = System.nanoTime();
			delta = delta + ((now - lastTime) / ns);
			lastTime = now;

			while (delta >= 1) {
				screen.update(frame_num);
				camera.update(this);
				frame_num++;
				delta--;
			}

			// Render after update
			render();
			frames++;

			// Calculate FPS every second
			if (now - lastFpsTime >= 1000000000) {
				currentFPS = frames;
				frames = 0;
				lastFpsTime = now;
			}
		}
	}

	public static void main(String[] args) {
		try {
			String filePath = "data/configs.cfg";
			Map<String, String> config = new HashMap<>();
			try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty() || line.startsWith("#")) {
						continue;
					}
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						String key = parts[0].trim();
						String value = parts[1].trim();
						config.put(key, value);
					}
				}
			} catch (IOException e) {
				System.err.println("Error reading the configuration file: " + e.getMessage());
			}
			game_title = config.get("game_title");
			game_version = config.get("game_version");
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			Dimension screenSize = toolkit.getScreenSize();
			USER_SCREEN_SIZE_W = screenSize.width;
			USER_SCREEN_SIZE_H = screenSize.height;
			active_scripts.add("init.lua");

			new Main();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}