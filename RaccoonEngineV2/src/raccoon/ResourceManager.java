package raccoon;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;

public class ResourceManager {

	public static boolean pack_into_rpk = false;
	
	private static String data_rpk = "data.rpk";
	private static String data_folder = "data";
	private static String data_folder_bgm = data_folder+"/bgm/";
	private static String data_folder_fonts = data_folder+"/fonts/";
	private static String data_folder_maps = data_folder+"/maps/";
	private static String data_folder_pics = data_folder+"/pics/";
	private static String data_folder_scripts = data_folder+"/scripts/";
	private static String data_folder_se = data_folder+"/se/";
	private static String data_folder_skybox = data_folder+"/skybox/";
	private static String data_folder_sprites = data_folder+"/sprites/";
	private static String data_folder_tex = data_folder+"/tex/";
	private static String img_type = ".png";
	private static String font_type = ".ttf";
	private static String sound_type = ".wav";
	private static String map_type = ".txt";
	private static String script_type = ".lua";

	public static ArrayList<Event> active_scripts = new ArrayList<>();
	public static HashMap<String, Texture> images = new HashMap<>();
	public static HashMap<String, Texture> fonts = new HashMap<>();
	public static HashMap<String, File> sounds = new HashMap<>();
	public static HashMap<String, String> level_data = new HashMap<>();
	public static HashMap<String, Sprite> sprites = new HashMap<>();

	private static void unpackRPK() {
		// TODO
		return;
	}

	private static void packRPK() {
		// TODO
		return;
	}

