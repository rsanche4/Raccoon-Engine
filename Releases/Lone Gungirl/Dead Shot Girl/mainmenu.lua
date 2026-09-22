local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

if REAPI:readVar("isMenu")==1 then
    REAPI:addUIToScreen("black.png", 0, 0, 255)
    if REAPI:readVar("menuFadeIn") < 255 then
        REAPI:writeVar("menuFadeIn", REAPI:readVar("menuFadeIn")+1)
    end
    local fadein = REAPI:readVar("menuFadeIn")
    REAPI:addUIToScreen("menubg.png", 0, 0, fadein)
    REAPI:addUIToScreen("girlmenu.png", fadein-180, -16, fadein)
    REAPI:addUIToScreen("gametitle.png", 10, 265-fadein, fadein)
    
    if REAPI:getFrameNumber()%10<5 then
        REAPI:displayText("Start", 270-REAPI:readVar("menuFadeIn"), 218, "font_16px.png")
    end

    if (REAPI:getKeyPressed("enter") and REAPI:readVar("pressEnterOnce")==1) then
        REAPI:writeVar("pressEnterOnce", 0)
    end

    if (REAPI:getKeyReleased("enter") and REAPI:readVar("pressEnterOnce")==0) then
        REAPI:writeVar("isIntro", 1)
        REAPI:writeVar("isMenu", 0)
        REAPI:writeVar("pressEnterOnce", 1)
        REAPI:stopBGM()
        REAPI:playBGM("prologue.wav", true)
        REAPI:playSE("start.wav", false)
        REAPI:endScript("mainmenu.lua")
    end

end

