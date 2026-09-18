package raccoon;

import java.util.HashMap;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Runs Lua scripts, keeping the expensive parts around between frames.
 *
 * WHAT USED TO HAPPEN, EVERY FRAME, FOR EVERY SCRIPT AND EVERY SPRITE
 *   1. build a whole new Lua environment (re-registering the entire stdlib)
 *   2. re-lex and re-compile the script's source text from scratch
 *   3. run it
 * Steps 1 and 2 produce exactly the same result every time after the first
 * frame, so they were pure waste - and they allocated a pile of short-lived
 * objects every frame, which is what causes GC hitches.
 *
 * WHAT HAPPENS NOW
 *   1. environment built ONCE per script, then reused
 *   2. source compiled ONCE per script, then reused
 *   3. run
 *
 * TWO WAYS TO WRITE A SCRIPT
 * --------------------------
 * Both work; pick per script.
 *
 * (a) PLAIN - exactly like before. The whole file runs every frame. Existing
 *     scripts including init.lua keep working with no changes at all.
 *
 * (b) update() - the file body runs ONCE as setup, and after that only the
 *     function called "update" runs each frame:
 *
 *         local script_index = ...
 *         local timer = 0                 -- survives between frames now
 *         RA:systemLog("set up once", "my_script")
 *
 *         function update()
 *             timer = timer + 1
 *         end
 *
 *     This is the cheaper and more usual way to write engine scripts: heavy
 *     one-time work stays out of the per-frame path.
 *
 * BECAUSE THE ENVIRONMENT PERSISTS, ordinary Lua variables now survive between
 * frames. RA:storeSet / RA:storeGet still exist and are still the right choice
 * for state that has to be shared BETWEEN scripts or saved to disk.
 *
 * SCOPE: one environment per script NAME. Two sprites running the same
 * behaviour script therefore share globals - key anything per-entity by the
 * sprite id (RA:storeSet("hp_"..id, ...)) or keep it in the sprite itself.
 *
 * THREADING: only ever called from the game loop thread, so plain HashMaps
 * are fine here.
 */
public class ScriptRunner {

	private static final HashMap<String, Globals> envs = new HashMap<>();
	private static final HashMap<String, LuaValue> chunks = new HashMap<>();

	/** Set once the file body has been run for a script. */
	private static final HashMap<String, Boolean> initialised = new HashMap<>();

	private static final String UPDATE_FUNCTION = "update";

	/**
	 * Runs one script for this frame.
	 *
	 * First call: builds the environment, compiles the source, runs the file
	 * body, then calls update() if the script defined one.
	 * Later calls: calls update() if there is one, otherwise re-runs the body.
	 */
	public static void run(String script_name, Varargs args) {
		String source = ResourceManager.getText(script_name);
		if (source == null) {
			System.err.println("[ScriptRunner] No such script: " + script_name);
			return;
		}

		try {
			Globals globals = envs.get(script_name);
			if (globals == null) {
				globals = JsePlatform.standardGlobals();
				globals.set("RA", CoerceJavaToLua.coerce(RaccoonAPI.instance()));
				envs.put(script_name, globals);
			}

			LuaValue chunk = chunks.get(script_name);
			if (chunk == null) {
				// Compiled against this script's own globals, once.
				chunk = globals.load(source, script_name);
				chunks.put(script_name, chunk);
			}

			if (initialised.get(script_name) == null) {
				initialised.put(script_name, Boolean.TRUE);
				chunk.invoke(args);
				// A script can remove itself during setup (init.lua does exactly
				// that). Don't call update() on it if so.
				if (envs.containsKey(script_name)) {
					LuaValue update = globals.get(UPDATE_FUNCTION);
					if (!update.isnil()) {
						update.invoke(args);
					}
				}
				return;
			}

			LuaValue update = globals.get(UPDATE_FUNCTION);
			if (!update.isnil()) {
				update.invoke(args);
			} else {
				chunk.invoke(args);
			}
		} catch (Exception e) {
			// One bad script should not take the whole frame down.
			System.err.println("[ScriptRunner] Error in '" + script_name + "': " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Drops a script's cached environment and compiled chunk. The next run
	 * re-reads the source, so this doubles as hot-reload while developing.
	 */
	public static void forget(String script_name) {
		envs.remove(script_name);
		chunks.remove(script_name);
		initialised.remove(script_name);
	}

	public static void forgetAll() {
		envs.clear();
		chunks.clear();
		initialised.clear();
	}

	public static int cachedCount() {
		return chunks.size();
	}
}
