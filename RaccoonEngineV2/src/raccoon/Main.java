package raccoon;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main implements Runnable {

	public static int frame_num = 0;
	public static int GAME_WID = 640;
    public static int GAME_HEI = 480;
    public static int cores = Runtime.getRuntime().availableProcessors();
    public static ExecutorService executor_threads = Executors.newFixedThreadPool(cores);
    private Thread thread;
    private boolean running;
    private int[] game_pixels = new int[GAME_WID * GAME_HEI];
    private Renderer renderer;
    private Camera camera;
    private Screen screen;

    public Main(Renderer renderer) {
        this.renderer = renderer;
    	thread = new Thread(this, "game_loop_thread");

    	ResourceManager.loadData();

        camera = new Camera();
        screen = new Screen();
        running = true;
        thread.start();
    }

    private void render() {
    	renderer.render(game_pixels);
    }

    @Override
    public void run() {
        final double targetFPS = 60.0;
        final double nsPerFrame = 1_000_000_000.0 / targetFPS;
        long lastTime = System.nanoTime();
        double delta = 0;
        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerFrame;
            lastTime = now;
            boolean updated = false;
            while (delta >= 1) {
                update();
                delta--;
                updated = true;
            }
            if (updated) render();
        }
    }

    private void update() {
    	game_pixels = screen.update(frame_num, game_pixels);
        camera.update();
        frame_num++;
    }

    public static void main(String[] args) {
    	Table.init();
    	Renderer renderer = new JavaSwingRenderer();
        new Main(renderer);
    }
}