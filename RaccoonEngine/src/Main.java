import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
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

// GPU acceleration imports
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.common.nio.Buffers;

public class Main extends JFrame implements Runnable, GLEventListener {
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
	private int[] gamepixels = new int[game_width*game_height]; 
	public static HashMap<String, Texture> allTextures = new HashMap<>();
	public static HashMap<String, Sprite> allSprites = new HashMap<>();
	public static long frame_num = 0;
	// GPU related variables
	private static GLCanvas canvas;
	private int textureId;
	private static ByteBuffer pixelBuffer;
	private static boolean textureInitialized = false;
	
	public Main() {
		thread = new Thread(this);
		image = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_INT_RGB);
		pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		
		// Initialize OpenGL
		GLProfile glProfile = GLProfile.getDefault();
		GLCapabilities glCapabilities = new GLCapabilities(glProfile);
		canvas = new GLCanvas(glCapabilities);
		canvas.addGLEventListener(this);
		
		// Create pixel buffer for GPU transfer
		pixelBuffer = Buffers.newDirectByteBuffer(SCREEN_W * SCREEN_H * 4); // RGBA
		
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
			
			// Setup JFrame with OpenGL canvas
			add(canvas);
			canvas.addKeyListener(camera);
			setSize(SCREEN_W, SCREEN_H);
			setResizable(false);
			setUndecorated(false);
			setLocation(0, 0);
			setTitle(game_title + " " + game_version.toString() + " | F4: Toggle Fullscreen | ESC: Quit");
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setBackground(Color.black);
			setLocationRelativeTo(null);
			setVisible(true);
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

	// OpenGL initialization
	@Override
	public void init(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		
		// Enable 2D textures
		gl.glEnable(GL.GL_TEXTURE_2D);
		
		// Generate texture ID
		IntBuffer textureBuffer = Buffers.newDirectIntBuffer(1);
		gl.glGenTextures(1, textureBuffer);
		textureId = textureBuffer.get(0);
		
		// Bind and configure texture
		gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
		
		// Set up orthographic projection
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrtho(0, 1, 0, 1, -1, 1);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// Disable depth testing for 2D rendering
		gl.glDisable(GL.GL_DEPTH_TEST);
		
		textureInitialized = true;
	}

	// OpenGL display method - this replaces the old render() method
	@Override
	public void display(GLAutoDrawable drawable) {		
	    GL2 gl = drawable.getGL().getGL2();
	    
	    // Reinitialize texture if needed
	    if (!textureInitialized) {
	        gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
	        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
	        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
	        textureInitialized = true;
	    }
	    
	    // Clear the screen
	    gl.glClear(GL.GL_COLOR_BUFFER_BIT);
		
		// Convert pixel array to ByteBuffer for GPU transfer
		pixelBuffer.clear();
		for (int i = 0; i < pixels.length; i++) {
			int pixel = pixels[i];
			// Convert from ARGB to RGBA
			pixelBuffer.put((byte) ((pixel >> 16) & 0xFF)); // R
			pixelBuffer.put((byte) ((pixel >> 8) & 0xFF));  // G
			pixelBuffer.put((byte) (pixel & 0xFF));         // B
			pixelBuffer.put((byte) 255);                    // A (full opacity)
		}
		pixelBuffer.flip();
		
		// Upload pixel data to GPU texture
		gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
		gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, SCREEN_W, SCREEN_H, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, pixelBuffer);
		
		// Render fullscreen quad with the texture
		gl.glBegin(GL2.GL_QUADS);
		gl.glTexCoord2f(0, 1); gl.glVertex2f(0, 0);
		gl.glTexCoord2f(1, 1); gl.glVertex2f(1, 0);
		gl.glTexCoord2f(1, 0); gl.glVertex2f(1, 1);
		gl.glTexCoord2f(0, 0); gl.glVertex2f(0, 1);
		gl.glEnd();
		
		// Force rendering
		gl.glFlush();
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glViewport(0, 0, width, height);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		// Clean up OpenGL resources
		GL2 gl = drawable.getGL().getGL2();
		if (textureId != 0) {
			gl.glDeleteTextures(1, new int[]{textureId}, 0);
		}
	}
	
	public static void reinitializeBuffers(JFrame frame) {
	    // Recreate the BufferedImage with new dimensions
	    image = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_INT_RGB);
	    pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	    
	    // Recreate the pixel buffer for GPU transfer
	    pixelBuffer = Buffers.newDirectByteBuffer(SCREEN_W * SCREEN_H * 4);
	    
	    // Update the screen object with new pixel array
	    Screen.updatePixelArrays(pixels);
	    
	    // Mark texture as needing reinitialization
	    textureInitialized = false;
	    
	    // Update the JFrame and canvas size
	    canvas.setPreferredSize(new Dimension(SCREEN_W, SCREEN_H));
	    canvas.setSize(SCREEN_W, SCREEN_H);
	    
	    frame.setSize(SCREEN_W, SCREEN_H);
	    frame.revalidate();
	    
	    // Force canvas to reinitialize texture on next display call
	    canvas.display();
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
	            frame_num++; // no need to wrap around because by the time this reaches its limit, 
	            // the Sun would have consumed planet Earth, the solar system would have collapse, humanity ended,
	            // and still there would 4 billion years left until the long limit is reached and program crashes. 
	            // Thus, no need for an extra wrap around instructions :)
	            delta--;
	            
	            // Move rendering inside the delta loop
	            canvas.display();
	            frames++;
	            
	            // Calculate FPS every second
	            if (now - lastFpsTime >= 1000000000) {
	                currentFPS = frames;
	                frames = 0;
	                lastFpsTime = now;
	            }
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