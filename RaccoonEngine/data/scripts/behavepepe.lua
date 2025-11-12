local spriteXPos, spriteYPos, spriteZPos, sprite_length, sprite_brightness, spritename, spriteId = ...

local new_pos = REAPI:direct_PathfindToward(spriteXPos, spriteYPos, spriteZPos, REAPI:get_player_pos_x(), REAPI:get_player_pos_y(), REAPI:get_player_pos_z(), 0.1)

REAPI:upsertSprite(REAPI:decodeCoordinatefromPathString(new_pos, 0), REAPI:decodeCoordinatefromPathString(new_pos, 1), REAPI:decodeCoordinatefromPathString(new_pos, 2), sprite_length, sprite_brightness, spritename, spriteId, "behavepepe.lua")