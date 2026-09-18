package raccoon;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/**
 * Raccoon Pack - a single-file bundle of everything that would otherwise sit
 * loose in the data/ folder.
 *
 * FILE LAYOUT
 * -----------
 *   "RPK1"                       4 byte magic
 *   int      entry_count
 *   repeated entry_count times:  (this block is the INDEX)
 *       UTF  folder              e.g. "tex"
 *       UTF  name                e.g. "wood.png"
 *       long offset              where the bytes start, RELATIVE to the blob region
 *       int  length              how many bytes
 *   ... blob region: every file's raw bytes, back to back ...
 *
 * Offsets are relative to the start of the blob region rather than absolute so
 * that the index can be written first without a second pass over the file.
 *
 * WHY THIS SHAPE
 * --------------
 * Because each entry records an offset and a length, opening an .rpk means
 * reading only the small index - NOT the asset bytes. Individual assets are
 * then pulled out one at a time with a seek + read whenever something actually
 * asks for them. That is exactly what ResourceManager's lazy loading needs, so
 * the pack format and the "stop loading everything into RAM" fix are really the
 * same feature seen from two sides.
 *
 * The loose data/ folder works the same way - see ResourceManager.
 */
public class Rpk {

	public static final String MAGIC = "RPK1";

	/** One file inside the pack. */
	public static class Entry {
		public String folder;
		public String name;
		public long offset;
		public int length;
	}

	private final File file;
	private final ArrayList<Entry> entries = new ArrayList<>();
	private long blob_region_start;

	private Rpk(File file) {
		this.file = file;
	}

	// ---------------------------------------------------------------- reading

	/** Reads ONLY the index. Asset bytes stay on disk until asked for. */
	public static Rpk open(File file) throws IOException {
		Rpk rpk = new Rpk(file);
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			byte[] magic = new byte[4];
			in.readFully(magic);
			if (!new String(magic, "US-ASCII").equals(MAGIC)) {
				throw new IOException("not a Raccoon Pack (bad magic)");
			}
			int count = in.readInt();
			// 4 magic + 4 count, then the index itself; track how long the index is.
			long header_size = 8;
			for (int i = 0; i < count; i++) {
				Entry e = new Entry();
				e.folder = in.readUTF();
				e.name = in.readUTF();
				e.offset = in.readLong();
				e.length = in.readInt();
				rpk.entries.add(e);
				// readUTF writes a 2-byte length prefix ahead of the modified-UTF8 bytes.
				header_size += 2 + utfLength(e.folder) + 2 + utfLength(e.name) + 8 + 4;
			}
			rpk.blob_region_start = header_size;
		}
		return rpk;
	}

	public ArrayList<Entry> entries() {
		return entries;
	}

	/** Pulls one asset's bytes out of the pack. */
	public byte[] read(Entry e) throws IOException {
		byte[] data = new byte[e.length];
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			raf.seek(blob_region_start + e.offset);
			raf.readFully(data);
		}
		return data;
	}

	// ---------------------------------------------------------------- writing

	/**
	 * Walks a data/ folder and writes it out as one .rpk.
	 * Only the folders the engine knows about are included.
	 */
	public static void pack(File data_folder, File out_file, String[] folders) throws IOException {
		ArrayList<Entry> index = new ArrayList<>();
		ArrayList<File> files = new ArrayList<>();

		long running_offset = 0;
		for (String folder : folders) {
			File dir = new File(data_folder, folder);
			if (!dir.isDirectory()) continue;
			File[] listed = dir.listFiles(File::isFile);
			if (listed == null) continue;
			for (File f : listed) {
				Entry e = new Entry();
				e.folder = folder;
				e.name = f.getName();
				e.offset = running_offset;
				e.length = (int) f.length();
				index.add(e);
				files.add(f);
				running_offset += e.length;
			}
		}

		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(out_file)))) {
			out.write(MAGIC.getBytes("US-ASCII"));
			out.writeInt(index.size());
			for (Entry e : index) {
				out.writeUTF(e.folder);
				out.writeUTF(e.name);
				out.writeLong(e.offset);
				out.writeInt(e.length);
			}
			byte[] buffer = new byte[8192];
			for (File f : files) {
				try (InputStream in = new FileInputStream(f)) {
					int n;
					while ((n = in.read(buffer)) > 0) {
						out.write(buffer, 0, n);
					}
				}
			}
		}
	}

	/** Byte length of a string once DataOutput.writeUTF has encoded it. */
	private static int utfLength(String s) {
		int len = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 0x0001 && c <= 0x007F) len += 1;
			else if (c > 0x07FF) len += 3;
			else len += 2;
		}
		return len;
	}
}
