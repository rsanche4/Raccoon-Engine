local script_index = ...

-- Quick way for debugging of having a random bullet everytime
local bulletid = REAPI:readVar("uuid") + 1
REAPI:writeVar("uuid", bulletid)

if REAPI:is_key_pressed_once("leftclick") then
    REAPI:upsertSprite(REAPI:get_player_pos_x(), REAPI:get_player_pos_y(), REAPI:get_player_pos_z(), 1000.0, 1.0, "shot.png", "shot"..bulletid, "shot_behavior.lua", 0)
end