local spriteXPos, spriteYPos, spriteZPos, sprite_length, sprite_brightness, spritename, spriteId = ...

local player_dist = REAPI:euclidean_distance_2D(spriteXPos, spriteZPos, REAPI:get_player_pos_x(), REAPI:get_player_pos_z())
local max_dist = 300
REAPI:changeBGMVol(math.max(0, 1-player_dist/max_dist))

local new_pos = REAPI:basicGreedyForgetfulNoCollisionPathfindToward(spriteXPos, spriteYPos, spriteZPos, REAPI:get_player_pos_x(), REAPI:get_player_pos_y(), REAPI:get_player_pos_z(), 0.5)

REAPI:upsertSprite(REAPI:decodeCoordinatefromPathString(new_pos, 0), REAPI:decodeCoordinatefromPathString(new_pos, 1), REAPI:decodeCoordinatefromPathString(new_pos, 2), sprite_length, sprite_brightness, spritename, spriteId, "behaveseldel.lua")