	private static void saveResource(File resource, String type) {
		if (type.contentEquals(img_type)) {
			BufferedImage image;
			try {
				image = ImageIO.read(resource);
				int IMG_WID = image.getWidth();
				int IMG_HEI = image.getHeight();
				int[] pixels = new int[IMG_WID * IMG_HEI];
				image.getRGB(0, 0, IMG_WID, IMG_HEI, pixels, 0, IMG_WID);
				for (int i=0; i<pixels.length; i++) {
					int color = pixels[i];
					if ((color >>> 24) == 0) {
						pixels[i] = -1;
					} else {
						int r = (color >> 16) & 0xFF;
		                int g = (color >> 8) & 0xFF;
		                int b = color & 0xFF;
						pixels[i] = Table.findClosestColorIndex(r, g, b);
					}
					
				}
				images.put(resource.getName(), new Texture(pixels, IMG_WID, IMG_HEI));
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (type.contentEquals(font_type)) {
			try {
		        Font font = Font.createFont(Font.TRUETYPE_FONT, resource).deriveFont(12f);
		        String font_file_name = resource.getName();
		        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		        Graphics2D probe_g = probe.createGraphics();
		        probe_g.setFont(font);
		        FontMetrics metrics = probe_g.getFontMetrics();
		        probe_g.dispose();
		        int glyph_height = metrics.getAscent() + metrics.getDescent();
		        for (int code = 32; code <= 126; code++) {
		            char c = (char) code;
		            String char_str = String.valueOf(c);
		            int glyph_width = metrics.charWidth(c);
		            if (glyph_width <= 0 || glyph_height <= 0) continue;
		            BufferedImage glyph_image = new BufferedImage(glyph_width, glyph_height, BufferedImage.TYPE_INT_ARGB);
		            Graphics2D g = glyph_image.createGraphics();
		            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		            g.setFont(font);
		            g.setColor(Color.BLACK);
		            g.fillRect(0, 0, glyph_width, glyph_height);
		            g.setColor(Color.WHITE);
		            g.drawString(char_str, 0, metrics.getAscent());
		            g.dispose();
		            int[] pixels = new int[glyph_width * glyph_height];
		            glyph_image.getRGB(0, 0, glyph_width, glyph_height, pixels, 0, glyph_width);
		            for (int i = 0; i < pixels.length; i++) {
		                int color = pixels[i];
		                if ((color >>> 24) == 0) {
		                	pixels[i] = -1;
		                } else {
		                	int r = (color >> 16) & 0xFF;
			                int g2 = (color >> 8) & 0xFF;
			                int b = color & 0xFF;
			                pixels[i] = Table.findClosestColorIndex(r, g2, b);
		                }
		                
		            }
		            String key = c + "_" + font_file_name;
		            fonts.put(key, new Texture(pixels, glyph_width, glyph_height));
		        }
		 
		    } catch (FontFormatException | IOException e) {
		        e.printStackTrace();
		    }
		} else if (type.contentEquals(sound_type)) {
			sounds.put(resource.getName(), resource);
		} else if (type.contentEquals(map_type) || type.contentEquals(script_type)) {
			try {
				String data = Files.readString(resource.toPath()).replace("\r\n", "\n");
				level_data.put(resource.getName(), data);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void loadData() {

		File data_folder_file = new File(data_folder);

		if (!data_folder_file.exists() || !data_folder_file.isDirectory()) {
			File rpk_file = new File(data_rpk);
			if (!rpk_file.exists()) {
				throw new RuntimeException("[ResourceManager] Fatal: neither '" + data_folder + "/' nor '" + data_rpk + "' were found. Cannot load game data.");
			}
			unpackRPK();
			return;
		}

		checkInFolder(data_folder_bgm, sound_type);
		checkInFolder(data_folder_fonts, font_type);
		checkInFolder(data_folder_maps, map_type);
		checkInFolder(data_folder_pics, img_type);
		checkInFolder(data_folder_scripts, script_type);
		File init_lua = new File(data_folder_scripts + "init" + script_type);
		if (!init_lua.exists()) {
			throw new RuntimeException("[ResourceManager] Fatal: 'init.lua' not found in '" + data_folder_scripts + "'. An init script is required.");
		}
		active_scripts.add(new Event("init"+script_type, 1));
		checkInFolder(data_folder_se, sound_type);
		checkInFolder(data_folder_skybox, img_type);
		File skybox_dir = new File(data_folder_skybox);
		File[] skybox_files = skybox_dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(img_type));
		if (skybox_files != null) {
			for (File img : skybox_files) {
				try {
					BufferedImage bi = ImageIO.read(img);
					int expected_w = 4 * Main.GAME_WID;
					int expected_h = Main.GAME_HEI;
					if (bi.getWidth() != expected_w || bi.getHeight() != expected_h) {
						throw new RuntimeException("[ResourceManager] Fatal: skybox image '" + img.getName() + "' has wrong dimensions (" + bi.getWidth() + "x" + bi.getHeight() + "). Expected " + expected_w + "x" + expected_h + " (4*GAME_WID x GAME_HEI).");
					}
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new RuntimeException("[ResourceManager] Fatal: could not read skybox image '" + img.getName() + "': " + e.getMessage());
				}
			}
		}

		checkInFolder(data_folder_sprites, img_type);
		File sprites_dir = new File(data_folder_sprites);
		File[] sprite_files = sprites_dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(img_type));
		if (sprite_files != null) {
			for (File img : sprite_files) {
				try {
					BufferedImage bi = ImageIO.read(img);
					int h = bi.getHeight();
					int expected_w = 8 * h;
					if (bi.getWidth() != expected_w) {
						throw new RuntimeException("[ResourceManager] Fatal: sprite '" + img.getName() + "' has wrong dimensions (" + bi.getWidth() + "x" + h +	"). Expected width = 8 * height = " + expected_w + ".");
					}
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new RuntimeException("[ResourceManager] Fatal: could not read sprite image '" + img.getName() + "': " + e.getMessage());
				}
			}
		}

		checkInFolder(data_folder_tex, img_type);
		if (pack_into_rpk) {
			packRPK();
		}
	}

	private static void checkInFolder(String path, String required_ext) {
		File folder = new File(path);

		if (!folder.exists() || !folder.isDirectory()) {
			throw new RuntimeException("[ResourceManager] Fatal: required folder '" + path + "' does not exist.");
		}

		File[] files = folder.listFiles(File::isFile);
		if (files != null) {
			for (File f : files) {
				if (!f.getName().toLowerCase().endsWith(required_ext)) {
					throw new RuntimeException("[ResourceManager] Fatal: unsupported file format '" + f.getName() +	"' found in '" + path + "'. Only " + required_ext + " files are supported here.");
				}
				saveResource(f, required_ext);
			}
		}
	}
}