const menu = document.getElementById('menu');
const editor = document.getElementById('editor');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

let zoom = 1.0;
let panX = 0;
let panZ = 0;
const gridSize = 32;

let mode = 'drawing';
let userRectangles = [];
let worldBoundary = null;
let currentShape = [];
let drawingLine = false;
let lineStart = null;
let currentMousePos = null;
let shapeStartVertex = null;
let currentConfigRect = null;
let selectedRectIndex = null;
let finalSectors = [];
let finalWalls = [];
let finalPortals = [];
const textureCache = new Map();

document.getElementById('newProjectBtn').addEventListener('click', () => {
    menu.style.display = 'none';
    editor.style.display = 'block';
    init();
});

document.getElementById('loadProjectBtn').addEventListener('click', () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = e => {
        const file = e.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = event => {
                try {
                    const projectData = JSON.parse(event.target.result);
                    loadProject(projectData);
                    menu.style.display = 'none';
                    editor.style.display = 'block';
                    init();
                } catch (error) {
                    console.error('Error loading project:', error);
                    alert('Error loading project file. Check console for details.');
                }
            };
            reader.readAsText(file);
        }
    };
    input.click();
});

function loadProject(projectData) {
    userRectangles = projectData.userRectangles || [];
    worldBoundary = projectData.worldBoundary || null;
    document.getElementById('rectCount').textContent = userRectangles.length;
}

function saveProject() {
    const projectData = {
        userRectangles: userRectangles,
        worldBoundary: worldBoundary
    };

    const json = JSON.stringify(projectData, null, 2);
    const blob = new Blob([json], {
        type: 'application/json'
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'project.json';
    a.click();
    URL.revokeObjectURL(url);
}

function clampPan() {
    // Never scroll past origin — keep (0,0) visible at bottom-left with a small margin
    const margin = 40;
    panX = Math.min(panX, margin);
    panZ = Math.min(panZ, margin);
}

function init() {
    resizeCanvas();
    // Place origin at bottom-left with a small margin
    panX = 40;
    panZ = 40;
    window.addEventListener('resize', resizeCanvas);
    canvas.addEventListener('wheel', handleZoom);
    canvas.addEventListener('mousedown', handleMouseDown);
    canvas.addEventListener('mousemove', handleMouseMove);
    canvas.addEventListener('mouseup', handleMouseUp);
    canvas.addEventListener('contextmenu', e => e.preventDefault());
    window.addEventListener('keydown', handleKeyDown);

    document.getElementById('saveProjectBtn').addEventListener('click', saveProject);
    document.getElementById('worldBoundaryBtn').addEventListener('click', startWorldBoundary);
    document.getElementById('downloadBtn').addEventListener('click', downloadMap);
    document.getElementById('saveConfig').addEventListener('click', saveConfiguration);

    draw();
}

function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    draw();
}

function screenToGrid(screenX, screenY) {
    // Origin at bottom-left: X increases right, Z increases upward
    const gridX = (screenX - panX) / (gridSize * zoom);
    const gridZ = (canvas.height - screenY - panZ) / (gridSize * zoom);
    return {
        x: Math.round(gridX),
        z: Math.round(gridZ)
    };
}

function gridToScreen(gridX, gridZ) {
    return {
        x: gridX * gridSize * zoom + panX,
        y: canvas.height - gridZ * gridSize * zoom - panZ
    };
}

function handleZoom(e) {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    const oldZoom = zoom;
    zoom *= delta;
    zoom = Math.max(0.1, Math.min(10, zoom));

    // Zoom toward mouse cursor
    const mouseX = e.clientX;
    const mouseY = e.clientY;
    panX = mouseX - (mouseX - panX) * (zoom / oldZoom);
    panZ = (canvas.height - mouseY) - ((canvas.height - mouseY) - panZ) * (zoom / oldZoom);

    clampPan();
    document.getElementById('zoomLevel').textContent = zoom.toFixed(1);
    for (const entry of textureCache.values()) entry.pattern = null;
    draw();
}

function handleKeyDown(e) {
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
        return;
    }

    if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault();

        if (mode === 'drawing') {
            // If a rectangle is selected, delete it
            if (selectedRectIndex !== null) {
                userRectangles.splice(selectedRectIndex, 1);
                selectedRectIndex = null;
                document.getElementById('rectCount').textContent = userRectangles.length;
                draw();
                return;
            }

            // Otherwise delete from current shape being drawn
            if (currentShape.length > 0) {
                currentShape.pop();
                if (currentShape.length === 0) {
                    shapeStartVertex = null;
                    lineStart = null;
                    drawingLine = false;
                }
                draw();
            }
        } else if (mode === 'worldBoundary') {
            if (currentShape.length > 0) {
                currentShape.pop();
                if (currentShape.length === 0) {
                    shapeStartVertex = null;
                    lineStart = null;
                    drawingLine = false;
                }
                draw();
            }
        }
    }
}

