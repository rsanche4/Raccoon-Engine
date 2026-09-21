local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

if REAPI:readVar("isIntro") == 1 then
    local separ = 10
    local initplace_x = 10
    local initplace = 275-REAPI:readVar("introFadeIn")

    if REAPI:readVar("introFadeIn") < 245 then
        REAPI:writeVar("introFadeIn", REAPI:readVar("introFadeIn")+1)
    end

    REAPI:addUIToScreen("menuDark.png", 0, 0, 255)
    REAPI:displayText("She was supposed to be in algebra", initplace_x, initplace, "font_8px.png")
    REAPI:displayText("class. Instead, she was up on the", initplace_x, initplace + separ, "font_8px.png")
    REAPI:displayText("school's rooftop, watching gun", initplace_x, initplace + separ*2, "font_8px.png")
    REAPI:displayText("videos and carving bullet calibers", initplace_x, initplace + separ*3, "font_8px.png")
    REAPI:displayText("into her notebook.", initplace_x, initplace + separ*4, "font_8px.png")
    REAPI:displayText("Teachers said it was a phase...", initplace_x, initplace + separ*5, "font_8px.png")
    REAPI:displayText("and a problem.", initplace_x, initplace + separ*6, "font_8px.png")
    REAPI:displayText(" ", initplace_x, initplace + separ*7, "font_8px.png")
    REAPI:displayText("Then the sky cracked open, the dead", initplace_x, initplace + separ*8, "font_8px.png")
    REAPI:displayText("started walking, and suddenly her", initplace_x, initplace + separ*9, "font_8px.png")
    REAPI:displayText("weird obsession with recoil patterns", initplace_x, initplace + separ*10, "font_8px.png")
    REAPI:displayText("didn't seem so weird anymore.", initplace_x, initplace + separ*11, "font_8px.png")
    REAPI:displayText(" ", initplace_x, initplace + separ*12, "font_8px.png")
    REAPI:displayText("Now, the only math problem she needs", initplace_x, initplace + separ*13, "font_8px.png")
    REAPI:displayText("to solve is subtracting the undead", initplace_x, initplace + separ*14, "font_8px.png")
    REAPI:displayText("from existence. And her beloved", initplace_x, initplace + separ*15, "font_8px.png")
    REAPI:displayText("magnum sure as hell has the solution.", initplace_x, initplace + separ*16, "font_8px.png")
    REAPI:displayText(" ", initplace_x, initplace + separ*17, "font_8px.png")
    if REAPI:getFrameNumber()%20<15 then
        REAPI:displayText("Press Start to Begin", initplace_x, initplace + separ*19, "font_8px.png")
    end

    if (REAPI:getKeyPressed("enter") and REAPI:readVar("pressEnterOnce")==1 and REAPI:readVar("isIntro")==1) then
        REAPI:writeVar("isIntro", 0)
        REAPI:writeVar("isGame", 1)
        REAPI:setPlayerX(10)
        REAPI:setPlayerY(10)
        REAPI:setPlayerDirection(1, 0, 0, -.66)
        REAPI:stopBGM()
        REAPI:playSE("start.wav", false)
        REAPI:playBGM("skyline.wav", true)
        REAPI:setWallLighting(false)
        REAPI:endScript("intro.lua")
    end

end


