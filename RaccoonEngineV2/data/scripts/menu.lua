local script_index = ...

local W, H = RA:uiScreenWidth(), RA:uiScreenHeight()
local FPS = RA:systemGetMaxFPS()

local FONT = "yumenikkifont.ttf"
local OPTIONS = { "New Game", "Dream Diary", "Quit" }

-- Layout: title.png is empty between y 254 and 455, so the list sits in there.
local MENU_TOP = 316                  -- y of the first option
local LINE_H   = 28                   -- distance between options

-- Look
local BRIGHT_SELECTED   = 0.5         -- true colours
local BRIGHT_UNSELECTED = 0.4         -- slightly dimmer, so you can tell them apart
local BLINK_SECONDS     = 0.25        -- the selected option is on this long, then off this long

-- Sound
local SE_VOLUME = 0.9

-- Load everything up front so the first keypress doesn't hitch.
RA:resourceLoad(FONT)
RA:resourceLoad("cursor.wav")
RA:resourceLoad("select.wav")

-- The text never changes, so centre each option once instead of every frame.
local option_x = {}
for i, text in ipairs(OPTIONS) do
    option_x[i] = math.floor((W - RA:uiTextWidth(text, FONT)) / 2)
end

local BLINK_FRAMES = math.max(1, math.floor(BLINK_SECONDS * FPS))
local selected = 1
local blink_frame = 0

-- True on the frame any of the given keys goes down.
local function pressed(...)
    for _, key in ipairs({...}) do
        if RA:inputGetKeyStatus(true, key) then return true end
    end
    return false
end

-- Move the cursor by +1 / -1, wrapping from the last option to the first and back.
local function move(step)
    selected = (selected - 1 + step) % #OPTIONS + 1
    blink_frame = 0                   -- restart the blink so the new option shows at once
    RA:audioPlaySE("cursor.wav", false, SE_VOLUME)
end

function update()
    blink_frame = blink_frame + 1

    -- Input
    if pressed("arrow_up", "w") then move(-1) end
    if pressed("arrow_down", "s") then move(1) end

    if pressed("enter", "z") then
        RA:audioPlaySE("select.wav", false, SE_VOLUME)
        -- Nothing happens yet. OPTIONS[selected] is the option that was chosen.
    end

    if pressed("esc") then
        RA:systemQuit()
    end

    -- Draw
    RA:uiFillRect(0, 0, W, H, 0, 0.5)                       -- black backdrop
    RA:uiDraw("title.png", 0, 0, 1, 0.5)

    local blink_on = math.floor(blink_frame / BLINK_FRAMES) % 2 == 0
    for i, text in ipairs(OPTIONS) do
        local y = MENU_TOP + (i - 1) * LINE_H
        if i ~= selected then
            RA:uiText(text, option_x[i], y, FONT, BRIGHT_UNSELECTED)
        elseif blink_on then
            RA:uiText(text, option_x[i], y, FONT, BRIGHT_SELECTED)
        end
    end
end