function handleMouseDown(e) {
    if (e.button === 0) {
        if (mode === 'partitioned') return;

        const gridPos = screenToGrid(e.clientX, e.clientY);

        // Check if clicking on start vertex to complete shape
        const clickingStartVertex = shapeStartVertex &&
            gridPos.x === shapeStartVertex.x &&
            gridPos.z === shapeStartVertex.z &&
            currentShape.length > 0;

        if (clickingStartVertex) {
            completeRectangle();
            return;
        }

        // If not currently drawing, check if clicking on a completed rectangle to select it
        if (currentShape.length === 0 && mode === 'drawing') {
            let clickedRectIndex = null;

            for (let i = 0; i < userRectangles.length; i++) {
                if (isPointInRectangle(gridPos, userRectangles[i])) {
                    clickedRectIndex = i;
                    break;
                }
            }

            if (clickedRectIndex !== null) {
                selectedRectIndex = clickedRectIndex;
                draw();
                return;
            } else {
                // Clicked on empty space, deselect
                if (selectedRectIndex !== null) {
                    selectedRectIndex = null;
                    draw();
                }
            }
        }

        // Deselect when starting to draw
        if (selectedRectIndex !== null && currentShape.length === 0) {
            selectedRectIndex = null;
        }

        // Start or continue drawing
        if (currentShape.length > 0) {
            const lastLine = currentShape[currentShape.length - 1];
            lineStart = {
                x: lastLine.x2,
                z: lastLine.z2
            };
        } else {
            lineStart = gridPos;
            shapeStartVertex = gridPos;
        }

        drawingLine = true;
    } else if (e.button === 2) {
        canvas.style.cursor = 'grabbing';
        const startPanX = panX;
        const startPanZ = panZ;
        const startX = e.clientX;
        const startY = e.clientY;

        function pan(e) {
            panX = startPanX + (e.clientX - startX);
            panZ = startPanZ - (e.clientY - startY); // invert Y for cartesian Z
            clampPan();
            draw();
        }

        function stopPan() {
            canvas.style.cursor = 'default';
            canvas.removeEventListener('mousemove', pan);
            canvas.removeEventListener('mouseup', stopPan);
        }

        canvas.addEventListener('mousemove', pan);
        canvas.addEventListener('mouseup', stopPan);
    }
}

function isPointInRectangle(gridPos, rect) {
    let inside = false;
    const vertices = rect.vertices;
    for (let i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
        const xi = vertices[i].x,
            zi = vertices[i].z;
        const xj = vertices[j].x,
            zj = vertices[j].z;

        const intersect = ((zi > gridPos.z) !== (zj > gridPos.z)) &&
            (gridPos.x < (xj - xi) * (gridPos.z - zi) / (zj - zi) + xi);
        if (intersect) inside = !inside;
    }
    return inside;
}

function handleMouseMove(e) {
    const gridPos = screenToGrid(e.clientX, e.clientY);
    currentMousePos = gridPos;
    document.getElementById('mouseX').textContent = gridPos.x;
    document.getElementById('mouseZ').textContent = gridPos.z;

    if (drawingLine) {
        draw();
    }
}

function handleMouseUp(e) {
    if (e.button === 0 && drawingLine) {
        const gridEnd = screenToGrid(e.clientX, e.clientY);

        if (gridEnd.x !== lineStart.x && gridEnd.z !== lineStart.z) {
            const dx = Math.abs(gridEnd.x - lineStart.x);
            const dz = Math.abs(gridEnd.z - lineStart.z);
            if (dx > dz) {
                gridEnd.z = lineStart.z;
            } else {
                gridEnd.x = lineStart.x;
            }
        }

        if (gridEnd.x !== lineStart.x || gridEnd.z !== lineStart.z) {
            currentShape.push({
                x1: lineStart.x,
                z1: lineStart.z,
                x2: gridEnd.x,
                z2: gridEnd.z
            });
        }

        drawingLine = false;
        draw();
    }
}

function completeRectangle() {
    if (currentShape.length < 3) return;

    const vertices = extractVertices(currentShape);

    if (mode === 'drawing') {
        currentConfigRect = {
            vertices: vertices,
            lines: [...currentShape],
            floorHeight: -2,
            ceilingHeight: 2,
            floorTexture: 'floor.png',
            floorBrightness: 1.0,
            floorTiled: 1,
            floorSkipTexture: false,
            ceilingTexture: 'ceiling.png',
            ceilingBrightness: 1.0,
            ceilingTiled: 1,
            ceilingSkipTexture: false,
            edges: {}
        };
        openConfigPanel(false);
    } else if (mode === 'worldBoundary') {
        const minX = Math.min(...vertices.map(v => v.x));
        const minZ = Math.min(...vertices.map(v => v.z));

        if (minX !== 0 || minZ !== 0) {
            alert('Invalid world boundary!\n\nThe boundary must touch both axes at the origin (0,0).\nMake sure the boundary starts at X=0 and Z=0.');
            currentShape = [];
            shapeStartVertex = null;
            lineStart = null;
            drawingLine = false;
            draw();
            return;
        }

        currentConfigRect = {
            vertices: vertices,
            lines: [...currentShape],
            floorHeight: -2,
            ceilingHeight: 2,
            floorTexture: 'floor.png',
            floorBrightness: 1.0,
            floorTiled: 1,
            floorSkipTexture: false,
            ceilingTexture: 'ceiling.png',
            ceilingBrightness: 1.0,
            ceilingTiled: 1,
            ceilingSkipTexture: false,
            walls: {}
        };
        openConfigPanel(true);
    }

    currentShape = [];
    shapeStartVertex = null;
    lineStart = null;
    drawingLine = false;
}

