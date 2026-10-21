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

	/** Sprites further away than this are not drawn at all. */
	public void worldSetSpriteRenderDistance(double dist) {
		systemLog("Setting sprite render distance to " + dist + ".", "worldSetSpriteRenderDistance");
		Screen.sprite_render_distance = dist;
	}

	public double worldGetSpriteRenderDistance() {
		return Screen.sprite_render_distance;
	}

	/**
	 * Loads a map on a background thread behind a loading screen. Prefer this
	 * over worldLoadMap for anything bigger than a test room - it keeps the
	 * window responsive instead of freezing mid-frame.
	 *
	 * The loading thread only READS the file (Rmap.read builds a whole new world on
	 * the side). The new world is swapped in on the game thread at the start of the
	 * first frame after loading ends (Screen.update), so the player, the scripts and
	 * the renderer never see a half-built world. A bad file leaves the old one.
	 */
	public void worldLoadMapAsync(String mapname, String loading_text) {
		runAsync(loading_text, () -> {
			Rmap.World world = readMapFile(mapname);
			if (world != null) {
				Screen.pending_world = world;
			}
		});
	}

	/**
	 * Loads a map from data/maps/ straight away, inside the current frame.
	 *
	 * MAP FILES are .rmap files saved by the map editor (RaccoonMapEditor). The layout,
	 * and every check made while reading, are in Rmap.java. In short: a grid
	 * of 1x1 cells, each with its own heights and four surfaces, optional edge settings
	 * on grid lines, one boundary surface for the map's outer edge, and the map's sprites.
	 *
	 * Loading a map removes the previous map's sprites; sprites made by scripts stay.
	 * If the file is missing or broken, the reason is printed to System.err and the
	 * world that was loaded before stays as it was.
	 */
	public void worldLoadMap(String mapname) {
		Rmap.World world = readMapFile(mapname);
		if (world != null) {
			Screen.installWorld(world);
		}
	}

	/** Reads and checks a map file. Returns null (after saying why) if it can't be used. */
	private Rmap.World readMapFile(String mapname) {
		systemLog("Loading map " + mapname + ".", "worldLoadMap");
		byte[] data = ResourceManager.getBytes(mapname);
		if (data == null) {
			System.err.println("[worldLoadMap] FAILED: there is no map called '" + mapname + "' in data/maps/.");
			systemLog("Failed! No such map: " + mapname, "worldLoadMap");
			return null;
		}
		try {
			return Rmap.read(data);
		} catch (IOException e) {
			// Deliberately printed to stderr, not just systemLog: systemLog is a no-op
			// whenever the debug console is off, and a broken map must never fail silently.
			System.err.println("[worldLoadMap] FAILED to read '" + mapname + "': " + e.getMessage());
			systemLog("Failed to read map data: " + e.getMessage(), "worldLoadMap");
			return null;
		}
	}

	/** Width of the loaded map in cells (X), or 0 with no map. */
	public int worldGetMapWidth() {
		return Screen.cells == null ? 0 : Screen.map_width;
	}

	/** Height of the loaded map in cells (Z), or 0 with no map. */
	public int worldGetMapHeight() {
		return Screen.cells == null ? 0 : Screen.map_height;
	}

	/** True if the world position (x, z) is on the loaded map. */
	public boolean worldIsInsideMap(double x, double z) {
		return Screen.cellIndexAt(x, z) >= 0;
	}

	/**
	 * Changes the face drawn on the map's outer edge, all the way round.
	 *   brightness - 0.0 to 1.0, 0.5 = true colours
	 *   tiled      - 0 or more, as in the map editor
	 */
	public void worldSetBoundaryTexture(String texture, double brightness, double tiled, boolean skip_texture) {
		String problem = surfaceProblem(texture, brightness, tiled);
		if (problem != null || Screen.cells == null) {
			systemLog("Failed! " + (problem != null ? problem : "No map is loaded."), "worldSetBoundaryTexture");
			return;
		}
		systemLog("Setting the boundary texture to " + texture + ".", "worldSetBoundaryTexture");
		Screen.boundary_texture = texture;
		Screen.boundary_brightness = lightLevel(brightness);
		Screen.boundary_tiled = tiled;
		Screen.boundary_skip_texture = skip_texture;
	}

	// ============================================================ world: cells
	//
	// One cell at a time, by cell coordinates: cell (x, z) covers x..x+1, z..z+1, with
	// 0 <= x < worldGetMapWidth() and 0 <= z < worldGetMapHeight().
	//
	//   surface: 0 = floor, 1 = ceiling, 2 = lower wall, 3 = upper wall
	//
	// THE WORLD'S SHAPE NEVER CHANGES: there are deliberately no height setters.
	// A height change could leave the player inside a floor or sprites floating, and the
	// engine doesn't try to handle that. What scripts CAN change is how things look.
	// To retexture an area, loop over its cells.
	//
	// Bad input (outside the map, a surface number that doesn't exist, brightness outside
	// 0..1 ...) does nothing and logs "Failed!" to the debug console.
	//
	// THREADING: scripts run in Screen.update after the render threads have finished
	// (latch.await), and not at all while a map loads, so these never race the renderer.

	/** The floor height of cell (x, z), or -1 if there is no such cell. */
	public double worldGetCellFloorHeight(int x, int z) {
		Cell cell = cellOrLog(x, z, "worldGetCellFloorHeight");
		return cell == null ? -1 : cell.floor_height;
	}

	/** The ceiling height of cell (x, z), or -1 if there is no such cell. */
	public double worldGetCellCeilHeight(int x, int z) {
		Cell cell = cellOrLog(x, z, "worldGetCellCeilHeight");
		return cell == null ? -1 : cell.ceil_height;
	}

	/** The texture of one surface of cell (x, z), or nil if there is no such cell or surface. */
	public String worldGetCellTexture(int x, int z, int surface) {
		Cell cell = cellOrLog(x, z, "worldGetCellTexture");
		if (cell == null) return null;
		switch (surface) {
			case 0: return cell.floor_texture;
			case 1: return cell.ceil_texture;
			case 2: return cell.lower_texture;
			case 3: return cell.upper_texture;
			default:
				systemLog("Failed! Surface must be 0 (floor), 1 (ceiling), 2 (lower wall) or 3 (upper wall).", "worldGetCellTexture");
				return null;
		}
	}

	/**
	 * Changes one surface of cell (x, z). Shows from the next frame on.
	 *   surface    - 0 floor, 1 ceiling, 2 lower wall, 3 upper wall
	 *   brightness - 0.0 to 1.0, 0.5 = true colours
	 *   tiled      - 0 or more, as in the map editor
	 *   skip_texture - true draws nothing there (a skipped ceiling shows the sky)
	 */
	public void worldSetCellSurface(int x, int z, int surface, String texture, double brightness,
			double tiled, boolean skip_texture) {
		Cell cell = cellOrLog(x, z, "worldSetCellSurface");
		if (cell == null) return;
		String problem = surfaceProblem(texture, brightness, tiled);
		if (problem != null) {
			systemLog("Failed! " + problem, "worldSetCellSurface");
			return;
		}
		int light = lightLevel(brightness);
		switch (surface) {
			case 0 -> {
				cell.floor_texture = texture;
				cell.floor_brightness = light;
				cell.floor_tiled = tiled;
				cell.floor_skip_texture = skip_texture;
			}
			case 1 -> {
				cell.ceil_texture = texture;
				cell.ceil_brightness = light;
				cell.ceil_tiled = tiled;
				cell.ceil_skip_texture = skip_texture;
			}
			case 2 -> {
				cell.lower_texture = texture;
				cell.lower_brightness = light;
				cell.lower_tiled = tiled;
				cell.lower_skip_texture = skip_texture;
			}
			case 3 -> {
				cell.upper_texture = texture;
				cell.upper_brightness = light;
				cell.upper_tiled = tiled;
				cell.upper_skip_texture = skip_texture;
			}
			default -> {
				systemLog("Failed! Surface must be 0 (floor), 1 (ceiling), 2 (lower wall) or 3 (upper wall).", "worldSetCellSurface");
				return;
			}
		}
		// The cell may now look like its neighbours, or stop looking like them.
		Screen.refreshCellLines(x, z);
	}

	// ============================================================ world: edges
	//
	// Edge settings sit on the grid lines BETWEEN cells:
	//
	//   orientation 0 = the line of constant X at x, running from z to z+1
	//   orientation 1 = the line of constant Z at z, running from x to x+1
	//   band: 0 = bottom override, 1 = middle, 2 = top override
	//
	// Only interior lines can have edge settings; the map's outer lines always use the
	// boundary texture (worldSetBoundaryTexture), so asking for one fails with a log.
	// A line that ends up with nothing set (not solid, no band enabled) goes back to
	// having no edge settings at all, exactly like painting "nothing" in the editor.

	/**
	 * Turns one band of a line on or off, and sets its texture.
	 *   bottom override - replaces the lower wall seen on this line (a door on one side)
	 *   middle          - drawn in the opening: fences, glass, grates
	 *   top override    - replaces the upper wall seen on this line
	 */
	public void worldSetEdgeBand(int x, int z, int orientation, int band, boolean enabled, String texture,
			double brightness, double tiled, boolean skip_texture) {
		int line = interiorLineOrLog(x, z, orientation, "worldSetEdgeBand");
		if (line < 0) return;
		if (band < 0 || band > 2) {
			systemLog("Failed! Band must be 0 (bottom override), 1 (middle) or 2 (top override).", "worldSetEdgeBand");
			return;
		}
		String problem = surfaceProblem(texture, brightness, tiled);
		if (problem != null) {
			systemLog("Failed! " + problem, "worldSetEdgeBand");
			return;
		}
		Edge edge = edgeForChange(line);
		int light = lightLevel(brightness);
		if (band == 0) {
			edge.bottom_enabled = enabled;
			edge.bottom_texture = texture;
			edge.bottom_brightness = light;
			edge.bottom_tiled = tiled;
			edge.bottom_skip_texture = skip_texture;
		} else if (band == 1) {
			edge.middle_enabled = enabled;
			edge.middle_texture = texture;
			edge.middle_brightness = light;
			edge.middle_tiled = tiled;
			edge.middle_skip_texture = skip_texture;
		} else {
			edge.top_enabled = enabled;
			edge.top_texture = texture;
			edge.top_brightness = light;
			edge.top_tiled = tiled;
			edge.top_skip_texture = skip_texture;
		}
		storeEdge(line, edge, x, z, orientation);
	}

	/** Makes a line block the player (a fence, an invisible wall) or stop blocking. */
	public void worldSetEdgeSolid(int x, int z, int orientation, boolean is_solid) {
		int line = interiorLineOrLog(x, z, orientation, "worldSetEdgeSolid");
		if (line < 0) return;
		Edge edge = edgeForChange(line);
		edge.solid = is_solid;
		storeEdge(line, edge, x, z, orientation);
	}

	/** True if a line is set to block the player. False (with a log) for outer or missing lines. */
	public boolean worldGetEdgeSolid(int x, int z, int orientation) {
		int line = interiorLineOrLog(x, z, orientation, "worldGetEdgeSolid");
		if (line < 0) return false;
		Edge edge = Screen.verticals[line];
		return edge != null && edge.solid;
	}

	// ----------------------------------------------------- helpers for the above

	private Cell cellOrLog(int x, int z, String call) {
		if (Screen.cells == null || x < 0 || z < 0 || x >= Screen.map_width || z >= Screen.map_height) {
			systemLog("Failed! There is no cell (" + x + ", " + z + ") in the loaded map.", call);
			return null;
		}
		return Screen.cells[z * Screen.map_width + x];
	}

	/** The slot of an interior line, or -1 (after logging why) for outer or missing lines. */
	private int interiorLineOrLog(int x, int z, int orientation, String call) {
		int line = Screen.lineIndex(x, z, orientation);
		if (line < 0) {
			systemLog("Failed! There is no line x " + x + ", z " + z + ", orientation " + orientation
					+ " in the loaded map (orientation is 0 or 1).", call);
			return -1;
		}
		if (Screen.isOuterLine(Screen.map_width, Screen.map_height, x, z, orientation)) {
			systemLog("Failed! That is an outer line of the map. Use worldSetBoundaryTexture for those.", call);
			return -1;
		}
		return line;
	}

	/** The line's edge settings, or fresh empty ones if it has none yet. */
	private Edge edgeForChange(int line) {
		Edge edge = Screen.verticals[line];
		return edge != null ? edge : new Edge();
	}

	/** Puts the edge back on its line - or nothing, if nothing is set any more. */
	private void storeEdge(int line, Edge edge, int x, int z, int orientation) {
		Screen.verticals[line] = edge.isEmpty() ? null : edge;
		Screen.refreshLine(x, z, orientation);
	}

	/** What is wrong with these surface settings, or null. The same rules as map files. */
	private String surfaceProblem(String texture, double brightness, double tiled) {
		if (texture == null) return "A texture name is needed.";
		if (!(brightness >= 0 && brightness <= 1)) return "Brightness must be 0 to 1.";
		if (!(tiled >= 0) || Double.isInfinite(tiled)) return "Tiled must be 0 or more.";
		return null;
	}

	private int lightLevel(double brightness) {
		return (int) (brightness * (Table.NUM_LIGHT_LEVELS - 1));
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
	 *     n|player_hp|100.0         n = number
	 *     s|current_map|level2.rmap s = string
	 *     b|has_red_key|true        b = boolean
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
	 *         RA:storeSet("map",  "level2.rmap")
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
