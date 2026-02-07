# Raccoon Editor

A web-based level editor for creating sector-based game maps using Manhattan Partitioning algorithm.

## Features

- **Grid-based drawing** with orthogonal line snapping
- **Sector configuration** with floor/ceiling and wall properties
- **Manhattan Partitioning** - automatically subdivides your level into a clean grid
- **Save/Load projects** to continue editing later
- **Export to map.txt** format for game engines

## Quick Start

### 1. Main Menu
- **NEW PROJECT** - Start a fresh level
- **LOAD PROJECT** - Continue a saved project (.json)

### 2. Drawing Rectangles
- **Left-click** to place vertices (horizontal/vertical lines only)
- **Click on start vertex** (magenta dot) to complete the rectangle
- **Right-click + drag** to pan the view
- **Mouse wheel** to zoom in/out
- **Delete/Backspace** to undo last vertex while drawing

### 3. Configure Rectangles
After completing each rectangle, a config panel opens:

**Floor/Ceiling Properties:**
- Height, texture, brightness

**Wall Properties (North/South/East/West):**
- Top/Middle/Bottom textures and brightness
- Solid flag: `1` = blocking, `0` = passthrough

Default: All walls are portals with `black.png` texture and passthrough.

### 4. Selecting & Deleting
- **Click inside** a completed rectangle to select it (highlights red)
- **Delete/Backspace** to remove selected rectangle
- **Click empty space** to deselect

### 5. Save Your Work
- **SAVE PROJECT** button - Download project.json
- Contains all rectangles and configurations
- Can be loaded later to continue editing

### 6. Define World Boundary
When ready to finalize:
1. Click **DEFINE WORLD BOUNDARY**
2. Draw a large rectangle encompassing all your sectors
3. Configure wall properties (texture/brightness only - these are solid walls)
4. Manhattan Partitioning runs automatically

### 7. Manhattan Partitioning
The algorithm:
- Extends all rectangle edges to the world boundary
- Creates a grid of smaller sectors
- Each sector inherits properties from its source rectangle
- Void spaces (empty areas) get world boundary properties
- Edges are classified as walls (boundary) or portals (internal)

### 8. Export
- **DOWNLOAD MAP.TXT** - Final level data in the format:
  ```
  [SECTORS]
  id floor_height ceiling_height floor_texture floor_brightness ceiling_texture ceiling_brightness
  
  [WALLS]
  x1 y1 x2 y2 sector_id texture brightness
  
  [PORTALS]
  x1 y1 x2 y2 sectorA sectorB bottom_texture bottom_brightness middle_texture middle_brightness top_texture top_brightness solid
  ```

## Controls

| Action | Input |
|--------|-------|
| Place vertex | Left-click |
| Complete shape | Click start vertex |
| Pan view | Right-click + drag |
| Zoom | Mouse wheel |
| Delete vertex/rectangle | Delete or Backspace |
| Select rectangle | Click inside it |

## Workflow Example

1. Start NEW PROJECT
2. Draw multiple rectangles for your rooms/areas
3. Configure each with desired textures and portal properties
4. **SAVE PROJECT** periodically
5. When done, click **DEFINE WORLD BOUNDARY**
6. Draw outer boundary and configure walls
7. Wait for Manhattan Partitioning
8. **DOWNLOAD MAP.TXT**

## Tips

- **Non-overlapping rectangles**: User rectangles shouldn't overlap (except world boundary)
- **World boundary**: Should encompass all rectangles
- **Portal properties**: Set before partitioning - they're inherited by subdivided sectors
- **Void spaces**: Areas inside world boundary but outside rectangles get default properties
- **Save often**: Use SAVE PROJECT to avoid losing work

## File Formats

**project.json** - Editor save file
- Contains `userRectangles` array with all configurations
- Load this to continue editing

**map.txt** - Final game map
- Sectors, walls, and portals in parseable format
- Ready for game engine import

## Technical Notes

- Grid size: 32 units
- Coordinates: Origin at center, Y-up
- Edge normalization: Lower X first, if equal then lower Y first
- Manhattan Partitioning runs on world boundary definition
- World boundary is never saved in project files (only final map.txt)
- If a wall or portal overlaps with another one, the one that gets drawn is the one we drew first on that overlapping part