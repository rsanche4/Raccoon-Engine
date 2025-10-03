local script_index = ...

REAPI:displayText("FPS:" .. tostring(REAPI:get_fps()), 10, 10, "font_32px.png")
REAPI:displayText(string.format("(%.2f,%.2f,%.2f,%.2f)", REAPI:get_player_pos_x(), REAPI:get_player_pos_y(), REAPI:get_player_pos_z(), REAPI:get_dir_player()), 10, 40, "font_32px.png")
REAPI:displayText(string.format("Sector ID:%.2f", REAPI:get_player_sector()), 10, 70, "font_32px.png")
REAPI:addUIToScreen("crosshair.png", 400, 300, 240)