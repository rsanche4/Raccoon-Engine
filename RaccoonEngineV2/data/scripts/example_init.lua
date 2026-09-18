-- Example init.lua showing the async loading screen.
local script_index = ...

RA:playerSetPosition(2, 2, 2, 0)
RA:playerSetWalk(2, 0.15, 0.03)
RA:worldSetSkybox("default_sky.png", 0.5)

-- Loads on a background thread with a loading screen in front of it.
-- Use this instead of worldLoadMap for anything bigger than a test room.
RA:worldLoadMapAsync("map.txt", "ENTERING THE FACILITY")

RA:scriptAdd("example_hud.lua", 10)

RA:scriptEnd(script_index)
