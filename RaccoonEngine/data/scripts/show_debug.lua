local script_index = ...

REAPI:displayText("FPS:" .. tostring(REAPI:get_fps()), 10, 10, "font_8px.png")
REAPI:displayText(string.format("(%.2f,%.2f,%.2f,%.2f)", REAPI:get_player_pos_x(), REAPI:get_player_pos_y(), REAPI:get_player_pos_z(), REAPI:get_dir_player()), 10, 20, "font_8px.png")
REAPI:displayText("Sector ID:" .. REAPI:get_player_sector(), 10, 30, "font_8px.png")