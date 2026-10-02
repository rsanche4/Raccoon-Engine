-- intro.lua: a black screen with a short notice and the controls.
-- The text fades in; once it has been up for a moment, Enter (or Z)
-- fades it out and starts the game.

local script_index = ...

local W, H = RA:uiScreenWidth(), RA:uiScreenHeight()
local FPS = RA:systemGetMaxFPS()
local FONT = "yumenikkifont.ttf"

-- Plain ASCII only: the engine draws characters 32-126, so curly quotes and
-- long dashes would simply not appear.
local PARAGRAPHS = {
    "This game is a fan-made 3D recreation of Yume Nikki, built on a custom 2.5D game engine.",
    "Unlike the original game, there are no effects to collect.",
    "To begin, go to bed in Madotsuki's room.",
}

local CONTROLS = {
    { "WASD",  "Move" },
    { "Mouse", "Look around" },
    { "E",     "Interact" },
    { "Esc",   "Quit" },
}

local PROMPT = "Press Enter to continue"

-- Layout
local MAX_WIDTH  = 520      -- paragraphs wrap at this width
local LINE_H     = 22       -- distance between lines
local BLOCK_GAP  = 14       -- extra space between paragraphs
local COLUMN_GAP = 12       -- space either side of the centre in the controls table
local PROMPT_Y   = H - 50

-- Timing, in seconds
local FADE_IN       = 0.75
local MIN_SHOW      = 1.0   -- Enter is ignored until the text has been up this long
local FADE_OUT      = 0.5
local BLINK_SECONDS = 0.5   -- the prompt is on this long, then off this long

local BRIGHT    = 0.5       -- true colours
local SE_VOLUME = 0.9

RA:resourceLoad(FONT)
RA:resourceLoad("select.wav")

-- Splits a paragraph into lines no wider than MAX_WIDTH.
local function wrap(text)
    local result, line = {}, ""
    for word in text:gmatch("%S+") do
        local candidate = (line == "") and word or (line .. " " .. word)
        if line ~= "" and RA:uiTextWidth(candidate, FONT) > MAX_WIDTH then
            result[#result + 1] = line
            line = word
        else
            line = candidate
        end
    end
    if line ~= "" then result[#result + 1] = line end
    return result
end

-- Work out where every line goes, once.
local lines = {}            -- each entry: { text, x, y }
local function add(text, x, y)
    lines[#lines + 1] = { text = text, x = x, y = y }
end

local blocks = {}
for _, p in ipairs(PARAGRAPHS) do
    blocks[#blocks + 1] = wrap(p)
end

-- Total height: every paragraph line, every controls row, and a gap after each paragraph.
local height = #CONTROLS * LINE_H + #blocks * BLOCK_GAP
for _, block in ipairs(blocks) do
    height = height + #block * LINE_H
end

-- Centre the whole block in the space above the prompt.
local y = math.floor((PROMPT_Y - height) / 2)
for _, block in ipairs(blocks) do
    for _, text in ipairs(block) do
        add(text, math.floor((W - RA:uiTextWidth(text, FONT)) / 2), y)
        y = y + LINE_H
    end
    y = y + BLOCK_GAP
end

-- Controls: keys right-aligned against the centre, actions left-aligned after it.
local centre = math.floor(W / 2)
for _, row in ipairs(CONTROLS) do
    add(row[1], centre - COLUMN_GAP - RA:uiTextWidth(row[1], FONT), y)
    add(row[2], centre + COLUMN_GAP, y)
    y = y + LINE_H
end

local prompt_x = math.floor((W - RA:uiTextWidth(PROMPT, FONT)) / 2)
local BLINK_FRAMES = math.max(1, math.floor(BLINK_SECONDS * FPS))

-- True on the frame any of the given keys goes down.
local function pressed(...)
    for _, key in ipairs({...}) do
        if RA:inputGetKeyStatus(true, key) then return true end
    end
    return false
end

local frame = 0
local leave_frame = nil     -- the frame Enter was pressed on

function update()
    frame = frame + 1
    local t = frame / FPS

    if pressed("esc") then
        RA:systemQuit()
    end

    if not leave_frame and t >= MIN_SHOW and pressed("enter", "z") then
        RA:audioPlaySE("select.wav", false, SE_VOLUME)
        leave_frame = frame
    end

    -- Fade in from black, and back out once Enter has been pressed.
    local brightness = BRIGHT * math.min(1, t / FADE_IN)
    if leave_frame then
        local p = (frame - leave_frame) / (FADE_OUT * FPS)
        if p >= 1 then
            RA:scriptAdd("game.lua", 10)
            RA:scriptEndByName("intro.lua")
            return
        end
        brightness = brightness * (1 - p)
    end

    RA:uiFillRect(0, 0, W, H, 0, 0.5)                       -- black backdrop
    for _, l in ipairs(lines) do
        RA:uiText(l.text, l.x, l.y, FONT, brightness)
    end

    -- The prompt only appears once Enter will be accepted, and blinks.
    if not leave_frame and t >= MIN_SHOW and math.floor(frame / BLINK_FRAMES) % 2 == 0 then
        RA:uiText(PROMPT, prompt_x, PROMPT_Y, FONT, BRIGHT)
    end
end
