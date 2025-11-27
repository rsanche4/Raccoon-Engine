local script_index = ...

local font = "font_32px.png"

REAPI:displayText("SCORE: 0000", 10, 10, font)

REAPI:addUIToScreen("pacman.png", 640-128, 10, 255)
REAPI:addUIToScreen("pacman.png", 640-96, 10, 255)
REAPI:addUIToScreen("pacman.png", 640-64, 10, 255)
REAPI:addUIToScreen("pacman.png", 640-32, 10, 255)