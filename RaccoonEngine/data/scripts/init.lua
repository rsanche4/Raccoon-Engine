local script_index = ...

REAPI:log("Running Init Script from Lua!!")
REAPI:set_player_pos(0.1, 1, 0.1)

REAPI:load_map("level_test.txt")
--REAPI:set_skybox("day.png", 1)
REAPI:add_script("show_debug.lua")
--REAPI:add_script("show_ui.lua")
REAPI:set_max_ray_steps(500)
REAPI:set_fog_settings(15,10,0, 5,500)
--REAPI:upsertSprite(0, -0.4, 50, 25.0, 1.0, "seldel.png", "seldel", "behaveseldel.lua")
--REAPI:upsertSprite(0, -0.4, 1, 30.0, 1.0, "streetlamp.png", "streetlamp", "behave.lua")
--REAPI:upsertSprite(0, -0.4, 10, 25.0, 1.0, "pepe.png", "pepe", "behavepepe.lua")
--REAPI:playBGM("seldel.wav", true, 0)
--REAPI:toggle_plane_texture(false)

REAPI:endme(script_index)