package raccoon;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

import javax.swing.JFrame;

public class Main extends JFrame implements Runnable {

    public static int current_fps = 0;
    public static int current_ups = 0;
    public static int GAME_WID = 640;
    public static int GAME_HEI = 480;
    public static int USER_SCREEN_SIZE_W;
    public static int USER_SCREEN_SIZE_H;
    public static int frame_num = 0;
    public static int cores = Runtime.getRuntime().availableProcessors();
    public static ExecutorService executor_threads = Executors.newFixedThreadPool(cores);
    public static HashMap<String, Texture> textures = new HashMap<>();
    public static PriorityQueue<Event> scripts = new PriorityQueue<>();

    private Thread thread;
    private static BufferedImage image;
    private static int[] pixels;
    private volatile boolean running;
    private int[] game_pixels = new int[GAME_WID * GAME_HEI];
    private Canvas canvas;
    private Camera camera;
    private Screen screen;

    public Main() {
        thread = new Thread(this, "game_loop_thread");

        image = new BufferedImage(USER_SCREEN_SIZE_W, USER_SCREEN_SIZE_H, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        List<Path> data_folders = List.of(
            Paths.get("data/tex"),
            Paths.get("data/sprites"),
            Paths.get("data/skybox"),
            Paths.get("data/pics")
        );

        try {
            for (Path folder : data_folders) {
                if (!Files.exists(folder)) {
                    continue;
                }

                Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toString().endsWith(".png")) {
                            textures.put(file.getFileName().toString(), new Texture(file.toString()));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            camera = new Camera();
            camera.setFrame(this);

            screen = new Screen(pixels, game_pixels);

            canvas = new Canvas();
            canvas.setPreferredSize(new Dimension(USER_SCREEN_SIZE_W, USER_SCREEN_SIZE_H));
            canvas.setMaximumSize(new Dimension(USER_SCREEN_SIZE_W, USER_SCREEN_SIZE_H));
            canvas.setMinimumSize(new Dimension(USER_SCREEN_SIZE_W, USER_SCREEN_SIZE_H));
            canvas.setFocusable(true);

            canvas.addKeyListener(camera);
            canvas.addMouseListener(camera);
            canvas.addMouseMotionListener(camera);

            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setBackground(Color.black);
            setResizable(false);
            setUndecorated(true);

            add(canvas);
            pack();

            setLocationRelativeTo(null);
            setVisible(true);

            canvas.requestFocus();
            canvas.createBufferStrategy(2);

            start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        thread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (Thread.currentThread() != thread) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void render() {
        BufferStrategy bs = canvas.getBufferStrategy();
        if (bs == null) {
            return;
        }

        do {
            do {
                Graphics g = bs.getDrawGraphics();

                try {
                    g.drawImage(image, 0, 0, USER_SCREEN_SIZE_W, USER_SCREEN_SIZE_H, null);
                } finally {
                    g.dispose();
                }

            } while (bs.contentsRestored());

            bs.show();
            Toolkit.getDefaultToolkit().sync();

        } while (bs.contentsLost());
    }

    @Override
    public void run() {
        final int target_ups = 60;
        final int target_fps = 60;

        final long ns_per_update = 1_000_000_000L / target_ups;
        final long ns_per_render = 1_000_000_000L / target_fps;
        final int max_updates_before_render = 5;

        long start_time = System.nanoTime();
        long next_update_time = start_time + ns_per_update;
        long next_render_time = start_time + ns_per_render;

        long stat_timer = System.currentTimeMillis();
        int frames = 0;
        int updates = 0;

        canvas.requestFocus();

        while (running) {
            long current_time = System.nanoTime();
            int update_count = 0;

            while (current_time >= next_update_time && update_count < max_updates_before_render) {
                update();
                updates++;
                update_count++;
                next_update_time += ns_per_update;
            }

            if (current_time >= next_update_time) {
                next_update_time = current_time + ns_per_update;
            }

            if (current_time >= next_render_time) {
                render();
                frames++;
                next_render_time += ns_per_render;

                if (current_time >= next_render_time) {
                    next_render_time = current_time + ns_per_render;
                }
            }

            if (System.currentTimeMillis() - stat_timer >= 1000) {
                current_fps = frames;
                current_ups = updates;

                frames = 0;
                updates = 0;
                stat_timer += 1000;
            }

            long next_event_time = Math.min(next_update_time, next_render_time);
            long sleep_nanos = next_event_time - System.nanoTime();

            if (sleep_nanos > 0) {
                LockSupport.parkNanos(sleep_nanos);
            } else {
                Thread.onSpinWait();
            }
        }
    }

    private void update() {
        screen.update(frame_num);
        camera.update(this);
        frame_num++;
    }

    public static void main(String[] args) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension screen_size = toolkit.getScreenSize();
            USER_SCREEN_SIZE_W = screen_size.width;
            USER_SCREEN_SIZE_H = screen_size.height;
            Table.init();
            scripts.add(new Event("init.lua", 1));
            new Main();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}