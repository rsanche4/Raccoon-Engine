// drawing.js - draws the map onto the canvas, and converts between screen and grid.
//
// The grid's origin is the bottom-left corner of the map: X goes right, Z goes up,
// the same way the engine sees the world from above.

const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

const CELL_PX = 32;                 // size of one cell at zoom 1
const SKY_FILL = '#0a1a4a';         // a skipped surface (sky)
const LOADING_FILL = '#111';        // texture still loading
const OUTSIDE_FILL = '#0a0a0a';
const WALL_OUTLINE = '#f80';        // cells whose floor reaches the ceiling (solid wall cells)

let zoom = 1.0;
let panX = 40;                      // screen position of the map's left edge
let panZ = 40;                      // distance of the map's bottom edge from the canvas bottom
let viewSurface = 'floor';          // 'floor' or 'ceiling'
let viewMode = 'textured';          // 'textured' or 'height'
let hoverCell = null;               // Tiles layer: { x, z } under the mouse, or null
let hoverLine = null;               // Edges layer: { x, z, orientation } nearest the mouse, or null
let selectedSprite = null;          // Sprites layer: the sprite the panel is editing, or null

let drawQueued = false;
let checker = null;

function resizeCanvas() {
    canvas.width = canvas.clientWidth;
    canvas.height = canvas.clientHeight;
    requestDraw();
}

function cellSizePx() {
    return CELL_PX * zoom;
}

// Grid position (may be fractional) -> canvas pixel.
function gridToScreen(gridX, gridZ) {
    return {
        x: panX + gridX * cellSizePx(),
        y: canvas.height - panZ - gridZ * cellSizePx()
    };
}

// Canvas pixel -> grid position (fractional). Math.floor of it is the cell.
function screenToGrid(screenX, screenY) {
    return {
        x: (screenX - panX) / cellSizePx(),
        z: (canvas.height - panZ - screenY) / cellSizePx()
    };
}

function clampPan() {
    // Never scroll past the origin: keep (0, 0) visible at the bottom-left with a small margin.
    const margin = 40;
    panX = Math.min(panX, margin);
    panZ = Math.min(panZ, margin);
}

function resetView() {
    zoom = 1.0;
    panX = 40;
    panZ = 40;
}

// Many things want a redraw (mouse moves, textures arriving); draw once per frame at most.
function requestDraw() {
    if (drawQueued) return;
    drawQueued = true;
    requestAnimationFrame(() => {
        drawQueued = false;
        draw();
    });
}

function draw() {
    ctx.imageSmoothingEnabled = false;
    ctx.fillStyle = OUTSIDE_FILL;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    if (!map) return;

    const cellPx = cellSizePx();

    // Only the cells that are on screen (maps can be 2000 x 2000).
    const x0 = Math.max(0, Math.floor(-panX / cellPx));
    const x1 = Math.min(map.width, Math.ceil((canvas.width - panX) / cellPx));
    const z0 = Math.max(0, Math.floor(-panZ / cellPx));
    const z1 = Math.min(map.height, Math.ceil((canvas.height - panZ) / cellPx));

    // Neighbouring cells that share one settings object are drawn as one rectangle.
    // Wall cells (floor >= ceiling) are collected into one path and outlined afterwards.
    const outlineWalls = cellPx >= 6;
    const wallPath = new Path2D();
    for (let z = z0; z < z1; z++) {
        const top = Math.round(canvas.height - panZ - (z + 1) * cellPx);
        const bottom = Math.round(canvas.height - panZ - z * cellPx);
        let x = x0;
        while (x < x1) {
            const cell = map.cells[z * map.width + x];
            let runEnd = x + 1;
            while (runEnd < x1 && map.cells[z * map.width + runEnd] === cell) runEnd++;
            const left = Math.round(panX + x * cellPx);
            const right = Math.round(panX + runEnd * cellPx);
            ctx.fillStyle = cellFill(cell);
            ctx.fillRect(left, top, right - left, bottom - top);
            if (outlineWalls && cell.floorHeight >= cell.ceilingHeight) {
                for (let wx = x; wx < runEnd; wx++) {
                    const wallLeft = Math.round(panX + wx * cellPx);
                    wallPath.rect(wallLeft + 2, top + 2, Math.round(cellPx) - 4, bottom - top - 4);
                }
            }
            x = runEnd;
        }
    }
    ctx.strokeStyle = WALL_OUTLINE;
    ctx.lineWidth = 2;
    ctx.stroke(wallPath);

    drawGridLines(x0, x1, z0, z1);
    drawMapOutline();
    drawEdges();
    drawSprites();
    drawHover();
    if (viewMode === 'height') drawHeightLegend();
}

function cellFill(cell) {
    if (viewMode === 'height') {
        return heightFill(viewSurface === 'floor' ? cell.floorHeight : cell.ceilingHeight);
    }
    const surface = viewSurface === 'floor' ? cell.floor : cell.ceiling;
    return surfaceFill(surface);
}

