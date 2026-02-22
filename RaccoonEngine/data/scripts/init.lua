local script_index = ...

if REAPI:getFrameNumber()==0 then
    REAPI:load_map("map.txt")
end

REAPI:displayText("Hello World! If you see this,", 10, 5, "font_16px_code.png", 255)
REAPI:displayText("it means the engine is running!", 10, 20, "font_16px_code.png", 255)

