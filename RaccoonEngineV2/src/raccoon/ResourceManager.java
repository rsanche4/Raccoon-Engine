package raccoon;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Finds game assets and hands them out - loading each one the first time it is
 * actually needed rather than all of them at startup.
 *
 * HOW IT WORKS NOW
 * ----------------
 * Startup builds a CATALOG: a cheap list of what exists and where to find it.
 * No image is decoded, no font is rasterised, no sound is read. For a loose
 * data/ folder that is a directory listing; for a data.rpk it is the pack's
 * index. Either way it is fast and costs almost no memory.
 *
 * The first time something asks for "wood.png", getImage() reads and decodes it
 * and keeps the result in a cache. Ask again and you get the cached copy. Call
 * unload("wood.png") and it is dropped; the next request loads it again.
 *
 * Lua drives this through RA:resourceLoad / RA:resourceUnload / RA:resourceUnloadAll
 * and RA:resourceLoadFolderAsync, so a game can say "these are the textures for
 * level 2" and then throw them away on the way out.
 *
 * TRADE-OFF WORTH KNOWING: validation (sprite sheets being 8x wide, skybox
 * dimensions) used to happen for every file at boot. It now happens when a file
 * is first loaded, so a bad asset surfaces the first time it is used rather
 * than at startup. RA:resourceValidateAll() forces the old all-at-once check
 * when you want it - handy to call once during development.
 *
 * THREADING: getImage() and friends get called from the render threads and from
 * the background loading thread, so the caches are concurrent maps and each
 * asset's load path is atomic.
 */
public class ResourceManager {

	// ---------------------------------------------------------- where stuff is

	public static final String FOLDER_BGM = "bgm";
	public static final String FOLDER_FONTS = "fonts";
	public static final String FOLDER_MAPS = "maps";
	public static final String FOLDER_PICS = "pics";
	public static final String FOLDER_SCRIPTS = "scripts";
	public static final String FOLDER_SE = "se";
	public static final String FOLDER_SKYBOX = "skybox";
	public static final String FOLDER_SPRITES = "sprites";
	public static final String FOLDER_TEX = "tex";

	public static final String[] ALL_FOLDERS = {
		FOLDER_BGM, FOLDER_FONTS, FOLDER_MAPS, FOLDER_PICS,
		FOLDER_SCRIPTS, FOLDER_SE, FOLDER_SKYBOX, FOLDER_SPRITES, FOLDER_TEX
	};

	private static final String IMG_TYPE = ".png";
	private static final String FONT_TYPE = ".ttf";
	private static final String SOUND_TYPE = ".wav";
	private static final String MAP_TYPE = ".txt";
	private static final String SCRIPT_TYPE = ".lua";

	private static final HashMap<String, String> REQUIRED_EXT = new HashMap<>();
	static {
		REQUIRED_EXT.put(FOLDER_BGM, SOUND_TYPE);
		REQUIRED_EXT.put(FOLDER_SE, SOUND_TYPE);
		REQUIRED_EXT.put(FOLDER_FONTS, FONT_TYPE);
		REQUIRED_EXT.put(FOLDER_MAPS, MAP_TYPE);
		REQUIRED_EXT.put(FOLDER_SCRIPTS, SCRIPT_TYPE);
		REQUIRED_EXT.put(FOLDER_PICS, IMG_TYPE);
		REQUIRED_EXT.put(FOLDER_SKYBOX, IMG_TYPE);
		REQUIRED_EXT.put(FOLDER_SPRITES, IMG_TYPE);
		REQUIRED_EXT.put(FOLDER_TEX, IMG_TYPE);
	}

	private static final String DATA_RPK = "data.rpk";
	private static final String DATA_FOLDER = "data";

	/** Flip to true (or pass --pack on the command line) to write data.rpk at startup. */
	public static boolean pack_into_rpk = false;

	// ------------------------------------------------------------- the catalog

	/** One known asset. Either file != null (loose folder) or rpk_entry != null (packed). */
	public static class AssetEntry {
		public String name;
		public String folder;
		public String type;
		public File file;
		public Rpk.Entry rpk_entry;
	}

	/** Everything the engine knows exists, keyed by bare filename ("wood.png"). */
	public static final HashMap<String, AssetEntry> catalog = new HashMap<>();
	private static Rpk rpk = null;

	// -------------------------------------------------------------- the caches

	public static final ConcurrentHashMap<String, Texture> images = new ConcurrentHashMap<>();
	/** Font glyphs, keyed "<char>_<fontfile>" e.g. "A_system_font.ttf". */
	public static final ConcurrentHashMap<String, Texture> fonts = new ConcurrentHashMap<>();
	/** Maps and scripts (both plain text), keyed by filename. */
	public static final ConcurrentHashMap<String, String> level_data = new ConcurrentHashMap<>();
	public static final ConcurrentHashMap<String, byte[]> sounds = new ConcurrentHashMap<>();

