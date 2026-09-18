-- Example: a HUD using the update() pattern.
-- Register it from init.lua with:  RA:scriptAdd("example_hud.lua", 10)
--
-- Everything above update() runs ONCE. Everything inside runs every frame.

local script_index = ...

-- One-time setup. Preload what the HUD needs so the first frame is not the
-- one that pays to decode it.
RA:resourceLoad("system_font.ttf")

local hp        = 100
local frames    = 0
local FONT      = "system_font.ttf"
local NATURAL   = 0.5   -- 0.5 is TRUE colour. 1.0 is pure white. See CHANGELOG.

function update()
    frames = frames + 1                 -- a local that survives between frames

    -- Health bar: background then fill.
    RA:uiFillRect(8, 8, 104, 10, 0, NATURAL)          -- 0   = black
    RA:uiFillRect(10, 10, hp, 6, 9, NATURAL)          -- 9   = red

    RA:uiTextDefault("HP " .. hp, 10, 24, NATURAL)

    -- Right-aligned text, using uiTextWidth to measure first.
    local label = "FRAME " .. frames
    local w = RA:uiTextWidth(label, FONT)
    RA:uiText(label, RA:uiScreenWidth() - w - 10, 10, FONT, NATURAL)

    -- Simple input handling. "true" means fire once per press, not per frame.
    if RA:inputGetKeyStatus(true, "space") then
        hp = math.max(0, hp - 5)
        RA:audioPlaySE("sample_se.wav", false, 0.6)
    end
end