function extractVertices(lines) {
    const vertices = [];
    const vertexSet = new Set();

    for (const line of lines) {
        const v1Key = `${line.x1},${line.z1}`;
        const v2Key = `${line.x2},${line.z2}`;

        if (!vertexSet.has(v1Key)) {
            vertices.push({
                x: line.x1,
                z: line.z1
            });
            vertexSet.add(v1Key);
        }
        if (!vertexSet.has(v2Key)) {
            vertices.push({
                x: line.x2,
                z: line.z2
            });
            vertexSet.add(v2Key);
        }
    }

    return vertices;
}

function getEdgeDirection(vertices, edge) {
    const minX = Math.min(...vertices.map(v => v.x));
    const maxX = Math.max(...vertices.map(v => v.x));
    const minZ = Math.min(...vertices.map(v => v.z));
    const maxZ = Math.max(...vertices.map(v => v.z));

    if (edge.z1 === edge.z2) {
        if (edge.z1 === maxZ) return 'north';
        if (edge.z1 === minZ) return 'south';
    }
    if (edge.x1 === edge.x2) {
        if (edge.x1 === maxX) return 'east';
        if (edge.x1 === minX) return 'west';
    }
    return null;
}

function openConfigPanel(isWorldBoundary) {
    const rect = currentConfigRect;
    document.getElementById('configTitle').textContent =
        isWorldBoundary ? 'Configure World Boundary' : 'Configure Rectangle';

    document.getElementById('floorHeight').value = rect.floorHeight;
    document.getElementById('ceilingHeight').value = rect.ceilingHeight;
    document.getElementById('floorTexture').value = rect.floorTexture;
    document.getElementById('floorBrightness').value = rect.floorBrightness;
    document.getElementById('floorTiled').value = rect.floorTiled !== undefined ? rect.floorTiled : 1;
    document.getElementById('floorSkipTexture').checked = rect.floorSkipTexture || false;
    document.getElementById('ceilingTexture').value = rect.ceilingTexture;
    document.getElementById('ceilingBrightness').value = rect.ceilingBrightness;
    document.getElementById('ceilingTiled').value = rect.ceilingTiled !== undefined ? rect.ceilingTiled : 1;
    document.getElementById('ceilingSkipTexture').checked = rect.ceilingSkipTexture || false;

    const wallsContainer = document.getElementById('wallsConfig');
    wallsContainer.innerHTML = '';

    for (const line of rect.lines) {
        const dir = getEdgeDirection(rect.vertices, line);
        if (!dir) continue;

        const wallSection = document.createElement('div');
        wallSection.className = 'wall-section';
        wallSection.innerHTML = `<h3>${dir.toUpperCase()} Wall</h3>`;

        if (isWorldBoundary) {
            const saved = rect.walls && rect.walls[dir];
            wallSection.innerHTML += `
                        <label>Texture:</label>
                        <input type="text" class="wall-texture" data-dir="${dir}" value="${saved ? saved.texture : 'wall.png'}">
                        <label>Brightness:</label>
                        <input type="number" class="wall-brightness" data-dir="${dir}" value="${saved ? saved.brightness : 1.0}" step="0.1" min="0">
                        <label>Tiled:</label>
                        <input type="number" class="wall-tiled" data-dir="${dir}" value="${saved ? saved.tiled : 1}" step="1" min="0">
                        <label>Skip Texture:</label>
                        <input type="checkbox" class="wall-skip-texture" data-dir="${dir}" ${saved && saved.skipTexture ? 'checked' : ''}>
                    `;
        } else {
            const saved = rect.edges && rect.edges[dir];
            wallSection.innerHTML += `
                        <label>Top Texture:</label>
                        <input type="text" class="edge-top-texture" data-dir="${dir}" value="${saved ? saved.topTexture : 'black.png'}">
                        <label>Top Brightness:</label>
                        <input type="number" class="edge-top-brightness" data-dir="${dir}" value="${saved ? saved.topBrightness : 1.0}" step="0.1" min="0">
                        <label>Top Tiled:</label>
                        <input type="number" class="edge-top-tiled" data-dir="${dir}" value="${saved ? saved.topTiled : 1}" step="1" min="0">
                        <label>Top Skip Texture:</label>
                        <input type="checkbox" class="edge-top-skip" data-dir="${dir}" ${saved && saved.topSkip ? 'checked' : ''}>
                        <label>Middle Texture:</label>
                        <input type="text" class="edge-mid-texture" data-dir="${dir}" value="${saved ? saved.midTexture : 'black.png'}">
                        <label>Middle Brightness:</label>
                        <input type="number" class="edge-mid-brightness" data-dir="${dir}" value="${saved ? saved.midBrightness : 1.0}" step="0.1" min="0">
                        <label>Middle Tiled:</label>
                        <input type="number" class="edge-mid-tiled" data-dir="${dir}" value="${saved ? saved.midTiled : 1}" step="1" min="0">
                        <label>Middle Skip Texture:</label>
                        <input type="checkbox" class="edge-mid-skip" data-dir="${dir}" ${saved && saved.midSkip ? 'checked' : ''}>
                        <label>Bottom Texture:</label>
                        <input type="text" class="edge-bot-texture" data-dir="${dir}" value="${saved ? saved.botTexture : 'black.png'}">
                        <label>Bottom Brightness:</label>
                        <input type="number" class="edge-bot-brightness" data-dir="${dir}" value="${saved ? saved.botBrightness : 1.0}" step="0.1" min="0">
                        <label>Bottom Tiled:</label>
                        <input type="number" class="edge-bot-tiled" data-dir="${dir}" value="${saved ? saved.botTiled : 1}" step="1" min="0">
                        <label>Bottom Skip Texture:</label>
                        <input type="checkbox" class="edge-bot-skip" data-dir="${dir}" ${saved && saved.botSkip ? 'checked' : ''}>
                        <label>Solid (1) or Passthrough (0):</label>
                        <input type="number" class="edge-solid" data-dir="${dir}" value="${saved ? saved.solid : 0}" min="0" max="1" step="1">
                    `;
        }

        wallsContainer.appendChild(wallSection);
    }

    document.getElementById('configPanel').style.display = 'block';
}

