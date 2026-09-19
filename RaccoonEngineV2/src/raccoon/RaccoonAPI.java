package raccoon;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaValue;

/**
 * Everything Lua can call, exposed to scripts as the global "RA".
 *
 *     RA:systemLog("hello", "my_script")
 *
 * Scripts get an instance of this class coerced into their environment, so any
 * public method here is immediately callable from Lua with no registration step.
 * Add a method, recompile, call it. See the USER ZONE at the bottom of the file
 * for where to put your own.
 */
public class RaccoonAPI {

	private static final RaccoonAPI api_instance = new RaccoonAPI();
	private static HashMap<String, Object> user_variables = new HashMap<>();
	public static boolean debug_console = false;
	private String system_font = "system_font.ttf";
	private static final int CONSOLE_MAX_LINES = 20;
	private static final ArrayDeque<String> console_lines = new ArrayDeque<>();
	private static int[] api_game_pixels;

	/** ScriptRunner needs this to bind "RA" into each script environment. */
	public static RaccoonAPI instance() {
		return api_instance;
	}

	public static String systemFont() {
		return api_instance.system_font;
	}

	// =========================================================== script driving

	/**
	 * Runs every active script, then every sprite's behaviour script, once.
	 *
	 * The heavy lifting (building a Lua environment, compiling source) is cached
	 * by ScriptRunner, so this is just dispatch now.
	 */
	public static void runUserScripts(int[] game_pixels) {
		api_game_pixels = game_pixels;
		ResourceManager.active_scripts.sort(Comparator.comparingInt(e -> e.priority));

		// Iterate a snapshot: a script is allowed to call scriptEnd on itself or
		// on another script, which mutates the live list mid-loop.
		List<Event> snapshot = new ArrayList<>(ResourceManager.active_scripts);
		for (Event ev : snapshot) {
			int live_index = ResourceManager.active_scripts.indexOf(ev);
			if (live_index < 0) {
				continue; // removed by an earlier script this frame
			}
			ScriptRunner.run(ev.script_name, LuaValue.varargsOf(new LuaValue[] {
					LuaValue.valueOf(live_index)
			}));
		}

		for (Map.Entry<String, Sprite> entry : new ArrayList<>(ResourceManager.sprites.entrySet())) {
			Sprite entity = entry.getValue();
			if (entity.behavior_script == null) {
				continue;
			}
			ScriptRunner.run(entity.behavior_script, LuaValue.varargsOf(new LuaValue[] {
					LuaValue.valueOf(entity.sprite_x_pos),
					LuaValue.valueOf(entity.sprite_y_pos),
					LuaValue.valueOf(entity.sprite_z_pos),
					LuaValue.valueOf(entity.sprite_length),
					LuaValue.valueOf(entity.sprite_brightness),
					LuaValue.valueOf(entity.spritename),
					LuaValue.valueOf(entity.ID),
					LuaValue.valueOf(entity.collision_radius),
					LuaValue.valueOf(entity.direction_rad)
			}));
		}
	}

	// =================================================================== system

	public void systemDebug(boolean is_debug) {
		debug_console = is_debug;
	}

	public void systemLog(String msg, String system_call) {
		if (debug_console) {
			String time = systemWorldTime();
			String output = "[" + time + "] " + system_call + ": " + msg;
			System.out.println(output);
			systemConsole(output);
		}
	}

	private void systemConsole(String msg) {
		console_lines.addLast(msg);
		if (console_lines.size() > CONSOLE_MAX_LINES) {
			console_lines.removeFirst();
		}
	}

	public static void systemDrawConsole() {
		if (!debug_console) return;
		int line_height = 14;
		int y = 4;
		for (String line : console_lines) {
			drawString(line, 4, y, api_instance.system_font, Table.NUM_LIGHT_LEVELS - 1, api_game_pixels);
			y += line_height;
		}
	}

	/**
	 * The engine's one text renderer. Used by the debug console, the loading
	 * screen and RA:uiText, so there is a single place where glyph drawing lives.
	 */
	public static void drawString(String text, int x, int y, String font_file, int brightness, int[] game_pixels) {
		if (game_pixels == null || text == null) return;
		if (brightness < 0) brightness = 0;
		if (brightness >= Table.NUM_LIGHT_LEVELS) brightness = Table.NUM_LIGHT_LEVELS - 1;
		int cursor_x = x;
		for (char c : text.toCharArray()) {
			Texture glyph = ResourceManager.getGlyph(c, font_file);
			if (glyph == null) continue;
			for (int gy = 0; gy < glyph.IMG_HEI; gy++) {
				for (int gx = 0; gx < glyph.IMG_WID; gx++) {
					int screen_x = cursor_x + gx;
					int screen_y = y + gy;
					if (screen_x < 0 || screen_x >= Main.GAME_WID) continue;
					if (screen_y < 0 || screen_y >= Main.GAME_HEI) continue;
					int color = glyph.pixels[gy * glyph.IMG_WID + gx];
					if (color >= 0) {
						game_pixels[screen_y * Main.GAME_WID + screen_x] = Table.SHADE_TABLE[brightness][color];
					}
				}
			}
			cursor_x += glyph.IMG_WID + 1;
		}
	}

