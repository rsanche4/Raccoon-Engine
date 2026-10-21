// editor.js - the editor's screens, top bar, popups, and mouse / keyboard input.
//
// Scripts load in this order: rmap_format.js, map_state.js, data_folder.js, drawing.js,
// panels.js, tools.js, then this file.

const startScreen = document.getElementById('startScreen');
const mapChoice = document.getElementById('mapChoice');
const editorScreen = document.getElementById('editor');
const mapNameInput = document.getElementById('mapNameInput');

let activeLayer = 'tiles';      // 'tiles', 'edges' or 'sprites'
let activeTool = 'pen';         // 'pen', 'bucket' or 'picker' (the Sprites layer has no tools)
let panning = null;             // during a right-drag: where it started

// ============================================================ start screen

document.getElementById('openFolderBtn').addEventListener('click', openDataFolder);

async function openDataFolder() {
    const message = document.getElementById('startMessage');
    if (!folderApiAvailable()) {
        message.textContent = 'This browser cannot open folders. Open the editor in Chrome or Edge.';
        return;
    }
    let warnings;
    try {
        warnings = await pickDataFolder();
    } catch (e) {
        if (e.name === 'AbortError') return;   // the folder dialog was closed
        message.textContent = e.message;
        return;
    }
    spritePanel = makeDefaultSpriteSettings();   // now that sprites/ is known
    startScreen.style.display = 'none';
    showMapChoice(false, warnings);
}

// ======================================================= new map / open map

document.getElementById('createMapBtn').addEventListener('click', createMap);
document.getElementById('mapChoiceCancelBtn').addEventListener('click', () => {
    mapChoice.style.display = 'none';
});

async function showMapChoice(canCancel, warnings = []) {
    document.getElementById('mapChoiceCancelBtn').style.display = canCancel ? '' : 'none';
    setText('mapChoiceMessage', warnings.join('\n'));

    const list = document.getElementById('mapList');
    list.innerHTML = '';
    const fileNames = await listMapFiles();
    if (fileNames.length === 0) {
        list.textContent = 'No .rmap files in maps/ yet.';
    }
    for (const fileName of fileNames) {
        const name = fileName.slice(0, -'.rmap'.length);
        const button = document.createElement('button');
        button.className = 'list-button';
        button.textContent = name;
        button.addEventListener('click', () => openMap(name));
        list.appendChild(button);
    }
    mapChoice.style.display = 'block';
}

// Before throwing the current map away for another one.
function okToLeaveMap() {
    if (!map || !mapDirty) return true;
    return confirm('"' + mapName + '" has unsaved changes. They will be lost. Continue?');
}

window.addEventListener('beforeunload', e => {
    if (map && mapDirty) {
        e.preventDefault();
        e.returnValue = '';   // the browser shows its own "leave site?" question
    }
});

function createMap() {
    if (!okToLeaveMap()) return;
    const cleaned = cleanMapName(document.getElementById('newMapName').value);
    if (cleaned.problem) {
        setText('mapChoiceMessage', cleaned.problem);
        return;
    }
    const width = Number(document.getElementById('newMapWidth').value);
    const height = Number(document.getElementById('newMapHeight').value);
    if (!isMapSize(width) || !isMapSize(height)) {
        setText('mapChoiceMessage', 'Width and height must be whole numbers from 1 to ' + MAX_MAP_SIZE + '.');
        return;
    }
    newMap(cleaned.name, width, height);
    enterEditor('New map ' + cleaned.name + ' (' + width + ' x ' + height + ').');
}

async function openMap(name) {
    if (!okToLeaveMap()) return;
    let loaded;
    try {
        loaded = rmapDecode(await readMapFile(name));
    } catch (e) {
        setText('mapChoiceMessage', 'Cannot open ' + name + '.rmap: ' + e.message);
        return;
    }
    setLoadedMap(name, loaded);
    enterEditor('Opened maps/' + name + '.rmap (' + loaded.width + ' x ' + loaded.height + ').');
}

function enterEditor(message) {
    mapChoice.style.display = 'none';
    editorScreen.style.display = 'block';
    selectedSprite = null;
    hoverCell = null;
    hoverLine = null;
    resetView();
    resizeCanvas();
    refreshTopBar();
    setLayer(activeLayer);
    refreshPanels();
    updateHoverStatus(null);
    setStatusMessage(message);
}

function isMapSize(n) {
    return Number.isInteger(n) && n >= 1 && n <= MAX_MAP_SIZE;
}

