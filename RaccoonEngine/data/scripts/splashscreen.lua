local script_index = ...

if REAPI:readVar("splashScreenTime") >= 255 then
    REAPI:add_script("warning_message.lua")
    REAPI:playBGM("menu_bgm.wav", true, 0.9)
    REAPI:endme(script_index)
end

local splash = "splash_" .. REAPI:padWithLeadingZeros(REAPI:getFrameNumber()%61, 2) .. ".png"
REAPI:addUIToScreen(splash, 160, 120, 255-math.min(255, math.abs(REAPI:readVar("splashScreenTime"))))
REAPI:writeVar("splashScreenTime", REAPI:readVar("splashScreenTime")+1)