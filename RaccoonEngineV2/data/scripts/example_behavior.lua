-- Example: a sprite behaviour script.
-- Attach it when you create the sprite:
--   RA:entityUpsertSprite("guard1", 8,2,10, 60, 0.5, "guard.png",
--                         "example_behavior.lua", 0.5, 0)
--
-- Behaviour scripts receive the sprite's full current state as arguments.
-- IMPORTANT: every sprite using THIS FILE shares one Lua environment, so
-- per-entity state must be keyed by the sprite's id.

local x, y, z, length, brightness, spritename, id, radius, dir = ...

local speed  = 0.02
local LEFT_X = 2
local RIGHT_X = 10

-- Per-entity state lives in the store, keyed by id.
local key = "patrol_right_" .. id
local going_right = RA:storeGet(key)
if going_right == nil then going_right = true end

local new_x = x
if going_right then
    new_x = x + speed
    if new_x > RIGHT_X then going_right = false end
else
    new_x = x - speed
    if new_x < LEFT_X then going_right = true end
end

RA:storeSet(key, going_right)

-- Write the new state back. Upsert with the same id updates in place.
RA:entityUpsertSprite(id, new_x, y, z, length, brightness / 31,
                      spritename, "example_behavior.lua", radius, dir)