	public static ArrayList<Event> active_scripts = new ArrayList<>();
	public static HashMap<String, Sprite> sprites = new HashMap<>();

	/** Drawn instead of a missing texture, so one typo never crashes a render thread. */
	private static Texture missing_texture = null;

	// ----------------------------------------------------- loading-screen state

	/** Read by Screen every frame. While true the world is not drawn. */
	public static volatile boolean is_loading = false;
	public static volatile String loading_message = "LOADING";
	/** 0.0 to 1.0, or negative for "no idea how long this takes". */
	public static volatile double loading_progress = -1;

	// =============================================================== start up

	/**
	 * Builds the catalog. Cheap: a directory listing, or an .rpk index read.
	 * Nothing is decoded here.
	 */
	public static void index() {
		catalog.clear();

		File rpk_file = new File(DATA_RPK);
		File data_folder_file = new File(DATA_FOLDER);

		if (data_folder_file.exists() && data_folder_file.isDirectory()) {
			indexFolder(data_folder_file);
			if (pack_into_rpk) {
				packNow();
			}
		} else if (rpk_file.exists()) {
			indexRpk(rpk_file);
		} else {
			throw new RuntimeException("[ResourceManager] Fatal: neither '" + DATA_FOLDER
					+ "/' nor '" + DATA_RPK + "' were found. Cannot load game data.");
		}

		if (!catalog.containsKey("init" + SCRIPT_TYPE)) {
			throw new RuntimeException("[ResourceManager] Fatal: 'init.lua' not found in "
					+ FOLDER_SCRIPTS + "/. An init script is required.");
		}
		active_scripts.add(new Event("init" + SCRIPT_TYPE, 1));
	}

	private static void indexFolder(File data_folder_file) {
		for (String folder : ALL_FOLDERS) {
			File dir = new File(data_folder_file, folder);
			if (!dir.exists() || !dir.isDirectory()) {
				throw new RuntimeException("[ResourceManager] Fatal: required folder '"
						+ DATA_FOLDER + "/" + folder + "' does not exist.");
			}
			String required = REQUIRED_EXT.get(folder);
			File[] files = dir.listFiles(File::isFile);
			if (files == null) continue;
			for (File f : files) {
				if (!f.getName().toLowerCase().endsWith(required)) {
					throw new RuntimeException("[ResourceManager] Fatal: unsupported file format '"
							+ f.getName() + "' found in '" + folder + "'. Only " + required
							+ " files are supported here.");
				}
				AssetEntry e = new AssetEntry();
				e.name = f.getName();
				e.folder = folder;
				e.type = required;
				e.file = f;
				catalog.put(e.name, e);
			}
		}
	}

	private static void indexRpk(File rpk_file) {
		try {
			rpk = Rpk.open(rpk_file);
		} catch (IOException ex) {
			throw new RuntimeException("[ResourceManager] Fatal: could not open '"
					+ DATA_RPK + "': " + ex.getMessage());
		}
		for (Rpk.Entry re : rpk.entries()) {
			AssetEntry e = new AssetEntry();
			e.name = re.name;
			e.folder = re.folder;
			e.type = REQUIRED_EXT.get(re.folder);
			e.rpk_entry = re;
			catalog.put(e.name, e);
		}
	}

	/**
	 * Writes the current data/ folder out as data.rpk.
	 *
	 * Refuses to run when there is no data/ folder to pack. Without that guard,
	 * calling this from a shipped game (which has only a data.rpk and no loose
	 * folder) would write an EMPTY pack straight over the real one and destroy
	 * every asset the game owns. Packing is a development-time action; it only
	 * makes sense when the loose folder is the source of truth.
	 */
	public static void packNow() {
		File src = new File(DATA_FOLDER);
		if (!src.exists() || !src.isDirectory()) {
			System.err.println("[ResourceManager] Refusing to pack: no '" + DATA_FOLDER
					+ "/' folder to pack from. (Packing is for development, when data/ exists. "
					+ "Running from an existing " + DATA_RPK + " has nothing to pack and would "
					+ "overwrite it with an empty archive.)");
			return;
		}
		if (catalog.isEmpty()) {
			System.err.println("[ResourceManager] Refusing to pack: catalog is empty, "
					+ "so the result would be an empty archive.");
			return;
		}
		try {
			Rpk.pack(src, new File(DATA_RPK), ALL_FOLDERS);
			System.out.println("[ResourceManager] Wrote " + DATA_RPK + " ("
					+ catalog.size() + " assets). You can ship just that one file now.");
		} catch (IOException ex) {
			System.err.println("[ResourceManager] Failed to write " + DATA_RPK + ": " + ex.getMessage());
		}
	}

