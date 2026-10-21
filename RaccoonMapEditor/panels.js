// panels.js - the settings panels on the left: Tiles, Edges and Sprites.
//
// Each panel edits its own working copy of settings. Pen and Bucket stamp a copy of the
// Tiles or Edges settings into the map, so editing a panel never changes what is already
// painted. The Sprites panel is different: while a sprite is selected it edits that sprite.

const allSurfaceControls = [];  // every surface editor on the page, for redrawing thumbnails

// ============================================================ surface controls

// Controls for one surface: texture (click to pick), brightness, tiled and skip.
// getSurface returns the surface object these controls edit; onChange runs after every edit.
// If getBand is given, the box also gets an "Enabled" checkbox for that band.
function makeSurfaceControls(parent, title, hint, getSurface, onChange, getBand = null) {
    const box = document.createElement('div');
    box.className = 'surface-box';
    box.innerHTML =
        '<h3>' + title + '</h3>' +
        (hint ? '<div class="hint">' + hint + '</div>' : '') +
        (getBand ? '<label class="check"><input type="checkbox" class="enabled"> Enabled</label>' : '') +
        '<button class="texture-button"><canvas class="thumb" width="32" height="32"></canvas>' +
        '<span class="texture-name"></span></button>' +
        '<label class="field">Brightness <input type="number" class="brightness" step="0.05" min="0" max="1"></label>' +
        '<div class="hint">0.5 = true colours</div>' +
        '<label class="field">Tiled <input type="number" class="tiled" step="1" min="0"></label>' +
        '<label class="check"><input type="checkbox" class="skip"> Skip texture</label>';
    parent.appendChild(box);

    const enabled = box.querySelector('.enabled');
    const thumb = box.querySelector('.thumb');
    const nameText = box.querySelector('.texture-name');
    const brightness = box.querySelector('.brightness');
    const tiled = box.querySelector('.tiled');
    const skip = box.querySelector('.skip');

    if (enabled) {
        enabled.addEventListener('change', () => {
            getBand().enabled = enabled.checked;
            onChange();
        });
    }
    box.querySelector('.texture-button').addEventListener('click', () => {
        openPalette('TEXTURE: ' + title, textureNames, drawThumbnail, getSurface().texture, name => {
            getSurface().texture = name;
            refresh();
            onChange();
        });
    });
    brightness.addEventListener('input', () => {
        getSurface().brightness = parseNumber(brightness.value);
        onChange();
    });
    tiled.addEventListener('input', () => {
        getSurface().tiled = parseNumber(tiled.value);
        onChange();
    });
    skip.addEventListener('change', () => {
        getSurface().skipTexture = skip.checked;
        onChange();
    });

    function refresh() {
        const s = getSurface();
        if (enabled) enabled.checked = getBand().enabled;
        drawThumbnail(thumb, s.texture);
        nameText.textContent = s.texture;
        brightness.value = s.brightness;
        tiled.value = s.tiled;
        skip.checked = s.skipTexture;
    }

    const controls = { refresh, thumb, getSurface };
    allSurfaceControls.push(controls);
    return controls;
}

// =================================================================== tiles

// The tile settings that Pen and Bucket stamp, starting at the default cell settings (makeDefaultCell).
let panelCell = makeDefaultCell();

const floorHeightInput = document.getElementById('floorHeightInput');
const ceilingHeightInput = document.getElementById('ceilingHeightInput');
floorHeightInput.addEventListener('input', () => {
    panelCell.floorHeight = parseNumber(floorHeightInput.value);
    showPanelProblem();
});
ceilingHeightInput.addEventListener('input', () => {
    panelCell.ceilingHeight = parseNumber(ceilingHeightInput.value);
    showPanelProblem();
});

