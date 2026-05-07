local script_index = ...

RA:playerSetPosition(2, 2, 2, 0)
RA:playerSetWalk(2, 0, 0)
RA:worldLoadMap("map.txt")
RA:worldSetSkybox("default_sky.png", 0.5)
RA:scriptEnd(script_index)