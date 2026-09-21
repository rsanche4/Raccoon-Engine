local script_index = ...

local total_time = 255
local menuTimeLightUp = REAPI:readVar("menuScreenTime")
local opacity = total_time-math.min(255, math.abs(menuTimeLightUp))
local sep = 20
local base = 120
local xstart = 96

REAPI:addUIToScreen("title2.png", xstart, 50, opacity)
if menuTimeLightUp<0 then
    REAPI:writeVar("menuScreenTime", menuTimeLightUp+3)
else
    local button_variant = math.floor(REAPI:getFrameNumber() / 30) % 2
    opacity = 255-button_variant*255
    REAPI:displayText("Continue", xstart, base, "font_16px.png", opacity)
    opacity = 255
    REAPI:displayText("New Game", xstart, base+sep, "font_16px.png", opacity)
    REAPI:displayText("Options", xstart, base+sep*2, "font_16px.png", opacity)
    REAPI:displayText("Controls", xstart, base+sep*3, "font_16px.png", opacity)
    --REAPI:addUIToScreen("menu_button" .. button_variant .. ".png", xstart, base, 255)
end
--REAPI:displayText("(c) 2026 Raffy's Playground. All Rights Reserved.", 0, 232, "font_8px.png", opacity)