local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

local splash_frames = {
    "splash_00.png", "splash_01.png", "splash_02.png", "splash_03.png", "splash_04.png",
    "splash_05.png", "splash_06.png", "splash_07.png", "splash_08.png", "splash_09.png",
    "splash_10.png", "splash_11.png", "splash_12.png", "splash_13.png", "splash_14.png",
    "splash_15.png", "splash_16.png", "splash_17.png", "splash_18.png", "splash_19.png",
    "splash_20.png", "splash_21.png", "splash_22.png", "splash_23.png", "splash_24.png",
    "splash_25.png", "splash_26.png", "splash_27.png", "splash_28.png", "splash_29.png",
    "splash_30.png", "splash_31.png", "splash_32.png", "splash_33.png", "splash_34.png",
    "splash_35.png", "splash_36.png", "splash_37.png", "splash_38.png", "splash_39.png",
    "splash_40.png", "splash_41.png", "splash_42.png", "splash_43.png", "splash_44.png",
    "splash_45.png", "splash_46.png", "splash_47.png", "splash_48.png", "splash_49.png",
    "splash_50.png", "splash_51.png", "splash_52.png", "splash_53.png", "splash_54.png",
    "splash_55.png", "splash_56.png", "splash_57.png", "splash_58.png", "splash_59.png",
    "splash_60.png"
}

if REAPI:readVar("SplashScreen") == 1 then
    REAPI:addUIToScreen("black.png", 0, 0, 255)
    if REAPI:readVar("splashScreenTime") >= 255 then
        REAPI:playBGM("menuBGM.wav", true)
        REAPI:writeVar("SplashScreen", 0)
        REAPI:writeVar("isMenu", 1)
        REAPI:endScript("splashscreen.lua")
    end

    local ind = (REAPI:getFrameNumber()%61)+1
    REAPI:addUIToScreen(splash_frames[ind], 0, 0, 255-REAPI:minInt(255, REAPI:abs(REAPI:readVar("splashScreenTime"))))
    REAPI:writeVar("splashScreenTime", REAPI:readVar("splashScreenTime")+2)
end