	// ========================================================= raw byte access

	/** Reads an asset's raw bytes, from the folder or out of the pack. */
	private static byte[] readBytes(AssetEntry e) throws IOException {
		if (e.file != null) {
			return Files.readAllBytes(e.file.toPath());
		}
		return rpk.read(e.rpk_entry);
	}

	// =============================================================== accessors

	/**
	 * Returns a texture, loading it on first use. Never returns null - a missing
	 * asset comes back as a magenta placeholder so a typo in a map file shows up
	 * on screen instead of crashing a render thread.
	 */
	public static Texture getImage(String name) {
		Texture cached = images.get(name);
		if (cached != null) return cached;

		AssetEntry e = catalog.get(name);
		if (e == null) {
			System.err.println("[ResourceManager] Unknown image '" + name + "' - using placeholder.");
			return missingTexture();
		}
		// computeIfAbsent is atomic per key, so two render threads asking at the
		// same moment decode it exactly once.
		Texture loaded = images.computeIfAbsent(name, n -> loadImage(e));
		return loaded != null ? loaded : missingTexture();
	}

	private static Texture loadImage(AssetEntry e) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(readBytes(e)));
			if (image == null) {
				System.err.println("[ResourceManager] Could not decode image '" + e.name + "'.");
				return null;
			}
			validateImage(e, image);
			int IMG_WID = image.getWidth();
			int IMG_HEI = image.getHeight();
			int[] pixels = new int[IMG_WID * IMG_HEI];
			image.getRGB(0, 0, IMG_WID, IMG_HEI, pixels, 0, IMG_WID);
			for (int i = 0; i < pixels.length; i++) {
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
			return new Texture(pixels, IMG_WID, IMG_HEI);
		} catch (IOException ex) {
			System.err.println("[ResourceManager] Failed reading image '" + e.name + "': " + ex.getMessage());
			return null;
		}
	}

	/**
	 * The size rules that used to be checked at startup, now checked on load.
	 * These throw, because a wrong-sized sprite sheet or skybox draws garbage
	 * rather than failing loudly, and that is much harder to debug.
	 */
	private static void validateImage(AssetEntry e, BufferedImage bi) {
		if (FOLDER_SPRITES.equals(e.folder)) {
			int h = bi.getHeight();
			int expected_w = Table.SPRITE_NUM_DIRECTIONS * h;
			if (bi.getWidth() != expected_w) {
				throw new RuntimeException("[ResourceManager] Sprite '" + e.name + "' has wrong dimensions ("
						+ bi.getWidth() + "x" + h + "). Expected width = "
						+ Table.SPRITE_NUM_DIRECTIONS + " * height = " + expected_w + ".");
			}
		} else if (FOLDER_SKYBOX.equals(e.folder)) {
			if (bi.getWidth() != Table.SKYBOX_WID) {
				throw new RuntimeException("[ResourceManager] Skybox '" + e.name + "' has wrong width ("
						+ bi.getWidth() + "). Expected " + Table.SKYBOX_WID + " (4 * GAME_WID).");
			}
			if (bi.getHeight() < Main.GAME_HEI) {
				throw new RuntimeException("[ResourceManager] Skybox '" + e.name + "' is too short ("
						+ bi.getHeight() + "). Needs at least GAME_HEI = " + Main.GAME_HEI
						+ ", and " + Table.SKYBOX_HEI + " to get the full look up/down range.");
			}
		}
	}

	/** Map or script text, loaded on first use. */
	public static String getText(String name) {
		String cached = level_data.get(name);
		if (cached != null) return cached;

		AssetEntry e = catalog.get(name);
		if (e == null) {
			System.err.println("[ResourceManager] Unknown text asset '" + name + "'.");
			return null;
		}
		return level_data.computeIfAbsent(name, n -> {
			try {
				return new String(readBytes(e), "UTF-8").replace("\r\n", "\n");
			} catch (IOException ex) {
				System.err.println("[ResourceManager] Failed reading '" + name + "': " + ex.getMessage());
				return null;
			}
		});
	}

	/** WAV bytes, loaded on first use. Sound plays straight from these. */
	public static byte[] getSoundBytes(String name) {
		byte[] cached = sounds.get(name);
		if (cached != null) return cached;

		AssetEntry e = catalog.get(name);
		if (e == null) {
			System.err.println("[ResourceManager] Unknown sound '" + name + "'.");
			return null;
		}
		return sounds.computeIfAbsent(name, n -> {
			try {
				return readBytes(e);
			} catch (IOException ex) {
				System.err.println("[ResourceManager] Failed reading sound '" + name + "': " + ex.getMessage());
				return null;
			}
		});
	}

	/**
	 * One glyph of a font. The whole font is rasterised the first time any of
	 * its glyphs is asked for, since that is one file read either way.
	 */
	public static Texture getGlyph(char c, String font_file) {
		String key = c + "_" + font_file;
		Texture cached = fonts.get(key);
		if (cached != null) return cached;
		loadFont(font_file);
		return fonts.get(key);
	}

	private static synchronized void loadFont(String font_file) {
		// Another thread may have finished while we waited on the lock.
		if (fonts.containsKey("A_" + font_file)) return;

		AssetEntry e = catalog.get(font_file);
		if (e == null) {
			System.err.println("[ResourceManager] Unknown font '" + font_file + "'.");
			return;
		}
		try {
			Font font = Font.createFont(Font.TRUETYPE_FONT,
					new ByteArrayInputStream(readBytes(e))).deriveFont(16f);
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			Graphics2D probe_g = probe.createGraphics();
			probe_g.setFont(font);
			FontMetrics metrics = probe_g.getFontMetrics();
			probe_g.dispose();
			int glyph_height = metrics.getAscent() + metrics.getDescent();
			for (int code = 32; code <= 126; code++) {
				char c = (char) code;
				int glyph_width = metrics.charWidth(c);
				if (glyph_width <= 0 || glyph_height <= 0) continue;
				BufferedImage glyph_image = new BufferedImage(glyph_width, glyph_height, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = glyph_image.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
				g.setFont(font);
				g.setColor(Color.WHITE);
				g.drawString(String.valueOf(c), 0, metrics.getAscent());
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
				fonts.put(c + "_" + font_file, new Texture(pixels, glyph_width, glyph_height));
			}
		} catch (Exception ex) {
			System.err.println("[ResourceManager] Failed loading font '" + font_file + "': " + ex.getMessage());
		}
	}

	private static synchronized Texture missingTexture() {
		if (missing_texture == null) {
			int size = 8;
			int[] px = new int[size * size];
			int magenta = Table.findClosestColorIndex(255, 0, 255);
			int black = Table.findClosestColorIndex(0, 0, 0);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					px[y * size + x] = (((x / 4) + (y / 4)) % 2 == 0) ? magenta : black;
				}
			}
			missing_texture = new Texture(px, size, size);
		}
		return missing_texture;
	}

	// ============================================================ load / unload

	/** Force something into memory ahead of time. */
	public static void load(String name) {
		AssetEntry e = catalog.get(name);
		if (e == null) {
			System.err.println("[ResourceManager] Cannot preload unknown asset '" + name + "'.");
			return;
		}
		if (IMG_TYPE.equals(e.type)) getImage(name);
		else if (FONT_TYPE.equals(e.type)) loadFont(name);
		else if (SOUND_TYPE.equals(e.type)) getSoundBytes(name);
		else getText(name);
	}

	/** Drop one asset from memory. It reloads by itself if something asks again. */
	public static void unload(String name) {
		images.remove(name);
		sounds.remove(name);
		level_data.remove(name);
		fonts.keySet().removeIf(k -> k.endsWith("_" + name));
		ScriptRunner.forget(name);
	}

	/** Drop everything cached. The catalog stays, so nothing is permanently lost. */
	public static void unloadAll() {
		images.clear();
		sounds.clear();
		level_data.clear();
		fonts.clear();
		ScriptRunner.forgetAll();
	}

	public static boolean isLoaded(String name) {
		return images.containsKey(name) || sounds.containsKey(name)
				|| level_data.containsKey(name) || fonts.containsKey("A_" + name);
	}

	public static int loadedCount() {
		return images.size() + sounds.size() + level_data.size() + fonts.size();
	}

	/** Every asset in one folder, reporting progress for a loading screen. */
	public static void loadFolder(String folder) {
		ArrayList<String> names = new ArrayList<>();
		for (Map.Entry<String, AssetEntry> en : catalog.entrySet()) {
			if (folder.equals(en.getValue().folder)) names.add(en.getKey());
		}
		for (int i = 0; i < names.size(); i++) {
			load(names.get(i));
			loading_progress = (i + 1) / (double) Math.max(1, names.size());
		}
	}

	/**
	 * Loads every catalogued asset and runs all the size checks. Slow on purpose.
	 * Call it once while developing to catch bad assets early, then stop.
	 */
	public static void validateAll() {
		for (String name : new ArrayList<>(catalog.keySet())) {
			load(name);
		}
	}
}
