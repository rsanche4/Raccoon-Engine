// map_state.js - the map being edited, and the functions that change it.
//
// The map has the shape described at the top of rmap_format.js.
//
// A cell object is NEVER edited in place. To change a cell, put a different object into
// map.cells. That is what lets thousands of cells share one object (a 2000 x 2000 map
// costs about 16 MB instead of 1.5 GB). Edge objects are treated the same way.
// Sprites are not shared, so the sprite panel edits a sprite object directly.

const DEFAULT_TEXTURE = 'null.png';
const MAX_MAP_SIZE = RMAP_MAX_SIZE;
const MAX_HEIGHT = RMAP_MAX_HEIGHT;

let map = null;
let mapName = '';
let savedFileName = null;   // the maps/<name>.rmap this map was opened from or last saved to
let mapDirty = false;       // changed since it was opened or saved

// Every edge in map.edges, looked up by its line: 'x,z,orientation' -> edge.
let edgesByLine = new Map();

// The lowest and highest floor and ceiling in the map, for the Height view. Worked out
// again only after the cells change (null = needs working out).
let heightRangeCache = null;

// ================================================================ cells

function makeSurface(texture = DEFAULT_TEXTURE, brightness = 0.5, tiled = 1, skipTexture = false) {
    return { texture, brightness, tiled, skipTexture };
}

// The settings every cell of a new map starts with.
function makeDefaultCell() {
    return {
        floorHeight: 0,
        ceilingHeight: 4,
        floor: makeSurface(),
        ceiling: makeSurface(),
        lowerWall: makeSurface(),
        upperWall: makeSurface()
    };
}

function copySurface(s) {
    return makeSurface(s.texture, s.brightness, s.tiled, s.skipTexture);
}

function copyCell(cell) {
    return {
        floorHeight: cell.floorHeight,
        ceilingHeight: cell.ceilingHeight,
        floor: copySurface(cell.floor),
        ceiling: copySurface(cell.ceiling),
        lowerWall: copySurface(cell.lowerWall),
        upperWall: copySurface(cell.upperWall)
    };
}

function surfacesEqual(a, b) {
    return a.texture === b.texture && a.brightness === b.brightness
        && a.tiled === b.tiled && a.skipTexture === b.skipTexture;
}

// True when two cells have the same settings in every field.
function cellsEqual(a, b) {
    if (a === b) return true;
    return a.floorHeight === b.floorHeight && a.ceilingHeight === b.ceilingHeight
        && surfacesEqual(a.floor, b.floor) && surfacesEqual(a.ceiling, b.ceiling)
        && surfacesEqual(a.lowerWall, b.lowerWall) && surfacesEqual(a.upperWall, b.upperWall);
}

// ================================================================== map

function newMap(name, width, height) {
    const cell = makeDefaultCell();
    map = {
        width,
        height,
        boundary: makeSurface(),
        cells: new Array(width * height).fill(cell),
        edges: [],
        sprites: []
    };
    mapName = name;
    savedFileName = null;
    mapDirty = false;
    heightRangeCache = null;
    indexEdges();
}

function setLoadedMap(name, loadedMap) {
    map = loadedMap;
    mapName = name;
    savedFileName = name;
    mapDirty = false;
    heightRangeCache = null;
    indexEdges();
}

// What shrinking to the new size would throw away: edges no longer on an interior line,
// and sprites no longer inside the map. Growing throws nothing away.
function resizeLosses(newWidth, newHeight) {
    const edges = map.edges.filter(e => !isInteriorLineOfSize(e.x, e.z, e.orientation, newWidth, newHeight));
    const sprites = map.sprites.filter(s => s.x >= newWidth || s.z >= newHeight);
    return { edges, sprites };
}

// Grows or shrinks the map. Cells keep their place from the bottom-left
// corner; new cells on the +X / +Z sides get the default settings.
function resizeMap(newWidth, newHeight) {
    const losses = resizeLosses(newWidth, newHeight);
    const fresh = makeDefaultCell();
    const cells = new Array(newWidth * newHeight);
    for (let z = 0; z < newHeight; z++) {
        for (let x = 0; x < newWidth; x++) {
            cells[z * newWidth + x] = (x < map.width && z < map.height) ? map.cells[z * map.width + x] : fresh;
        }
    }
    map.cells = cells;
    map.width = newWidth;
    map.height = newHeight;
    const lostEdges = new Set(losses.edges);
    const lostSprites = new Set(losses.sprites);
    map.edges = map.edges.filter(e => !lostEdges.has(e));
    map.sprites = map.sprites.filter(s => !lostSprites.has(s));
    indexEdges();
    heightRangeCache = null;
    mapDirty = true;
    return losses;
}

function isInsideMap(x, z) {
    return x >= 0 && x < map.width && z >= 0 && z < map.height;
}

function cellIndex(x, z) {
    return z * map.width + x;
}

