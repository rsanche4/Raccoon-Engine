-- Final init.lua for walking-sim
RA:playerSetPosition(16, 5, 16, 0)  -- Center of map, slightly above ground
RA:playerSetWalk(4, 0.15, 0.03)   -- Normal walking bob

-- Set environment
RA:worldSetSkybox("blue_sky.png", 0.8)  -- Blue sky
RA:worldLoadMapAsync("map.txt", "ENTERING GRASS FIELD...")

-- Remove init script
RA:scriptEnd(...)