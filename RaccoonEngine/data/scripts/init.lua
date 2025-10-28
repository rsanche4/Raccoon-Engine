local script_index = ...

REAPI:log("Running Init Script from Lua!!")
REAPI:set_player_pos(0, 0, 0)
--REAPI:set_move_speed(1)
REAPI:load_map("testroom.txt")
--REAPI:add_script("show_debug.lua")
--REAPI:add_script("show_ui.lua")
--REAPI:set_max_ray_steps(500)
--REAPI:set_fog_settings(15,10,0, 15,50)
--REAPI:upsertSprite(0, -0.4, 1, 30.0, 1.0, "streetlamp.png", "streetlamp", "behave.lua")
--REAPI:upsertSprite(0, -0.4, 10, 30.0, 1.0, "pepe.png", "pepe", "behavepepe.lua")
REAPI:playBGM("bgm.wav", true)
REAPI:endme(script_index)