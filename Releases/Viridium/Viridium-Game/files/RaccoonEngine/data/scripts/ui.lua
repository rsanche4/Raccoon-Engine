local script_index = ...

local fade_duration = 255
local frame = REAPI:readVar("levelHubFadeTime")
if (REAPI:readVar("level")=="hub" and frame<fade_duration) then
      -- frames until fully gone
    local start_opacity = 255
    local opacity = math.max(0.0, start_opacity - frame)

    REAPI:addUIToScreen("fadein.png", 0, 0, opacity, 1, 0)
    REAPI:writeVar("levelHubFadeTime", frame+1)
end

REAPI:addUIToScreen("uiball.png", 0, 0, 255, 1, 0)
REAPI:displayText(REAPI:debug_stats("fps"), 0, 0, "font_8px_code.png", 255)