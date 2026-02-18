local script_index = ...

REAPI:addUIToScreen("main_title.png", 0, 0, 255)
REAPI:displayText("Kingdom Turn", 128, 148, "font_32px.png", 255)
local button_variant = math.floor(REAPI:getFrameNumber() / 30) % 2
local opacity = 255-button_variant*255
REAPI:displayText("Start Match!", 224, 240, "font_16px.png", opacity)

if REAPI:is_key_pressed_once("enter") then
    REAPI:add_script("gameplay.lua")
    REAPI:endme(script_index)
end
