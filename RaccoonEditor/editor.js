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

        document.getElementById('newMapBtn').addEventListener('click', () => {
            menu.style.display = 'none';
            editor.style.display = 'block';
            initEditor();
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
            if (e.key === 'Delete' || e.key === 'Backspace') {
                e.preventDefault();
                
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
                
                if (shapeStartVertex && 
                    gridPos.x === shapeStartVertex.x && 
                    gridPos.y === shapeStartVertex.y &&
                    currentShape.length > 0) {
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
                for (const shape of completedShapes) {
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
                            topBrightness: 1.0
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
            
            portalBottomTextures.forEach((input, idx) => {
                const key = input.dataset.key;
                sector.wallData[key].bottomTexture = input.value;
                sector.wallData[key].bottomBrightness = parseFloat(portalBottomBrightnesses[idx].value);
                sector.wallData[key].middleTexture = portalMiddleTextures[idx].value;
                sector.wallData[key].middleBrightness = parseFloat(portalMiddleBrightnesses[idx].value);
                sector.wallData[key].topTexture = portalTopTextures[idx].value;
                sector.wallData[key].topBrightness = parseFloat(portalTopBrightnesses[idx].value);
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
                        output += `${coords[0]} ${coords[1]} ${coords[2]} ${coords[3]} ${sector.id} ${wallData.otherSector} ${wallData.bottomTexture} ${wallData.bottomBrightness} ${wallData.middleTexture} ${wallData.middleBrightness} ${wallData.topTexture} ${wallData.topBrightness}\n`;
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