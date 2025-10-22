const menu = document.getElementById('menu');
const editor = document.getElementById('editor');
const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

let zoom = 1.0;
let panX = 0;
let panY = 0;
let gridSize = 32;

let lines = [];
let drawingLine = false;
let lineStart = null;
let currentMousePos = null;
let currentShape = [];
let completedShapes = [];
let shapeStartVertex = null;

let finalized = false;
let sectors = [];
let hoveredSector = null;
let currentConfigSector = null;
let loadedMapData = null;
let selectedShape = null;

document.getElementById('newMapBtn').addEventListener('click', () => {
    menu.style.display = 'none';
    editor.style.display = 'block';
    initEditor();
});

document.getElementById('loadMapBtn').addEventListener('click', () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.txt';
    input.onchange = e => {
        const file = e.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = event => {
                try {
                    loadedMapData = parseMapFile(event.target.result);
                    menu.style.display = 'none';
                    document.getElementById('loadDialog').style.display = 'block';
                } catch (error) {
                    console.error('Error parsing map file:', error);
                    alert('Error loading map file. Check console for details.');
                }
            };
            reader.readAsText(file);
        }
    };
    input.click();
});

document.getElementById('loadDrawingBtn').addEventListener('click', () => {
    document.getElementById('loadDialog').style.display = 'none';
    editor.style.display = 'block';
    initEditor();
    loadMapInDrawingMode(loadedMapData);
});

document.getElementById('loadFinalizedBtn').addEventListener('click', () => {
    document.getElementById('loadDialog').style.display = 'none';
    editor.style.display = 'block';
    initEditor();
    loadMapInFinalizedMode(loadedMapData);
});

document.getElementById('cancelLoadBtn').addEventListener('click', () => {
    document.getElementById('loadDialog').style.display = 'none';
    menu.style.display = 'block';
    loadedMapData = null;
});

function initEditor() {
    resizeCanvas();
    window.addEventListener('resize', resizeCanvas);
    canvas.addEventListener('wheel', handleZoom);
    canvas.addEventListener('mousedown', handleMouseDown);
    canvas.addEventListener('mousemove', handleMouseMove);
    canvas.addEventListener('mouseup', handleMouseUp);
    canvas.addEventListener('contextmenu', e => e.preventDefault());
    window.addEventListener('keydown', handleKeyDown);
    
    document.getElementById('finalizeBtn').addEventListener('click', finalizeMap);
    document.getElementById('saveBtn').addEventListener('click', saveMap);
    document.getElementById('saveConfig').addEventListener('click', saveConfiguration);
    document.getElementById('cancelConfig').addEventListener('click', () => {
        document.getElementById('configPanel').style.display = 'none';
    });
    
    draw();
}

function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    draw();
}

function screenToGrid(screenX, screenY) {
    const worldX = (screenX - canvas.width / 2 - panX) / zoom;
    const worldY = -(screenY - canvas.height / 2 - panY) / zoom;
    return {
        x: Math.round(worldX / gridSize),
        y: Math.round(worldY / gridSize)
    };
}

function gridToScreen(gridX, gridY) {
    const worldX = gridX * gridSize;
    const worldY = -gridY * gridSize;
    return {
        x: worldX * zoom + canvas.width / 2 + panX,
        y: worldY * zoom + canvas.height / 2 + panY
    };
}

function handleZoom(e) {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    zoom *= delta;
    zoom = Math.max(0.1, Math.min(10, zoom));
    document.getElementById('zoomLevel').textContent = zoom.toFixed(1);
    draw();
}

function handleKeyDown(e) {
    // Don't intercept keys if user is typing in an input field
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
        return;
    }
    
    if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault();
        
        // If a shape is selected, delete it
        if (selectedShape !== null) {
            completedShapes.splice(selectedShape, 1);
            selectedShape = null;
            document.getElementById('lineCount').textContent = completedShapes.length;
            
            if (completedShapes.length === 0) {
                document.getElementById('finalizeBtn').style.display = 'none';
            }
            draw();
            return;
        }
        
        // Otherwise, delete from current shape or last completed shape
        if (currentShape.length > 0) {
            currentShape.pop();
            document.getElementById('currentLines').textContent = currentShape.length;
            
            if (currentShape.length === 0) {
                shapeStartVertex = null;
                lineStart = null;
                drawingLine = false;
            }
            draw();
        }
        else if (completedShapes.length > 0) {
            completedShapes.pop();
            document.getElementById('lineCount').textContent = completedShapes.length;
            
            if (completedShapes.length === 0) {
                document.getElementById('finalizeBtn').style.display = 'none';
            }
            draw();
        }
    }
}

