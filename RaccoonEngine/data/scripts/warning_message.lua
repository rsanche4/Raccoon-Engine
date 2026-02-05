local script_index = ...

local total_time = 255

if REAPI:readVar("warningMesTime") >= total_time then
    REAPI:playSE("menu_ambiance.wav", true, 0.8)
    REAPI:endme(script_index)
end

local sep = 30
local base = 120
local opacity = total_time-math.min(255, math.abs(REAPI:readVar("warningMesTime")))
REAPI:displayText("The content of this videogame is", 40, base, "font_16px.png", opacity)
REAPI:displayText("purely fictional.", 40, base+sep, "font_16px.png", opacity)
REAPI:displayText("This game uses an autosave feature.", 40, base+sep*3, "font_16px.png", opacity)
REAPI:displayText("Do not turn off the game while", 40, base+sep*4, "font_16px.png", opacity)
REAPI:displayText("this symbol is displayed.", 40, base+sep*5, "font_16px.png", opacity)
REAPI:addUIToScreen("saveicon.png", 288+0.04*REAPI:getFrameNumber()%5, base+sep*6, opacity)
REAPI:writeVar("warningMesTime", REAPI:readVar("warningMesTime")+0.6)