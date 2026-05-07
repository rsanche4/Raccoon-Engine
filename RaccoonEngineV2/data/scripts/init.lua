local script_index = ...

RA:playerSetPosition(2, 2, 2, 0)
RA:playerSetWalk(2, 0.15, 0.03)
RA:worldLoadMap("map.txt")
RA:scriptEnd(script_index)