// Height view: flat grey, brighter = higher, from the map's lowest to highest
// floor (or ceiling, following the Surface toggle).
function shownHeightRange() {
    const range = heightRange();
    return viewSurface === 'floor'
        ? { min: range.floorMin, max: range.floorMax }
        : { min: range.ceilingMin, max: range.ceilingMax };
}

function heightFill(height) {
    const { min, max } = shownHeightRange();
    const t = max > min ? (height - min) / (max - min) : 0.5;
    const v = Math.round(40 + t * 200);
    return 'rgb(' + v + ',' + v + ',' + v + ')';
}

function drawHeightLegend() {
    const { min, max } = shownHeightRange();
    const width = 220;
    const left = canvas.width - width - 12;
    const top = canvas.height - 64;
    ctx.fillStyle = 'rgba(0, 0, 0, 0.85)';
    ctx.fillRect(left, top, width, 52);
    ctx.strokeStyle = '#0f0';
    ctx.lineWidth = 1;
    ctx.strokeRect(left + 0.5, top + 0.5, width - 1, 51);
    const bar = ctx.createLinearGradient(left + 10, 0, left + width - 10, 0);
    bar.addColorStop(0, 'rgb(40,40,40)');
    bar.addColorStop(1, 'rgb(240,240,240)');
    ctx.fillStyle = bar;
    ctx.fillRect(left + 10, top + 22, width - 20, 10);
    ctx.fillStyle = '#0f0';
    ctx.font = '12px Courier New';
    ctx.textBaseline = 'top';
    ctx.textAlign = 'left';
    ctx.fillText((viewSurface === 'floor' ? 'FLOOR' : 'CEILING') + ' HEIGHT', left + 10, top + 6);
    ctx.fillText('' + min, left + 10, top + 36);
    ctx.textAlign = 'right';
    ctx.fillText('' + max, left + width - 10, top + 36);
    ctx.textAlign = 'left';
}

// A fill style showing a surface as the engine would texture it from above.
function surfaceFill(surface) {
    if (surface.skipTexture) return SKY_FILL;
    const tex = getTexture(surface.texture);
    let pattern;
    let width;
    let height;
    if (tex.failed) {
        pattern = checkerPattern();
        width = 8;
        height = 8;
    } else if (!tex.img) {
        return LOADING_FILL;
    } else {
        if (!tex.pattern) tex.pattern = ctx.createPattern(tex.img, 'repeat');
        pattern = tex.pattern;
        width = tex.img.naturalWidth;
        height = tex.img.naturalHeight;
    }
    pattern.setTransform(textureMatrix(surface.tiled, width, height));
    return pattern;
}

// Places a texture the way the engine samples floors and ceilings (Screen.java):
// one copy covers (1 + tiled) cells each way, lined up with the world origin, and the
// image's first row lies along the low-Z edge. With Z pointing up the screen, that
// means the image appears flipped top-to-bottom, exactly as it lies in the game.
function textureMatrix(tiled, imageWidth, imageHeight) {
    const copySize = (1 + tiled) * cellSizePx();
    const origin = gridToScreen(0, 0);
    return new DOMMatrix([copySize / imageWidth, 0, 0, -copySize / imageHeight, origin.x, origin.y]);
}

// The engine's missing-texture placeholder: an 8x8 magenta/black checker of 4x4 blocks.
function makeCheckerCanvas() {
    const c = document.createElement('canvas');
    c.width = 8;
    c.height = 8;
    const g = c.getContext('2d');
    g.fillStyle = '#000';
    g.fillRect(0, 0, 8, 8);
    g.fillStyle = '#f0f';
    g.fillRect(0, 0, 4, 4);
    g.fillRect(4, 4, 4, 4);
    return c;
}

function checkerPattern() {
    if (!checker) checker = ctx.createPattern(makeCheckerCanvas(), 'repeat');
    return checker;
}

function drawGridLines(x0, x1, z0, z1) {
    const cellPx = cellSizePx();
    if (cellPx < 8) return;   // too dense to be useful
    const left = gridToScreen(x0, 0).x;
    const right = gridToScreen(x1, 0).x;
    const top = gridToScreen(0, z1).y;
    const bottom = gridToScreen(0, z0).y;
    ctx.strokeStyle = 'rgba(0, 0, 0, 0.35)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let x = x0; x <= x1; x++) {
        const sx = Math.round(gridToScreen(x, 0).x) + 0.5;
        ctx.moveTo(sx, top);
        ctx.lineTo(sx, bottom);
    }
    for (let z = z0; z <= z1; z++) {
        const sy = Math.round(gridToScreen(0, z).y) + 0.5;
        ctx.moveTo(left, sy);
        ctx.lineTo(right, sy);
    }
    ctx.stroke();
}

