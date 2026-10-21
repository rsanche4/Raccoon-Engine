// rmap_format.js - reads and writes Raccoon Engine .rmap map files (format version 1).
//
// The byte layout is written out at the top of the engine's reader,
// RaccoonEngineV2/src/raccoon/Rmap.java. That reader makes exactly the same checks as
// rmapValidate below, so if you change one, change the other.
//
// Loaded by the editor as a plain <script>, and by Node with require() for tools.
//
// What a map looks like in JavaScript:
//
//   surface = { texture, brightness, tiled, skipTexture }
//   cell    = { floorHeight, ceilingHeight, floor, ceiling, lowerWall, upperWall }   (four surfaces)
//   band    = { enabled, surface }
//   edge    = { x, z, orientation, solid, bottom, middle, top }                     (three bands)
//   sprite  = { id, image, x, z, standOnFloor, y, size, brightness,
//               behaviorScript, collisionRadius, direction }
//   map     = { width, height, boundary, cells, edges, sprites }
//
// map.cells has width * height entries, cell (x, z) at index z * width + x.
// Several cells may share one cell object (the editor never edits a cell object in place),
// and rmapDecode hands back shared objects: every cell that used the same table entry in
// the file gets the same object.

const RMAP_VERSION = 1;
const RMAP_MAX_SIZE = 2000;      // largest map width or height, in cells
const RMAP_MAX_HEIGHT = 2000;    // largest floor or ceiling height
const RMAP_MAX_SPRITES = 65535;  // sprite_count is a u16
const RMAP_MAX_STRING = 65535;   // strings store their byte length in a u16

const rmapTextEncoder = new TextEncoder();
const rmapTextDecoder = new TextDecoder('utf-8', { fatal: true });

// =============================================================== writing

function rmapEncode(map) {
    rmapValidate(map);

    const w = rmapMakeWriter();
    w.ascii('RMAP');
    w.u16(RMAP_VERSION);
    w.u16(map.width);
    w.u16(map.height);

    rmapWriteSurface(w, map.boundary);

    // Cell settings table: each distinct cell once, then one table index per cell.
    // Looking cells up by object first is fast, because the editor shares objects;
    // the key lookup still catches equal cells that happen to be separate objects.
    const table = [];
    const indexByObject = new Map();
    const indexByKey = new Map();
    const indexes = new Uint32Array(map.cells.length);
    for (let i = 0; i < map.cells.length; i++) {
        const cell = map.cells[i];
        let index = indexByObject.get(cell);
        if (index === undefined) {
            const key = rmapCellKey(cell);
            index = indexByKey.get(key);
            if (index === undefined) {
                index = table.length;
                table.push(cell);
                indexByKey.set(key, index);
            }
            indexByObject.set(cell, index);
        }
        indexes[i] = index;
    }
    w.u32(table.length);
    for (const cell of table) {
        w.f64(cell.floorHeight);
        w.f64(cell.ceilingHeight);
        rmapWriteSurface(w, cell.floor);
        rmapWriteSurface(w, cell.ceiling);
        rmapWriteSurface(w, cell.lowerWall);
        rmapWriteSurface(w, cell.upperWall);
    }
    for (let i = 0; i < indexes.length; i++) {
        w.u32(indexes[i]);
    }

    w.u32(map.edges.length);
    for (const edge of map.edges) {
        w.u16(edge.x);
        w.u16(edge.z);
        w.u8(edge.orientation);
        w.u8(edge.solid ? 1 : 0);
        rmapWriteBand(w, edge.bottom);
        rmapWriteBand(w, edge.middle);
        rmapWriteBand(w, edge.top);
    }

    w.u16(map.sprites.length);
    for (const sprite of map.sprites) {
        w.str(sprite.id);
        w.str(sprite.image);
        w.f64(sprite.x);
        w.f64(sprite.z);
        w.u8(sprite.standOnFloor ? 1 : 0);
        w.f64(sprite.y);
        w.f64(sprite.size);
        w.f64(sprite.brightness);
        w.str(sprite.behaviorScript);
        w.f64(sprite.collisionRadius);
        w.f64(sprite.direction);
    }

    w.ascii('END!');
    return w.finish();
}

function rmapWriteSurface(w, surface) {
    w.str(surface.texture);
    w.f64(surface.brightness);
    w.f64(surface.tiled);
    w.u8(surface.skipTexture ? 1 : 0);
}

function rmapWriteBand(w, band) {
    w.u8(band.enabled ? 1 : 0);
    rmapWriteSurface(w, band.surface);
}

