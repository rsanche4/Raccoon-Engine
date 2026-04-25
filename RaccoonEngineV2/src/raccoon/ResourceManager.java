package raccoon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.PriorityQueue;

import javax.imageio.ImageIO;

public class ResourceManager {

	public static boolean pack_into_rpk = true;
    public static String data_rpk = "data.rpk";
    public static String data_folder = "data";
    public static String data_folder_bgm = data_folder+"/bgm/";
    public static String data_folder_fonts = data_folder+"/fonts/";
    public static String data_folder_maps = data_folder+"/maps/";
    public static String data_folder_pics = data_folder+"/pics/";
    public static String data_folder_scripts = data_folder+"/scripts/";
    public static String data_folder_se = data_folder+"/se/";
    public static String data_folder_skybox = data_folder+"/skybox/";
    public static String data_folder_sprites = data_folder+"/sprites/";
    public static String data_folder_tex = data_folder+"/tex/";

	// TODO NOTE THIS IS IMPORTANT DO NOT EVEN ATTEMPT TO STORE ALL THAT. ONLY GET WHAT IS NECESSARY, AND LOAD IT FROM THE FILE WHEN CALLED BY STRINGS ETC. DO NOT OVERLOAD UR MEMORY
	// Perhaps a better way is to store an index to where that info is stored outside or something. Instead of the raw info
    // public static HashMap<String, Texture> textures = new HashMap<>(); 	// TODO here we need to actually store the colors that we are reading from the data and convert them to the closest ones in our allowed ANSI colors for our game engine
    // public static HashMap<String, LuaJ or something> all_scripts = etc // TODO like this
    public static PriorityQueue<Event> scripts = new PriorityQueue<>();
    // public static Sounds type of thing idk all Sounds so that we can recall them later and play them TODO
    // TODO Misc folder can go because we can simply use the RaccoonAPI to add stuff directly and use it as we see fit
    // TODO saves folder should be started when needed by an API if called, so yeah that we dont really check

    private static void unpackRPK() {

    }

    private static void packRPK() {

    }

    private static void manageDiskResources() {

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

		checkFolder(data_folder_bgm, ".wav");
		checkFolder(data_folder_fonts, ".ttf");
		checkFolder(data_folder_maps, ".txt");
		checkFolder(data_folder_pics, ".png");
		checkFolder(data_folder_scripts, ".lua");
		File init_lua = new File(data_folder_scripts + "init.lua");
		if (!init_lua.exists()) {
			throw new RuntimeException("[ResourceManager] Fatal: 'init.lua' not found in '" + data_folder_scripts + "'. An init script is required.");
		}
		scripts.add(new Event("init.lua", 1));
		checkFolder(data_folder_se, ".wav");
		checkFolder(data_folder_skybox, ".png");
		File skybox_dir = new File(data_folder_skybox);
		File[] skybox_files = skybox_dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
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

		checkFolder(data_folder_sprites, ".png");
		File sprites_dir = new File(data_folder_sprites);
		File[] sprite_files = sprites_dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".png"));
		if (sprite_files != null) {
			for (File img : sprite_files) {
				try {
					BufferedImage bi = ImageIO.read(img);
					int h = bi.getHeight();
					int expected_w = 8 * h;
					if (bi.getWidth() != expected_w) {
						throw new RuntimeException("[ResourceManager] Fatal: sprite '" + img.getName() +
							"' has wrong dimensions (" + bi.getWidth() + "x" + h +
							"). Expected width = 8 * height = " + expected_w + ".");
					}
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new RuntimeException("[ResourceManager] Fatal: could not read sprite image '" + img.getName() + "': " + e.getMessage());
				}
			}
		}

		checkFolder(data_folder_tex, ".png");
		if (pack_into_rpk) {
			packRPK();
			unpackRPK();
		} else {
			manageDiskResources();
		}
	}

	private static void checkFolder(String path, String required_ext) {
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
			}
		}
	}
}