function saveConfiguration() {
    const rect = currentConfigRect;
    const isWorldBoundary = mode === 'worldBoundary';

    rect.floorHeight = parseFloat(document.getElementById('floorHeight').value);
    rect.ceilingHeight = parseFloat(document.getElementById('ceilingHeight').value);
    rect.floorTexture = document.getElementById('floorTexture').value;
    rect.floorBrightness = parseFloat(document.getElementById('floorBrightness').value);
    rect.floorTiled = parseInt(document.getElementById('floorTiled').value);
    rect.floorSkipTexture = document.getElementById('floorSkipTexture').checked;
    rect.ceilingTexture = document.getElementById('ceilingTexture').value;
    rect.ceilingBrightness = parseFloat(document.getElementById('ceilingBrightness').value);
    rect.ceilingTiled = parseInt(document.getElementById('ceilingTiled').value);
    rect.ceilingSkipTexture = document.getElementById('ceilingSkipTexture').checked;

    if (isWorldBoundary) {
        const wallTextures = document.querySelectorAll('.wall-texture');
        const wallBrightnesses = document.querySelectorAll('.wall-brightness');
        const wallTileds = document.querySelectorAll('.wall-tiled');
        const wallSkips = document.querySelectorAll('.wall-skip-texture');

        wallTextures.forEach((input, idx) => {
            const dir = input.dataset.dir;
            rect.walls[dir] = {
                texture: input.value,
                brightness: parseFloat(wallBrightnesses[idx].value),
                tiled: parseInt(wallTileds[idx].value),
                skipTexture: wallSkips[idx].checked
            };
        });

        worldBoundary = rect;
        document.getElementById('configPanel').style.display = 'none';
        currentConfigRect = null;

        setTimeout(() => runManhattanPartitioning(), 100);
    } else {
        const topTextures = document.querySelectorAll('.edge-top-texture');
        const topBrightnesses = document.querySelectorAll('.edge-top-brightness');
        const topTileds = document.querySelectorAll('.edge-top-tiled');
        const topSkips = document.querySelectorAll('.edge-top-skip');
        const midTextures = document.querySelectorAll('.edge-mid-texture');
        const midBrightnesses = document.querySelectorAll('.edge-mid-brightness');
        const midTileds = document.querySelectorAll('.edge-mid-tiled');
        const midSkips = document.querySelectorAll('.edge-mid-skip');
        const botTextures = document.querySelectorAll('.edge-bot-texture');
        const botBrightnesses = document.querySelectorAll('.edge-bot-brightness');
        const botTileds = document.querySelectorAll('.edge-bot-tiled');
        const botSkips = document.querySelectorAll('.edge-bot-skip');
        const solids = document.querySelectorAll('.edge-solid');

        topTextures.forEach((input, idx) => {
            const dir = input.dataset.dir;
            rect.edges[dir] = {
                topTexture: input.value,
                topBrightness: parseFloat(topBrightnesses[idx].value),
                topTiled: parseInt(topTileds[idx].value),
                topSkip: topSkips[idx].checked,
                midTexture: midTextures[idx].value,
                midBrightness: parseFloat(midBrightnesses[idx].value),
                midTiled: parseInt(midTileds[idx].value),
                midSkip: midSkips[idx].checked,
                botTexture: botTextures[idx].value,
                botBrightness: parseFloat(botBrightnesses[idx].value),
                botTiled: parseInt(botTileds[idx].value),
                botSkip: botSkips[idx].checked,
                solid: parseInt(solids[idx].value)
            };
        });

        userRectangles.push(rect);
        document.getElementById('rectCount').textContent = userRectangles.length;
        document.getElementById('configPanel').style.display = 'none';
        currentConfigRect = null;

        draw();
    }
}

function startWorldBoundary() {
    if (userRectangles.length === 0) {
        alert('Draw at least one rectangle first!');
        return;
    }
    mode = 'worldBoundary';
    document.getElementById('modeText').textContent = 'World Boundary';
    document.getElementById('worldBoundaryBtn').style.display = 'none';
    document.getElementById('saveProjectBtn').style.display = 'none';
    draw();
}