// Two cells with the same key have the same settings in every field.
function rmapCellKey(cell) {
    const parts = [cell.floorHeight, cell.ceilingHeight];
    for (const s of [cell.floor, cell.ceiling, cell.lowerWall, cell.upperWall]) {
        parts.push(s.texture, s.brightness, s.tiled, s.skipTexture);
    }
    return JSON.stringify(parts);
}

// A growing big-endian byte buffer.
function rmapMakeWriter() {
    let buffer = new ArrayBuffer(4096);
    let view = new DataView(buffer);
    let pos = 0;

    function ensure(byteCount) {
        if (pos + byteCount <= buffer.byteLength) return;
        let size = buffer.byteLength * 2;
        while (size < pos + byteCount) size *= 2;
        const bigger = new ArrayBuffer(size);
        new Uint8Array(bigger).set(new Uint8Array(buffer, 0, pos));
        buffer = bigger;
        view = new DataView(buffer);
    }

    return {
        u8(value) { ensure(1); view.setUint8(pos, value); pos += 1; },
        u16(value) { ensure(2); view.setUint16(pos, value, false); pos += 2; },
        u32(value) { ensure(4); view.setUint32(pos, value, false); pos += 4; },
        f64(value) { ensure(8); view.setFloat64(pos, value, false); pos += 8; },
        ascii(text) {
            for (let i = 0; i < text.length; i++) this.u8(text.charCodeAt(i));
        },
        str(text) {
            const bytes = rmapTextEncoder.encode(text);
            if (bytes.length > RMAP_MAX_STRING) {
                throw new Error('Text is too long to save (' + bytes.length + ' bytes, the limit is '
                    + RMAP_MAX_STRING + '): "' + text.slice(0, 40) + '..."');
            }
            this.u16(bytes.length);
            ensure(bytes.length);
            new Uint8Array(buffer, pos, bytes.length).set(bytes);
            pos += bytes.length;
        },
        finish() {
            return new Uint8Array(buffer.slice(0, pos));
        }
    };
}

// =============================================================== reading

// bytes: a Uint8Array (or an ArrayBuffer) holding a whole .rmap file.
// Returns a map, or throws an Error whose message says what is wrong with the file.
function rmapDecode(bytes) {
    if (bytes instanceof ArrayBuffer) bytes = new Uint8Array(bytes);
    const r = rmapMakeReader(bytes);

    if (r.ascii(4) !== 'RMAP') {
        throw new Error('This is not an .rmap file (it does not start with "RMAP").');
    }
    const version = r.u16();
    if (version > RMAP_VERSION) {
        throw new Error('This map was saved by a newer editor (format version ' + version
            + '). This editor can only open version ' + RMAP_VERSION + '.');
    }
    if (version !== RMAP_VERSION) {
        throw new Error('Unknown .rmap format version ' + version + '.');
    }

    const width = r.u16();
    const height = r.u16();
    // Checked now rather than in rmapValidate: the size decides how much is read next.
    rmapCheckSize(width, height);

    const boundary = rmapReadSurface(r);

    const entryCount = r.u32();
    if (entryCount < 1) {
        throw new Error('The cell settings table is empty.');
    }
    const table = [];
    for (let i = 0; i < entryCount; i++) {
        const entry = {
            floorHeight: r.f64(),
            ceilingHeight: r.f64(),
            floor: rmapReadSurface(r),
            ceiling: rmapReadSurface(r),
            lowerWall: rmapReadSurface(r),
            upperWall: rmapReadSurface(r)
        };
        // Every entry is checked, even one no cell uses, so the engine and the editor
        // refuse exactly the same files.
        rmapCheckCell(entry, 'Cell settings entry ' + i);
        table.push(entry);
    }

    const cells = new Array(width * height);
    for (let i = 0; i < cells.length; i++) {
        const index = r.u32();
        if (index >= entryCount) {
            throw new Error('Cell (' + (i % width) + ', ' + Math.floor(i / width) + ') uses settings entry '
                + index + ', but the table only has ' + entryCount + ' entries.');
        }
        cells[i] = table[index];
    }

    const edgeCount = r.u32();
    const edges = [];
    for (let i = 0; i < edgeCount; i++) {
        edges.push({
            x: r.u16(),
            z: r.u16(),
            orientation: r.u8(),
            solid: r.bool(),
            bottom: rmapReadBand(r),
            middle: rmapReadBand(r),
            top: rmapReadBand(r)
        });
    }

    const spriteCount = r.u16();
    const sprites = [];
    for (let i = 0; i < spriteCount; i++) {
        sprites.push({
            id: r.str(),
            image: r.str(),
            x: r.f64(),
            z: r.f64(),
            standOnFloor: r.bool(),
            y: r.f64(),
            size: r.f64(),
            brightness: r.f64(),
            behaviorScript: r.str(),
            collisionRadius: r.f64(),
            direction: r.f64()
        });
    }

    if (r.ascii(4) !== 'END!') {
        throw new Error('The file is damaged: the "END!" footer is missing.');
    }
    if (!r.atEnd()) {
        throw new Error('The file is damaged: there are extra bytes after the "END!" footer.');
    }

    const map = { width, height, boundary, cells, edges, sprites };
    rmapValidate(map);
    return map;
}