const tileSurfaces = document.getElementById('tileSurfaces');
const tileSurfaceControls = [
    makeSurfaceControls(tileSurfaces, 'FLOOR', '',
        () => panelCell.floor, showPanelProblem),
    makeSurfaceControls(tileSurfaces, 'CEILING', 'Skip shows the sky (outdoors).',
        () => panelCell.ceiling, showPanelProblem),
    makeSurfaceControls(tileSurfaces, 'LOWER WALL', 'The side of this cell where its floor sticks up above a neighbour\'s.',
        () => panelCell.lowerWall, showPanelProblem),
    makeSurfaceControls(tileSurfaces, 'UPPER WALL', 'The face of this cell where its ceiling hangs below a neighbour\'s.',
        () => panelCell.upperWall, showPanelProblem)
];

function refreshTilesPanel() {
    floorHeightInput.value = panelCell.floorHeight;
    ceilingHeightInput.value = panelCell.ceilingHeight;
    for (const controls of tileSurfaceControls) controls.refresh();
}

function tilesPanelProblem() {
    try {
        rmapCheckCell(panelCell, 'The panel');
        return null;
    } catch (e) {
        return e.message;
    }
}

// =================================================================== edges

// The edge settings that the Edges pen paints. Nothing set = the pen clears lines.
let edgePanel = makeEmptyEdgeSettings();

function makeEmptyEdgeSettings() {
    return { solid: false, bottom: makeBand(), middle: makeBand(), top: makeBand() };
}

const edgeSolidInput = document.getElementById('edgeSolidInput');
edgeSolidInput.addEventListener('change', () => {
    edgePanel.solid = edgeSolidInput.checked;
    showPanelProblem();
});

const edgeBands = document.getElementById('edgeBands');
const edgeBandControls = [
    makeSurfaceControls(edgeBands, 'BOTTOM OVERRIDE', 'Replaces the lower wall on this line only (a door on one face of a wall).',
        () => edgePanel.bottom.surface, showPanelProblem, () => edgePanel.bottom),
    makeSurfaceControls(edgeBands, 'MIDDLE', 'Drawn in the opening: fences, glass, grates. See-through pixels show what is behind.',
        () => edgePanel.middle.surface, showPanelProblem, () => edgePanel.middle),
    makeSurfaceControls(edgeBands, 'TOP OVERRIDE', 'Replaces the upper wall on this line only.',
        () => edgePanel.top.surface, showPanelProblem, () => edgePanel.top)
];

function refreshEdgesPanel() {
    edgeSolidInput.checked = edgePanel.solid;
    for (const controls of edgeBandControls) controls.refresh();
}

function edgesPanelProblem() {
    try {
        rmapCheckBand(edgePanel.bottom, 'Bottom override');
        rmapCheckBand(edgePanel.middle, 'Middle');
        rmapCheckBand(edgePanel.top, 'Top override');
        return null;
    } catch (e) {
        return e.message;
    }
}

// A copy of the panel's edge settings, ready to be shared by every line of one stroke.
function edgeSettingsFromPanel() {
    return {
        solid: edgePanel.solid,
        bottom: copyBand(edgePanel.bottom),
        middle: copyBand(edgePanel.middle),
        top: copyBand(edgePanel.top)
    };
}

// ================================================================= sprites
//
// The panel always shows spritePanel. Placing a sprite copies it. While a sprite is
// selected, the panel shows that sprite and every edit goes to the sprite as well.

let spritePanel = makeDefaultSpriteSettings();

// The settings for the first sprite.
function makeDefaultSpriteSettings() {
    return {
        image: spriteNames.length > 0 ? spriteNames[0] : '',
        standOnFloor: true,
        y: 0,
        size: 320,
        brightness: 0.5,
        behaviorScript: '',
        collisionRadius: 0,
        direction: 0
    };
}

const spriteIdInput = document.getElementById('spriteIdInput');
const spriteXInput = document.getElementById('spriteXInput');
const spriteZInput = document.getElementById('spriteZInput');
const spriteStandInput = document.getElementById('spriteStandInput');
const spriteYInput = document.getElementById('spriteYInput');
const spriteSizeInput = document.getElementById('spriteSizeInput');
const spriteBrightnessInput = document.getElementById('spriteBrightnessInput');
const spriteImageButton = document.getElementById('spriteImageBtn');
const spriteScriptInput = document.getElementById('spriteScriptInput');
const spriteRadiusInput = document.getElementById('spriteRadiusInput');

