// tools.js - what Pen, Bucket and Picker do on each layer, and sprite placing / dragging.

let painting = null;        // during a pen stroke: { layer, settings, last } (see the start functions)
let draggingSprite = null;  // during a sprite drag: { offsetX, offsetZ, moved }

// =================================================================== tiles

function startTilePen(c) {
    const problem = tilesPanelProblem();
    if (problem) {
        setStatusMessage(problem);
        return;
    }
    // Every cell this stroke touches gets this one object.
    painting = { layer: 'tiles', settings: copyCell(panelCell), last: c, changed: 0 };
    stampCell(c.x, c.z);
    requestDraw();
}

function continueTilePen(c) {
    if (c.x === painting.last.x && c.z === painting.last.z) return;
    penLine(painting.last, c);
    painting.last = c;
    requestDraw();
}

// Stamps every cell on the way from one cell to the next, so fast drags leave no gaps.
// It moves one axis at a time, so a diagonal stroke never leaves cells that only touch
// at a corner.
function penLine(from, to) {
    let x = from.x;
    let z = from.z;
    const dx = Math.abs(to.x - x);
    const dz = Math.abs(to.z - z);
    const stepX = x < to.x ? 1 : -1;
    const stepZ = z < to.z ? 1 : -1;
    let error = dx - dz;
    while (x !== to.x || z !== to.z) {
        const e2 = 2 * error;
        if (e2 > -dz) {
            error -= dz;
            x += stepX;
            stampCell(x, z);
        }
        if (e2 < dx) {
            error += dx;
            z += stepZ;
            stampCell(x, z);
        }
    }
}

function stampCell(x, z) {
    if (isInsideMap(x, z) && setCell(cellIndex(x, z), painting.settings)) painting.changed++;
}

// Flood fill, 4-connected: the clicked cell's connected area of cells that are
// identical in everything to it. An explicit stack, because maps can be 2000 x 2000.
function bucketFill(c) {
    if (!isInsideMap(c.x, c.z)) return;
    const problem = tilesPanelProblem();
    if (problem) {
        setStatusMessage(problem);
        return;
    }
    const start = cellIndex(c.x, c.z);
    const target = map.cells[start];
    const fill = copyCell(panelCell);
    if (cellsEqual(target, fill)) {
        setStatusMessage('Bucket: that area already has these settings.');
        return;
    }

    const width = map.width;
    const height = map.height;
    const visited = new Uint8Array(width * height);
    const stack = [start];
    visited[start] = 1;
    let changed = 0;
    while (stack.length > 0) {
        const i = stack.pop();
        if (setCell(i, fill)) changed++;
        const x = i % width;
        const z = (i - x) / width;
        const neighbours = [];
        if (x > 0) neighbours.push(i - 1);
        if (x < width - 1) neighbours.push(i + 1);
        if (z > 0) neighbours.push(i - width);
        if (z < height - 1) neighbours.push(i + width);
        for (const n of neighbours) {
            if (!visited[n] && cellsEqual(map.cells[n], target)) {
                visited[n] = 1;
                stack.push(n);
            }
        }
    }
    setStatusMessage('Bucket: ' + changed + ' cells filled.');
    requestDraw();
}

// Copies the clicked cell's settings into the panel.
function pickCell(c) {
    if (!isInsideMap(c.x, c.z)) return;
    panelCell = copyCell(cellAt(c.x, c.z));
    refreshTilesPanel();
    showPanelProblem();
    setStatusMessage('Picked the settings of cell (' + c.x + ', ' + c.z + ').');
}

// =================================================================== edges

// The interior grid line nearest a grid position, or null if the nearest line is an
// outer line or outside the map (outer lines can't be painted).
//
// Close to a grid corner it also returns null: there, two lines are about equally near.
// Without this, dragging along a line would also paint a stub of every line it crosses.
const CORNER_DEAD_ZONE = 0.2;

function nearestLine(g) {
    const toVertical = Math.abs(g.x - Math.round(g.x));     // distance to the nearest constant-X line
    const toHorizontal = Math.abs(g.z - Math.round(g.z));   // distance to the nearest constant-Z line
    if (toVertical < CORNER_DEAD_ZONE && toHorizontal < CORNER_DEAD_ZONE) return null;
    let line;
    if (toVertical <= toHorizontal) {
        line = { x: Math.round(g.x), z: Math.floor(g.z), orientation: 0 };
    } else {
        line = { x: Math.floor(g.x), z: Math.round(g.z), orientation: 1 };
    }
    return isInteriorLine(line.x, line.z, line.orientation) ? line : null;
}

function startEdgePen(g) {
    const problem = edgesPanelProblem();
    if (problem) {
        setStatusMessage(problem);
        return;
    }
    // Every line this stroke touches shares these settings.
    painting = { layer: 'edges', settings: edgeSettingsFromPanel(), last: g, changed: 0 };
    paintLineAt(g);
    requestDraw();
}