function rmapReadSurface(r) {
    return {
        texture: r.str(),
        brightness: r.f64(),
        tiled: r.f64(),
        skipTexture: r.bool()
    };
}

function rmapReadBand(r) {
    return {
        enabled: r.bool(),
        surface: rmapReadSurface(r)
    };
}

// Reads big-endian values from the front of a Uint8Array.
function rmapMakeReader(bytes) {
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let pos = 0;

    function need(byteCount) {
        if (pos + byteCount > bytes.length) {
            throw new Error('The file ends too early. It is cut off, or it is not an .rmap file.');
        }
    }

    return {
        u8() { need(1); const v = view.getUint8(pos); pos += 1; return v; },
        u16() { need(2); const v = view.getUint16(pos, false); pos += 2; return v; },
        u32() { need(4); const v = view.getUint32(pos, false); pos += 4; return v; },
        f64() { need(8); const v = view.getFloat64(pos, false); pos += 8; return v; },
        bool() {
            const v = this.u8();
            if (v > 1) {
                throw new Error('The file is damaged: a yes/no value is ' + v + ' (it must be 0 or 1).');
            }
            return v === 1;
        },
        ascii(length) {
            let text = '';
            for (let i = 0; i < length; i++) text += String.fromCharCode(this.u8());
            return text;
        },
        str() {
            const length = this.u16();
            need(length);
            let text;
            try {
                text = rmapTextDecoder.decode(bytes.subarray(pos, pos + length));
            } catch (e) {
                throw new Error('The file is damaged: a text field is not valid UTF-8.');
            }
            pos += length;
            return text;
        },
        atEnd() {
            return pos === bytes.length;
        }
    };
}

// ============================================================ validation

// Throws an Error describing the first problem found. The same rules run when saving
// (so the editor never writes a bad file) and when opening (so a bad file is refused).
function rmapValidate(map) {
    rmapCheckSize(map.width, map.height);
    rmapCheckSurface(map.boundary, 'The boundary surface');

    if (!Array.isArray(map.cells) || map.cells.length !== map.width * map.height) {
        throw new Error('The map should have ' + (map.width * map.height) + ' cells.');
    }
    // Shared cell objects only need checking once.
    const checked = new Set();
    for (let i = 0; i < map.cells.length; i++) {
        const cell = map.cells[i];
        if (checked.has(cell)) continue;
        checked.add(cell);
        rmapCheckCell(cell, 'Cell (' + (i % map.width) + ', ' + Math.floor(i / map.width) + ')');
    }

    const seenLines = new Set();
    for (const edge of map.edges) {
        const where = 'The edge at x ' + edge.x + ', z ' + edge.z + ', orientation ' + edge.orientation;
        rmapCheckEdgeLine(map, edge, where);
        const key = edge.x + ',' + edge.z + ',' + edge.orientation;
        if (seenLines.has(key)) {
            throw new Error(where + ' is listed twice.');
        }
        seenLines.add(key);
        if (typeof edge.solid !== 'boolean') {
            throw new Error(where + ': solid must be true or false.');
        }
        rmapCheckBand(edge.bottom, where + ', bottom band');
        rmapCheckBand(edge.middle, where + ', middle band');
        rmapCheckBand(edge.top, where + ', top band');
        if (!edge.solid && !edge.bottom.enabled && !edge.middle.enabled && !edge.top.enabled) {
            throw new Error(where + ' has nothing set (not solid, no band enabled), so it should not be stored.');
        }
    }

    if (map.sprites.length > RMAP_MAX_SPRITES) {
        throw new Error('A map can hold at most ' + RMAP_MAX_SPRITES + ' sprites.');
    }
    const seenIds = new Set();
    for (const sprite of map.sprites) {
        const where = 'Sprite "' + sprite.id + '"';
        if (typeof sprite.id !== 'string' || typeof sprite.image !== 'string'
                || typeof sprite.behaviorScript !== 'string') {
            throw new Error(where + ': id, image and behaviour script must be text.');
        }
        if (seenIds.has(sprite.id)) {
            throw new Error('Two sprites share the id "' + sprite.id + '". Sprite ids must be unique.');
        }
        seenIds.add(sprite.id);
        rmapCheckNumber(sprite.x, where + ' x');
        rmapCheckNumber(sprite.z, where + ' z');
        if (sprite.x < 0 || sprite.x >= map.width || sprite.z < 0 || sprite.z >= map.height) {
            throw new Error(where + ' at (' + sprite.x + ', ' + sprite.z + ') is outside the map.');
        }
        if (typeof sprite.standOnFloor !== 'boolean') {
            throw new Error(where + ': Stand on floor must be true or false.');
        }
        rmapCheckNumber(sprite.y, where + ' y');
        rmapCheckNumber(sprite.size, where + ' size');
        rmapCheckBrightness(sprite.brightness, where);
        rmapCheckNumber(sprite.collisionRadius, where + ' collision radius');
        rmapCheckNumber(sprite.direction, where + ' direction');
    }
}

