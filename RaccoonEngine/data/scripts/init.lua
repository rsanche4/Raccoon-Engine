local script_index = ...

REAPI:log("Running Init Script from Lua!!")
REAPI:set_player_pos(0, 0, 4)
REAPI:set_skybox("afternoon_skybox.png", 1.0)
REAPI:load_map("level_test.txt")
REAPI:add_script("show_debug.lua")
REAPI:addSprite(3.51, -0.76, -8.48, 5.0, 1.0, "raccoony_sprite.png", "raccoony", "behave.lua")
--REAPI:playBGM("gray.wav", true)
REAPI:endme(script_index)