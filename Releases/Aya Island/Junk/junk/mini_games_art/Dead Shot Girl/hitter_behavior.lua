local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

if (REAPI:readVar("health")<=0 or REAPI:readVar("score")==999) then
    REAPI:remove_entity(event_name)
end

local speed = 0.05
local ent_id = event_name

local animationsWalk = {"sprite_zom_walk0_maleteacher.png", "sprite_zom_walk1_maleteacher.png", "sprite_zom_walk2_maleteacher.png", "sprite_zom_walk1_maleteacher.png"}
local atkAnim = "sprite_zom_atk_maleteacher.png"
local isAtk = false

local animation_index = 1
if REAPI:getFrameNumber()%16 < 4 then
    animation_index = 2
elseif REAPI:getFrameNumber()%16 < 8 then
    animation_index = 3
elseif REAPI:getFrameNumber()%16 < 12 then
    animation_index = 4
end

if (REAPI:readVar("health")>0 and REAPI:abs(event_x-REAPI:getPlayerX())<REAPI:get_buffer_dist() and REAPI:abs(event_y-REAPI:getPlayerY())<REAPI:get_buffer_dist()) then
    REAPI:writeVar("health", REAPI:readVar("health")-1)
    REAPI:addUIToScreen("claweffect.png", 0, 0, 175)
    REAPI:playSE("commonatk.wav", false)
    isAtk = true
end

if (REAPI:readVar("gunshotAnimation")==1) then
    local zx = event_y
    local zy = -event_x
    local px = REAPI:getPlayerY()
    local py = -REAPI:getPlayerX()
    local zvectorx = zx-px
    local zvectory = zy-py
    local pvectorx = REAPI:cos(REAPI:getPlayerDegree()*3.14/180)   
    local pvectory = REAPI:sin(REAPI:getPlayerDegree()*3.14/180)
    local angle_between = REAPI:getAngleBetweenVectors(zvectorx, zvectory, pvectorx, pvectory)
    local hit_threshold_angle = 4
    local hit_threshold_len = 2
    if (angle_between<hit_threshold_angle and REAPI:euclidean_distance(zx, px, zy, py)<hit_threshold_len) then
        REAPI:writeVar("score", REAPI:readVar("score")+1)
        REAPI:playSE("commondead.wav", false)
        REAPI:writeVar("playerKillsCurrentWave", REAPI:readVar("playerKillsCurrentWave")+1)
        REAPI:remove_entity(ent_id)
    end
end

if isAtk then
    local movexy = REAPI:pathfindToward(ent_id, event_x, event_y, REAPI:getPlayerX(), REAPI:getPlayerY(), speed)
    REAPI:edit_entity(ent_id, atkAnim, REAPI:decodeXfromPathString(movexy), REAPI:decodeYfromPathString(movexy))
else
    local movexy = REAPI:pathfindToward(ent_id, event_x, event_y, REAPI:getPlayerX(), REAPI:getPlayerY(), speed)
    REAPI:edit_entity(ent_id, animationsWalk[animation_index], REAPI:decodeXfromPathString(movexy), REAPI:decodeYfromPathString(movexy))
end