function drawMapOutline() {
    const a = gridToScreen(0, 0);
    const b = gridToScreen(map.width, map.height);
    ctx.strokeStyle = '#0f0';
    ctx.lineWidth = 2;
    ctx.strokeRect(a.x, b.y, b.x - a.x, a.y - b.y);
}

function drawHover() {
    if (hoverCell) {
        const a = gridToScreen(hoverCell.x, hoverCell.z);
        const b = gridToScreen(hoverCell.x + 1, hoverCell.z + 1);
        ctx.strokeStyle = '#ff0';
        ctx.lineWidth = 2;
        ctx.strokeRect(a.x, b.y, b.x - a.x, a.y - b.y);
    }
    if (hoverLine) {
        ctx.strokeStyle = '#000';
        ctx.lineWidth = 6;
        strokeLine(hoverLine.x, hoverLine.z, hoverLine.orientation);
        ctx.strokeStyle = '#0ff';
        ctx.lineWidth = 4;
        strokeLine(hoverLine.x, hoverLine.z, hoverLine.orientation);
    }
}

// Lines with edge settings, in every view: yellow, or red when solid. A black outline
// underneath keeps them visible on any texture.
function drawEdges() {
    const cellPx = cellSizePx();
    const visible = map.edges.filter(edge => {
        const a = gridToScreen(edge.x, edge.z);
        return a.x > -cellPx && a.x < canvas.width + cellPx && a.y > -cellPx && a.y < canvas.height + cellPx;
    });
    ctx.strokeStyle = '#000';
    ctx.lineWidth = 5;
    for (const edge of visible) strokeLine(edge.x, edge.z, edge.orientation);
    ctx.lineWidth = 3;
    for (const edge of visible) {
        ctx.strokeStyle = edge.solid ? '#f00' : '#ff0';
        strokeLine(edge.x, edge.z, edge.orientation);
    }
}

function strokeLine(x, z, orientation) {
    const a = gridToScreen(x, z);
    const b = orientation === 0 ? gridToScreen(x, z + 1) : gridToScreen(x + 1, z);
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.stroke();
}

// On-screen size of a sprite's square. Never too small to click.
function spriteSizePx() {
    return Math.max(14, cellSizePx() * 0.6);
}

// Each sprite: its first frame, plus a tick pointing the way it faces.
function drawSprites() {
    const size = spriteSizePx();
    for (const sprite of map.sprites) {
        const c = gridToScreen(sprite.x, sprite.z);
        if (c.x < -size || c.x > canvas.width + size || c.y < -size || c.y > canvas.height + size) continue;
        const left = c.x - size / 2;
        const top = c.y - size / 2;
        drawImageFrame(ctx, getSpriteImage(sprite.image), left, top, size, true);
        const selected = sprite === selectedSprite;
        ctx.strokeStyle = selected ? '#ff0' : '#0ff';
        ctx.lineWidth = selected ? 3 : 1;
        ctx.strokeRect(left, top, size, size);
        // Angles grow toward +Z, which is up the screen.
        ctx.strokeStyle = selected ? '#ff0' : '#0ff';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(c.x, c.y);
        ctx.lineTo(c.x + Math.cos(sprite.direction) * size * 0.8, c.y - Math.sin(sprite.direction) * size * 0.8);
        ctx.stroke();
    }
}

// Draws an image (or the checker if it is missing) into a square.
// firstFrameOnly: sprite sheets hold 8 frames side by side; frame 0 is the left square.
function drawImageFrame(g, entry, left, top, size, firstFrameOnly) {
    g.imageSmoothingEnabled = false;
    if (entry.failed) {
        g.drawImage(makeCheckerCanvas(), left, top, size, size);
    } else if (entry.img) {
        const w = firstFrameOnly ? Math.min(entry.img.naturalWidth, entry.img.naturalHeight) : entry.img.naturalWidth;
        g.drawImage(entry.img, 0, 0, w, entry.img.naturalHeight, left, top, size, size);
    } else {
        g.fillStyle = LOADING_FILL;
        g.fillRect(left, top, size, size);
    }
}

// Small canvases for the panels and the palette, showing images as-is (not flipped).
function drawThumbnail(thumbCanvas, textureName) {
    const g = thumbCanvas.getContext('2d');
    g.clearRect(0, 0, thumbCanvas.width, thumbCanvas.height);
    drawImageFrame(g, getTexture(textureName), 0, 0, thumbCanvas.width, false);
}

function drawSpriteThumbnail(thumbCanvas, imageName) {
    const g = thumbCanvas.getContext('2d');
    g.clearRect(0, 0, thumbCanvas.width, thumbCanvas.height);
    if (imageName === '') return;
    drawImageFrame(g, getSpriteImage(imageName), 0, 0, thumbCanvas.width, true);
}