async function runManhattanPartitioning() {
    mode = 'partitioned';
    document.getElementById('modeText').textContent = 'Partitioned';
    document.getElementById('loadingBar').style.display = 'block';

    await new Promise(resolve => setTimeout(resolve, 100));

    updateProgress(10, 'Collecting coordinates...');
    const xCoords = new Set();
    const zCoords = new Set();

    for (const v of worldBoundary.vertices) {
        xCoords.add(v.x);
        zCoords.add(v.z);
    }

    for (const rect of userRectangles) {
        for (const line of rect.lines) {
            if (line.x1 === line.x2) {
                xCoords.add(line.x1);
            }
            if (line.z1 === line.z2) {
                zCoords.add(line.z1);
            }
        }
    }

    const xArray = Array.from(xCoords).sort((a, b) => a - b);
    const zArray = Array.from(zCoords).sort((a, b) => a - b);

    updateProgress(30, 'Generating grid sectors...');
    await new Promise(resolve => setTimeout(resolve, 50));

    const gridRectangles = [];
    for (let i = 0; i < xArray.length - 1; i++) {
        for (let j = 0; j < zArray.length - 1; j++) {
            const rect = {
                minX: xArray[i],
                maxX: xArray[i + 1],
                minZ: zArray[j],
                maxZ: zArray[j + 1]
            };
            gridRectangles.push(rect);
        }
    }

    updateProgress(50, 'Assigning properties...');
    await new Promise(resolve => setTimeout(resolve, 50));

    finalSectors = [];
    let sectorId = 0;

    for (const gridRect of gridRectangles) {
        const centerX = (gridRect.minX + gridRect.maxX) / 2;
        const centerZ = (gridRect.minZ + gridRect.maxZ) / 2;

        let sourceRect = null;
        for (const userRect of userRectangles) {
            if (isPointInPolygon(centerX, centerZ, userRect.vertices)) {
                sourceRect = userRect;
                break;
            }
        }

        const sector = {
            id: sectorId++,
            minX: gridRect.minX,
            maxX: gridRect.maxX,
            minZ: gridRect.minZ,
            maxZ: gridRect.maxZ,
            vertices: [{
                    x: gridRect.minX,
                    z: gridRect.minZ
                },
                {
                    x: gridRect.maxX,
                    z: gridRect.minZ
                },
                {
                    x: gridRect.maxX,
                    z: gridRect.maxZ
                },
                {
                    x: gridRect.minX,
                    z: gridRect.maxZ
                }
            ],
            sourceRect: sourceRect || 'void',
            floorHeight: sourceRect ? sourceRect.floorHeight : worldBoundary.floorHeight,
            ceilingHeight: sourceRect ? sourceRect.ceilingHeight : worldBoundary.ceilingHeight,
            floorTexture: sourceRect ? sourceRect.floorTexture : worldBoundary.floorTexture,
            floorBrightness: sourceRect ? sourceRect.floorBrightness : worldBoundary.floorBrightness,
            floorTiled: sourceRect ? (sourceRect.floorTiled !== undefined ? sourceRect.floorTiled : 1) : (worldBoundary.floorTiled !== undefined ? worldBoundary.floorTiled : 1),
            floorSkipTexture: sourceRect ? (sourceRect.floorSkipTexture || false) : (worldBoundary.floorSkipTexture || false),
            ceilingTexture: sourceRect ? sourceRect.ceilingTexture : worldBoundary.ceilingTexture,
            ceilingBrightness: sourceRect ? sourceRect.ceilingBrightness : worldBoundary.ceilingBrightness,
            ceilingTiled: sourceRect ? (sourceRect.ceilingTiled !== undefined ? sourceRect.ceilingTiled : 1) : (worldBoundary.ceilingTiled !== undefined ? worldBoundary.ceilingTiled : 1),
            ceilingSkipTexture: sourceRect ? (sourceRect.ceilingSkipTexture || false) : (worldBoundary.ceilingSkipTexture || false)
        };

        finalSectors.push(sector);
    }

    updateProgress(70, 'Classifying edges...');
    await new Promise(resolve => setTimeout(resolve, 50));

    classifyEdges();

    updateProgress(100, 'Complete!');
    await new Promise(resolve => setTimeout(resolve, 500));

    document.getElementById('loadingBar').style.display = 'none';
    document.getElementById('downloadBtn').style.display = 'block';

    draw();
}

function isPointInPolygon(x, z, vertices) {
    let inside = false;
    for (let i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
        const xi = vertices[i].x,
            zi = vertices[i].z;
        const xj = vertices[j].x,
            zj = vertices[j].z;

        const intersect = ((zi > z) !== (zj > z)) &&
            (x < (xj - xi) * (z - zi) / (zj - zi) + xi);
        if (intersect) inside = !inside;
    }
    return inside;
}

