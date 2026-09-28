local script_index = ...
RA:systemDebug(false)
RA:audioPlayBGM("music.wav", true, 0.9)
RA:scriptAdd("menu.lua", 10)
RA:scriptEnd(script_index)