	public String systemWorldTime() {
		LocalDateTime now = LocalDateTime.now();
		return String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d", now.getYear(), now.getMonthValue(),
				now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond(), now.getNano() / 1000000);
	}

	public int systemStartTime() {
		return (int) ((System.currentTimeMillis() - Main.start_time) / 1000);
	}

	public int systemGetMaxFPS() {
		return Main.MAX_FPS;
	}

	public int systemGetFrameNumber() {
		return Main.frame_num;
	}

	public void systemQuit() {
		System.exit(0);
	}

	// =================================================================== script

	public void scriptAdd(String script_name, int priority) {
		systemLog("Adding " + script_name + " to active scripts.", "scriptAdd");
		ResourceManager.active_scripts.add(new Event(script_name, priority));
	}

	public void scriptEnd(int script_index) {
		if (script_index < 0 || script_index >= ResourceManager.active_scripts.size()) {
			systemLog("Failed! No script at index " + script_index + ".", "scriptEnd");
			return;
		}
		systemLog("Removing script #" + script_index + ".", "scriptEnd");
		ResourceManager.active_scripts.remove(script_index);
	}

	public void scriptEndByName(String script_name) {
		systemLog("Removing script " + script_name + ".", "scriptEndByName");
		for (int i = 0; i < ResourceManager.active_scripts.size(); i++) {
			if (ResourceManager.active_scripts.get(i).script_name.contentEquals(script_name)) {
				scriptEnd(i);
				return;
			}
		}
	}

	/**
	 * Throws away a script's cached environment and compiled code so the next
	 * run re-reads it from disk. Handy while developing: edit a .lua, call this,
	 * see the change without restarting.
	 */
	public void scriptReload(String script_name) {
		systemLog("Reloading " + script_name + ".", "scriptReload");
		ResourceManager.level_data.remove(script_name);
		ScriptRunner.forget(script_name);
	}

	// ==================================================================== audio

	private static Sound bgm = null;
	private static final HashMap<String, Sound> active_se = new HashMap<>();

	/**
	 * Volume is 0.0 to 1.0. Lua numbers arrive as doubles, hence the signature.
	 */
	public void audioPlayBGM(String bgm_name, boolean loop, double volume) {
		systemLog("Playing BGM " + bgm_name + ".", "audioPlayBGM");
		audioStopBGM();
		byte[] data = ResourceManager.getSoundBytes(bgm_name);
		if (data == null) {
			systemLog("Failed! No such sound: " + bgm_name, "audioPlayBGM");
			return;
		}
		bgm = new Sound(data, loop, (float) volume);
	}

	public void audioChangeBGMVol(double volume) {
		if (bgm != null) {
			bgm.setVolume((float) volume);
		}
	}

	public void audioStopBGM() {
		if (bgm != null) {
			bgm.stopSound();
			bgm.close();
			bgm = null;
		}
	}

	/**
	 * Sound effects are tracked by name so they can be stopped or adjusted
	 * later. Playing a name that is already going restarts it.
	 */
	public void audioPlaySE(String se_name, boolean loop, double volume) {
		byte[] data = ResourceManager.getSoundBytes(se_name);
		if (data == null) {
			systemLog("Failed! No such sound: " + se_name, "audioPlaySE");
			return;
		}
		audioStopSE(se_name);
		active_se.put(se_name, new Sound(data, loop, (float) volume));
		// Drop finished one-shots so the map doesn't grow without bound.
		active_se.entrySet().removeIf(e -> !e.getValue().isPlaying() && !e.getKey().equals(se_name));
	}

	public void audioChangeSEVol(String se_name, double volume) {
		Sound s = active_se.get(se_name);
		if (s != null) {
			s.setVolume((float) volume);
		}
	}

	public void audioStopSE(String se_name) {
		Sound s = active_se.remove(se_name);
		if (s != null) {
			s.stopSound();
			s.close();
		}
	}

	public void audioStopAll() {
		audioStopBGM();
		for (Sound s : new ArrayList<>(active_se.values())) {
			s.stopSound();
			s.close();
		}
		active_se.clear();
	}

	// ======================================================================= ui
	//
	// These draw straight onto the frame buffer AFTER the world has been
	// rendered, so they always sit on top and ignore the depth buffer. That is
	// what you want for a HUD.
	//
	// Only call them from a script (they need the frame buffer the engine hands
	// to runUserScripts each frame).
	//
	// BRIGHTNESS, and this trips everybody up once:
	//   0.5  = the image's TRUE colours. This is the value you normally want.
	//   <0.5 = fades toward black.
	//   >0.5 = fades toward WHITE, and 1.0 is pure white with no detail left.
	// It is not a 0-to-full dimmer; 0.5 is the middle of a black-to-white ramp.
	// That is the same convention the map file uses (every sample map writes
	// 0.5 for normal lighting), so it is consistent - just not obvious.

	/**
	 * Draws text.
	 *   text       - what to write
	 *   x, y       - top-left corner, in the 640x480 internal resolution
	 *   font_name  - a .ttf in data/fonts, e.g. "system_font.ttf"
	 *   brightness - 0.0 (black) to 1.0 (white)
	 */
	public void uiText(String text, int x, int y, String font_name, double brightness) {
		if (api_game_pixels == null) return;
		int b = (int) (clamp01(brightness) * (Table.NUM_LIGHT_LEVELS - 1));
		drawString(text, x, y, font_name, b, api_game_pixels);
	}

	/** Same as uiText but with the built-in font, for quick HUD work. */
	public void uiTextDefault(String text, int x, int y, double brightness) {
		uiText(text, x, y, system_font, brightness);
	}

	/**
	 * Draws an image from data/pics.
	 *   pic_name   - e.g. "hud_frame.png"
	 *   x, y       - top-left corner
	 *   scale      - 1.0 is original size, 2.0 is double, and so on
	 *   brightness - 0.0 to 1.0
	 * Transparent pixels are skipped, so cut-out HUD art works as expected.
	 */
	public void uiDraw(String pic_name, int x, int y, double scale, double brightness) {
		if (api_game_pixels == null) return;
		Texture pic = ResourceManager.getImage(pic_name);
		if (pic == null) return;
		if (scale <= 0) scale = 1;
		int b = (int) (clamp01(brightness) * (Table.NUM_LIGHT_LEVELS - 1));
		int out_w = (int) (pic.IMG_WID * scale);
		int out_h = (int) (pic.IMG_HEI * scale);
		for (int oy = 0; oy < out_h; oy++) {
			int screen_y = y + oy;
			if (screen_y < 0 || screen_y >= Main.GAME_HEI) continue;
			int src_v = (int) (oy / scale);
			if (src_v >= pic.IMG_HEI) continue;
			for (int ox = 0; ox < out_w; ox++) {
				int screen_x = x + ox;
				if (screen_x < 0 || screen_x >= Main.GAME_WID) continue;
				int src_u = (int) (ox / scale);
				if (src_u >= pic.IMG_WID) continue;
				int color = pic.pixels[src_v * pic.IMG_WID + src_u];
				if (color >= 0) {
					api_game_pixels[screen_y * Main.GAME_WID + screen_x] = Table.SHADE_TABLE[b][color];
				}
			}
		}
	}

	/**
	 * A solid rectangle - health bars, letterboxing, menu backdrops.
	 * palette_index is 0-255 in the engine palette (15 is white, 0 is black,
	 * 9 is red). brightness is 0.0 to 1.0.
	 */
	public void uiFillRect(int x, int y, int w, int h, int palette_index, double brightness) {
		if (api_game_pixels == null) return;
		if (palette_index < 0 || palette_index >= Table.NUM_COLORS) return;
		int b = (int) (clamp01(brightness) * (Table.NUM_LIGHT_LEVELS - 1));
		int color = Table.SHADE_TABLE[b][palette_index];
		for (int oy = 0; oy < h; oy++) {
			int screen_y = y + oy;
			if (screen_y < 0 || screen_y >= Main.GAME_HEI) continue;
			for (int ox = 0; ox < w; ox++) {
				int screen_x = x + ox;
				if (screen_x < 0 || screen_x >= Main.GAME_WID) continue;
				api_game_pixels[screen_y * Main.GAME_WID + screen_x] = color;
			}
		}
	}

	/** Width in pixels the given text would take, for centring things. */
	public int uiTextWidth(String text, String font_name) {
		if (text == null) return 0;
		int total = 0;
		for (char c : text.toCharArray()) {
			Texture glyph = ResourceManager.getGlyph(c, font_name);
			if (glyph != null) total += glyph.IMG_WID + 1;
		}
		return total;
	}

	public int uiScreenWidth() {
		return Main.GAME_WID;
	}

	public int uiScreenHeight() {
		return Main.GAME_HEI;
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	// ================================================================= resources
	//
	// Nothing is in memory until something asks for it. These let a script say
	// what it wants ahead of time and throw it away afterwards.

	public void resourceLoad(String name) {
		ResourceManager.load(name);
	}

	public void resourceUnload(String name) {
		systemLog("Unloading " + name + ".", "resourceUnload");
		ResourceManager.unload(name);
	}

	/** Frees every cached asset. The catalog stays, so anything reloads on demand. */
	public void resourceUnloadAll() {
		systemLog("Unloading all cached assets.", "resourceUnloadAll");
		ResourceManager.unloadAll();
	}

	public boolean resourceIsLoaded(String name) {
		return ResourceManager.isLoaded(name);
	}

	public int resourceLoadedCount() {
		return ResourceManager.loadedCount();
	}

	/**
	 * Loads a whole folder ("tex", "sprites", "se", ...) on a background thread
	 * with a loading screen in front of it. Poll RA:loadingIsActive() to know
	 * when it has finished.
	 */
	public void resourceLoadFolderAsync(String folder, String loading_text) {
		runAsync(loading_text, () -> ResourceManager.loadFolder(folder));
	}

	/** Loads and size-checks every asset. Slow. For development. */
	public void resourceValidateAll() {
		ResourceManager.validateAll();
	}

	/** Writes the current data/ folder out as a single data.rpk you can ship. */
	public void resourcePackRpk() {
		systemLog("Packing data/ into data.rpk.", "resourcePackRpk");
		ResourceManager.packNow();
	}

	// ================================================================== loading

	public boolean loadingIsActive() {
		return ResourceManager.is_loading;
	}

	public double loadingProgress() {
		return ResourceManager.loading_progress;
	}

	public void loadingSetMessage(String msg) {
		ResourceManager.loading_message = msg;
	}

	/**
	 * Shared plumbing for "do this slowly, on another thread, behind a loading
	 * screen". While is_loading is true Screen draws the loading screen and
	 * skips both the world render and all scripts, so the background job has the
	 * world data to itself.
	 */
	private void runAsync(String loading_text, Runnable job) {
		if (ResourceManager.is_loading) {
			systemLog("Failed! Something is already loading.", "loading");
			return;
		}
		ResourceManager.loading_message = (loading_text == null || loading_text.isEmpty())
				? "LOADING" : loading_text;
		ResourceManager.loading_progress = -1;
		ResourceManager.is_loading = true;
		Thread t = new Thread(() -> {
			try {
				job.run();
			} catch (Exception e) {
				System.err.println("[RaccoonAPI] Background load failed: " + e.getMessage());
				e.printStackTrace();
			} finally {
				ResourceManager.loading_progress = 1;
				ResourceManager.is_loading = false;
			}
		}, "raccoon_loading_thread");
		t.setDaemon(true);
		t.start();
	}

	// ==================================================================== world

	public void worldSetSkybox(String skyboxname, double brightness) {
		systemLog("Setting skybox to " + skyboxname + ".", "worldSetSkybox");
		Screen.skybox = skyboxname;
		Screen.skybox_brightness = (int) (clamp01(brightness) * (Table.NUM_LIGHT_LEVELS - 1));
	}

	public void worldSetSkyboxOffset(int offset) {
		systemLog("Setting skybox offset by " + offset + ".", "worldSetSkyboxOffset");
		Screen.sky_offset = offset;
	}

	public int worldGetSectorCountLimit() {
		return Screen.MAX_NUM_SECTORS;
	}

	public void worldSetSectorCountLimit(int lim) {
		systemLog("Setting sector limit.", "worldSetSectorCountLimit");
		Screen.MAX_NUM_SECTORS = lim;
	}

	/** Sprites further away than this are not drawn at all. */
	public void worldSetSpriteRenderDistance(double dist) {
		systemLog("Setting sprite render distance to " + dist + ".", "worldSetSpriteRenderDistance");
		Screen.sprite_render_distance = dist;
	}

	public double worldGetSpriteRenderDistance() {
		return Screen.sprite_render_distance;
	}

	private double[] worldLoadMapHelperNormalize(double x1, double z1, double x2, double z2) {
		if (x1 > x2 || (x1 == x2 && z1 > z2)) {
			double tmpX = x1, tmpZ = z1;
			x1 = x2; z1 = z2;
			x2 = tmpX; z2 = tmpZ;
		}
		return new double[] { x1, z1, x2, z2 };
	}

	/**
	 * Loads a map on a background thread behind a loading screen. Prefer this
	 * over worldLoadMap for anything bigger than a test room - it keeps the
	 * window responsive instead of freezing mid-frame.
	 */
	public void worldLoadMapAsync(String mapname, String loading_text) {
		runAsync(loading_text, () -> worldLoadMap(mapname));
	}

	/**
	 * MAP FILE FORMAT
	 * ---------------
	 * Unchanged, so existing maps and the existing editor export still work.
	 *
	 *   [SIZE]     width height
	 *   [SECTORS]  one per sector
	 *   [WALLS]    the outer shell     -> becomes Edge.TYPE_WORLD_BOUNDARY
	 *   [PORTALS]  between two sectors -> becomes Edge.TYPE_WALL
	 *
	 * The section names kept their old spelling on purpose: renaming them would
	 * break every map already exported from the editor. [BOUNDARIES] and [EDGES]
	 * are accepted as aliases, so the editor can be renamed later without
	 * touching the engine again.
	 */
	public void worldLoadMap(String mapname) {
		systemLog("Loading map " + mapname + ".", "worldLoadMap");
		Screen.sectors = new Sector[Screen.MAX_NUM_SECTORS];
		Screen.map_width = 0;
		Screen.map_height = 0;
		Screen.sectors_count = 0;
		int selected = -1;
		try {
			systemLog("Reading level data.", "worldLoadMap");
			String maptxt = ResourceManager.getText(mapname);
			if (maptxt == null) {
				systemLog("Not a map. Resetting sectors to 0.", "worldLoadMap");
				return;
			}
			String[] lines = maptxt.split("\n");
			for (String line : lines) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (line.equals("[SIZE]")) {
					systemLog("Reading map size and establishing verticals.", "worldLoadMap");
					selected = 3;
					continue;
				}
				if (line.equals("[SECTORS]")) {
					systemLog("Reading sectors.", "worldLoadMap");
					selected = 0;
					continue;
				}
				if (line.equals("[WALLS]") || line.equals("[BOUNDARIES]")) {
					systemLog("Reading world boundaries.", "worldLoadMap");
					selected = 1;
					continue;
				}
				if (line.equals("[PORTALS]") || line.equals("[EDGES]")) {
					systemLog("Reading walls and initializing collision.", "worldLoadMap");
					Screen.portal_collision_data = new boolean[Screen.sectors_count * Screen.sectors_count];
					selected = 2;
					continue;
				}

				String[] parts = line.split("\\s+");
				switch (selected) {
					case 0 -> {
						if (Screen.sectors_count >= Screen.MAX_NUM_SECTORS) {
							systemLog("Failed! Too many sectors. This means your map is too big. Try simplifying it with less sectors!", "worldLoadMap");
							return;
						}
						int sector_id = Integer.parseInt(parts[0]);
						double floor_height = Double.parseDouble(parts[1]);
						double ceil_height = Double.parseDouble(parts[2]);
						if (floor_height < 0 || floor_height > Screen.LIMIT_MAP_COORD
								|| ceil_height < 0 || ceil_height > Screen.LIMIT_MAP_COORD) {
							systemLog("Failed! Your height value is bigger than the world coordinate limit of " + Screen.LIMIT_MAP_COORD + ".", "worldLoadMap");
							return;
						}
						double floor_brightness = Double.parseDouble(parts[4]);
						double ceil_brightness = Double.parseDouble(parts[8]);
						if (floor_brightness < 0 || floor_brightness > 1 || ceil_brightness < 0 || ceil_brightness > 1) {
							systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
							return;
						}
						int actual_floor_brightness = (int) (floor_brightness * (Table.NUM_LIGHT_LEVELS - 1));
						int actual_ceil_brightness = (int) (ceil_brightness * (Table.NUM_LIGHT_LEVELS - 1));
						Screen.sectors[sector_id] = new Sector(sector_id, floor_height, ceil_height,
								parts[3], actual_floor_brightness, Double.parseDouble(parts[5]),
								Boolean.parseBoolean(parts[6]), parts[7], actual_ceil_brightness,
								Double.parseDouble(parts[9]), Boolean.parseBoolean(parts[10]));
						Screen.sectors_count++;
					}
					case 1 -> {
						// World boundary: one sector, one texture, ray stops.
						double[] xz = worldLoadMapHelperNormalize(
								Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
								Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
						double x1 = xz[0], z1 = xz[1], x2 = xz[2], z2 = xz[3];
						if (x1 < 0 || z1 < 0 || x2 < 0 || z2 < 0) {
							systemLog("Failed! Your map has negative values! No bueno!", "worldLoadMap");
							return;
						}
						int sector_id = Integer.parseInt(parts[4]);
						String texture = parts[5];
						double brightness = Double.parseDouble(parts[6]);
						if (brightness < 0 || brightness > 1) {
							systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
							return;
						}
						int actual_brightness = (int) (brightness * (Table.NUM_LIGHT_LEVELS - 1));
						double tiled = Double.parseDouble(parts[7]);
						boolean skip_texture = Boolean.parseBoolean(parts[8]);
						if (z1 == z2) {
							Screen.sectors[sector_id].updateSectorBoundary(z1, 1);
							int start_x = (int) Math.floor(x1);
							int end_x = (int) Math.floor(x2);
							for (int px = start_x; px < end_x; px++) {
								int key = Screen.makeWallIndex(px, (int) z1, 1);
								if (key < 0) continue;
								Screen.verticals[key] = Edge.worldBoundary(px, z1, x2, z2, sector_id,
										texture, actual_brightness, tiled, skip_texture);
							}
						} else if (x1 == x2) {
							Screen.sectors[sector_id].updateSectorBoundary(x1, 0);
							int start_z = (int) Math.floor(z1);
							int end_z = (int) Math.floor(z2);
							for (int pz = start_z; pz < end_z; pz++) {
								int key = Screen.makeWallIndex((int) x1, pz, 0);
								if (key < 0) continue;
								Screen.verticals[key] = Edge.worldBoundary(x1, pz, x2, z2, sector_id,
										texture, actual_brightness, tiled, skip_texture);
							}
						}
					}
					case 2 -> {
						// Wall: between two sectors, three texture bands, ray passes through.
						double[] xz = worldLoadMapHelperNormalize(
								Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
								Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
						double x1 = xz[0], z1 = xz[1], x2 = xz[2], z2 = xz[3];
						if (x1 < 0 || z1 < 0 || x2 < 0 || z2 < 0) {
							systemLog("Failed! Your map has negative values! No bueno!", "worldLoadMap");
							return;
						}
						int sector_a = Integer.parseInt(parts[4]);
						int sector_b = Integer.parseInt(parts[5]);
						String bottom_tex = parts[6];
						double bottom_brightness = Double.parseDouble(parts[7]);
						String middle_tex = parts[10];
						double middle_brightness = Double.parseDouble(parts[11]);
						String top_tex = parts[14];
						double top_brightness = Double.parseDouble(parts[15]);
						if (bottom_brightness < 0 || bottom_brightness > 1
								|| middle_brightness < 0 || middle_brightness > 1
								|| top_brightness < 0 || top_brightness > 1) {
							systemLog("Failed! Your brightness value is invalid.", "worldLoadMap");
							return;
						}
						int actual_bottom_brightness = (int) (bottom_brightness * (Table.NUM_LIGHT_LEVELS - 1));
						double bottom_tiled = Double.parseDouble(parts[8]);
						boolean bottom_skip_texture = Boolean.parseBoolean(parts[9]);
						int actual_middle_brightness = (int) (middle_brightness * (Table.NUM_LIGHT_LEVELS - 1));
						double middle_tiled = Double.parseDouble(parts[12]);
						boolean middle_skip_texture = Boolean.parseBoolean(parts[13]);
						int actual_top_brightness = (int) (top_brightness * (Table.NUM_LIGHT_LEVELS - 1));
						double top_tiled = Double.parseDouble(parts[16]);
						boolean top_skip_texture = Boolean.parseBoolean(parts[17]);
						boolean is_solid = Boolean.parseBoolean(parts[18]);
						Screen.portal_collision_data[sector_a * Screen.sectors_count + sector_b] = is_solid;
						Screen.portal_collision_data[sector_b * Screen.sectors_count + sector_a] = is_solid;
						if (z1 == z2) {
							Screen.sectors[sector_a].updateSectorBoundary(z1, 1);
							Screen.sectors[sector_b].updateSectorBoundary(z1, 1);
							int start_x = (int) Math.floor(x1);
							int end_x = (int) Math.floor(x2);
							for (int px = start_x; px < end_x; px++) {
								int key = Screen.makeWallIndex(px, (int) z1, 1);
								if (key < 0) continue;
								Screen.verticals[key] = Edge.wall(px, z1, x2, z2, sector_a, sector_b,
										bottom_tex, actual_bottom_brightness, bottom_tiled, bottom_skip_texture,
										middle_tex, actual_middle_brightness, middle_tiled, middle_skip_texture,
										top_tex, actual_top_brightness, top_tiled, top_skip_texture, is_solid);
							}
						} else if (x1 == x2) {
							Screen.sectors[sector_a].updateSectorBoundary(x1, 0);
							Screen.sectors[sector_b].updateSectorBoundary(x1, 0);
							int start_z = (int) Math.floor(z1);
							int end_z = (int) Math.floor(z2);
							for (int pz = start_z; pz < end_z; pz++) {
								int key = Screen.makeWallIndex((int) x1, pz, 0);
								if (key < 0) continue;
								Screen.verticals[key] = Edge.wall(x1, pz, x2, z2, sector_a, sector_b,
										bottom_tex, actual_bottom_brightness, bottom_tiled, bottom_skip_texture,
										middle_tex, actual_middle_brightness, middle_tiled, middle_skip_texture,
										top_tex, actual_top_brightness, top_tiled, top_skip_texture, is_solid);
							}
						}
					}
					case 3 -> {
						Screen.map_width = Integer.parseInt(parts[0]);
						Screen.map_height = Integer.parseInt(parts[1]);
						if (Screen.map_width > Screen.LIMIT_MAP_COORD || Screen.map_height > Screen.LIMIT_MAP_COORD) {
							systemLog("Failed! Your world is bigger than the world coordinate limit of " + Screen.LIMIT_MAP_COORD + ".", "worldLoadMap");
							return;
						}
						Screen.vertical_length = (Screen.map_width + 1) * (Screen.map_height + 1) * 2;
						Screen.verticals = new Edge[Screen.vertical_length];
					}
				}
			}
		} catch (Exception e) {
			systemLog("Failed to read map data. Exception " + e.getMessage(), "worldLoadMap");
		}
	}

	public void worldSetPortalCollision(int sector_a, int sector_b, boolean is_solid) {
		systemLog("Setting collision to " + is_solid + " from sector " + sector_a + " to " + sector_b + ".", "worldSetPortalCollision");
		Screen.portal_collision_data[sector_a * Screen.sectors_count + sector_b] = is_solid;
	}

	public void worldChangeSectorVals(boolean is_floor, int sector_id, String texture, double brightness, int tiled, boolean skip_texture) {
		systemLog("Setting sector values.", "worldChangeSectorVals");
		if (sector_id < 0 || sector_id >= Screen.sectors_count) {
			systemLog("Failed! Sector ID must be valid.", "worldChangeSectorVals");
			return;
		}
		if (brightness < 0 || brightness > 1) {
			systemLog("Failed! Invalid brightness.", "worldChangeSectorVals");
			return;
		}
		if (is_floor) {
			Screen.sectors[sector_id].floor_texture = texture;
			Screen.sectors[sector_id].floor_tiled = tiled;
			Screen.sectors[sector_id].floor_brightness = (int) (brightness * (Table.NUM_LIGHT_LEVELS - 1));
			Screen.sectors[sector_id].floor_skip_texture = skip_texture;
		} else {
			Screen.sectors[sector_id].ceil_texture = texture;
			Screen.sectors[sector_id].ceil_tiled = tiled;
			Screen.sectors[sector_id].ceil_brightness = (int) (brightness * (Table.NUM_LIGHT_LEVELS - 1));
			Screen.sectors[sector_id].ceil_skip_texture = skip_texture;
		}
	}

	/**
	 * Retextures one edge at runtime.
	 *   band: 0 = bottom, 1 = middle, 2 = top.
	 * A world boundary only has a middle band (that is its single wall texture),
	 * so pass 1 for those.
	 */
	public void worldChangeVerticalVals(int x, int z, int is_vertical, String texture,
			boolean skip_texture, double brightness, int tiled, int band) {
		systemLog("Setting vertical values.", "worldChangeVerticalVals");
		int index = Screen.makeWallIndex(x, z, is_vertical);
		if (index < 0) {
			systemLog("Failed! Out of bounds index.", "worldChangeVerticalVals");
			return;
		}
		if (brightness < 0 || brightness > 1) {
			systemLog("Failed! Invalid brightness.", "worldChangeVerticalVals");
			return;
		}
		Edge edge = Screen.verticals[index];
		if (edge == null) {
			systemLog("Failed! Nothing at that grid edge.", "worldChangeVerticalVals");
			return;
		}
		int actual_brightness = (int) (brightness * (Table.NUM_LIGHT_LEVELS - 1));
		if (edge.isWorldBoundary()) {
			// One band only; ignore whatever band was asked for.
			edge.middle_texture = texture;
			edge.middle_skip_texture = skip_texture;
			edge.middle_brightness = actual_brightness;
			edge.middle_tiled = tiled;
			return;
		}
		if (band == 0) {
			edge.bottom_texture = texture;
			edge.bottom_skip_texture = skip_texture;
			edge.bottom_brightness = actual_brightness;
			edge.bottom_tiled = tiled;
		} else if (band == 1) {
			edge.middle_texture = texture;
			edge.middle_skip_texture = skip_texture;
			edge.middle_brightness = actual_brightness;
			edge.middle_tiled = tiled;
		} else if (band == 2) {
			edge.top_texture = texture;
			edge.top_skip_texture = skip_texture;
			edge.top_brightness = actual_brightness;
			edge.top_tiled = tiled;
		} else {
			systemLog("Failed! Band must be 0 (bottom), 1 (middle) or 2 (top).", "worldChangeVerticalVals");
		}
	}

	// =================================================================== entity

	public void entityUpsertSprite(String sprite_id, double sprite_x_pos, double sprite_y_pos,
			double sprite_z_pos, double sprite_length, double sprite_brightness, String spritename,
			String behavior_script, double collision_radius, double direction_rad) {
		int actual_brightness = (int) (clamp01(sprite_brightness) * (Table.NUM_LIGHT_LEVELS - 1));
		ResourceManager.sprites.put(sprite_id, new Sprite(sprite_id, sprite_x_pos, sprite_y_pos,
				sprite_z_pos, sprite_length, spritename, behavior_script, actual_brightness,
				collision_radius, direction_rad));
	}

	public void entityRemoveSprite(String sprite_id) {
		systemLog("Removing entity " + sprite_id + ".", "entityRemoveSprite");
		ResourceManager.sprites.remove(sprite_id);
	}

	public int entityCount() {
		return ResourceManager.sprites.size();
	}

	// =================================================================== player

	public double playerGetPosition(int dimension_number) {
		switch (dimension_number) {
			case 0: return Camera.player_x;
			case 1: return Camera.player_y;
			case 2: return Camera.player_z;
			default: systemLog("Invalid dimension " + dimension_number, "playerGetPosition");
		}
		return -1;
	}

	public void playerSetPosition(double x, double y, double z, double dir) {
		if (x < 0 || y < 0 || z < 0 || dir < 0 || dir >= Table.pi2) {
			systemLog("Failed to set player at given position. Invalid values.", "playerSetPosition");
			return;
		}
		systemLog("Setting player position to [" + x + ", " + y + ", " + z + "]. Direction=" + dir + ".", "playerSetPosition");
		Camera.player_x = x;
		Camera.player_y = y;
		Camera.player_z = z;
		Camera.direction_rad = dir;
	}

	public int playerGetSector() {
		return Camera.player_sector;
	}

	public double playerGetDirection() {
		return Camera.direction_rad;
	}

	public void playerSetMoveSpeed(double move_speed) {
		systemLog("Setting move speed to " + move_speed + ".", "playerSetMoveSpeed");
		Camera.move_speed = move_speed;
	}

	public void playerSetTurnSpeed(double turn_speed) {
		systemLog("Setting turn speed to " + turn_speed + ".", "playerSetTurnSpeed");
		Camera.turn_speed = turn_speed;
	}

	public void playerSetPitchSpeed(double pitch_speed) {
		systemLog("Setting pitch speed to " + pitch_speed + ".", "playerSetPitchSpeed");
		Camera.pitch_speed = pitch_speed;
	}

	public void playerSetFly() {
		systemLog("Turning on jetpack.", "playerSetFly");
		Camera.jetpack = true;
	}

	public void playerSetWalk(double floor_offset, double bob_speed, double bob_amount) {
		systemLog("Turning off jetpack. Setting walking variables.", "playerSetWalk");
		Camera.jetpack = false;
		Camera.FLOOR_OFFSET = floor_offset;
		Camera.BOB_SPEED = bob_speed;
		Camera.BOB_AMOUNT = bob_amount;
	}

	public void playerSetGravity(double grav) {
		systemLog("Setting player gravity.", "playerSetGravity");
		Camera.GRAVITY = grav;
	}

	public double playerGetGravity() {
		return Camera.GRAVITY;
	}

	// ==================================================================== input

	public void inputSetMouseSensitivity(double sens) {
		systemLog("Setting mouse sensitivity to " + sens + ".", "inputSetMouseSensitivity");
		Camera.mouse_sens = sens;
	}

	public boolean inputGetKeyStatus(boolean is_once, String keyname) {
		Integer vk = Camera.KEY_MAP.get(keyname.toLowerCase());
		if (vk == null) {
			System.err.println("Unknown key: " + keyname);
			return false;
		}
		return is_once ? Camera.isOnce(vk) : Camera.isHeld(vk);
	}

	// ==================================================================== store

	public void storeSet(String key, Object val) {
		user_variables.put(key, val);
	}

	public Object storeGet(String key) {
		return user_variables.get(key);
	}

	public void storeClear() {
		user_variables.clear();
	}

	/**
	 * SAVING
	 * ------
	 * What counts as "the save" is entirely game-specific, so the engine does
	 * not try to guess. What it can do honestly is persist the store, since that
	 * is the one place the engine already knows about.
	 *
	 * This writes every storeSet value to a plain text file, one per line:
	 *
	 *     n|player_hp|100.0        n = number
	 *     s|current_map|level2.txt s = string
	 *     b|has_red_key|true       b = boolean
	 *
	 * Text on purpose: you can open a save in an editor and see what went wrong.
	 *
	 * LIMITS, and how to grow past them:
	 *   - only numbers, strings and booleans survive; Lua tables do not. Flatten
	 *     them ("inv_1", "inv_2", ...) or serialise to a string yourself.
	 *   - keys cannot contain the '|' separator.
	 *   - player position, the current map, sprite positions and script state
	 *     are NOT captured automatically. Put whatever matters into the store
	 *     before saving:
	 *
	 *         RA:storeSet("map",  "level2.txt")
	 *         RA:storeSet("px",   RA:playerGetPosition(0))
	 *         RA:storeSet("py",   RA:playerGetPosition(1))
	 *         RA:storeSet("pz",   RA:playerGetPosition(2))
	 *         RA:storeSaveGameState("slot1.sav")
	 *
	 *     and put it back on load:
	 *
	 *         RA:storeLoadGameState("slot1.sav")
	 *         RA:worldLoadMapAsync(RA:storeGet("map"), "LOADING")
	 *         RA:playerSetPosition(RA:storeGet("px"), ..., 0)
	 *
	 *   - if you would rather write your own format (binary, versioned, one file
	 *     per slot), the USER ZONE at the bottom of this file is the place for it.
	 *
	 * Returns true on success.
	 */
	public boolean storeSaveGameState(String filename) {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("# Raccoon Engine save - ").append(systemWorldTime()).append("\n");
			for (Map.Entry<String, Object> e : user_variables.entrySet()) {
				String key = e.getKey();
				Object val = e.getValue();
				if (key.contains("|")) {
					systemLog("Skipping key with '|' in it: " + key, "storeSaveGameState");
					continue;
				}
				if (val instanceof Number) {
					sb.append("n|").append(key).append("|").append(((Number) val).doubleValue()).append("\n");
				} else if (val instanceof Boolean) {
					sb.append("b|").append(key).append("|").append(val).append("\n");
				} else if (val instanceof String) {
					sb.append("s|").append(key).append("|").append(((String) val).replace("\n", " ")).append("\n");
				} else if (val != null) {
					systemLog("Skipping key '" + key + "': cannot save a " + val.getClass().getSimpleName() + ".", "storeSaveGameState");
				}
			}
			Path p = Paths.get(filename);
			Files.write(p, sb.toString().getBytes(StandardCharsets.UTF_8));
			systemLog("Saved to " + filename + ".", "storeSaveGameState");
			return true;
		} catch (IOException ex) {
			systemLog("Failed to save: " + ex.getMessage(), "storeSaveGameState");
			return false;
		}
	}

	/** Reads a file written by storeSaveGameState back into the store. */
	public boolean storeLoadGameState(String filename) {
		File f = new File(filename);
		if (!f.exists()) {
			systemLog("No save file at " + filename + ".", "storeLoadGameState");
			return false;
		}
		try {
			for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split("\\|", 3);
				if (parts.length != 3) continue;
				switch (parts[0]) {
					case "n" -> user_variables.put(parts[1], Double.parseDouble(parts[2]));
					case "b" -> user_variables.put(parts[1], Boolean.parseBoolean(parts[2]));
					case "s" -> user_variables.put(parts[1], parts[2]);
					default -> systemLog("Unknown save line type '" + parts[0] + "'.", "storeLoadGameState");
				}
			}
			systemLog("Loaded " + filename + ".", "storeLoadGameState");
			return true;
		} catch (Exception ex) {
			systemLog("Failed to load: " + ex.getMessage(), "storeLoadGameState");
			return false;
		}
	}

	// ============================================================================
	// USER ZONE - this part is yours
	// ============================================================================
	//
	// Anything you add below is callable from Lua straight away as
	// RA:yourFunction(...). No registration, no binding step: the whole object
	// is handed to every script.
	//
	// WHY YOU WANT THIS: Lua scripts run every frame and are re-entered from the
	// top each time. Java is the right home for anything that is hot (runs per
	// frame over lots of data), or that has to REMEMBER something between
	// frames. Fields on this class are instance fields on a singleton, so they
	// simply persist - no store round-trip, no re-parsing, full speed.
	//
	// Rough rule: if it is game DESIGN, write it in Lua. If it is a SYSTEM -
	// pathfinding, an inventory model, a damage table, an AI director - write it
	// here and let Lua drive it.
	//
	// Example: a persistent counter Lua could never keep on its own.
	//
	//     private int shots_fired = 0;
	//
	//     public void userRegisterShot() {
	//         shots_fired++;
	//     }
	//
	//     public int userGetShotsFired() {
	//         return shots_fired;
	//     }
	//
	// then in Lua:  RA:userRegisterShot()   /   local n = RA:userGetShotsFired()
	//
	// Keep the "user" prefix on your own methods and they will never collide
	// with an engine call added later.

	public String userExampleFunc() {
		return "This is a dummy function. Create your wonderful user-specific functions like this! "
				+ "Add fields up here too - they persist between frames, unlike Lua locals.";
	}
}
