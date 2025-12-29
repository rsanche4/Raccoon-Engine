local spriteXPos, spriteYPos, spriteZPos, sprite_length, sprite_brightness, spritename, spriteId, collision_radius = ...

local new_pos = REAPI:linePathfind(spriteXPos, spriteYPos, spriteZPos, 118, 0, 20, 0.02, false, false)

REAPI:upsertSprite(REAPI:decodeCoordinatefromPathString(new_pos, 0), REAPI:decodeCoordinatefromPathString(new_pos, 1), REAPI:decodeCoordinatefromPathString(new_pos, 2), sprite_length, sprite_brightness, spritename, spriteId, "shot_behavior.lua", collision_radius)