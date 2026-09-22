local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

if (REAPI:abs(REAPI:getPlayerX()-event_x)<1 and REAPI:abs(REAPI:getPlayerY()-event_y)<1) then
    REAPI:stopBGM()
    REAPI:playBGM("runshoot.wav", true)
    REAPI:setWallLighting(true)
    REAPI:transferToWorld("ClassWorld", 2, 9, 1, 0, 0, -.66)
end