function rmapCheckSize(width, height) {
    if (!Number.isInteger(width) || !Number.isInteger(height)
            || width < 1 || width > RMAP_MAX_SIZE || height < 1 || height > RMAP_MAX_SIZE) {
        throw new Error('The map size ' + width + ' x ' + height + ' is not allowed. Width and height must be 1 to '
            + RMAP_MAX_SIZE + '.');
    }
}

function rmapCheckCell(cell, where) {
    rmapCheckNumber(cell.floorHeight, where + ' floor height');
    rmapCheckNumber(cell.ceilingHeight, where + ' ceiling height');
    if (cell.floorHeight < 0 || cell.ceilingHeight > RMAP_MAX_HEIGHT || cell.floorHeight > cell.ceilingHeight) {
        throw new Error(where + ' has floor ' + cell.floorHeight + ' and ceiling ' + cell.ceilingHeight
            + '. Heights must be 0 <= floor <= ceiling <= ' + RMAP_MAX_HEIGHT + '.');
    }
    rmapCheckSurface(cell.floor, where + ' floor surface');
    rmapCheckSurface(cell.ceiling, where + ' ceiling surface');
    rmapCheckSurface(cell.lowerWall, where + ' lower wall surface');
    rmapCheckSurface(cell.upperWall, where + ' upper wall surface');
}

function rmapCheckSurface(surface, where) {
    if (typeof surface.texture !== 'string') {
        throw new Error(where + ': the texture name must be text.');
    }
    rmapCheckBrightness(surface.brightness, where);
    rmapCheckNumber(surface.tiled, where + ' tiled');
    if (surface.tiled < 0) {
        throw new Error(where + ' has tiled ' + surface.tiled + '. Tiled must be 0 or more.');
    }
    if (typeof surface.skipTexture !== 'boolean') {
        throw new Error(where + ': skip texture must be true or false.');
    }
}

function rmapCheckBand(band, where) {
    if (typeof band.enabled !== 'boolean') {
        throw new Error(where + ': enabled must be true or false.');
    }
    rmapCheckSurface(band.surface, where);
}

function rmapCheckBrightness(brightness, where) {
    rmapCheckNumber(brightness, where + ' brightness');
    if (brightness < 0 || brightness > 1) {
        throw new Error(where + ' has brightness ' + brightness + '. Brightness must be 0 to 1.');
    }
}

function rmapCheckNumber(value, what) {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
        throw new Error(what + ' is ' + value + ', which is not a usable number.');
    }
}

// Edges may only sit on interior grid lines.
//   orientation 0 = a line of constant X: x 1..W-1, running from z to z+1 (z 0..H-1)
//   orientation 1 = a line of constant Z: z 1..H-1, running from x to x+1 (x 0..W-1)
function rmapCheckEdgeLine(map, edge, where) {
    if (!Number.isInteger(edge.x) || !Number.isInteger(edge.z)) {
        throw new Error(where + ': x and z must be whole numbers.');
    }
    if (edge.orientation === 0) {
        if (edge.x < 1 || edge.x > map.width - 1 || edge.z < 0 || edge.z > map.height - 1) {
            throw new Error(where + ' is not on an interior line. Edge settings cannot go on the map\'s outer lines.');
        }
    } else if (edge.orientation === 1) {
        if (edge.z < 1 || edge.z > map.height - 1 || edge.x < 0 || edge.x > map.width - 1) {
            throw new Error(where + ' is not on an interior line. Edge settings cannot go on the map\'s outer lines.');
        }
    } else {
        throw new Error(where + ': orientation must be 0 (constant-X line) or 1 (constant-Z line).');
    }
}

// Node: require('./rmap_format.js'). In the editor these are plain globals.
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        RMAP_VERSION, RMAP_MAX_SIZE, RMAP_MAX_HEIGHT, RMAP_MAX_SPRITES, RMAP_MAX_STRING,
        rmapEncode, rmapDecode, rmapValidate
    };
}