function classifyEdges() {
    finalWalls = [];
    finalPortals = [];

    const worldMinX = Math.min(...worldBoundary.vertices.map(v => v.x));
    const worldMaxX = Math.max(...worldBoundary.vertices.map(v => v.x));
    const worldMinZ = Math.min(...worldBoundary.vertices.map(v => v.z));
    const worldMaxZ = Math.max(...worldBoundary.vertices.map(v => v.z));

    const edgeMap = new Map();

    for (const sector of finalSectors) {
        const edges = [{
                x1: sector.minX,
                z1: sector.minZ,
                x2: sector.maxX,
                z2: sector.minZ
            },
            {
                x1: sector.maxX,
                z1: sector.minZ,
                x2: sector.maxX,
                z2: sector.maxZ
            },
            {
                x1: sector.minX,
                z1: sector.maxZ,
                x2: sector.maxX,
                z2: sector.maxZ
            },
            {
                x1: sector.minX,
                z1: sector.minZ,
                x2: sector.minX,
                z2: sector.maxZ
            }
        ];

        for (const edge of edges) {
            const key = normalizeEdgeKey(edge.x1, edge.z1, edge.x2, edge.z2);
            if (!edgeMap.has(key)) {
                edgeMap.set(key, []);
            }
            edgeMap.get(key).push(sector.id);
        }
    }

    for (const [key, sectorIds] of edgeMap) {
        const [x1, z1, x2, z2] = key.split(',').map(Number);

        const isOnBoundary =
            (x1 === worldMinX && x2 === worldMinX) ||
            (x1 === worldMaxX && x2 === worldMaxX) ||
            (z1 === worldMinZ && z2 === worldMinZ) ||
            (z1 === worldMaxZ && z2 === worldMaxZ);

        if (isOnBoundary) {
            const dir = getWallDirection(x1, z1, x2, z2, worldMinX, worldMaxX, worldMinZ, worldMaxZ);
            const wallData = worldBoundary.walls[dir];

            for (const sectorId of sectorIds) {
                finalWalls.push({
                    x1,
                    z1,
                    x2,
                    z2,
                    sectorId,
                    texture: wallData ? wallData.texture : 'wall.png',
                    brightness: wallData ? wallData.brightness : 1.0,
                    tiled: wallData ? (wallData.tiled !== undefined ? wallData.tiled : 1) : 1,
                    skipTexture: wallData ? (wallData.skipTexture || false) : false
                });
            }
        } else if (sectorIds.length === 2) {
            const edgeProps = getEdgeProperties(x1, z1, x2, z2);

            finalPortals.push({
                x1,
                z1,
                x2,
                z2,
                sectorA: sectorIds[0],
                sectorB: sectorIds[1],
                topTexture: edgeProps.topTexture,
                topBrightness: edgeProps.topBrightness,
                topTiled: edgeProps.topTiled,
                topSkip: edgeProps.topSkip,
                midTexture: edgeProps.midTexture,
                midBrightness: edgeProps.midBrightness,
                midTiled: edgeProps.midTiled,
                midSkip: edgeProps.midSkip,
                botTexture: edgeProps.botTexture,
                botBrightness: edgeProps.botBrightness,
                botTiled: edgeProps.botTiled,
                botSkip: edgeProps.botSkip,
                solid: edgeProps.solid
            });
        }
    }
}

function getWallDirection(x1, z1, x2, z2, minX, maxX, minZ, maxZ) {
    if (z1 === z2) {
        if (z1 === maxZ) return 'north';
        if (z1 === minZ) return 'south';
    }
    if (x1 === x2) {
        if (x1 === maxX) return 'east';
        if (x1 === minX) return 'west';
    }
    return 'north';
}

function getEdgeProperties(x1, z1, x2, z2) {
    for (const rect of userRectangles) {
        for (const line of rect.lines) {
            if (isEdgePartOfLine(x1, z1, x2, z2, line.x1, line.z1, line.x2, line.z2)) {
                const dir = getEdgeDirection(rect.vertices, line);
                if (dir && rect.edges[dir]) {
                    return {
                        topTexture: rect.edges[dir].topTexture,
                        topBrightness: rect.edges[dir].topBrightness,
                        topTiled: rect.edges[dir].topTiled !== undefined ? rect.edges[dir].topTiled : 1,
                        topSkip: rect.edges[dir].topSkip || false,
                        midTexture: rect.edges[dir].midTexture,
                        midBrightness: rect.edges[dir].midBrightness,
                        midTiled: rect.edges[dir].midTiled !== undefined ? rect.edges[dir].midTiled : 1,
                        midSkip: rect.edges[dir].midSkip || false,
                        botTexture: rect.edges[dir].botTexture,
                        botBrightness: rect.edges[dir].botBrightness,
                        botTiled: rect.edges[dir].botTiled !== undefined ? rect.edges[dir].botTiled : 1,
                        botSkip: rect.edges[dir].botSkip || false,
                        solid: rect.edges[dir].solid
                    };
                }
            }
        }
    }

    return {
        topTexture: 'black.png',
        topBrightness: 1.0,
        topTiled: 1,
        topSkip: false,
        midTexture: 'black.png',
        midBrightness: 1.0,
        midTiled: 1,
        midSkip: false,
        botTexture: 'black.png',
        botBrightness: 1.0,
        botTiled: 1,
        botSkip: false,
        solid: 0
    };
}

function isEdgePartOfLine(ex1, ez1, ex2, ez2, lx1, lz1, lx2, lz2) {
    if (ex1 === ex2 && lx1 === lx2 && ex1 === lx1) {
        const eMin = Math.min(ez1, ez2),
            eMax = Math.max(ez1, ez2);
        const lMin = Math.min(lz1, lz2),
            lMax = Math.max(lz1, lz2);
        return eMin >= lMin && eMax <= lMax;
    }
    if (ez1 === ez2 && lz1 === lz2 && ez1 === lz1) {
        const eMin = Math.min(ex1, ex2),
            eMax = Math.max(ex1, ex2);
        const lMin = Math.min(lx1, lx2),
            lMax = Math.max(lx1, lx2);
        return eMin >= lMin && eMax <= lMax;
    }
    return false;
}

function normalizeEdgeKey(x1, z1, x2, z2) {
    if (x1 < x2 || (x1 === x2 && z1 < z2)) {
        return `${x1},${z1},${x2},${z2}`;
    } else {
        return `${x2},${z2},${x1},${z1}`;
    }
}