// Applies one sprite setting if it is valid, to the panel and to the selected sprite.
function setSpriteField(field, value, problem) {
    if (problem) {
        setText('panelMessage', problem);
        return;
    }
    setText('panelMessage', '');
    spritePanel[field] = value;
    if (selectedSprite) {
        selectedSprite[field] = value;
        mapDirty = true;
        requestDraw();
    }
}

spriteIdInput.addEventListener('change', () => {
    if (!selectedSprite) return;
    const id = spriteIdInput.value.trim();
    if (id === '') {
        setText('panelMessage', 'A sprite needs an id.');
    } else if (spriteIdTaken(id, selectedSprite)) {
        setText('panelMessage', 'Another sprite already has the id "' + id + '". Sprite ids must be unique.');
    } else {
        setText('panelMessage', '');
        selectedSprite.id = id;
        mapDirty = true;
    }
    spriteIdInput.value = selectedSprite.id;
    refreshSpriteSelectionLine();
});

spriteXInput.addEventListener('input', () => {
    if (!selectedSprite) return;
    const x = parseNumber(spriteXInput.value);
    if (!(x >= 0 && x < map.width)) {
        setText('panelMessage', 'X must be from 0 up to (not including) ' + map.width + '.');
        return;
    }
    setText('panelMessage', '');
    selectedSprite.x = x;
    mapDirty = true;
    requestDraw();
});

spriteZInput.addEventListener('input', () => {
    if (!selectedSprite) return;
    const z = parseNumber(spriteZInput.value);
    if (!(z >= 0 && z < map.height)) {
        setText('panelMessage', 'Z must be from 0 up to (not including) ' + map.height + '.');
        return;
    }
    setText('panelMessage', '');
    selectedSprite.z = z;
    mapDirty = true;
    requestDraw();
});

spriteStandInput.addEventListener('change', () => {
    setSpriteField('standOnFloor', spriteStandInput.checked, null);
    if (!spriteStandInput.checked) {
        // Start the typed height just above the floor under the sprite.
        let floor = 0;
        if (selectedSprite) floor = cellAt(Math.floor(selectedSprite.x), Math.floor(selectedSprite.z)).floorHeight;
        setSpriteField('y', floor + 0.5, null);
    }
    refreshSpritesPanel();
});

spriteYInput.addEventListener('input', () => {
    const y = parseNumber(spriteYInput.value);
    setSpriteField('y', y, Number.isFinite(y) ? null : 'Y must be a number.');
});

spriteSizeInput.addEventListener('input', () => {
    const size = parseNumber(spriteSizeInput.value);
    setSpriteField('size', size, size > 0 ? null : 'Size must be a number above 0.');
});

spriteBrightnessInput.addEventListener('input', () => {
    const b = parseNumber(spriteBrightnessInput.value);
    setSpriteField('brightness', b, b >= 0 && b <= 1 ? null : 'Brightness must be 0 to 1.');
});

spriteRadiusInput.addEventListener('input', () => {
    const r = parseNumber(spriteRadiusInput.value);
    setSpriteField('collisionRadius', r, r >= 0 ? null : 'Collision radius must be 0 or more.');
});

spriteScriptInput.addEventListener('change', () => {
    setSpriteField('behaviorScript', spriteScriptInput.value, null);
});

spriteImageButton.addEventListener('click', () => {
    openPalette('SPRITE IMAGE', spriteNames, drawSpriteThumbnail, spritePanel.image, name => {
        setSpriteField('image', name, null);
        refreshSpritesPanel();
    }, 'There are no .png files in sprites/.');
});

