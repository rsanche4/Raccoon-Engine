local script_index = ...

local total_time = 255
local menuTimeLightUp = REAPI:readVar("menuScreenTime")
local opacity = total_time-math.min(255, math.abs(menuTimeLightUp))
local frame_variant = math.floor(REAPI:getFrameNumber() / 90) % 2
REAPI:addUIToScreen("menu_img" .. frame_variant .. ".png", 0, 0, opacity) -- TODO placeholder image just change to the actual menu img
-- Over here just include a real world location in game and disable movement TODO just make sure its sunny and got nice trees
local sep = 40
local base = 186
local xstart = 65

REAPI:addUIToScreen("title.png", -251+opacity, 50, opacity)
if menuTimeLightUp<0 then
    REAPI:writeVar("menuScreenTime", menuTimeLightUp+0.6)
else
    local button_variant = math.floor(REAPI:getFrameNumber() / 30) % 2
    opacity = 255-button_variant*75
    REAPI:displayText("Continue", xstart, base, "font_32px.png", opacity)
    opacity = 255
    REAPI:displayText("New Game", xstart, base+sep, "font_32px.png", opacity)
    REAPI:displayText("Options", xstart, base+sep*2, "font_32px.png", opacity)
    REAPI:displayText("Controls", xstart, base+sep*3, "font_32px.png", opacity)
    REAPI:addUIToScreen("menu_button" .. button_variant .. ".png", xstart, base, 255)
end
REAPI:displayText("(c) 2026 Raffy's Playground. All Rights Reserved.", 124, 472, "font_8px.png", opacity)