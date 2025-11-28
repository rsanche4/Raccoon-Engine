local script_index = ...

REAPI:log("Running Init Script from Lua!!")
REAPI:set_player_pos(165.08583069, 1, -58.284534451)
REAPI:set_dir_player(1.30920017)
REAPI:load_map("openworld.txt")
--REAPI:set_skybox("day.png", 1)
REAPI:add_script("show_debug.lua")
--REAPI:add_script("show_ui.lua")
max_r = 500
REAPI:set_max_ray_steps(max_r)
REAPI:set_fog_settings(15,10,0, 5,max_r)
--REAPI:upsertSprite(0, -0.4, 50, 25.0, 1.0, "seldel.png", "seldel", "behaveseldel.lua")
--REAPI:upsertSprite(0, -0.4, 1, 30.0, 1.0, "streetlamp.png", "streetlamp", "behave.lua")
--REAPI:upsertSprite(0, -0.4, 10, 25.0, 1.0, "pepe.png", "pepe", "behavepepe.lua")
--REAPI:playBGM("gray.wav", true, 1)
--REAPI:toggle_plane_texture(false)

REAPI:endme(script_index)