// The map name is also its file name: maps/<name>.rmap.
function cleanMapName(text) {
    let name = text.trim();
    if (name.toLowerCase().endsWith('.rmap')) name = name.slice(0, -'.rmap'.length);
    if (name === '') {
        return { problem: 'Give the map a name.' };
    }
    if (/[\\/:*?"<>|]/.test(name)) {
        return { problem: 'A map name cannot contain any of these: \\ / : * ? " < > |' };
    }
    return { name };
}

// ================================================================== top bar

mapNameInput.addEventListener('input', () => {
    mapName = mapNameInput.value;
});
document.getElementById('surfaceFloorBtn').addEventListener('click', () => setViewSurface('floor'));
document.getElementById('surfaceCeilingBtn').addEventListener('click', () => setViewSurface('ceiling'));
document.getElementById('modeTexturedBtn').addEventListener('click', () => setViewMode('textured'));
document.getElementById('modeHeightBtn').addEventListener('click', () => setViewMode('height'));
document.getElementById('applySizeBtn').addEventListener('click', applyMapSize);
document.getElementById('boundaryBtn').addEventListener('click', openBoundaryDialog);
document.getElementById('saveBtn').addEventListener('click', saveMap);
document.getElementById('openBtn').addEventListener('click', () => showMapChoice(true));

function refreshTopBar() {
    mapNameInput.value = mapName;
    document.getElementById('mapWidthInput').value = map.width;
    document.getElementById('mapHeightInput').value = map.height;
    setActive('surfaceFloorBtn', viewSurface === 'floor');
    setActive('surfaceCeilingBtn', viewSurface === 'ceiling');
    setActive('modeTexturedBtn', viewMode === 'textured');
    setActive('modeHeightBtn', viewMode === 'height');
    const boundaryButton = document.getElementById('boundaryBtn');
    drawThumbnail(boundaryButton.querySelector('.thumb'), map.boundary.texture);
    boundaryButton.querySelector('.texture-name').textContent =
        map.boundary.texture + (map.boundary.skipTexture ? ' (skip)' : '');
}

function setViewSurface(surface) {
    viewSurface = surface;
    refreshTopBar();
    requestDraw();
}

function setViewMode(mode) {
    viewMode = mode;
    refreshTopBar();
    requestDraw();
}

// W x H + Apply. Growing adds default cells on the +X / +Z sides; shrinking
// asks first, saying how many edges and sprites go with the cut-off part.
function applyMapSize() {
    const width = Number(document.getElementById('mapWidthInput').value);
    const height = Number(document.getElementById('mapHeightInput').value);
    if (!isMapSize(width) || !isMapSize(height)) {
        setStatusMessage('Width and height must be whole numbers from 1 to ' + MAX_MAP_SIZE + '.');
        refreshTopBar();
        return;
    }
    if (width === map.width && height === map.height) return;
    if (width < map.width || height < map.height) {
        const losses = resizeLosses(width, height);
        const ok = confirm('Shrink the map from ' + map.width + ' x ' + map.height + ' to ' + width + ' x ' + height + '?\n\n'
            + 'The cut-off cells will be lost, along with ' + losses.edges.length + ' edge(s) and '
            + losses.sprites.length + ' sprite(s).');
        if (!ok) {
            refreshTopBar();
            return;
        }
    }
    const oldSize = map.width + ' x ' + map.height;
    const losses = resizeMap(width, height);
    if (selectedSprite && losses.sprites.includes(selectedSprite)) selectSprite(null);
    hoverCell = null;
    hoverLine = null;
    refreshTopBar();
    refreshSpritesPanel();
    requestDraw();
    setStatusMessage('Resized from ' + oldSize + ' to ' + width + ' x ' + height + '.'
        + (losses.edges.length + losses.sprites.length > 0
            ? ' Removed ' + losses.edges.length + ' edge(s) and ' + losses.sprites.length + ' sprite(s).' : ''));
}

async function saveMap() {
    const cleaned = cleanMapName(mapNameInput.value);
    if (cleaned.problem) {
        setStatusMessage('Not saved: ' + cleaned.problem);
        return;
    }
    const name = cleaned.name;
    let bytes;
    try {
        bytes = rmapEncode(map);
    } catch (e) {
        setStatusMessage('Not saved: ' + e.message);
        return;
    }
    // Saving over the file this map came from is normal; saving over a different one asks first.
    if (name !== savedFileName && await mapFileExists(name)) {
        if (!confirm('maps/' + name + '.rmap already exists. Overwrite it?')) return;
    }
    try {
        await writeMapFile(name, bytes);
    } catch (e) {
        setStatusMessage('Saving failed: ' + e.message);
        return;
    }
    mapName = name;
    savedFileName = name;
    mapDirty = false;
    refreshTopBar();
    setStatusMessage('Saved maps/' + name + '.rmap (' + Math.ceil(bytes.length / 1024) + ' KB).');
}

// ========================================================== boundary dialog

let boundaryDraft = makeSurface();
const boundaryControls = makeSurfaceControls(document.getElementById('boundarySurface'),
    'BOUNDARY', '', () => boundaryDraft, () => {});

document.getElementById('boundaryOkBtn').addEventListener('click', () => {
    try {
        rmapCheckSurface(boundaryDraft, 'The boundary');
    } catch (e) {
        setStatusMessage(e.message);
        return;
    }
    setBoundary(boundaryDraft);
    closePopup('boundaryDialog');
    refreshTopBar();
    setStatusMessage('Boundary surface set.');
});
document.getElementById('boundaryCancelBtn').addEventListener('click', () => closePopup('boundaryDialog'));

function openBoundaryDialog() {
    boundaryDraft = copySurface(map.boundary);
    boundaryControls.refresh();
    document.getElementById('boundaryDialog').style.display = 'block';
}

// ========================================================= layers and tools

document.getElementById('layerTilesBtn').addEventListener('click', () => setLayer('tiles'));
document.getElementById('layerEdgesBtn').addEventListener('click', () => setLayer('edges'));
document.getElementById('layerSpritesBtn').addEventListener('click', () => setLayer('sprites'));
document.getElementById('toolPenBtn').addEventListener('click', () => setTool('pen'));
document.getElementById('toolBucketBtn').addEventListener('click', () => setTool('bucket'));
document.getElementById('toolPickerBtn').addEventListener('click', () => setTool('picker'));

function setLayer(layer) {
    activeLayer = layer;
    if (layer === 'edges' && activeTool === 'bucket') activeTool = 'pen';   // no bucket on edges
    hoverCell = null;
    hoverLine = null;
    showPanelFor(layer);
    refreshToolButtons();
    requestDraw();
}

function setTool(tool) {
    if (activeLayer === 'sprites') return;
    if (tool === 'bucket' && activeLayer !== 'tiles') return;   // Bucket is Tiles-only
    activeTool = tool;
    refreshToolButtons();
}

function refreshToolButtons() {
    setActive('layerTilesBtn', activeLayer === 'tiles');
    setActive('layerEdgesBtn', activeLayer === 'edges');
    setActive('layerSpritesBtn', activeLayer === 'sprites');
    const noTools = activeLayer === 'sprites';
    setActive('toolPenBtn', !noTools && activeTool === 'pen');
    setActive('toolBucketBtn', !noTools && activeTool === 'bucket');
    setActive('toolPickerBtn', !noTools && activeTool === 'picker');
    document.getElementById('toolPenBtn').disabled = noTools;
    document.getElementById('toolPickerBtn').disabled = noTools;
    document.getElementById('toolBucketBtn').disabled = activeLayer !== 'tiles';
}

// ================================================================= palette

let paletteThumbs = [];

document.getElementById('paletteCancelBtn').addEventListener('click', () => closePopup('palette'));

// Shows a grid of images to pick from. drawFn draws one thumbnail (drawThumbnail for
// textures, drawSpriteThumbnail for sprite sheets).
function openPalette(title, names, drawFn, currentName, onPick, emptyText = 'There are no .png files in tex/.') {
    setText('paletteTitle', title);
    const grid = document.getElementById('paletteGrid');
    grid.innerHTML = '';
    paletteThumbs = [];
    if (names.length === 0) {
        grid.textContent = emptyText;
    }
    for (const name of names) {
        const item = document.createElement('button');
        item.className = 'palette-item' + (name === currentName ? ' selected' : '');
        const thumb = document.createElement('canvas');
        thumb.className = 'thumb';
        thumb.width = 48;
        thumb.height = 48;
        const label = document.createElement('span');
        label.textContent = name;
        item.append(thumb, label);
        item.addEventListener('click', () => {
            closePopup('palette');
            onPick(name);
        });
        grid.appendChild(item);
        drawFn(thumb, name);
        paletteThumbs.push({ thumb, name, drawFn });
    }
    document.getElementById('palette').style.display = 'block';
}

// Called by data_folder.js whenever an image finishes loading.
function refreshThumbnails() {
    for (const controls of allSurfaceControls) {
        drawThumbnail(controls.thumb, controls.getSurface().texture);
    }
    for (const item of paletteThumbs) {
        item.drawFn(item.thumb, item.name);
    }
    if (map) {
        drawThumbnail(document.querySelector('#boundaryBtn .thumb'), map.boundary.texture);
        drawSpriteThumbnail(spriteImageButton.querySelector('.thumb'), (selectedSprite || spritePanel).image);
    }
}

function closePopup(id) {
    document.getElementById(id).style.display = 'none';
}

function popupOpen() {
    for (const id of ['palette', 'boundaryDialog', 'mapChoice']) {
        if (document.getElementById(id).style.display === 'block') return true;
    }
    return false;
}

// =================================================================== mouse

canvas.addEventListener('mousedown', onMouseDown);
canvas.addEventListener('wheel', onWheel, { passive: false });
canvas.addEventListener('contextmenu', e => e.preventDefault());
window.addEventListener('mousemove', onMouseMove);
window.addEventListener('mouseup', onMouseUp);
window.addEventListener('blur', () => {
    if (painting) finishPenStroke();
    if (draggingSprite) finishSpriteDrag();
    panning = null;
});
window.addEventListener('resize', () => {
    if (map) resizeCanvas();
});

function canvasPoint(e) {
    const r = canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
}

function gridUnderMouse(e) {
    const p = canvasPoint(e);
    return screenToGrid(p.x, p.y);
}

function cellUnderMouse(e) {
    const g = gridUnderMouse(e);
    return { x: Math.floor(g.x), z: Math.floor(g.z) };
}

function onMouseDown(e) {
    if (!map || popupOpen()) return;
    if (e.button === 2) {
        panning = { x: e.clientX, y: e.clientY, panX, panZ };
        canvas.style.cursor = 'grabbing';
        return;
    }
    if (e.button !== 0) return;

    if (activeLayer === 'tiles') {
        const c = cellUnderMouse(e);
        if (activeTool === 'pen') startTilePen(c);
        else if (activeTool === 'bucket') bucketFill(c);
        else if (activeTool === 'picker') pickCell(c);
    } else if (activeLayer === 'edges') {
        const g = gridUnderMouse(e);
        if (activeTool === 'pen') startEdgePen(g);
        else if (activeTool === 'picker') pickEdge(g);
    } else if (activeLayer === 'sprites') {
        // Clicking a sprite selects it and starts a drag; clicking empty space places one.
        const g = gridUnderMouse(e);
        let sprite = spriteAtPoint(canvasPoint(e));
        if (sprite) {
            selectSprite(sprite);
            setStatusMessage('Selected ' + sprite.id + '.');
        } else if (isInsideMap(Math.floor(g.x), Math.floor(g.z))) {
            sprite = placeSprite(g);
        }
        if (sprite) startSpriteDrag(sprite, g);
    }
}

function onMouseMove(e) {
    if (!map) return;
    if (panning) {
        panX = panning.panX + (e.clientX - panning.x);
        panZ = panning.panZ - (e.clientY - panning.y);   // screen Y grows down, Z grows up
        clampPan();
        requestDraw();
    }
    if (painting) {
        if (painting.layer === 'tiles') continueTilePen(cellUnderMouse(e));
        else continueEdgePen(gridUnderMouse(e));
    }
    if (draggingSprite) {
        continueSpriteDrag(gridUnderMouse(e));
    }
    updateHover(e);
}

function onMouseUp(e) {
    if (e.button === 2 && panning) {
        panning = null;
        canvas.style.cursor = 'default';
    }
    if (e.button === 0) {
        if (painting) finishPenStroke();
        if (draggingSprite) finishSpriteDrag();
        updateHover(e);
    }
}

function onWheel(e) {
    e.preventDefault();
    if (!map) return;
    const p = canvasPoint(e);
    const oldZoom = zoom;
    zoom *= e.deltaY > 0 ? 0.9 : 1.1;
    zoom = Math.max(0.1, Math.min(10, zoom));
    // Zoom toward the mouse.
    panX = p.x - (p.x - panX) * (zoom / oldZoom);
    const fromBottom = canvas.height - p.y;
    panZ = fromBottom - (fromBottom - panZ) * (zoom / oldZoom);
    clampPan();
    requestDraw();
}

// What the mouse is over: a cell (Tiles), a grid line (Edges) or a sprite (Sprites).
function updateHover(e) {
    const p = canvasPoint(e);
    const overCanvas = p.x >= 0 && p.y >= 0 && p.x < canvas.width && p.y < canvas.height;
    const g = gridUnderMouse(e);
    const c = { x: Math.floor(g.x), z: Math.floor(g.z) };

    let newCell = null;
    let newLine = null;
    if (overCanvas && activeLayer === 'tiles' && isInsideMap(c.x, c.z)) newCell = c;
    if (overCanvas && activeLayer === 'edges') newLine = nearestLine(g);

    const cellChanged = JSON.stringify(newCell) !== JSON.stringify(hoverCell);
    const lineChanged = JSON.stringify(newLine) !== JSON.stringify(hoverLine);
    hoverCell = newCell;
    hoverLine = newLine;
    if (cellChanged || lineChanged) requestDraw();
    updateHoverStatus(overCanvas ? { g, c, p } : null);
}

// ================================================================ keyboard

window.addEventListener('keydown', onKeyDown);

function onKeyDown(e) {
    if (editorScreen.style.display !== 'block') return;
    if (popupOpen()) {
        if (e.key === 'Escape') {
            closePopup('palette');
            closePopup('boundaryDialog');
            if (document.getElementById('mapChoiceCancelBtn').style.display !== 'none') closePopup('mapChoice');
        }
        return;
    }
    const tag = e.target.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
    if (e.ctrlKey || e.metaKey || e.altKey) return;

    const key = e.key.toLowerCase();
    if (key === 'p') setTool('pen');
    else if (key === 'b') setTool('bucket');
    else if (key === 'i') setTool('picker');
    else if (key === '1') setLayer('tiles');
    else if (key === '2') setLayer('edges');
    else if (key === '3') setLayer('sprites');
    else if (e.key === 'Delete' && activeLayer === 'sprites') deleteSelectedSprite();
    else if (e.key === 'Escape' && activeLayer === 'sprites') selectSprite(null);
}

// ================================================================== status

// at: { g, c, p } for the mouse position over the canvas, or null.
function updateHoverStatus(at) {
    let text = '';
    if (at && activeLayer === 'tiles' && hoverCell) {
        const cell = cellAt(hoverCell.x, hoverCell.z);
        text = '(' + hoverCell.x + ', ' + hoverCell.z + ')   floor ' + cell.floorHeight + '   ceiling ' + cell.ceilingHeight
            + '   floor: ' + surfaceLabel(cell.floor) + '   ceiling: ' + surfaceLabel(cell.ceiling);
    } else if (at && activeLayer === 'edges') {
        if (hoverLine) {
            const edge = getEdge(hoverLine.x, hoverLine.z, hoverLine.orientation);
            text = 'line x ' + hoverLine.x + ', z ' + hoverLine.z + (hoverLine.orientation === 0 ? ' (constant X)' : ' (constant Z)')
                + '   ' + (edge ? describeEdgeSettings(edge) : 'nothing set');
        } else if (isInsideMap(at.c.x, at.c.z)) {
            text = 'no line here (corners are skipped; outer lines use the boundary)';
        }
    } else if (at && activeLayer === 'sprites') {
        const sprite = spriteAtPoint(at.p);
        if (sprite) {
            text = sprite.id + '   at (' + roundForDisplay(sprite.x) + ', ' + roundForDisplay(sprite.z) + ')   ' + sprite.image;
        } else if (isInsideMap(at.c.x, at.c.z)) {
            text = '(' + roundForDisplay(at.g.x) + ', ' + roundForDisplay(at.g.z) + ')   click to place a sprite';
        }
    }
    setText('statusHover', text);
}

function describeEdgeSettings(edge) {
    const band = b => (b.enabled ? surfaceLabel(b.surface) : 'off');
    return (edge.solid ? 'SOLID   ' : '') + 'bottom: ' + band(edge.bottom) + '   middle: ' + band(edge.middle)
        + '   top: ' + band(edge.top);
}

function surfaceLabel(s) {
    return s.texture + (s.skipTexture ? ' (skip)' : '');
}

function setStatusMessage(text) {
    setText('statusMessage', text);
}

// ================================================================= helpers

function setText(id, text) {
    document.getElementById(id).textContent = text;
}

function setActive(id, isActive) {
    document.getElementById(id).classList.toggle('active', isActive);
}

// An empty box is NaN rather than 0, so the panel reports it instead of quietly using 0.
function parseNumber(text) {
    return text.trim() === '' ? NaN : Number(text);
}
