package raccoon;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Reads .rmap map files (format version 1) - the files the map editor saves into
 * data/maps/.
 *
 * FILE LAYOUT
 * -----------
 * The editor's reader/writer (RaccoonMapEditor/rmap_format.js) writes exactly this and
 * makes exactly the same checks, so if you change one, change the other.
 *
 *   "RMAP"                       4 byte magic
 *   u16      version             = 1
 *   u16      map_width  W        1..2000
 *   u16      map_height H        1..2000
 *   SURFACE  boundary            the face on every outer line of the map
 *   u32      entry_count         >= 1, then that many cell settings:
 *       f64      floor_height     0 <= floor <= ceiling <= 2000
 *       f64      ceil_height
 *       SURFACE  floor, ceiling, lower wall, upper wall
 *   W*H x u32 table index        one per cell, cell index = z * W + x
 *   u32      edge_count          then that many edges, interior lines only:
 *       u16 x, u16 z, u8 orientation (0 = constant-X line, 1 = constant-Z line)
 *       u8  solid
 *       BAND bottom override, BAND middle, BAND top override
 *   u16      sprite_count        then that many sprites:
 *       STR id (unique), STR image, f64 x, f64 z, u8 stand_on_floor, f64 y,
 *       f64 size, f64 brightness, STR behavior_script ("" = none),
 *       f64 collision_radius, f64 direction
 *   "END!"                       and nothing after it
 *
 *   SURFACE = STR texture, f64 brightness (0..1), f64 tiled (>= 0), u8 skip_texture
 *   BAND    = u8 enabled, SURFACE
 *   STR     = u16 byte length + that many bytes of UTF-8 (NOT readUTF's modified UTF-8)
 *
 * Everything is big-endian, which is what DataInputStream reads anyway. Every number
 * must be a real number, every flag 0 or 1, and an edge that has nothing set (not
 * solid, no band enabled) is never stored. The table means a map made of a few kinds of
 * cell stays small: one index per cell.
 *
 * WHY read() BUILDS A SEPARATE World
 * ----------------------------------
 * read() fills in a brand new World and never touches Screen. That is what makes
 * RA:worldLoadMapAsync safe: the file is read on the loading thread while the game thread
 * carries on with the old world, and the finished World is swapped in later, on the game
 * thread, by Screen.installWorld. If anything in the file is wrong, read() throws an
 * IOException saying what, and the old world simply stays.
 */
public class Rmap {

	public static final int VERSION = 1;

	/** One map file, fully read and checked, ready for Screen.installWorld. */
	public static class World {
		public int map_width;
		public int map_height;
		public Cell[] cells;
		public Edge[] verticals;
		public boolean[] line_active;
		public String boundary_texture;
		public int boundary_brightness;
		public double boundary_tiled;
		public boolean boundary_skip_texture;
		public ArrayList<Sprite> sprites = new ArrayList<>();
	}

	public static World read(byte[] data) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		try {
			return readWorld(in, data.length);
		} catch (EOFException e) {
			throw new IOException("The file ends too early. It is cut off, or it is not an .rmap file.");
		}
	}

	private static World readWorld(DataInputStream in, int file_length) throws IOException {
		// ---------------------------------------------------------------- header
		byte[] magic = new byte[4];
		in.readFully(magic);
		if (!new String(magic, StandardCharsets.US_ASCII).equals("RMAP")) {
			throw new IOException("This is not an .rmap file (it does not start with \"RMAP\").");
		}
		int version = in.readUnsignedShort();
		if (version > VERSION) {
			throw new IOException("This map was saved by a newer editor (format version " + version
					+ "). This engine reads version " + VERSION + ".");
		}
		if (version != VERSION) {
			throw new IOException("Unknown .rmap format version " + version + ".");
		}

		World w = new World();
		w.map_width = in.readUnsignedShort();
		w.map_height = in.readUnsignedShort();
		if (w.map_width < 1 || w.map_width > Screen.LIMIT_MAP_COORD
				|| w.map_height < 1 || w.map_height > Screen.LIMIT_MAP_COORD) {
			throw new IOException("The map size " + w.map_width + " x " + w.map_height
					+ " is not allowed. Width and height must be 1 to " + Screen.LIMIT_MAP_COORD + ".");
		}
		int width = w.map_width;
		int height = w.map_height;

		// ------------------------------------------------------ boundary surface
		String where = "The boundary surface";
		w.boundary_texture = readString(in);
		w.boundary_brightness = readBrightness(in, where);
		w.boundary_tiled = readTiled(in, where);
		w.boundary_skip_texture = readFlag(in);

		// --------------------------------------------------- cell settings table
		long entry_count = in.readInt() & 0xFFFFFFFFL;
		if (entry_count < 1) {
			throw new IOException("The cell settings table is empty.");
		}
		// Every entry takes well over one byte, so a count bigger than the file is damage,
		// and must not make us allocate a giant array.
		if (entry_count > file_length) {
			throw new IOException("The file is damaged: the cell settings table claims " + entry_count + " entries.");
		}
		Cell[] table = new Cell[(int) entry_count];
		for (int i = 0; i < table.length; i++) {
			table[i] = readCell(in, "Cell settings entry " + i);
		}

		// ----------------------------------------------------------------- cells
		// Every cell gets its OWN copy of its table entry, so a script changing one
		// cell can never change another.
		w.cells = new Cell[width * height];
		for (int i = 0; i < w.cells.length; i++) {
			long index = in.readInt() & 0xFFFFFFFFL;
			if (index >= table.length) {
				throw new IOException("Cell (" + (i % width) + ", " + (i / width) + ") uses settings entry " + index
						+ ", but the table only has " + table.length + " entries.");
			}
			w.cells[i] = copyCell(table[(int) index]);
		}

		// ----------------------------------------------------------------- edges
		w.verticals = new Edge[(width + 1) * (height + 1) * 2];
		long edge_count = in.readInt() & 0xFFFFFFFFL;
		if (edge_count > file_length) {
			throw new IOException("The file is damaged: it claims " + edge_count + " edges.");
		}
		for (long n = 0; n < edge_count; n++) {
			int x = in.readUnsignedShort();
			int z = in.readUnsignedShort();
			int orientation = in.readUnsignedByte();
			where = "The edge at x " + x + ", z " + z + ", orientation " + orientation;
			if (orientation > 1) {
				throw new IOException(where + ": orientation must be 0 (constant-X line) or 1 (constant-Z line).");
			}
			if (!isInteriorLine(width, height, x, z, orientation)) {
				throw new IOException(where + " is not on an interior line. Edge settings cannot go on the map's outer lines.");
			}
			Edge e = new Edge();
			e.solid = readFlag(in);
			e.bottom_enabled = readFlag(in);
			e.bottom_texture = readString(in);
			e.bottom_brightness = readBrightness(in, where + ", bottom band");
			e.bottom_tiled = readTiled(in, where + ", bottom band");
			e.bottom_skip_texture = readFlag(in);
			e.middle_enabled = readFlag(in);
			e.middle_texture = readString(in);
			e.middle_brightness = readBrightness(in, where + ", middle band");
			e.middle_tiled = readTiled(in, where + ", middle band");
			e.middle_skip_texture = readFlag(in);
			e.top_enabled = readFlag(in);
			e.top_texture = readString(in);
			e.top_brightness = readBrightness(in, where + ", top band");
			e.top_tiled = readTiled(in, where + ", top band");
			e.top_skip_texture = readFlag(in);
			if (e.isEmpty()) {
				throw new IOException(where + " has nothing set (not solid, no band enabled), so it should not be stored.");
			}
			int slot = Screen.lineSlot(width, x, z, orientation);
			if (w.verticals[slot] != null) {
				throw new IOException(where + " is listed twice.");
			}
			w.verticals[slot] = e;
		}

		// --------------------------------------------------------------- sprites
		int sprite_count = in.readUnsignedShort();
		HashSet<String> ids = new HashSet<>();
		for (int n = 0; n < sprite_count; n++) {
			String id = readString(in);
			where = "Sprite \"" + id + "\"";
			String image = readString(in);
			double x = readNumber(in, where + " x");
			double z = readNumber(in, where + " z");
			boolean stand_on_floor = readFlag(in);
			double y = readNumber(in, where + " y");
			double size = readNumber(in, where + " size");
			double brightness = readNumber(in, where + " brightness");
			String behavior_script = readString(in);
			double collision_radius = readNumber(in, where + " collision radius");
			double direction = readNumber(in, where + " direction");
			if (!ids.add(id)) {
				throw new IOException("Two sprites share the id \"" + id + "\". Sprite ids must be unique.");
			}
			if (x < 0 || x >= width || z < 0 || z >= height) {
				throw new IOException(where + " at (" + x + ", " + z + ") is outside the map.");
			}
			if (brightness < 0 || brightness > 1) {
				throw new IOException(where + " has brightness " + brightness + ". Brightness must be 0 to 1.");
			}
			if (stand_on_floor) {
				// drawSprites puts a sprite's bottom edge size / (2 * retina_dist) below its
				// centre, using the same projection walls use. Lifting the centre by that much
				// sets the sprite's bottom on its cell's floor. Heights never change at runtime,
				// so working this out once here is enough.
				Cell under = w.cells[(int) z * width + (int) x];
				y = under.floor_height + size / (2 * Camera.retina_dist);
			}
			int light = (int) (brightness * (Table.NUM_LIGHT_LEVELS - 1));
			// "" in the file means "no behaviour script"; the engine uses null for that.
			String script = behavior_script.isEmpty() ? null : behavior_script;
			w.sprites.add(new Sprite(id, x, y, z, size, image, script, light, collision_radius, direction));
		}

		// ---------------------------------------------------------------- footer
		byte[] footer = new byte[4];
		in.readFully(footer);
		if (!new String(footer, StandardCharsets.US_ASCII).equals("END!")) {
			throw new IOException("The file is damaged: the \"END!\" footer is missing.");
		}
		if (in.available() > 0) {
			throw new IOException("The file is damaged: there are extra bytes after the \"END!\" footer.");
		}

		// Which lines the renderer has to stop at. Worked out here, on whatever thread is
		// loading, so the swap on the game thread stays instant.
		w.line_active = new boolean[w.verticals.length];
		for (int z = 0; z <= height; z++) {
			for (int x = 0; x <= width; x++) {
				if (z < height) {
					w.line_active[Screen.lineSlot(width, x, z, 0)] = Screen.isLineActive(w.cells, w.verticals, width, height, x, z, 0);
				}
				if (x < width) {
					w.line_active[Screen.lineSlot(width, x, z, 1)] = Screen.isLineActive(w.cells, w.verticals, width, height, x, z, 1);
				}
			}
		}
		return w;
	}

	// ================================================================ pieces

	private static Cell readCell(DataInputStream in, String where) throws IOException {
		Cell c = new Cell();
		c.floor_height = readNumber(in, where + " floor height");
		c.ceil_height = readNumber(in, where + " ceiling height");
		if (c.floor_height < 0 || c.ceil_height > Screen.LIMIT_MAP_COORD || c.floor_height > c.ceil_height) {
			throw new IOException(where + " has floor " + c.floor_height + " and ceiling " + c.ceil_height
					+ ". Heights must be 0 <= floor <= ceiling <= " + Screen.LIMIT_MAP_COORD + ".");
		}
		c.floor_texture = readString(in);
		c.floor_brightness = readBrightness(in, where + " floor surface");
		c.floor_tiled = readTiled(in, where + " floor surface");
		c.floor_skip_texture = readFlag(in);
		c.ceil_texture = readString(in);
		c.ceil_brightness = readBrightness(in, where + " ceiling surface");
		c.ceil_tiled = readTiled(in, where + " ceiling surface");
		c.ceil_skip_texture = readFlag(in);
		c.lower_texture = readString(in);
		c.lower_brightness = readBrightness(in, where + " lower wall surface");
		c.lower_tiled = readTiled(in, where + " lower wall surface");
		c.lower_skip_texture = readFlag(in);
		c.upper_texture = readString(in);
		c.upper_brightness = readBrightness(in, where + " upper wall surface");
		c.upper_tiled = readTiled(in, where + " upper wall surface");
		c.upper_skip_texture = readFlag(in);
		return c;
	}

	private static Cell copyCell(Cell from) {
		Cell c = new Cell();
		c.floor_height = from.floor_height;
		c.ceil_height = from.ceil_height;
		c.floor_texture = from.floor_texture;
		c.floor_brightness = from.floor_brightness;
		c.floor_tiled = from.floor_tiled;
		c.floor_skip_texture = from.floor_skip_texture;
		c.ceil_texture = from.ceil_texture;
		c.ceil_brightness = from.ceil_brightness;
		c.ceil_tiled = from.ceil_tiled;
		c.ceil_skip_texture = from.ceil_skip_texture;
		c.lower_texture = from.lower_texture;
		c.lower_brightness = from.lower_brightness;
		c.lower_tiled = from.lower_tiled;
		c.lower_skip_texture = from.lower_skip_texture;
		c.upper_texture = from.upper_texture;
		c.upper_brightness = from.upper_brightness;
		c.upper_tiled = from.upper_tiled;
		c.upper_skip_texture = from.upper_skip_texture;
		return c;
	}

	/**
	 * Edge settings may only sit on interior lines:
	 *   orientation 0 (constant X): x 1..W-1, z 0..H-1
	 *   orientation 1 (constant Z): z 1..H-1, x 0..W-1
	 */
	private static boolean isInteriorLine(int width, int height, int x, int z, int orientation) {
		if (orientation == 0) {
			return x >= 1 && x <= width - 1 && z >= 0 && z <= height - 1;
		}
		return z >= 1 && z <= height - 1 && x >= 0 && x <= width - 1;
	}

	private static double readNumber(DataInputStream in, String what) throws IOException {
		double v = in.readDouble();
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			throw new IOException(what + " is " + v + ", which is not a usable number.");
		}
		return v;
	}

	/** Brightness is 0.0 - 1.0 in the file and a light level int in the engine. */
	private static int readBrightness(DataInputStream in, String where) throws IOException {
		double b = readNumber(in, where + " brightness");
		if (b < 0 || b > 1) {
			throw new IOException(where + " has brightness " + b + ". Brightness must be 0 to 1.");
		}
		return (int) (b * (Table.NUM_LIGHT_LEVELS - 1));
	}

	private static double readTiled(DataInputStream in, String where) throws IOException {
		double t = readNumber(in, where + " tiled");
		if (t < 0) {
			throw new IOException(where + " has tiled " + t + ". Tiled must be 0 or more.");
		}
		return t;
	}

	private static boolean readFlag(DataInputStream in) throws IOException {
		int v = in.readUnsignedByte();
		if (v > 1) {
			throw new IOException("The file is damaged: a yes/no value is " + v + " (it must be 0 or 1).");
		}
		return v == 1;
	}

	/**
	 * A u16 byte length, then that many bytes of UTF-8. Deliberately NOT readUTF: that
	 * reads Java's "modified UTF-8", which is not what the editor writes.
	 */
	private static String readString(DataInputStream in) throws IOException {
		int length = in.readUnsignedShort();
		byte[] bytes = new byte[length];
		in.readFully(bytes);
		try {
			return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			throw new IOException("The file is damaged: a text field is not valid UTF-8.");
		}
	}
}
