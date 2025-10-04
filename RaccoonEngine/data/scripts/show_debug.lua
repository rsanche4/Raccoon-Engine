local script_index = ...

local font = "font_8px.png"
REAPI:displayText("FPS:" .. tostring(REAPI:get_fps()), 10, 10, font)
REAPI:displayText(string.format("(%.2f,%.2f,%.2f,%.2f)", REAPI:get_player_pos_x(), REAPI:get_player_pos_y(), REAPI:get_player_pos_z(), REAPI:get_dir_player()), 10, 20, font)
REAPI:displayText("Sector ID:" .. REAPI:get_player_sector(), 10, 30, font)