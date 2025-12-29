local script_index = ...

REAPI:load_map("debugA.txt")
REAPI:set_player_pos(0, 1, 0)
REAPI:set_dir_player(0.1)
REAPI:set_max_ray_steps(1000)
REAPI:set_fog_settings(0, 0, 1, 25, 1000)
REAPI:add_script("ui.lua")
REAPI:add_script("player_script.lua")

-- debugging bullets
REAPI:writeVar("uuid", 0)

REAPI:endme(script_index)