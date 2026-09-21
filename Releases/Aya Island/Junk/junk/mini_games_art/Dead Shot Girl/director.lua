local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

local director_on = true
if  (REAPI:readVar("health")<=0 or REAPI:readVar("score")==999) then
    director_on = false
end

local constant_spawning = false
local spawn_points = {
    {1, 1},
    {5, 1},
    {8, 3},
    {8, 17},
    {1, 15},
    {8, 23},
    {4, 24}
}
local spawn_points_len = #spawn_points
local population_array = {"sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent0.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent1.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_femalestudent2.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent0.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent1.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_malestudent2.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_femaleteacher.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_jock.png", "sprite_zom_walk0_maleteacher.png", "sprite_zom_walk0_maleteacher.png", "sprite_zom_walk0_maleteacher.png", "sprite_zom_walk0_maleteacher.png", "sprite_zom_walk0_maleteacher.png", "sprite_zom_walk0_maleteacher.png", "sprite_zom_walk0_maleteacher.png", "sprite_zom_walk0_maleteacher.png"}

if (REAPI:readVar("isGame") == 1 and director_on) then
    local remaining = REAPI:readVar("directorVarZ")
    if remaining > 0 then
        local spawn_count = REAPI:minInt(remaining, REAPI:read_entity_limit()-REAPI:read_entity_count())
        if spawn_count>0 then
            --local spawn_cur = {0, 0}
            --local spawn_second = {0, 0}
            --local spawn_third = {0, 0}
            --local cur_max_dist = 99999999
            --local second_cur_max_dist = 99999999
            --local third_cur_max_dist = 99999999
            --for i = 1, spawn_points_len do
            --    local spoint = spawn_points[i]
            --    local cur_dist = REAPI:manhattan_distance(REAPI:getPlayerX(), spoint[1], REAPI:getPlayerY(), spoint[2])
            --    if cur_dist < cur_max_dist then
            --        third_cur_max_dist = second_cur_max_dist
            --        spawn_third[1] = spawn_second[1]
            --        spawn_third[2] = spawn_second[2]
                    
            --        second_cur_max_dist = cur_max_dist
            --        spawn_second[1] = spawn_cur[1]
            --        spawn_second[2] = spawn_cur[2]
                    
            --        cur_max_dist = cur_dist
            --        spawn_cur[1] = spoint[1]
            --        spawn_cur[2] = spoint[2]
            --    elseif cur_dist < second_cur_max_dist then
            --        third_cur_max_dist = second_cur_max_dist
            --        spawn_third[1] = spawn_second[1]
            --        spawn_third[2] = spawn_second[2]
                    
            --        second_cur_max_dist = cur_dist
            --        spawn_second[1] = spoint[1]
            --        spawn_second[2] = spoint[2]
            --    elseif cur_dist < third_cur_max_dist then
            --        third_cur_max_dist = cur_dist
            --        spawn_third[1] = spoint[1]
            --        spawn_third[2] = spoint[2]
            --    end
            --end
            for i = 1, spawn_count do
                --local top_3_spawns = {spawn_cur, spawn_second, spawn_third}
                local rpind = REAPI:randomInt(1, #spawn_points)
                local point = spawn_points[rpind]
                local base_x = point[1] + 0.5
                local base_y = point[2] + 0.5
                local sprite = population_array[REAPI:randomInt(1, 100)]
                local zombie_id = sprite .. REAPI:readVar("uuid")
                REAPI:writeVar("uuid", REAPI:readVar("uuid")+1)
                local script = ""
                if (sprite=="sprite_zom_walk0_femalestudent0.png" or sprite=="sprite_zom_walk0_femalestudent1.png" or sprite=="sprite_zom_walk0_femalestudent2.png" or sprite=="sprite_zom_walk0_malestudent0.png" or sprite=="sprite_zom_walk0_malestudent1.png" or sprite=="sprite_zom_walk0_malestudent2.png") then
                    script = "common_behavior.lua"
                elseif sprite=="sprite_zom_walk0_femaleteacher.png" then
                    script = "stunner_behavior.lua"
                elseif sprite=="sprite_zom_walk0_maleteacher.png" then
                    script = "hitter_behavior.lua"
                else
                    script = "rusher_behavior.lua"
                end
                REAPI:add_entity(zombie_id, sprite, base_x, base_y, script)
            end
            remaining = remaining - spawn_count
            REAPI:writeVar("directorVarZ", remaining)
        end
    elseif (constant_spawning or REAPI:readVar("playerKillsCurrentWave")>=REAPI:readVar("directorVarZInit")) then
        REAPI:writeVar("directorVarZInit", REAPI:readVar("directorVarZInit")+REAPI:readVar("directorVarBeta"))
        REAPI:writeVar("directorVarZ", REAPI:readVar("directorVarZInit"))
        REAPI:writeVar("directorVarWaveCount", REAPI:readVar("directorVarWaveCount")+1)
        REAPI:writeVar("playerKillsCurrentWave", 0)
    end
end
