-- game.lua: the actual game. For now, it loads the first map and lets you walk around.

local script_index = ...

local MAP = "world.rmap"

-- Where the player starts. This MUST be a point inside the map: read the
-- coordinates off the map editor's status line. y gets pulled down onto the floor
-- by gravity, so it only needs to be somewhere between the floor and ceiling.
local SPAWN_X, SPAWN_Y, SPAWN_Z = 5, 2, 5
local SPAWN_DIR = 0                     -- facing, in radians: 0 looks along +X

RA:audioStopBGM()                       -- the title music ends here
RA:worldLoadMapAsync(MAP, "LOADING")
RA:playerSetPosition(SPAWN_X, SPAWN_Y, SPAWN_Z, SPAWN_DIR)
RA:playerSetWalk(1.0, 0, 0)       -- eye height above the floor, head-bob speed, head-bob amount

local frames_loaded = 0                 -- frames since the map finished loading

function update()
    if RA:loadingIsActive() then return end    -- the engine is showing its loading screen
    frames_loaded = frames_loaded + 1

    -- Catch a spawn point outside the map: there the player can't move at all.
    -- This waits for the SECOND frame after loading, because the engine swaps the
    -- new map in at the start of a frame.
    if frames_loaded == 2 then
        if not RA:worldIsInsideMap(SPAWN_X, SPAWN_Z) then
            print("[game.lua] The spawn point (" .. SPAWN_X .. ", " .. SPAWN_Z .. ") is outside the map "
                  .. MAP .. ". Change SPAWN_X / SPAWN_Z to a point inside the map.")
        end
    end

    if RA:inputGetKeyStatus(true, "e") then
        -- Interact. Nothing to interact with yet; the bed comes later.
    end

    if RA:inputGetKeyStatus(true, "esc") then
        RA:systemQuit()
    end
end
