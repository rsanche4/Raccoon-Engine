package raccoon;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.swing.*;

public class JavaSwingRenderer implements Renderer {

    private int[] pixels;
    private BufferedImage image;
    private JFrame frame;
    private JPanel panel;

    public JavaSwingRenderer() {
        image = new BufferedImage(Table.USER_SCREEN_SIZE_W, Table.USER_SCREEN_SIZE_H, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.drawImage(image, 0, 0, null);
            }
        };
        panel.setPreferredSize(new Dimension(Table.USER_SCREEN_SIZE_W, Table.USER_SCREEN_SIZE_H));

        frame = new JFrame("Raccoon Engine");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    @Override
    public void render(int[] game_pixels) {
    	for (int y = 0; y < Table.render_h; y++) {
            int screen_y = Table.screen_y[y];
            int src_offset = Table.src_offset[y];
            for (int x = 0; x < Table.render_w; x++) {
            	int color = game_pixels[src_offset + Table.src_x[x]];
            	int i = screen_y + x;
            	if (color>=0) {
            		pixels[i] = Table.PALETTE[color];
            	} else {
            		pixels[i] = Table.PALETTE[0];
            	}
            }
        }
        panel.repaint();
    }
}