// Dragging follows the lines under the mouse. The path between two mouse positions is
// walked in small steps so a fast drag still paints every line it crosses.
function continueEdgePen(g) {
    const from = painting.last;
    const distance = Math.hypot(g.x - from.x, g.z - from.z);
    const steps = Math.max(1, Math.ceil(distance / 0.25));
    for (let i = 1; i <= steps; i++) {
        paintLineAt({ x: from.x + (g.x - from.x) * i / steps, z: from.z + (g.z - from.z) * i / steps });
    }
    painting.last = g;
    requestDraw();
}

function paintLineAt(g) {
    const line = nearestLine(g);
    if (line && setEdge(line.x, line.z, line.orientation, painting.settings)) painting.changed++;
}

function pickEdge(g) {
    const line = nearestLine(g);
    if (!line) {
        setStatusMessage('No line picked: point at the middle of an interior line (outer lines use the boundary).');
        return;
    }
    const edge = getEdge(line.x, line.z, line.orientation);
    if (edge) {
        edgePanel = {
            solid: edge.solid,
            bottom: copyBand(edge.bottom),
            middle: copyBand(edge.middle),
            top: copyBand(edge.top)
        };
        setStatusMessage('Picked the settings of ' + describeLine(line) + '.');
    } else {
        // Nothing set: not solid and no bands. The band textures are kept for next time.
        edgePanel.solid = false;
        edgePanel.bottom.enabled = false;
        edgePanel.middle.enabled = false;
        edgePanel.top.enabled = false;
        setStatusMessage(describeLine(line) + ' has nothing set. Painting now clears lines.');
    }
    refreshEdgesPanel();
    showPanelProblem();
}

function describeLine(line) {
    return 'the line x ' + line.x + ', z ' + line.z + (line.orientation === 0 ? ' (constant X)' : ' (constant Z)');
}

// ================================================================ finishing

function finishPenStroke() {
    const what = painting.layer === 'tiles' ? ' cells painted.' : ' lines changed.';
    setStatusMessage(painting.changed > 0 ? 'Pen: ' + painting.changed + what : '');
    painting = null;
    requestDraw();
}

// ================================================================= sprites

// The top-most sprite whose square is under a canvas point, or null.
function spriteAtPoint(p) {
    const half = spriteSizePx() / 2;
    for (let i = map.sprites.length - 1; i >= 0; i--) {
        const c = gridToScreen(map.sprites[i].x, map.sprites[i].z);
        if (Math.abs(p.x - c.x) <= half && Math.abs(p.y - c.y) <= half) return map.sprites[i];
    }
    return null;
}

function selectSprite(sprite) {
    selectedSprite = sprite;
    if (sprite) {
        // The panel now shows this sprite, and the next placed sprite starts from it.
        spritePanel = {
            image: sprite.image,
            standOnFloor: sprite.standOnFloor,
            y: sprite.y,
            size: sprite.size,
            brightness: sprite.brightness,
            behaviorScript: sprite.behaviorScript,
            collisionRadius: sprite.collisionRadius,
            direction: sprite.direction
        };
    }
    setText('panelMessage', '');
    refreshSpritesPanel();
    requestDraw();
}

// Clicking empty space places a new sprite with the panel's settings, free placement.
function placeSprite(g) {
    if (spritePanel.image === '') {
        setStatusMessage('There are no sprite images in sprites/, so a sprite cannot be placed.');
        return null;
    }
    const sprite = {
        id: newSpriteId(),
        image: spritePanel.image,
        x: g.x,
        z: g.z,
        standOnFloor: spritePanel.standOnFloor,
        y: spritePanel.y,
        size: spritePanel.size,
        brightness: spritePanel.brightness,
        behaviorScript: spritePanel.behaviorScript,
        collisionRadius: spritePanel.collisionRadius,
        direction: spritePanel.direction
    };
    addSprite(sprite);
    selectSprite(sprite);
    setStatusMessage('Placed ' + sprite.id + '.');
    return sprite;
}

function startSpriteDrag(sprite, g) {
    draggingSprite = { offsetX: g.x - sprite.x, offsetZ: g.z - sprite.z, moved: false };
}

// Moves the selected sprite with the mouse, keeping it inside the map.
function continueSpriteDrag(g) {
    const insideX = map.width - 0.001;
    const insideZ = map.height - 0.001;
    selectedSprite.x = Math.min(insideX, Math.max(0, g.x - draggingSprite.offsetX));
    selectedSprite.z = Math.min(insideZ, Math.max(0, g.z - draggingSprite.offsetZ));
    draggingSprite.moved = true;
    mapDirty = true;
    spriteXInput.value = roundForDisplay(selectedSprite.x);
    spriteZInput.value = roundForDisplay(selectedSprite.z);
    requestDraw();
}

function finishSpriteDrag() {
    if (draggingSprite.moved) {
        setStatusMessage('Moved ' + selectedSprite.id + ' to (' + roundForDisplay(selectedSprite.x) + ', '
            + roundForDisplay(selectedSprite.z) + ').');
    }
    draggingSprite = null;
}

function deleteSelectedSprite() {
    if (!selectedSprite) return;
    const id = selectedSprite.id;
    removeSprite(selectedSprite);
    selectSprite(null);
    setStatusMessage('Removed ' + id + '.');
}
