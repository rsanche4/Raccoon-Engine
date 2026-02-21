local script_index = ...

if REAPI:getFrameNumber()==0 then
    REAPI:playBGM("menu_theme.wav", true, 1.0)
end

REAPI:writeVar("zoom", 3.0)
REAPI:writeVar("cam_x", 0)
REAPI:writeVar("cam_y", 0)

REAPI:writeVar("Gold",0)
REAPI:writeVar("Wood",0)
REAPI:writeVar("Stone",0)
REAPI:writeVar("Food",0)
REAPI:writeVar("Popcap",3) -- this is the minim population we can have, add to this as we keep track of the number of houses
REAPI:writeVar("Popcount",3)

REAPI:writeVar("P1Timer", 60)
REAPI:writeVar("P2Timer", 60)

REAPI:addUIToScreen("main_title2.png", 0, 0, 255, 1.0, 0x00000000)

REAPI:addUIToScreen("main_title1.png", 0, math.max(0, 200-REAPI:getFrameNumber()), 255, 1.0, 0x00000000)
REAPI:addUIToScreen("main_title0.png", 0, 0, 255, 1.0, 0x00000000)
--REAPI:displayText("Kingdom Turn", 128, 148, "font_32px.png", 255)
local button_variant = math.floor(REAPI:getFrameNumber() / 30) % 2
local opacity = 255-button_variant*255
REAPI:displayText("Start", 220, 310, "font_32px_code.png", opacity)

if REAPI:is_key_pressed_once("enter") then
    REAPI:add_script("init_gameplay.lua")
    REAPI:stopBGM()
    REAPI:endme(script_index)
end
