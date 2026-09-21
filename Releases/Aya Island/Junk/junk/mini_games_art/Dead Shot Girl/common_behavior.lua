local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

if (REAPI:readVar("health")<=0 or REAPI:readVar("score")==999) then
    REAPI:remove_entity(event_name)
end

local speed = 0.05 + (REAPI:randomInt(1, 10)/1000)
local ent_id = event_name

local animationsWalk = {"sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk1_femalestudent0.png", "sprite_zom_walk2_femalestudent0.png", "sprite_zom_walk1_femalestudent0.png"}
local atkAnim = "sprite_zom_atk_femalestudent0.png"

if string.find(ent_id, "femalestudent1") then
    animationsWalk = {"sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk1_femalestudent1.png", "sprite_zom_walk2_femalestudent1.png", "sprite_zom_walk1_femalestudent1.png"}
    atkAnim = "sprite_zom_atk_femalestudent1.png"
elseif string.find(ent_id, "femalestudent2") then
    animationsWalk = {"sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk1_femalestudent2.png", "sprite_zom_walk2_femalestudent2.png", "sprite_zom_walk1_femalestudent2.png"}
    atkAnim = "sprite_zom_atk_femalestudent2.png"
elseif string.find(ent_id, "_malestudent0") then
    animationsWalk = {"sprite_zom_walk0_malestudent0.png", "sprite_zom_walk1_malestudent0.png", "sprite_zom_walk2_malestudent0.png", "sprite_zom_walk1_malestudent0.png"}
    atkAnim = "sprite_zom_atk_malestudent0.png"
elseif string.find(ent_id, "_malestudent1") then
    animationsWalk = {"sprite_zom_walk0_malestudent1.png", "sprite_zom_walk1_malestudent1.png", "sprite_zom_walk2_malestudent1.png", "sprite_zom_walk1_malestudent1.png"}
    atkAnim = "sprite_zom_atk_malestudent1.png"
elseif string.find(ent_id, "_malestudent2") then
    animationsWalk = {"sprite_zom_walk0_malestudent2.png", "sprite_zom_walk1_malestudent2.png", "sprite_zom_walk2_malestudent2.png", "sprite_zom_walk1_malestudent2.png"}
    atkAnim = "sprite_zom_atk_malestudent2.png"
end

local isAtk = false

local animation_index = 1
if REAPI:getFrameNumber()%16 < 4 then
    animation_index = 2
elseif REAPI:getFrameNumber()%16 < 8 then
    animation_index = 3
elseif REAPI:getFrameNumber()%16 < 12 then
    animation_index = 4
end

if (REAPI:readVar("health")>0 and REAPI:abs(event_x-REAPI:getPlayerX())<REAPI:get_buffer_dist() and REAPI:abs(event_y-REAPI:getPlayerY())<REAPI:get_buffer_dist() and REAPI:getFrameNumber()%20 < 1) then
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
        if (string.find(ent_id, "female")) then
            REAPI:playSE("commonfemdead.wav", false)
        else
            REAPI:playSE("commondead.wav", false)
        end
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




