# Raccoon Level Editor Documentation

## Table of Contents
1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Drawing Mode](#drawing-mode)
4. [Finalized Mode](#finalized-mode)
5. [Loading Maps](#loading-maps)
6. [Saving Maps](#saving-maps)
7. [Editor Usage Guide](#editor-usage-guide)
8. [Map Structure](#map-structure)

---

## Overview

The Raccoon Level Editor is a web-based 2D level editor designed for creating Doom-style maps composed of rectangular sectors. It uses a grid-based system with orthogonal (horizontal/vertical only) wall placement and supports multi-sector connectivity through portals.

**Key Features:**
- Grid-based drawing with snap-to-grid vertices
- Zoom and pan navigation
- Sector-based architecture (floors, ceilings, walls, portals)
- Texture and brightness configuration per surface
- Import/Export to text-based map format

---

## Getting Started

### Initial Screen
When you open the editor, you'll see two options:
- **NEW MAP**: Start creating a map from scratch
- **LOAD MAP**: Import an existing map file

### Navigation Controls
- **Mouse Wheel**: Zoom in/out (0.1x to 10x)
- **Right-Click + Drag**: Pan the viewport
- **Left-Click**: Draw lines or select objects (depending on mode)

### Grid System
- Origin at (0, 0) in the center of the screen
- Positive X extends right, Negative X extends left
- Positive Y extends UP, Negative Y extends DOWN (standard Cartesian coordinates)
- Grid snaps to whole number coordinates

---

## Drawing Mode

### Creating Shapes
Maps are built from closed rectangular shapes (sectors). Each shape must be a closed loop.

**Drawing Process:**
1. **Click** on a grid vertex to start drawing
   - A magenta/pink dot marks your starting vertex
2. **Click** additional vertices to create connected lines
   - Lines automatically snap to horizontal or vertical
   - Current shape appears in cyan
3. **Click** back on the starting vertex (pink dot) to close the shape
   - Completed shapes turn yellow
   - You can now start a new shape

**Rules:**
- Lines must be horizontal or vertical (no diagonals)
- All lines snap to grid intersections
- Each shape must be closed before starting a new one
- You cannot "lift the pencil" until returning to the start

### Editing in Drawing Mode

**Deleting Lines:**
- **Delete/Backspace** while drawing: Removes the last line in your current shape
- Keep pressing to undo lines one by one

**Selecting & Deleting Shapes:**
1. **Click inside** any completed shape to select it (turns red)
2. **Delete/Backspace**: Removes the selected shape
3. **Click elsewhere**: Deselects

**Alternative Quick Delete:**
- **Delete/Backspace** without selection: Removes the most recently completed shape

### Info Panel (Top Left)
- **Zoom**: Current zoom level
- **Position**: Mouse cursor grid coordinates (X, Y)
- **Shapes**: Number of completed shapes
- **Current Lines**: Lines in the shape you're currently drawing

---

## Finalized Mode

Once you've finished drawing all your sectors, click **FINALIZE MAP** (top right).

### What Happens:
- Drawing mode is disabled
- Each shape becomes a configurable sector with an ID (starting from 1)
- Walls are automatically classified as:
  - **WALLS**: Outer boundaries (only in one sector)
  - **PORTALS**: Shared edges between two sectors
- Sectors are color-coded:
  - **Yellow with transparency**: Unconfigured sector
  - **Green with transparency**: Configured sector
  - **Cyan highlight**: Currently hovered sector
- White numbers show sector IDs in the center of each sector

### Configuring Sectors

**To Configure:**
1. **Click** on any sector
2. A configuration panel appears with:

**Sector Properties:**
- **Floor Height**: Vertical position of the floor (default: -2)
- **Ceiling Height**: Vertical position of the ceiling (default: 2)
- **Floor Texture**: Texture filename for the floor (e.g., "floor.png")
- **Floor Brightness**: Lighting multiplier for floor (0.0 to inf, default: 1.0)
- **Ceiling Texture**: Texture filename for ceiling (e.g., "ceiling.png")
- **Ceiling Brightness**: Lighting multiplier for ceiling

**Wall Configuration:**
Each wall is listed with its coordinates. Depending on type:

**For WALLS (outer boundaries):**
- **Texture**: Wall texture filename
- **Brightness**: Lighting multiplier

**For PORTALS (connecting two sectors):**
- Shows which sector it connects to
- **Bottom Texture**: Texture below the portal opening
- **Bottom Brightness**: Lighting for bottom section
- **Middle Texture**: Texture in the portal opening (use "black.png" for open passage)
- **Middle Brightness**: Lighting for middle section
- **Top Texture**: Texture above the portal opening
- **Top Brightness**: Lighting for top section

**Save/Cancel:**
- **SAVE**: Applies changes and marks sector as configured (turns green)
- **CANCEL**: Closes panel without saving changes

---

## Loading Maps

### Load Process
1. Click **LOAD MAP** on the main menu
2. Select a `.txt` map file
3. Choose loading mode:
   - **DRAWING MODE**: Edit the map structure (add/remove/modify sectors)
   - **FINALIZED MODE**: Configure sector properties only
   - **CANCEL**: Return to main menu

### Drawing Mode (After Load)
- All sectors appear as yellow editable shapes
- You can:
  - Select and delete existing sectors
  - Draw new sectors
  - Edit the map layout
- Click **FINALIZE MAP** when ready to configure properties

### Finalized Mode (After Load)
- Map loads with all configurations intact
- Sectors appear as configured (green) or unconfigured (yellow)
- Click sectors to view/edit their properties
- Ready to save immediately if no changes needed

---

## Saving Maps

Click **SAVE MAP** (top right, appears after finalization).

**Output:**
- Downloads as `map.txt`
- Text-based format with three sections
- All coordinates normalized (smaller values first)
- Ready to use in your game engine

---

## Editor Usage Guide

### Typical Workflow

**Creating a New Map:**
1. Click **NEW MAP**
2. Draw rectangular sectors by clicking vertices
3. Close each shape by clicking the starting vertex
4. Select and delete unwanted shapes if needed
5. Click **FINALIZE MAP**
6. Click each sector to configure properties
7. Set floor/ceiling heights and textures
8. Configure wall and portal textures
9. Click **SAVE MAP**

**Editing an Existing Map:**
1. Click **LOAD MAP**
2. Choose **DRAWING MODE** to modify structure, or **FINALIZED MODE** to only edit properties
3. Make your changes
4. Click **SAVE MAP**

### Best Practices

**Planning Your Map:**
- Sketch your layout on paper first
- Remember: all sectors must be rectangles with orthogonal walls
- Plan which sectors connect (portals) vs which are isolated

**Drawing Tips:**
- Use zoom to work on detailed areas
- Use pan to navigate large maps
- Draw larger rooms first, then subdivide as needed
- Keep sector sizes reasonable (avoid extremely large or small sectors)

**Portal Design:**
- Portals must share an entire edge between two sectors
- No partial connections allowed
- A sector can have 0-4 portals (any combination of walls and portals)
- Use middle texture "transparent.png" for open doorways
- Use visible middle textures for windows or grates

**Texture Naming:**
- Use consistent naming conventions (e.g., "wall01.png", "floor_stone.png")
- Keep filenames simple and descriptive
- Remember: the editor only stores names, not the actual textures

**Height Planning:**
- Floor height < Ceiling height (or you'll have inverted rooms!)
- Portal ceiling/floor differences create steps or height changes
- Use negative numbers for lower areas, positive for higher areas

### Common Workflows

**Creating a Simple Room:**
1. Draw a rectangle
2. Close it
3. Finalize
4. Configure the sector with your desired properties

**Creating Connected Rooms:**
1. Draw first rectangle
2. Draw second rectangle sharing one edge with the first
3. Continue for additional rooms
4. Finalize
5. Configure each sector
6. Portals are automatically detected on shared edges

**Creating Multi-Level Areas:**
1. Create sectors as normal
2. In finalized mode, give sectors different floor/ceiling heights
3. Connected sectors with different heights will show steps/elevation changes

---

## Map Structure

### File Format

The saved map uses a text-based format with three sections:

```
[SECTORS]
[WALLS]
[PORTALS]
```

### Coordinate System
- **Origin**: (0, 0) is at the center
- **X-axis**: Negative left, Positive right
- **Y-axis**: Negative down, Positive UP (Cartesian standard)
- **Units**: Integer grid units
- **Normalization**: Coordinates are always saved with smaller values first

### Section Details

#### [SECTORS]
Defines each sector's basic properties.

**Format:**
SectorID FloorHeight CeilingHeight FloorTexture FloorBrightness CeilingTexture CeilingBrightness

**Example:**
[SECTORS]
1 -2 1 floor.png 1.2 ceiling.png 0.5
2 -3 2 grass.png 1.2 black.png 1.0
3 -2 2 floor.png 1.2 floor.png 0.5

**Fields:**
- `SectorID`: Unique identifier (starts at 1, never 0)
- `FloorHeight`: Vertical position of floor (float)
- `CeilingHeight`: Vertical position of ceiling (float)
- `FloorTexture`: Texture filename (string, no spaces)
- `FloorBrightness`: Lighting multiplier (float, typically 0.0-2.0)
- `CeilingTexture`: Texture filename (string, no spaces)
- `CeilingBrightness`: Lighting multiplier (float, typically 0.0-2.0)

#### [WALLS]
Defines outer walls that don't connect to other sectors.

**Format:**
X1 Y1 X2 Y2 SectorID Texture Brightness

**Example:**
[WALLS]
-3 8 10 8 1 wall4h.png 0.5
10 -7 10 8 1 wall4h.png 1.0
-10 8 -3 8 2 wall6h.png 0.5

**Fields:**
- `X1 Y1`: Start vertex coordinates (integers)
- `X2 Y2`: End vertex coordinates (integers)
- `SectorID`: Which sector this wall belongs to
- `Texture`: Wall texture filename (string, no spaces)
- `Brightness`: Lighting multiplier (float)

**Note:** Coordinates are normalized so X1 < X2, or if X1 == X2, then Y1 < Y2

#### [PORTALS]
Defines shared walls between two sectors.

**Format:**
X1 Y1 X2 Y2 SectorA SectorB BottomTexture BottomBrightness MiddleTexture MiddleBrightness TopTexture TopBrightness

**Example:**
[PORTALS]
-3 -7 -3 8 1 2 wall.png 1.0 black.png 1.0 wall.png 1.0
-10 -7 -3 -7 2 3 wall.png 0.5 door.png 1.0 wall.png 0.5

**Fields:**
- `X1 Y1 X2 Y2`: Shared wall coordinates (normalized)
- `SectorA`: First sector ID
- `SectorB`: Second sector ID
- `BottomTexture`: Texture for lower portion (below opening)
- `BottomBrightness`: Lighting for bottom
- `MiddleTexture`: Texture for passage itself (use "black.png" for open)
- `MiddleBrightness`: Lighting for middle
- `TopTexture`: Texture for upper portion (above opening)
- `TopBrightness`: Lighting for top

**Portal Rendering:**
The three textures allow for:
- **Bottom**: Shows when connected sector has higher floor
- **Middle**: The passage/opening itself
- **Top**: Shows when connected sector has lower ceiling

### Reserved Values

- **Sector 0**: Reserved by the engine (never used in editor)
- **Sector IDs**: Always start at 1 and increment
- **Negative Heights**: Valid for underground/below-origin areas

### Validation Rules

The editor assumes the user follows these rules (no automatic validation):

1. **Closed Shapes**: All sectors must be closed loops
2. **Orthogonal**: Only horizontal or vertical lines
3. **Rectangles**: All shapes should have 4 sides
4. **Complete Portals**: Shared edges must be identical (same start/end coordinates)
5. **No Gaps**: Portal edges must match exactly between sectors
6. **Height Logic**: Floor height should be less than ceiling height
7. **Unique Edges**: Each wall segment appears in at most 2 sectors

### Example Complete Map
```
[SECTORS]
1 -2 1 floor.png 1.2 floor.png 0.5
2 -3 2 grass.png 1.2 black.png 1.0
3 -2 2 floor.png 1.2 floor.png 0.5
4 -1 2 floor.png 1.2 floor.png 0.5
[WALLS]
-3 8 10 8 1 wall4h.png 0.5
10 -7 10 8 1 wall4h.png 1.0
-10 8 -3 8 2 wall6h.png 0.5
-10 -7 -10 8 2 wall6h.png 1.0
-10 -10 -10 -7 3 wall3h_with_cuteanime.png 1.0
-10 -10 -3 -10 3 wall3h.png 0.5
-3 -10 10 -10 4 walled_fence.png 0.5
10 -10 10 -7 4 wall3h_with_cuteanime2hd.png 1.0
[PORTALS]
-3 -7 -3 8 1 2 wall.png 1.0 fence2.png 1.0 wall.png 1.0
-10 -7 -3 -7 2 3 wall.png 0.5 black.png 1.0 wall.png 0.5
-3 -7 10 -7 1 4 wall.png 0.5 black.png 1.0 wall.png 1.0
-3 -10 -3 -7 3 4 wall.png 1.0 black.png 1.0 wall.png 0.5
```

---

## Troubleshooting

**Can't draw after loading:**
- Make sure you selected "DRAWING MODE" when loading
- Try clicking on empty grid space away from existing shapes

**Shape won't close:**
- Click exactly on the pink starting vertex
- Make sure your last line connects to the start

**Can't delete a shape:**
- Click inside the shape first (it turns red)
- Then press Delete/Backspace

**Portal not appearing:**
- Ensure two rectangles share an ENTIRE edge
- Coordinates must match exactly
- Finalize the map to see portal classification

**Sector appears unconfigured:**
- Yellow sectors haven't been configured yet
- Click the sector and press SAVE to mark as configured

---

## Keyboard Shortcuts

- **Delete / Backspace**: Delete selected shape or last line
- **Mouse Wheel Up**: Zoom in
- **Mouse Wheel Down**: Zoom out

## Tips & Tricks

1. **Quick Selection**: Click shapes rapidly to cycle through overlapping sectors
2. **Precision Zoom**: Zoom in close when drawing detailed areas
3. **Copy Strategy**: Save your map, edit the text file to duplicate sectors
4. **Texture Preview**: Keep a reference sheet of texture names handy
5. **Version Control**: Save multiple versions of your map with different filenames

---

**Happy Mapping!**