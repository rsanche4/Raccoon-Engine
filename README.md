# Raccoon Engine Documentation

## Index

- [Introduction](#introduction)
  - [1. What is the Raccoon Engine?](#1-what-is-the-raccoon-engine)
  - [2. Why Build This?](#2-why-build-this)
  - [3. About Me](#3-about-me)
  - [4. Installation Guide](#4-installation-guide)
- [Technical Overview](#technical-overview)
  - [5. Why Java?](#5-why-java)
  - [6. Core Concepts Explained](#6-core-concepts-explained)
- [Building and Customizing](#building-and-customizing)
  - [7. Editor Usage Guide](#7-editor-usage-guide)
  - [8. Map Structure](#8-map-structure)
  - [9. Textures & Skyboxes](#9-textures--skyboxes)
  - [10. Collision Handling](#10-collision-handling)
- [Game Logic and Scripting](#game-logic-and-scripting)
  - [11. Control Scheme](#11-control-scheme)
  - [12. Scripting Your Game](#12-scripting-your-game)
  - [13. The `config.cfg` File](#13-the-configcfg-file)
  - [14. API Reference](#14-api-reference)
- [Gameplay and Distribution](#gameplay-and-distribution)
  - [15. Publishing Your Game!](#15-publishing-your-game)
  - [16. Check Out Demo & Tutorial!](#16-check-out-demo--tutorial)
- [Final Bits](#final-bits)
  - [17. Known Limitations and Constraints](#17-known-limitations-and-constraints)
  - [18. License Info](#18-license-info)
  - [19. Contact Me](#19-contact-me)

---

## Introduction

### 1. What is the Raccoon Engine?

The Raccoon Engine is a lightweight game engine inspired by the simplicity and flexibility of Build Engine-style architectures and grid stepping logic of raycasters. It combines a Java core with a Lua frontend for easy scripting and rapid prototyping.

### 2. Why Build This?

I am glad you asked! Well, during the summer of 2025, I had a lot of free time on my hands due to personal circumstances in my life. I had already been working on a game engine that used raycasting instead since 2022. I was able to finish it, but I felt like it was too limited still. Raycasting was great, but I could accomplish a lot more. I wanted to create my own 3D engine, but I did not want to go full rasterization, so I decided to opt in for something simpler. It was here where I remembered one of my favorite childhood games, DOOM. And thus, I learned a lot about how it was able to create its complex scenes without full 3D!

### 3. About Me

I am Rafael Sanchez, a slightly sleep-deprived but highly passionate nerd with a lot of free time on my hands haha. I spend most of my time chilling at 127.0.0.1, building fun stuff, going on long walks. I also love math! It's funny that I actually did not study game dev in college but Machine Learning instead. The weird turns of life! Oh and I also like acting and do fun theater shows every now and then. Boy do I have a diverse profile. Follow me on [linkedin](https://www.linkedin.com/in/rafael-sanchez4/)!

### 4. Installation Guide

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

---

## Technical Overview

### 5. Why Java?

You might be wondering why I chose Java for a raycasting game engine instead of the more typical choices like C++ or C. Honestly, Java is just good enough for this type of project. Raycasting engines aren't pushing the bleeding edge of performance. The techniques used in Wolfenstein 3D worked on early 90s hardware, so modern Java on today's machines handles it without breaking a sweat. Not to mention good old Minecraft was also written in Java originally, so nothing like paying homage to the legend! On top of that, Java isn't limited to just software rendering. With libraries like LWJGL (Lightweight Java Game Library) or JOGL, you can access OpenGL directly from Java. This opens up possibilities for hardware-accelerated graphics while still enjoying Java's ecosystem. It's the best of both worlds: high-level language convenience with low-level graphics power when you need it.

### 6. Core Concepts Explained

**The Raycasting Foundation**

For each vertical column on the screen, the engine casts a single ray from the player's position outward into the world. These rays travel through the 2D grid map, checking for intersections with walls. The distance to the nearest wall determines how tall that wall appears on screen. Essentially, closer walls are taller, distant walls are shorter. This creates the illusion of depth and perspective.

**DDA Algorithm (Digital Differential Analysis)**

The engine uses the DDA algorithm to efficiently step through the grid. Instead of checking every possible point along the ray, DDA intelligently jumps from grid line to grid line (both horizontal and vertical), checking only the grid cells the ray actually passes through. This makes collision detection fast and accurate.

**Projection and Scaling**

Once a wall is hit, the engine calculates the perpendicular distance to avoid the "fish-eye" distortion effect. This distance is then used to determine the wall's height on screen using trigonometry and projection.

**Portal Rendering**

When a ray passes through a sector boundary (a portal), the engine continues into the next convex room or sector, where the rendering process is repeated but only on the smaller window of the portal through which we can see the next sector. This allows for multi-room environments with varying heights, creating complex interconnected spaces while maintaining performance.

**Billboarded Sprites**

Objects, enemies, and items are rendered as 2D images that always face the player. The engine calculates the sprite's distance and screen position, then scales and draws the appropriate image. To create a pseudo-3D effect, different sprite images are displayed based on the viewing angle (front, side, back views), giving objects a sense of dimensionality without true 3D geometry.

**Texture Mapping**

When a ray hits a wall, the engine determines exactly where on that wall the collision occurred. This hit point maps to a specific column in the wall's texture. The texture is then scaled vertically to match the wall's screen height and drawn column by column.

For floor and ceiling tiles, the process is similar but works in reverse: for each pixel on screen, the engine calculates which world tile it corresponds to and samples the appropriate pixel from that tile's texture. This creates the illusion of textured floors stretching into the distance.

**Depth Buffer and Lighting**

A depth buffer tracks the distance of everything rendered on screen. This depth information adjusts the brightness of walls, floors, and sprites. Closer objects should appear brighter, distant objects fade into darkness. This creates atmospheric fog effects and realistic lighting without expensive lighting calculations.

**Brush Up Your Math Skills**

If all of this sounded confusing, you are not alone! I was very much confused by the entire jargon of 3D graphics when I first started my journey all the way back in 2022. Here are some very nice resources that helped me very much understand a lot about how to accomplish all of this. (Keep in mind, I did my own flavor of rendering. I did not follow these tutorials but mixed and matched algorithms with what was easier to understand and what worked better for me!):

https://lodev.org/cgtutor/raycasting.html -> A tutorial on Wolfestein 3D rendering algorithm for raycasting!

https://youtu.be/NbSee-XM7WA -> DDA more easily explained.

https://youtu.be/fSjc8vLMg8c -> An overview on how to re-create the DOOM engine.

https://youtu.be/eoXn6nwV694 -> A great video explaining the main concepts behind perspective projection.

---

## Building and Customizing

### 7. Editor Usage Guide

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

### 8. Map Structure

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

### 9. Textures & Skyboxes

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

### 10. Collision Handling

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

---

## Game Logic and Scripting

### 11. Control Scheme

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

### 12. Scripting Your Game

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

### 13. The `config.cfg` File

The `config.cfg` file can be found in the `Data/` folder. It contains the following entries: `game_title`, `game_version`, `game_res`, and `window_res`.

**game_title**: The title of your game that appears in the window's title bar. This can be anything you want. It's simply the name of your project displayed to players.

**game_version**: The version number of your game. Set this to whatever versioning scheme you prefer (e.g., "1.0", "0.5 Beta", "Alpha Build 3").

**game_res**: This is the internal rendering resolution i.e. the resolution at which all game logic and rendering calculations are performed. It's strongly recommended to keep this at `640x480` for the authentic retro look and optimal performance. This classic resolution gives you that nostalgic chunky-pixel aesthetic while keeping the raycasting fast.

**window_res**: This is the actual window size displayed on your screen. The game rendering is scaled up from `game_res` to match this window size. If you set `window_res` equal to your screen resolution, the game will run in fullscreen mode.

All resolutions must follow the format `widthxheight` (e.g., `1280x720`, `1920x1080`, `800x600`). Make sure there are no spaces and the 'x' is lowercase.

### 14. API Reference

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

---

## Gameplay and Distribution

### 15. Publishing Your Game!

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

### 16. Check Out Demo & Tutorial!

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

---

## Final Bits

### 17. Known Limitations and Constraints

The Raccoon Engine uses a sector-based portal rendering system inspired by Doom and the Build Engine. Each vertical column on screen renders exactly one floor, one ceiling, and the walls in between. This is an elegant constraint that enables fast performance while creating rich, multi-layered 3D environments. However, as a raycaster at its core, the engine has some inherent limitations:

**No Rooms Above Rooms**

This is the classic limitation that Doom also had to deal with. Since each sector only stores two values (floor height and ceiling height), you cannot place one sector directly on top of another. This is simply impossible with the raycasting approach.

**Single Axis of Rotation**

Because of the column-based rendering, true looking up and down would cause visual distortions. The engine is locked to horizontal rotation only (yaw), giving you 1 degree of rotational freedom. Your view always remains level with the horizon.

**Billboarded Sprites**

There are no true 3D polygons for objects or enemies. Instead, sprites are "billboarded", or flat images always facing the player. To create the illusion of 3D, the engine displays different sprite images based on the viewing angle (front view, side view, back view, etc.), giving that classic pseudo-3D look without the complexity of polygon rendering.

**No Sprite Stacking**

Sprites cannot overlap or stack on top of each other in 3D space. This simplifies collision detection and reduces CPU overhead, but means you'll need to plan object placement carefully.

**Grid-Aligned Walls Only**

The engine uses a strict grid-based system where all walls must be aligned horizontally or vertically. You cannot create diagonal walls, angled corridors, or sloped surfaces. While this is more restrictive than Doom's approach (which allowed arbitrary wall angles), it makes raycasting significantly faster and completely avoids the epsilon/precision problems that Doom's BSP system had to handle. The tradeoff is simplicity and performance for geometric flexibility: your walls snap to the grid, keeping everything clean and predictable.

**Sector Count Considerations**

Try to keep your world under 100 sectors for optimal performance. If your map grows larger, consider splitting it into separate map files and using teleporters or level transitions to move the player between them. This keeps rendering efficient and frame rates smooth.

Note: These limitations aren't bugs! They're design choices that make the engine fast, understandable, and give it that distinctive retro feel!

### 18. License Info

Raccoon Engine is released under the MIT License, one of the most permissive open-source licenses out there. You're free to use the engine for personal or commercial projects, modify the source code however you like, distribute your games without any restrictions, fork the project and create your own variations, etc. The only requirement is that you include the original copyright notice and license text in any distributions of the source code. Basically, you can do whatever you want with Raccoon Engine. Build games, sell them, modify the engine, share your improvements, etc. The MIT License is designed to get out of your way and let you create. For the full legal text, see the LICENSE file included with the engine.

### 19. Contact Me

You are still here? Oh my! Well, I am glad you managed to read through the documentation. If you have any further questions, feel free to [email me](mailto:raffysplayground@gmail.com)!