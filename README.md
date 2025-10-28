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
  - [14. REAPI](#14-reapi)
- [Gameplay and Distribution](#gameplay-and-distribution)
  - [15. Publishing Your Game!](#15-publishing-your-game)
  - [16. Check Out the Demo!](#16-check-out-the-demo)
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

I am Rafael Sanchez, a slightly sleep-deprived but highly passionate nerd with a lot of free time on my hands haha. I spend most of my time chilling at 127.0.0.1, building fun stuff, going on long walks. I also love math! It's funny that I actually did not study game dev in college but Machine Learning instead. The weird turns of life! Oh and I also like acting and do fun theater shows every now and then. Boy do I have a diverse profile. Connect with me on [linkedin](https://www.linkedin.com/in/rafael-sanchez4/)!

### 4. Installation Guide

#### Prerequisites

- **Java Development Kit (JDK)** - Install the latest JDK for your operating system
- **Eclipse IDE for Java Developers** - Download from [eclipse.org](https://www.eclipse.org/downloads/)

#### Installation Steps

#### 1. Open the Project

1. Launch Eclipse IDE
2. Go to **File -> Open Projects from File System**
3. Select the `RaccoonEngine` folder
4. Click **Finish**

#### 2. Configure Libraries

1. Right-click the project in Eclipse -> **Build Path -> Configure Build Path**
2. Go to the **Libraries** tab
3. Click **Add External JARs**
4. Navigate to the `lib/` folder in the project directory
5. Select all `.jar` files and click **Open**
6. Click **Apply and Close**

#### 3. Run the Engine

Click the **Run** button (green play icon).

### Development Workflow

**Important:** You should not need to modify the engine source code. This is only for you to run the engine as a developer. All game development happens in the `Data/` folder and the Raccoon Editor. The engine source is available for reference and advanced customization only.

---

## Technical Overview

### 5. Why Java?

You might be wondering why I chose Java for a raycasting game engine instead of the more typical choices like C++ or C. Honestly, Java is just good enough for this type of project. Raycasting engines aren't pushing the bleeding edge of performance. The techniques used in Wolfenstein 3D worked on early 90s hardware, so modern Java on today's machines handles it without breaking a sweat. Not to mention good old Minecraft was also written in Java originally, so nothing like paying homage to the legend!

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

Objects, enemies, and items are rendered as 2D images that always face the player. The engine calculates the sprite's distance and screen position, then scales and projects the appropriate image onto screenspace.

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

Since this section is too long, I decided to split it into a different file called `RaccoonEditor.md`. I suggest you take a look at that file instead if you want to learn more about how to use the editor! The main goal of the editor is to create the map structure and map file that will be read by the engine to render your wonderful scenes. Have fun!

### 8. Map Structure

Inside the editor documentation file you will also find guides on the structure of a map. It is surprisingly simple, although with some restrictions given the design choices of the engine.

### 9. Textures & Skyboxes

There is no limits on the size of the textures that you want to display. It is recommended however that you do not include really huge texture files as they simply occupy more space. The skybox in particular does have 1 limitation: it needs to be 4 times the size of the game's width, and the same height as the game's. Since I decided to lock the engine at 640x480 for best performance and retro aesthetic, the skybox thus needs to be 4*640x480 in size, or 2560x480. This is important! If you for some reason give a skybox that has a different size, it is probable the engine will either crash or display some bizarre skybox.

### 10. Collision Handling

The collisions of the game are mainly handled very simply. If you are crouching, the rule is that you cannot "climb" to a sector that is higher than where you are. Otherwise you can simply walk up to any sector (as long as it's not much taller than you) and you can climb it up! This helps a lot when it comes to creating stairs, etc. Other than that, any texture that is displayed between sectors is also collidable. The only things that are non-solid would be sprites, so keep this in mind when you design your levels!

---

## Game Logic and Scripting

### 11. Control Scheme

| Key           | Action / Description       |
|---------------|----------------------------|
| *Arrow Left   | Turn left                  |
| *Arrow Right  | Turn right                 |
| *Arrow Up     | Move forward               |
| *Arrow Down   | Move backward              |
| Enter         | Confirm                    |
| Ctrl          | Interact #1                |
| Space         | Interact #2                |
| *Comma (,)    | Strafe left                |
| *Period (.)   | Strafe right               |
| 1             | Extra Buttons #1           |
| 2             | Extra Buttons #2           |
| 3             | Extra Buttons #3           |
| 4             | Extra Buttons #4           |
| *Page Up      | Jump                       |
| *Page Down    | Crouch                     |
| *F4           | Toggle fullscreen          |
| *Escape       | Exit game                  |

Note*: These buttons have a hardcoded functionality in the engine. These means that you cannot change what they do. The majority of these are just movement buttons. But the "interact" are up to you how you want to use them. For example, 1-4 could be used for switching to different items, space could be used for interacting with other objects, etc!

### 12. Scripting Your Game

Scripting your game using this API gives you a high degree of control over virtually every aspect of the game world. You can manipulate player movement, adjust physics parameters like gravity and speed, spawn or remove entities dynamically, and even control the rendering of UI elements and text. The Lua scripts act as your interface to the engine, allowing you to implement gameplay logic, interactive events, or even entire quests without touching the underlying Java code. Because the API exposes functions for everything from map loading to sound management, you can prototype, test, and iterate quickly, which makes the development process much more fluid and creative. It feels like you're building a living world with your own set of rules, where the engine handles the heavy lifting of rendering, collisions, and input detection.

At the same time, scripting requires careful planning and structure. Each script runs in the context of the active game world, meaning that you have to be aware of what other scripts are doing and how they interact. Proper use of sprite IDs, sector references, and event triggers is essential to avoid conflicts or unexpected behavior. The API encourages modularity: you can attach behavior scripts to individual entities, trigger sound effects or UI changes at specific moments, and adjust gameplay mechanics on the fly. The learning curve is not just about understanding each function-it's also about thinking in terms of dynamic systems and how player actions, environment, and scripted logic intertwine. Once you get used to this mindset, scripting becomes less about "coding" and more about orchestrating a living, interactive experience.

### 13. The `config.cfg` File

The `config.cfg` file can be found in the `Data/` folder. It contains the following entries: `game_title`, `game_version`.

**game_title**: The title of your game that appears in the window's title bar. This can be anything you want. It's simply the name of your project displayed to players.

**game_version**: The version number of your game. Set this to whatever versioning scheme you prefer (e.g., "1.0", "0.5 Beta", "Alpha Build 3").

### 14. REAPI

Since the source files are accessible, you can dive into the main section of the code where most of your scripting will take place: ReApi.java. This class contains all the functions available for developing your games. If you need any newer API functions with fancy functionality that isn't in the base engine, then you are free to edit this file and add it with any arguments you'd like! This way you can basically use the power of the Java Backend while still being able to script your game in Lua. Then, to use them from Lua (or other scripts), simply locate the function you need in this class (or that you created) and call it with the appropriate arguments. For example, you can check init.lua to see how to log a message to the console (note that this is different from displaying a text UI to the actual game window. For that you would use displayText()!). Here's a quick example:

`REAPI:log("Hello world!")`

---

## Gameplay and Distribution

### 15. Publishing Your Game!

For Java, there are several ways to turn your game into an executable, with Launch4j being one popular option. Typically, this involves creating an executable JAR file or using scripts to handle the launch. While this process is beyond the scope of this guide, I'm happy to help if you need assistance-just send me an email! My contact information can be found at the end.

### 16. Check Out the Demo!

If you are curious to see videos of me fooling around in my engine, (as well as a bit of a devlog), feel free to check out the Raccoon Engine's youtube channel!

[![Watch the Demo](https://img.youtube.com/vi/pxAxZ3L9OsA/0.jpg)](https://www.youtube.com/@raccoony38)

---

## Final Bits

### 17. Known Limitations and Constraints

The Raccoon Engine uses a sector-based portal rendering system inspired by Doom and the Build Engine. Each vertical column on screen renders exactly one floor, one ceiling, and the walls in between. This is an elegant constraint that enables fast performance while creating rich, multi-layered 3D environments. However, as a raycaster at its core, the engine has some inherent limitations:

**No Rooms Above Rooms**

This is the classic limitation that Doom also had to deal with. Since each sector only stores two values (floor height and ceiling height), you cannot place one sector directly on top of another. This is simply impossible with the raycasting approach.

**Single Axis of Rotation**

Because of the column-based rendering, true looking up and down would cause visual distortions. The engine is locked to horizontal rotation only (yaw), giving you 1 degree of rotational freedom. Your view always remains level with the horizon.

**Billboarded Sprites**

There are no true 3D polygons for objects or enemies. Instead, sprites are "billboarded", or flat images always facing the player. To create the illusion of 3D, you could accomplish with the help of scripting the ability to change the sprite image being shown to a different one depending on the viewing angle (front view, side view, back view, etc.).

**Grid-Aligned Walls Only**

The engine uses a strict grid-based system where all walls must be aligned horizontally or vertically. You cannot create diagonal walls, angled corridors, or sloped surfaces. While this is more restrictive than Doom's approach (which allowed arbitrary wall angles), it makes raycasting significantly faster and completely avoids the epsilon/precision problems that Doom's BSP system had to handle. The tradeoff is simplicity and performance for geometric flexibility: your walls snap to the grid, keeping everything clean and predictable.

**Sector Count and Map Size Considerations**

Try to keep your world under reasonable amount of sectors and walls for optimal performance. If your map grows larger (for example more than 100 sectors or more than 100 units long), consider splitting it into separate map files and using teleporters or level transitions to move the player between them.

**The Void Glitch**

So this is a documented glitch that happened also similarly with Doom, however in my case it has to do with the boundaries between sectors. My theory is that when you are standing exactly between two sectors, something happens with the math only when you are exactly looking at 0 degrees exactly (which rarely happens after the player moves or we add an epsilon). The engine might get slightly confused as to which sectors you belong to, or how to render things effectively, because we instead see what appears to be a bizarre void of black, with warped texture floors and ceiling. I decided to leave this as it doesn't break any game whatsoever, it rarely happens, and it looks kinda cool! Of course, these artifacts were part of original game engines back in the 90s also, and thus in the spirit of a true retro enthusiast, I have decided to leave The Void Glitch as a feature, and not a bug! Haha After all, these limitations aren't bugs! They're design choices that make the engine fast, understandable, and give it that distinctive retro feel! (Note: if you do not want this glitch to happen at all, it can be easily avoided by simply not starting your game looking exactly at 0 direction. You can start with a small epsilon like 0.001)

### 18. License Info

Raccoon Engine is released under the MIT License, one of the most permissive open-source licenses out there. You're free to use the engine for personal or commercial projects, modify the source code however you like, distribute your games without any restrictions, fork the project and create your own variations, etc. The only requirement is that you include the original copyright notice and license text in any distributions of the source code. Basically, you can do whatever you want with Raccoon Engine. Build games, sell them, modify the engine, share your improvements, etc. The MIT License is designed to get out of your way and let you create. For the full legal text, see the LICENSE file included with the engine.

### 19. Contact Me

You are still here? Oh my! Well, I am glad you managed to read through the documentation. If you have any further questions, feel free to [email me](mailto:rsanzek25@gmail.com)!