function updateProgress(percent, message) {
    document.getElementById('progressFill').style.width = percent + '%';
}

function downloadMap() {
    const mapWidth = Math.max(...worldBoundary.vertices.map(v => v.x)) + 1;
    const mapHeight = Math.max(...worldBoundary.vertices.map(v => v.z)) + 1;

    let output = `[SIZE]\n${mapWidth} ${mapHeight}\n`;
    output += '[SECTORS]\n';

    for (const sector of finalSectors) {
        // Sector(ID, floor_height, ceil_height, floor_texture, floor_brightness, floor_tiled, floor_skip_texture, ceil_texture, ceil_brightness, ceil_tiled, ceil_skip_texture)
        output += `${sector.id} ${sector.floorHeight} ${sector.ceilingHeight} ${sector.floorTexture} ${sector.floorBrightness} ${sector.floorTiled} ${sector.floorSkipTexture} ${sector.ceilingTexture} ${sector.ceilingBrightness} ${sector.ceilingTiled} ${sector.ceilingSkipTexture}\n`;
    }

    output += '[WALLS]\n';
    for (const wall of finalWalls) {
        // Wall(x1, z1, x2, z2, sector_a, wall_texture, wall_brightness, wall_tiled, skip_wall_texture)
        output += `${wall.x1} ${wall.z1} ${wall.x2} ${wall.z2} ${wall.sectorId} ${wall.texture} ${wall.brightness} ${wall.tiled} ${wall.skipTexture}\n`;
    }

    output += '[PORTALS]\n';
    for (const portal of finalPortals) {
        // Portal(x1, z1, x2, z2, sector_a, sector_b, bottom_texture, bottom_brightness, bottom_tiled, bottom_skip_texture, middle_texture, middle_brightness, middle_tiled, middle_skip_texture, top_texture, top_brightness, top_tiled, top_skip_texture)
        output += `${portal.x1} ${portal.z1} ${portal.x2} ${portal.z2} ${portal.sectorA} ${portal.sectorB} ${portal.botTexture} ${portal.botBrightness} ${portal.botTiled} ${portal.botSkip} ${portal.midTexture} ${portal.midBrightness} ${portal.midTiled} ${portal.midSkip} ${portal.topTexture} ${portal.topBrightness} ${portal.topTiled} ${portal.topSkip}\n`;
    }

    const blob = new Blob([output], {
        type: 'text/plain'
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'map.txt';
    a.click();
    URL.revokeObjectURL(url);
}

function loadTexture(textureName) {
    if (textureCache.has(textureName)) return textureCache.get(textureName);

    const entry = {
        img: null,
        pattern: null,
        failed: false
    };
    textureCache.set(textureName, entry);

    const img = new Image();
    img.onload = () => {
        entry.img = img;
        draw();
    };
    img.onerror = () => {
        entry.failed = true;
    };
    img.src = 'tex/' + textureName;

    return entry;
}

function draw() {
    ctx.fillStyle = '#0a0a0a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.strokeStyle = '#1a1a1a';
    ctx.lineWidth = 1;

    // Draw grid lines only in positive quadrant
    const originScreen = gridToScreen(0, 0);

    // Vertical lines (constant X)
    const startGridX = Math.max(0, Math.floor((0 - panX) / (gridSize * zoom)));
    const endGridX = Math.ceil((canvas.width - panX) / (gridSize * zoom));
    for (let i = startGridX; i <= endGridX; i++) {
        const sx = gridToScreen(i, 0).x;
        ctx.beginPath();
        ctx.moveTo(sx, 0);
        ctx.lineTo(sx, canvas.height);
        ctx.stroke();
    }

    // Horizontal lines (constant Z)
    // Convert screen top and bottom to grid Z, clamp to >= 0
    const screenTopZ = screenToGrid(0, 0).z;
    const screenBotZ = screenToGrid(0, canvas.height).z;
    const startGridZ = Math.max(0, Math.floor(screenBotZ) - 1);
    const endGridZ = Math.ceil(screenTopZ) + 1;
    for (let j = startGridZ; j <= endGridZ; j++) {
        const sy = gridToScreen(0, j).y;
        ctx.beginPath();
        ctx.moveTo(0, sy);
        ctx.lineTo(canvas.width, sy);
        ctx.stroke();
    }

    // Draw axes
    ctx.strokeStyle = '#0f0';
    ctx.lineWidth = 2;
    // X axis (Z=0, horizontal)
    ctx.beginPath();
    ctx.moveTo(originScreen.x, originScreen.y);
    ctx.lineTo(canvas.width, originScreen.y);
    ctx.stroke();
    // Z axis (X=0, vertical going up)
    ctx.beginPath();
    ctx.moveTo(originScreen.x, originScreen.y);
    ctx.lineTo(originScreen.x, 0);
    ctx.stroke();

    if (mode === 'partitioned') {
        for (const sector of finalSectors) {
            const isVoid = sector.sourceRect === 'void';
            ctx.fillStyle = isVoid ? 'rgba(100, 100, 100, 0.2)' : 'rgba(0, 255, 0, 0.2)';

            ctx.beginPath();
            const p1 = gridToScreen(sector.minX, sector.minZ);
            const p2 = gridToScreen(sector.maxX, sector.minZ);
            const p3 = gridToScreen(sector.maxX, sector.maxZ);
            const p4 = gridToScreen(sector.minX, sector.maxZ);
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.lineTo(p3.x, p3.y);
            ctx.lineTo(p4.x, p4.y);
            ctx.closePath();
            ctx.fill();

            ctx.strokeStyle = '#ff0';
            ctx.lineWidth = 1;
            ctx.stroke();

            const centerX = (sector.minX + sector.maxX) / 2;
            const centerZ = (sector.minZ + sector.maxZ) / 2;
            const screenCenter = gridToScreen(centerX, centerZ);
            ctx.fillStyle = '#fff';
            ctx.font = '12px Courier New';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(sector.id, screenCenter.x, screenCenter.y);
        }

        ctx.strokeStyle = '#f00';
        ctx.lineWidth = 2;
        for (const wall of finalWalls) {
            const p1 = gridToScreen(wall.x1, wall.z1);
            const p2 = gridToScreen(wall.x2, wall.z2);
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.stroke();
        }
    } else {
        ctx.strokeStyle = '#ff0';
        ctx.lineWidth = 2;
        for (let i = 0; i < userRectangles.length; i++) {
            const rect = userRectangles[i];

            // Build polygon path from vertices
            const screenVerts = rect.vertices.map(v => gridToScreen(v.x, v.z));
            ctx.beginPath();
            ctx.moveTo(screenVerts[0].x, screenVerts[0].y);
            for (let k = 1; k < screenVerts.length; k++) {
                ctx.lineTo(screenVerts[k].x, screenVerts[k].y);
            }
            ctx.closePath();

            // Fill with floor texture if available, else dark fallback
            const texName = rect.floorTexture;
            if (texName) {
                const entry = loadTexture(texName);
                if (entry.img) {
                    if (!entry.pattern || entry._patternZoom !== zoom) {
                        // Scale the pattern so one texture tile = one grid cell
                        const offscreen = document.createElement('canvas');
                        const tileSize = Math.max(1, Math.round(gridSize * zoom));
                        offscreen.width = tileSize;
                        offscreen.height = tileSize;
                        const octx = offscreen.getContext('2d');
                        octx.drawImage(entry.img, 0, 0, tileSize, tileSize);
                        entry.pattern = ctx.createPattern(offscreen, 'repeat');
                        entry._patternZoom = zoom;
                    }
                    // Offset the pattern so it aligns to the grid origin
                    const originScreen = gridToScreen(0, 0);
                    const matrix = new DOMMatrix();
                    matrix.translateSelf(originScreen.x % (gridSize * zoom), originScreen.y % (gridSize * zoom));
                    entry.pattern.setTransform(matrix);
                    ctx.fillStyle = entry.pattern;
                } else {
                    ctx.fillStyle = '#111';
                }
            } else {
                ctx.fillStyle = '#111';
            }
            ctx.fill();

            // Highlight selected rectangle in red
            if (i === selectedRectIndex) {
                ctx.strokeStyle = '#f00';
                ctx.lineWidth = 3;
            } else {
                ctx.strokeStyle = '#ff0';
                ctx.lineWidth = 2;
            }

            for (const line of rect.lines) {
                const p1 = gridToScreen(line.x1, line.z1);
                const p2 = gridToScreen(line.x2, line.z2);
                ctx.beginPath();
                ctx.moveTo(p1.x, p1.y);
                ctx.lineTo(p2.x, p2.y);
                ctx.stroke();
            }
        }

        if (worldBoundary) {
            ctx.strokeStyle = '#f00';
            ctx.lineWidth = 3;
            for (const line of worldBoundary.lines) {
                const p1 = gridToScreen(line.x1, line.z1);
                const p2 = gridToScreen(line.x2, line.z2);
                ctx.beginPath();
                ctx.moveTo(p1.x, p1.y);
                ctx.lineTo(p2.x, p2.y);
                ctx.stroke();
            }
        }

        ctx.strokeStyle = '#0ff';
        ctx.lineWidth = 2;
        for (const line of currentShape) {
            const p1 = gridToScreen(line.x1, line.z1);
            const p2 = gridToScreen(line.x2, line.z2);
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.stroke();
        }

        if (drawingLine && lineStart && currentMousePos) {
            let snapEnd = {
                ...currentMousePos
            };

            if (snapEnd.x !== lineStart.x && snapEnd.z !== lineStart.z) {
                const dx = Math.abs(snapEnd.x - lineStart.x);
                const dz = Math.abs(snapEnd.z - lineStart.z);
                if (dx > dz) {
                    snapEnd.z = lineStart.z;
                } else {
                    snapEnd.x = lineStart.x;
                }
            }

            const p1 = gridToScreen(lineStart.x, lineStart.z);
            const p2 = gridToScreen(snapEnd.x, snapEnd.z);

            ctx.strokeStyle = '#0ff';
            ctx.lineWidth = 2;
            ctx.setLineDash([5, 5]);
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.stroke();
            ctx.setLineDash([]);
        }

        if (currentMousePos) {
            const p = gridToScreen(currentMousePos.x, currentMousePos.z);
            ctx.fillStyle = '#0ff';
            ctx.beginPath();
            ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
            ctx.fill();
        }

        if (shapeStartVertex) {
            const p = gridToScreen(shapeStartVertex.x, shapeStartVertex.z);
            ctx.fillStyle = '#f0f';
            ctx.beginPath();
            ctx.arc(p.x, p.y, 6, 0, Math.PI * 2);
            ctx.fill();
        }
    }
}