local script_index = ...

REAPI:log("Running Init Script from Lua!!")
REAPI:set_player_pos(0, 0, 4)
REAPI:set_skybox("afternoon_skybox.png", 1.0)
REAPI:load_map("level_test.txt")
REAPI:add_script("show_debug.lua")
REAPI:endme(script_index)