// The 8-way direction dial. Angles grow counter-clockwise from +X, so on the
// Z-up map "up" is a quarter turn. Saved as radians, multiples of pi/4.
const DIAL_LAYOUT = [
    [3, '&#8598;'], [2, '&#8593;'], [1, '&#8599;'],
    [4, '&#8592;'], [-1, ''], [0, '&#8594;'],
    [5, '&#8601;'], [6, '&#8595;'], [7, '&#8600;']
];
const dialButtons = [];
const directionDial = document.getElementById('directionDial');
for (const [step, arrow] of DIAL_LAYOUT) {
    const button = document.createElement('button');
    button.className = 'tbtn dial-button';
    if (step < 0) {
        button.disabled = true;
    } else {
        button.innerHTML = arrow;
        button.title = (step * 45) + ' degrees';
        button.addEventListener('click', () => {
            setSpriteField('direction', step * Math.PI / 4, null);
            refreshSpritesPanel();
        });
        dialButtons.push({ button, step });
    }
    directionDial.appendChild(button);
}

function refreshSpritesPanel() {
    const s = selectedSprite || spritePanel;
    spriteIdInput.disabled = !selectedSprite;
    spriteXInput.disabled = !selectedSprite;
    spriteZInput.disabled = !selectedSprite;
    spriteIdInput.value = selectedSprite ? selectedSprite.id : '';
    spriteXInput.value = selectedSprite ? roundForDisplay(selectedSprite.x) : '';
    spriteZInput.value = selectedSprite ? roundForDisplay(selectedSprite.z) : '';
    spriteStandInput.checked = s.standOnFloor;
    document.getElementById('spriteYRow').style.display = s.standOnFloor ? 'none' : '';
    spriteYInput.value = s.y;
    spriteSizeInput.value = s.size;
    spriteBrightnessInput.value = s.brightness;
    spriteRadiusInput.value = s.collisionRadius;
    spriteImageButton.querySelector('.texture-name').textContent = s.image === '' ? '(none)' : s.image;
    drawSpriteThumbnail(spriteImageButton.querySelector('.thumb'), s.image);

    // Behaviour scripts: "none" plus every .lua in scripts/, plus this sprite's script if
    // it is not in scripts/ (so a map from elsewhere still shows what it uses).
    spriteScriptInput.innerHTML = '';
    const options = [['', 'none']];
    for (const name of scriptNames) options.push([name, name]);
    if (s.behaviorScript !== '' && !scriptNames.includes(s.behaviorScript)) {
        options.push([s.behaviorScript, s.behaviorScript + ' (not in scripts/)']);
    }
    for (const [value, label] of options) {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = label;
        spriteScriptInput.appendChild(option);
    }
    spriteScriptInput.value = s.behaviorScript;

    for (const { button, step } of dialButtons) {
        button.classList.toggle('active', Math.abs(s.direction - step * Math.PI / 4) < 1e-9);
    }
    refreshSpriteSelectionLine();
}

function refreshSpriteSelectionLine() {
    setText('spriteSelection', selectedSprite
        ? 'SELECTED: ' + selectedSprite.id + '   (Esc to deselect, Delete to remove)'
        : 'NOTHING SELECTED: these are the settings for the next sprite you place.');
}

function roundForDisplay(n) {
    return Math.round(n * 1000) / 1000;
}

// ======================================================= switching panels

function showPanelFor(layer) {
    document.getElementById('tilesPanel').style.display = layer === 'tiles' ? '' : 'none';
    document.getElementById('edgesPanel').style.display = layer === 'edges' ? '' : 'none';
    document.getElementById('spritesPanel').style.display = layer === 'sprites' ? '' : 'none';
    setText('panelMessage', '');
    showPanelProblem();
}

function refreshPanels() {
    refreshTilesPanel();
    refreshEdgesPanel();
    refreshSpritesPanel();
    showPanelProblem();
}

// Shows what is wrong with the active panel's settings, if anything.
function showPanelProblem() {
    if (activeLayer === 'tiles') setText('panelMessage', tilesPanelProblem() || '');
    else if (activeLayer === 'edges') setText('panelMessage', edgesPanelProblem() || '');
}
