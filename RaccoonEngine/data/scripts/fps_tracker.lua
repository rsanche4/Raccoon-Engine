local script_index = ...

local fps = REAPI:debug_stats("fps")
REAPI:displayText(fps, 0, 0, "font_16px_code.png", 255)