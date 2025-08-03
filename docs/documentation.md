# 🦝 Raccoon Engine Documentation

## 📑 Index

- [📘 Introduction](#-introduction)
  - [1. What is the Raccoon Engine?](#1-what-is-the-raccoon-engine)
  - [2. Why I Built This](#2-why-i-built-this)
  - [3. About Me](#3-about-me)
  - [4. Installation Guide](#4-installation-guide)
- [⚙️ Technical Overview](#️-technical-overview)
  - [5. Why Python?](#5-why-python)
  - [6. Core Concepts Explained](#6-core-concepts-explained)
- [🛠️ Building and Customizing](#-building-and-customizing)
  - [7. Editor Usage Guide](#7-editor-usage-guide)
  - [8. Map Structure](#8-map-structure)
  - [9. Textures & Skyboxes](#9-textures--skyboxes)
  - [10. Collision Handling](#10-collision-handling)
- [🧠 Game Logic and Scripting](#-game-logic-and-scripting)
  - [11. Control Scheme](#11-control-scheme)
  - [12. Scripting Your Game](#12-scripting-your-game)
  - [13. The `config.cfg` File](#13-the-configcfg-file)
  - [14. API Reference](#14-api-reference)
- [🎮 Gameplay and Distribution](#-gameplay-and-distribution)
  - [15. Running Your Game](#15-running-your-game)
  - [16. Check out the Demo!](#16-check-out-the-demo)
- [🏱 Final Bits](#-final-bits)
  - [17. Known Limitations and Constraints](#17-known-limitations-and-constraints)
  - [18. Contact Me](#18-contact-me)

---

## 📘 Introduction

### 1. What is the Raccoon Engine?
The Raccoon Game Engine is a full-featured retro-style 3D engine inspired by DOOM, including an intuitive sector-based editor that lets you build worlds with customizable wall heights, floors, ceilings, and more - just like the classics. It uses a Binary Space Partitioning (BSP) tree to efficiently sort polygons for correct rendering order. The engine supports directional sprites and textured walls, allowing you to create complex elements like fences and detailed environments. Built entirely in Python with native scripting, it allows you to create any sort of games and worlds! The engine leverages Pygame for rendering and uses minimal dependencies for speed and simplicity, making it an accessible and extensible platform for retro 3D game development and experimentation.

### 2. Why I Built This
I am glad you asked! Well, during the summer of 2025, I had a lot of free time on my hands due to personal circumstances in my life. I had already been working on a game engine that used raycasting instead since 2022. I was able to finish it, but I felt like it was too limited still. Raycasting was great, but I could accomplish a lot more. I wanted to create my own 3D engine, but I did not want to go full rasterization, so I decided to opt in for something simpler. It was here where I remembered one of my favorite childhood games, DOOM. And thus, I learned a lot about how it was able to create its complex scenes without full 3D!

### 3. About Me
I am Rafael Sanchez — a slightly sleep-deprived but highly passionate nerd with a lot of free time on my hands haha. I spend most of my time chilling at 127.0.0.1, building fun stuff, going on long walks. I also love math & AI! It's funny that I actually did not study game dev in college but Machine Learning instead. The weird turns of life! Oh and I also like acting and do fun theater shows every now and then. Boy do I have a diverse profile. Follow me on linkedin! -> https://www.linkedin.com/in/rafael-sanchez4/

### 4. Installation Guide
To install Python, go to [https://www.python.org/downloads/](https://www.python.org/downloads/) and download version 3.12.4 for your operating system. On **Windows**, make sure to check the box that says **"Add Python to PATH"** during installation. After installation, open a terminal (Command Prompt or Terminal) and verify that Python is installed by typing: `python --version`. After this, you are set! Usually pip is installed with python, try to double check it is version 24.0 you are installing. So also type in the terminal: `pip --version`. If you see the versions that's good! Now you are ready to install all the requirements for our project. Navigate with your terminal to this folder and type: `pip install -r requirements.txt`. You should now wait for all the packages to be downloaded. Once ready, we are good to go. Note: You don't need to install these specific versions. You could pull the latest, but I cannot guarantee you won't run into compatibility issues. Try and see!

## ⚙️ Technical Overview

### 5. Why Python?
Technical reasoning behind the choice of language and tools.

### 6. Core Concepts Explained
In-depth explanation of important concepts like: what u need to know on the math, links to these algorithms. Why this seemed sensible to do and made sense. The rendering pipeline should also be here and we explain basically the steps. Binary Space Partitioning (BSP) Digital Differential Analyzer (DDA) 2.5D Projection and Raycasting

## 🛠️ Building and Customizing

### 7. Editor Usage Guide
How to use the built-in map editor, and how to export and place maps into the game directory.

### 8. Map Structure
Overview of the `.json` map format used by the engine.

### 9. Textures & Skyboxes
How to import and use textures. Setting up dynamic or static skyboxes. Pictures.

### 10. Collision Handling
How collisions work in the engine. Basically walls have collision and sprites have collision. You can do an invisible wall by putting an empty wall type thing, since walls have collision anyways.

## 🧠 Game Logic and Scripting

### 11. Control Scheme
List of default input keys and how to customize them.

### 12. Scripting Your Game
Writing scripts to control gameplay logic, events, and game flow. Music how show it etc, events. All of it. NPCs everything is here basically. Include Sprites here basically.

### 13. The `config.cfg` File
How to customize global settings and preferences.

### 14. API Reference
Available API calls for interacting with the engine programmatically. The actual api reference needs to be here, its gonna be long but it includes all the calls and their description, arguments, etc.

## 🎮 Gameplay and Distribution

### 15. Running Your Game
How to launch your game: simply run `python game.py`. Also include here how to turn ur game to an exec for Windows, Apple, And Unix.

### 16. Check out the Demo!
Right now there is a demo already written in the files! It is not much, as it is just our mascot Raccoony patrolling around a big city. Fun!

## 🏱 Final Bits

### 17. Known Limitations and Constraints
Let's talk finally on the limitations behind the engine. From resolution to all the limits that we have and why they are there.

### 18. Contact Me
You are still here? Oh my! Well, I am glad you managed to read through the documentation. If you have any further questions, feel free to email me at rsanzek25@gmail.com