function handleMouseDown(e) {
    if (e.button === 0) {
        if (finalized) {
            if (hoveredSector !== null) {
                openConfigPanel(hoveredSector);
            }
            return;
        }
        
        const gridPos = screenToGrid(e.clientX, e.clientY);
        
        // Check if clicking on starting vertex to complete shape
        const clickingStartVertex = shapeStartVertex && 
            gridPos.x === shapeStartVertex.x && 
            gridPos.y === shapeStartVertex.y &&
            currentShape.length > 0;
        
        if (clickingStartVertex) {
            completedShapes.push([...currentShape]);
            currentShape = [];
            shapeStartVertex = null;
            lineStart = null;
            drawingLine = false;
            document.getElementById('lineCount').textContent = completedShapes.length;
            document.getElementById('currentLines').textContent = 0;
            
            if (completedShapes.length > 0) {
                document.getElementById('finalizeBtn').style.display = 'block';
            }
            
            draw();
            return;
        }
        
        // Only check for shape selection if we're not currently drawing
        if (currentShape.length === 0) {
            const clickPos = {x: e.clientX, y: e.clientY};
            let clickedShape = null;
            
            for (let i = 0; i < completedShapes.length; i++) {
                if (isPointInShape(clickPos, completedShapes[i])) {
                    clickedShape = i;
                    break;
                }
            }
            
            if (clickedShape !== null) {
                selectedShape = clickedShape;
                draw();
                return;
            }
        }
        
        // Deselect if clicking elsewhere while a shape is selected
        if (selectedShape !== null) {
            selectedShape = null;
        }
        
        // Start or continue drawing
        if (currentShape.length > 0) {
            const lastLine = currentShape[currentShape.length - 1];
            lineStart = {x: lastLine.x2, y: lastLine.y2};
        } else {
            lineStart = gridPos;
            shapeStartVertex = gridPos;
        }
        
        drawingLine = true;
    } else if (e.button === 2) {
        canvas.style.cursor = 'grabbing';
        const startPanX = panX;
        const startPanY = panY;
        const startX = e.clientX;
        const startY = e.clientY;
        
        function pan(e) {
            panX = startPanX + (e.clientX - startX);
            panY = startPanY + (e.clientY - startY);
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

function handleMouseMove(e) {
    const gridPos = screenToGrid(e.clientX, e.clientY);
    currentMousePos = gridPos;
    document.getElementById('mouseX').textContent = gridPos.x;
    document.getElementById('mouseY').textContent = gridPos.y;
    
    if (finalized) {
        const screenPos = {x: e.clientX, y: e.clientY};
        hoveredSector = null;
        for (let i = 0; i < sectors.length; i++) {
            if (isPointInSector(screenPos, sectors[i])) {
                hoveredSector = i;
                break;
            }
        }
        draw();
        return;
    }
    
    if (drawingLine) {
        draw();
    }
}

function isPointInShape(screenPos, shape) {
    const gridPos = screenToGrid(screenPos.x, screenPos.y);
    
    // Get vertices from shape
    const vertices = [];
    const vertexSet = new Set();
    
    for (const line of shape) {
        const v1Key = `${line.x1},${line.y1}`;
        const v2Key = `${line.x2},${line.y2}`;
        
        if (!vertexSet.has(v1Key)) {
            vertices.push({x: line.x1, y: line.y1});
            vertexSet.add(v1Key);
        }
        if (!vertexSet.has(v2Key)) {
            vertices.push({x: line.x2, y: line.y2});
            vertexSet.add(v2Key);
        }
    }
    
    // Point-in-polygon test
    let inside = false;
    for (let i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
        const xi = vertices[i].x, yi = vertices[i].y;
        const xj = vertices[j].x, yj = vertices[j].y;
        
        const intersect = ((yi > gridPos.y) !== (yj > gridPos.y))
            && (gridPos.x < (xj - xi) * (gridPos.y - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
    }
    return inside;
}

function handleMouseUp(e) {
    if (e.button === 0 && drawingLine) {
        const gridEnd = screenToGrid(e.clientX, e.clientY);
        
        if (gridEnd.x !== lineStart.x && gridEnd.y !== lineStart.y) {
            const dx = Math.abs(gridEnd.x - lineStart.x);
            const dy = Math.abs(gridEnd.y - lineStart.y);
            if (dx > dy) {
                gridEnd.y = lineStart.y;
            } else {
                gridEnd.x = lineStart.x;
            }
        }
        
        if (gridEnd.x !== lineStart.x || gridEnd.y !== lineStart.y) {
            currentShape.push({
                x1: lineStart.x,
                y1: lineStart.y,
                x2: gridEnd.x,
                y2: gridEnd.y
            });
            document.getElementById('currentLines').textContent = currentShape.length;
        }
        
        drawingLine = false;
        draw();
    }
}

function draw() {
    ctx.fillStyle = '#0a0a0a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    ctx.strokeStyle = '#1a1a1a';
    ctx.lineWidth = 1;
    
    const centerX = canvas.width / 2 + panX;
    const centerY = canvas.height / 2 + panY;
    const scaledGrid = gridSize * zoom;
    
    const startX = Math.floor(-centerX / scaledGrid) - 1;
    const endX = Math.ceil((canvas.width - centerX) / scaledGrid) + 1;
    const startY = Math.floor(-centerY / scaledGrid) - 1;
    const endY = Math.ceil((canvas.height - centerY) / scaledGrid) + 1;
    
    for (let i = startX; i <= endX; i++) {
        const x = centerX + i * scaledGrid;
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, canvas.height);
        ctx.stroke();
    }
    
    for (let i = startY; i <= endY; i++) {
        const y = centerY + i * scaledGrid;
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(canvas.width, y);
        ctx.stroke();
    }
    
    ctx.strokeStyle = '#0f0';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(centerX, 0);
    ctx.lineTo(centerX, canvas.height);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(0, centerY);
    ctx.lineTo(canvas.width, centerY);
    ctx.stroke();
    
    if (!finalized) {
        ctx.strokeStyle = '#ff0';
        ctx.lineWidth = 2;
        for (let i = 0; i < completedShapes.length; i++) {
            const shape = completedShapes[i];
            
            // Highlight selected shape
            if (i === selectedShape) {
                ctx.strokeStyle = '#f00';
                ctx.lineWidth = 3;
            } else {
                ctx.strokeStyle = '#ff0';
                ctx.lineWidth = 2;
            }
            
            for (const line of shape) {
                const p1 = gridToScreen(line.x1, line.y1);
                const p2 = gridToScreen(line.x2, line.y2);
                ctx.beginPath();
                ctx.moveTo(p1.x, p1.y);
                ctx.lineTo(p2.x, p2.y);
                ctx.stroke();
            }
        }
    } else {
        for (let i = 0; i < sectors.length; i++) {
            const sector = sectors[i];
            
            if (i === hoveredSector) {
                ctx.fillStyle = 'rgba(0, 255, 255, 0.3)';
            } else if (sector.configured) {
                ctx.fillStyle = 'rgba(0, 255, 0, 0.2)';
            } else {
                ctx.fillStyle = 'rgba(255, 255, 0, 0.2)';
            }
            
            ctx.beginPath();
            const firstPoint = gridToScreen(sector.vertices[0].x, sector.vertices[0].y);
            ctx.moveTo(firstPoint.x, firstPoint.y);
            for (let j = 1; j < sector.vertices.length; j++) {
                const point = gridToScreen(sector.vertices[j].x, sector.vertices[j].y);
                ctx.lineTo(point.x, point.y);
            }
            ctx.closePath();
            ctx.fill();
            
            ctx.strokeStyle = '#ff0';
            ctx.lineWidth = 2;
            ctx.stroke();
            
            const center = getSectorCenter(sector);
            const screenCenter = gridToScreen(center.x, center.y);
            ctx.fillStyle = '#fff';
            ctx.font = '16px Courier New';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(sector.id, screenCenter.x, screenCenter.y);
        }
    }
    
    ctx.strokeStyle = '#0ff';
    ctx.lineWidth = 2;
    for (const line of currentShape) {
        const p1 = gridToScreen(line.x1, line.y1);
        const p2 = gridToScreen(line.x2, line.y2);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();
    }
    
    if (drawingLine && lineStart && currentMousePos && !finalized) {
        let snapEnd = {...currentMousePos};
        
        if (snapEnd.x !== lineStart.x && snapEnd.y !== lineStart.y) {
            const dx = Math.abs(snapEnd.x - lineStart.x);
            const dy = Math.abs(snapEnd.y - lineStart.y);
            if (dx > dy) {
                snapEnd.y = lineStart.y;
            } else {
                snapEnd.x = lineStart.x;
            }
        }
        
        const p1 = gridToScreen(lineStart.x, lineStart.y);
        const p2 = gridToScreen(snapEnd.x, snapEnd.y);
        
        ctx.strokeStyle = '#0ff';
        ctx.lineWidth = 2;
        ctx.setLineDash([5, 5]);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();
        ctx.setLineDash([]);
    }
    
    if (currentMousePos && !finalized) {
        const p = gridToScreen(currentMousePos.x, currentMousePos.y);
        ctx.fillStyle = '#0ff';
        ctx.beginPath();
        ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
        ctx.fill();
    }
    
    if (shapeStartVertex && !finalized) {
        const p = gridToScreen(shapeStartVertex.x, shapeStartVertex.y);
        ctx.fillStyle = '#f0f';
        ctx.beginPath();
        ctx.arc(p.x, p.y, 6, 0, Math.PI * 2);
        ctx.fill();
    }
}

function finalizeMap() {
    if (completedShapes.length === 0) return;
    
    finalized = true;
    document.getElementById('finalizeBtn').style.display = 'none';
    document.getElementById('saveBtn').style.display = 'block';
    
    sectors = detectSectors();
    classifyWalls();
    
    draw();
}

function detectSectors() {
    const detectedSectors = [];
    let sectorId = 1;
    
    for (const shape of completedShapes) {
        const vertices = [];
        const vertexSet = new Set();
        
        for (const line of shape) {
            const v1Key = `${line.x1},${line.y1}`;
            const v2Key = `${line.x2},${line.y2}`;
            
            if (!vertexSet.has(v1Key)) {
                vertices.push({x: line.x1, y: line.y1});
                vertexSet.add(v1Key);
            }
            if (!vertexSet.has(v2Key)) {
                vertices.push({x: line.x2, y: line.y2});
                vertexSet.add(v2Key);
            }
        }
        
        vertices.sort((a, b) => {
            if (a.x !== b.x) return a.x - b.x;
            return a.y - b.y;
        });
        
        if (vertices.length === 4) {
            const sorted = [
                vertices[0],
                vertices[2],
                vertices[3],
                vertices[1]
            ];
            vertices.splice(0, 4, ...sorted);
        }
        
        detectedSectors.push({
            id: sectorId++,
            vertices: vertices,
            walls: [...shape],
            floorHeight: -2,
            ceilingHeight: 2,
            floorTexture: 'floor.png',
            floorBrightness: 1.0,
            ceilingTexture: 'ceiling.png',
            ceilingBrightness: 1.0,
            wallData: {},
            configured: false
        });
    }
    
    return detectedSectors;
}

function classifyWalls() {
    const wallMap = new Map();
    
    for (let i = 0; i < sectors.length; i++) {
        const sector = sectors[i];
        for (const wall of sector.walls) {
            const key = normalizeWallKey(wall.x1, wall.y1, wall.x2, wall.y2);
            if (!wallMap.has(key)) {
                wallMap.set(key, []);
            }
            wallMap.get(key).push(i);
        }
    }
    
    for (let i = 0; i < sectors.length; i++) {
        const sector = sectors[i];
        for (const wall of sector.walls) {
            const key = normalizeWallKey(wall.x1, wall.y1, wall.x2, wall.y2);
            const sectorIndices = wallMap.get(key);
            
            if (sectorIndices.length === 1) {
                sector.wallData[key] = {
                    type: 'wall',
                    texture: 'wall.png',
                    brightness: 1.0
                };
            } else if (sectorIndices.length === 2) {
                const otherSectorIdx = sectorIndices[0] === i ? sectorIndices[1] : sectorIndices[0];
                sector.wallData[key] = {
                    type: 'portal',
                    otherSector: sectors[otherSectorIdx].id,
                    bottomTexture: 'wall.png',
                    bottomBrightness: 1.0,
                    middleTexture: 'transparent.png',
                    middleBrightness: 1.0,
                    topTexture: 'wall.png',
                    topBrightness: 1.0,
                    solid: 0
                };
            }
        }
    }
}

function normalizeWallKey(x1, y1, x2, y2) {
    if (x1 < x2 || (x1 === x2 && y1 < y2)) {
        return `${x1},${y1},${x2},${y2}`;
    } else {
        return `${x2},${y2},${x1},${y1}`;
    }
}

function isPointInSector(screenPos, sector) {
    const gridPos = screenToGrid(screenPos.x, screenPos.y);
    
    let inside = false;
    const vertices = sector.vertices;
    for (let i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
        const xi = vertices[i].x, yi = vertices[i].y;
        const xj = vertices[j].x, yj = vertices[j].y;
        
        const intersect = ((yi > gridPos.y) !== (yj > gridPos.y))
            && (gridPos.x < (xj - xi) * (gridPos.y - yi) / (yj - yi) + xi);
        if (intersect) inside = !inside;
    }
    return inside;
}

function getSectorCenter(sector) {
    let sumX = 0, sumY = 0;
    for (const v of sector.vertices) {
        sumX += v.x;
        sumY += v.y;
    }
    return {
        x: sumX / sector.vertices.length,
        y: sumY / sector.vertices.length
    };
}

function openConfigPanel(sectorIdx) {
    currentConfigSector = sectorIdx;
    const sector = sectors[sectorIdx];
    
    document.getElementById('sectorNum').textContent = sector.id;
    document.getElementById('floorHeight').value = sector.floorHeight;
    document.getElementById('ceilingHeight').value = sector.ceilingHeight;
    document.getElementById('floorTexture').value = sector.floorTexture;
    document.getElementById('floorBrightness').value = sector.floorBrightness;
    document.getElementById('ceilingTexture').value = sector.ceilingTexture;
    document.getElementById('ceilingBrightness').value = sector.ceilingBrightness;
    
    const wallsContainer = document.getElementById('wallsContainer');
    wallsContainer.innerHTML = '';
    
    for (const wall of sector.walls) {
        const key = normalizeWallKey(wall.x1, wall.y1, wall.x2, wall.y2);
        const wallData = sector.wallData[key];
        
        const wallSection = document.createElement('div');
        wallSection.className = 'wall-section';
        
        const coords = key.split(',');
        wallSection.innerHTML = `<h3>Wall (${coords[0]},${coords[1]}) to (${coords[2]},${coords[3]})</h3>`;
        
        if (wallData.type === 'wall') {
            wallSection.innerHTML += `
                <label>Texture:</label>
                <input type="text" class="wall-texture" data-key="${key}" value="${wallData.texture}">
                <label>Brightness:</label>
                <input type="number" class="wall-brightness" data-key="${key}" value="${wallData.brightness}" step="0.1" min="0">
            `;
        } else if (wallData.type === 'portal') {
            wallSection.innerHTML += `
                <p>Portal to Sector ${wallData.otherSector}</p>
                <label>Bottom Texture:</label>
                <input type="text" class="portal-bottom-texture" data-key="${key}" value="${wallData.bottomTexture}">
                <label>Bottom Brightness:</label>
                <input type="number" class="portal-bottom-brightness" data-key="${key}" value="${wallData.bottomBrightness}" step="0.1" min="0">
                <label>Middle Texture:</label>
                <input type="text" class="portal-middle-texture" data-key="${key}" value="${wallData.middleTexture}">
                <label>Middle Brightness:</label>
                <input type="number" class="portal-middle-brightness" data-key="${key}" value="${wallData.middleBrightness}" step="0.1" min="0">
                <label>Top Texture:</label>
                <input type="text" class="portal-top-texture" data-key="${key}" value="${wallData.topTexture}">
                <label>Top Brightness:</label>
                <input type="number" class="portal-top-brightness" data-key="${key}" value="${wallData.topBrightness}" step="0.1" min="0">
                <label>Solid (1) or Passthrough (0):</label>
                <input type="number" class="portal-solid" data-key="${key}" value="${wallData.solid}" min="0" max="1" step="1">
            `;
        }
        
        wallsContainer.appendChild(wallSection);
    }
    
    document.getElementById('configPanel').style.display = 'block';
}

function saveConfiguration() {
    if (currentConfigSector === null) return;
    
    const sector = sectors[currentConfigSector];
    
    sector.floorHeight = parseFloat(document.getElementById('floorHeight').value);
    sector.ceilingHeight = parseFloat(document.getElementById('ceilingHeight').value);
    sector.floorTexture = document.getElementById('floorTexture').value;
    sector.floorBrightness = parseFloat(document.getElementById('floorBrightness').value);
    sector.ceilingTexture = document.getElementById('ceilingTexture').value;
    sector.ceilingBrightness = parseFloat(document.getElementById('ceilingBrightness').value);
    
    const wallTextures = document.querySelectorAll('.wall-texture');
    const wallBrightnesses = document.querySelectorAll('.wall-brightness');
    wallTextures.forEach((input, idx) => {
        const key = input.dataset.key;
        sector.wallData[key].texture = input.value;
        sector.wallData[key].brightness = parseFloat(wallBrightnesses[idx].value);
    });
    
    const portalBottomTextures = document.querySelectorAll('.portal-bottom-texture');
    const portalBottomBrightnesses = document.querySelectorAll('.portal-bottom-brightness');
    const portalMiddleTextures = document.querySelectorAll('.portal-middle-texture');
    const portalMiddleBrightnesses = document.querySelectorAll('.portal-middle-brightness');
    const portalTopTextures = document.querySelectorAll('.portal-top-texture');
    const portalTopBrightnesses = document.querySelectorAll('.portal-top-brightness');
    const portalSolids = document.querySelectorAll('.portal-solid');
    
    portalBottomTextures.forEach((input, idx) => {
        const key = input.dataset.key;
        sector.wallData[key].bottomTexture = input.value;
        sector.wallData[key].bottomBrightness = parseFloat(portalBottomBrightnesses[idx].value);
        sector.wallData[key].middleTexture = portalMiddleTextures[idx].value;
        sector.wallData[key].middleBrightness = parseFloat(portalMiddleBrightnesses[idx].value);
        sector.wallData[key].topTexture = portalTopTextures[idx].value;
        sector.wallData[key].topBrightness = parseFloat(portalTopBrightnesses[idx].value);
        sector.wallData[key].solid = parseInt(portalSolids[idx].value);
    });
    
    sector.configured = true;
    document.getElementById('configPanel').style.display = 'none';
    draw();
}

function saveMap() {
    let output = '[SECTORS]\n';
    
    for (const sector of sectors) {
        output += `${sector.id} ${sector.floorHeight} ${sector.ceilingHeight} ${sector.floorTexture} ${sector.floorBrightness} ${sector.ceilingTexture} ${sector.ceilingBrightness}\n`;
    }
    
    output += '[WALLS]\n';
    const processedWalls = new Set();
    
    for (const sector of sectors) {
        for (const wall of sector.walls) {
            const key = normalizeWallKey(wall.x1, wall.y1, wall.x2, wall.y2);
            const wallData = sector.wallData[key];
            
            if (wallData.type === 'wall' && !processedWalls.has(key)) {
                const coords = key.split(',').map(Number);
                output += `${coords[0]} ${coords[1]} ${coords[2]} ${coords[3]} ${sector.id} ${wallData.texture} ${wallData.brightness}\n`;
                processedWalls.add(key);
            }
        }
    }
    
    output += '[PORTALS]\n';
    const processedPortals = new Set();
    
    for (const sector of sectors) {
        for (const wall of sector.walls) {
            const key = normalizeWallKey(wall.x1, wall.y1, wall.x2, wall.y2);
            const wallData = sector.wallData[key];
            
            if (wallData.type === 'portal' && !processedPortals.has(key)) {
                const coords = key.split(',').map(Number);
                output += `${coords[0]} ${coords[1]} ${coords[2]} ${coords[3]} ${sector.id} ${wallData.otherSector} ${wallData.bottomTexture} ${wallData.bottomBrightness} ${wallData.middleTexture} ${wallData.middleBrightness} ${wallData.topTexture} ${wallData.topBrightness} ${wallData.solid}\n`;
                processedPortals.add(key);
            }
        }
    }
    
    const blob = new Blob([output], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'map.txt';
    a.click();
    URL.revokeObjectURL(url);
}

function parseMapFile(content) {
    const lines = content.trim().split('\n');
    const data = {
        sectors: [],
        walls: [],
        portals: []
    };
    
    let currentSection = null;
    
    for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        
        if (trimmed === '[SECTORS]') {
            currentSection = 'sectors';
        } else if (trimmed === '[WALLS]') {
            currentSection = 'walls';
        } else if (trimmed === '[PORTALS]') {
            currentSection = 'portals';
        } else if (currentSection === 'sectors') {
            const parts = trimmed.split(' ');
            data.sectors.push({
                id: parseInt(parts[0]),
                floorHeight: parseFloat(parts[1]),
                ceilingHeight: parseFloat(parts[2]),
                floorTexture: parts[3],
                floorBrightness: parseFloat(parts[4]),
                ceilingTexture: parts[5],
                ceilingBrightness: parseFloat(parts[6])
            });
        } else if (currentSection === 'walls') {
            const parts = trimmed.split(' ');
            data.walls.push({
                x1: parseInt(parts[0]),
                y1: parseInt(parts[1]),
                x2: parseInt(parts[2]),
                y2: parseInt(parts[3]),
                sectorId: parseInt(parts[4]),
                texture: parts[5],
                brightness: parseFloat(parts[6])
            });
        } else if (currentSection === 'portals') {
            const parts = trimmed.split(' ');
            data.portals.push({
                x1: parseInt(parts[0]),
                y1: parseInt(parts[1]),
                x2: parseInt(parts[2]),
                y2: parseInt(parts[3]),
                sectorA: parseInt(parts[4]),
                sectorB: parseInt(parts[5]),
                bottomTexture: parts[6],
                bottomBrightness: parseFloat(parts[7]),
                middleTexture: parts[8],
                middleBrightness: parseFloat(parts[9]),
                topTexture: parts[10],
                topBrightness: parseFloat(parts[11]),
                solid: parts[12] !== undefined ? parseInt(parts[12]) : 0
            });
        }
    }
    
    return data;
}

function loadMapInDrawingMode(mapData) {
    completedShapes = [];
    currentShape = [];
    shapeStartVertex = null;
    lineStart = null;
    drawingLine = false;
    selectedShape = null;
    finalized = false;
    
    // Group walls and portals by sector to reconstruct shapes
    const sectorWalls = new Map();
    
    for (const wall of mapData.walls) {
        if (!sectorWalls.has(wall.sectorId)) {
            sectorWalls.set(wall.sectorId, []);
        }
        sectorWalls.get(wall.sectorId).push({
            x1: wall.x1, y1: wall.y1, x2: wall.x2, y2: wall.y2
        });
    }
    
    for (const portal of mapData.portals) {
        if (!sectorWalls.has(portal.sectorA)) {
            sectorWalls.set(portal.sectorA, []);
        }
        sectorWalls.get(portal.sectorA).push({
            x1: portal.x1, y1: portal.y1, x2: portal.x2, y2: portal.y2
        });
        
        if (!sectorWalls.has(portal.sectorB)) {
            sectorWalls.set(portal.sectorB, []);
        }
        sectorWalls.get(portal.sectorB).push({
            x1: portal.x1, y1: portal.y1, x2: portal.x2, y2: portal.y2
        });
    }
    
    // Convert to completed shapes
    for (const [sectorId, walls] of sectorWalls) {
        completedShapes.push(walls);
    }
    
    document.getElementById('lineCount').textContent = completedShapes.length;
    if (completedShapes.length > 0) {
        document.getElementById('finalizeBtn').style.display = 'block';
    }
    
    draw();
}

function loadMapInFinalizedMode(mapData) {
    finalized = true;
    completedShapes = [];
    
    // Reconstruct shapes first
    const sectorWalls = new Map();
    
    for (const wall of mapData.walls) {
        if (!sectorWalls.has(wall.sectorId)) {
            sectorWalls.set(wall.sectorId, []);
        }
        sectorWalls.get(wall.sectorId).push({
            x1: wall.x1, y1: wall.y1, x2: wall.x2, y2: wall.y2
        });
    }
    
    for (const portal of mapData.portals) {
        if (!sectorWalls.has(portal.sectorA)) {
            sectorWalls.set(portal.sectorA, []);
        }
        sectorWalls.get(portal.sectorA).push({
            x1: portal.x1, y1: portal.y1, x2: portal.x2, y2: portal.y2
        });
        
        if (!sectorWalls.has(portal.sectorB)) {
            sectorWalls.set(portal.sectorB, []);
        }
        sectorWalls.get(portal.sectorB).push({
            x1: portal.x1, y1: portal.y1, x2: portal.x2, y2: portal.y2
        });
    }
    
    for (const [sectorId, walls] of sectorWalls) {
        completedShapes.push(walls);
    }
    
    // Detect sectors
    sectors = detectSectors();
    
    // Apply loaded sector data
    for (const sectorData of mapData.sectors) {
        const sector = sectors.find(s => s.id === sectorData.id);
        if (sector) {
            sector.floorHeight = sectorData.floorHeight;
            sector.ceilingHeight = sectorData.ceilingHeight;
            sector.floorTexture = sectorData.floorTexture;
            sector.floorBrightness = sectorData.floorBrightness;
            sector.ceilingTexture = sectorData.ceilingTexture;
            sector.ceilingBrightness = sectorData.ceilingBrightness;
            sector.configured = true;
        }
    }
    
    // Classify walls
    classifyWalls();
    
    // Apply wall data
    for (const wall of mapData.walls) {
        const sector = sectors.find(s => s.id === wall.sectorId);
        if (sector) {
            const key = normalizeWallKey(wall.x1, wall.y1, wall.x2, wall.y2);
            if (sector.wallData[key]) {
                sector.wallData[key].texture = wall.texture;
                sector.wallData[key].brightness = wall.brightness;
            }
        }
    }
    
    // Apply portal data
    for (const portal of mapData.portals) {
        const sectorA = sectors.find(s => s.id === portal.sectorA);
        const sectorB = sectors.find(s => s.id === portal.sectorB);
        const key = normalizeWallKey(portal.x1, portal.y1, portal.x2, portal.y2);
        
        if (sectorA && sectorA.wallData[key]) {
            sectorA.wallData[key].bottomTexture = portal.bottomTexture;
            sectorA.wallData[key].bottomBrightness = portal.bottomBrightness;
            sectorA.wallData[key].middleTexture = portal.middleTexture;
            sectorA.wallData[key].middleBrightness = portal.middleBrightness;
            sectorA.wallData[key].topTexture = portal.topTexture;
            sectorA.wallData[key].topBrightness = portal.topBrightness;
            sectorA.wallData[key].solid = portal.solid;
        }
        
        if (sectorB && sectorB.wallData[key]) {
            sectorB.wallData[key].bottomTexture = portal.bottomTexture;
            sectorB.wallData[key].bottomBrightness = portal.bottomBrightness;
            sectorB.wallData[key].middleTexture = portal.middleTexture;
            sectorB.wallData[key].middleBrightness = portal.middleBrightness;
            sectorB.wallData[key].topTexture = portal.topTexture;
            sectorB.wallData[key].topBrightness = portal.topBrightness;
            sectorB.wallData[key].solid = portal.solid;
        }
    }
    
    document.getElementById('finalizeBtn').style.display = 'none';
    document.getElementById('saveBtn').style.display = 'block';
    
    draw();
}