function cellAt(x, z) {
    return map.cells[cellIndex(x, z)];
}

// Puts a cell object into the map. Returns true if that changed anything.
function setCell(index, newCell) {
    const oldCell = map.cells[index];
    if (oldCell === newCell || cellsEqual(oldCell, newCell)) return false;
    map.cells[index] = newCell;
    mapDirty = true;
    heightRangeCache = null;
    return true;
}

// { floorMin, floorMax, ceilingMin, ceilingMax } over the whole map.
function heightRange() {
    if (heightRangeCache) return heightRangeCache;
    const range = { floorMin: Infinity, floorMax: -Infinity, ceilingMin: Infinity, ceilingMax: -Infinity };
    let previous = null;
    for (const cell of map.cells) {
        if (cell === previous) continue;   // neighbours often share one object
        previous = cell;
        range.floorMin = Math.min(range.floorMin, cell.floorHeight);
        range.floorMax = Math.max(range.floorMax, cell.floorHeight);
        range.ceilingMin = Math.min(range.ceilingMin, cell.ceilingHeight);
        range.ceilingMax = Math.max(range.ceilingMax, cell.ceilingHeight);
    }
    heightRangeCache = range;
    return range;
}

function setBoundary(surface) {
    if (surfacesEqual(map.boundary, surface)) return;
    map.boundary = copySurface(surface);
    mapDirty = true;
}

// ================================================================ edges
//
// A grid line is (x, z, orientation), as in rmap_format.js:
//   orientation 0 = a line of constant X at x, running from z to z+1
//   orientation 1 = a line of constant Z at z, running from x to x+1

function makeBand(enabled = false, surface = makeSurface()) {
    return { enabled, surface };
}

function copyBand(band) {
    return makeBand(band.enabled, copySurface(band.surface));
}

function bandsEqual(a, b) {
    return a.enabled === b.enabled && surfacesEqual(a.surface, b.surface);
}

function lineKey(x, z, orientation) {
    return x + ',' + z + ',' + orientation;
}

// Only interior lines can carry edge settings; the outer lines use the boundary.
function isInteriorLine(x, z, orientation) {
    return isInteriorLineOfSize(x, z, orientation, map.width, map.height);
}

function isInteriorLineOfSize(x, z, orientation, width, height) {
    if (orientation === 0) return x >= 1 && x <= width - 1 && z >= 0 && z <= height - 1;
    return z >= 1 && z <= height - 1 && x >= 0 && x <= width - 1;
}

// True when the settings say nothing at all, so the line is stored as nothing.
function isNothingSet(settings) {
    return !settings.solid && !settings.bottom.enabled && !settings.middle.enabled && !settings.top.enabled;
}

function edgeSettingsEqual(a, b) {
    return a.solid === b.solid && bandsEqual(a.bottom, b.bottom)
        && bandsEqual(a.middle, b.middle) && bandsEqual(a.top, b.top);
}

function indexEdges() {
    edgesByLine = new Map();
    for (const edge of map.edges) {
        edgesByLine.set(lineKey(edge.x, edge.z, edge.orientation), edge);
    }
}

function getEdge(x, z, orientation) {
    return edgesByLine.get(lineKey(x, z, orientation)) || null;
}

// Gives a line the settings { solid, bottom, middle, top }. Settings with nothing set clear
// the line. The bands are stored as given, so a caller painting many lines can share them.
// Returns true if that changed anything.
function setEdge(x, z, orientation, settings) {
    const key = lineKey(x, z, orientation);
    const old = edgesByLine.get(key) || null;
    if (isNothingSet(settings)) {
        if (!old) return false;
        map.edges.splice(map.edges.indexOf(old), 1);
        edgesByLine.delete(key);
        mapDirty = true;
        return true;
    }
    if (old && edgeSettingsEqual(old, settings)) return false;
    const edge = {
        x, z, orientation,
        solid: settings.solid,
        bottom: settings.bottom,
        middle: settings.middle,
        top: settings.top
    };
    if (old) {
        map.edges[map.edges.indexOf(old)] = edge;
    } else {
        map.edges.push(edge);
    }
    edgesByLine.set(key, edge);
    mapDirty = true;
    return true;
}

// ============================================================== sprites

function spriteIdTaken(id, exceptSprite = null) {
    return map.sprites.some(s => s.id === id && s !== exceptSprite);
}

// sprite_1, sprite_2, ... the first one not in use.
function newSpriteId() {
    let n = 1;
    while (spriteIdTaken('sprite_' + n)) n++;
    return 'sprite_' + n;
}

function addSprite(sprite) {
    map.sprites.push(sprite);
    mapDirty = true;
}

function removeSprite(sprite) {
    const index = map.sprites.indexOf(sprite);
    if (index < 0) return;
    map.sprites.splice(index, 1);
    mapDirty = true;
}
