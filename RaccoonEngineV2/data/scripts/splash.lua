-- splash.lua: the company logo, then the title screen fades in.
--
-- The engine has no transparency blending, so every fade goes through black:
-- a fade is just an image's brightness moving between 0 (black) and 0.5
-- (its true colours).

local script_index = ...

local W, H = RA:uiScreenWidth(), RA:uiScreenHeight()
local FPS = RA:systemGetMaxFPS()   -- ticks per second, so the timings hold if you change it

-- Timeline, in seconds. Tweak freely.
local LOGO_FADE_IN  = 1.0
local LOGO_HOLD     = 2.0
local LOGO_FADE_OUT = 1.0
local BLACK_PAUSE   = 0.5
local TITLE_FADE_IN = 1.5

-- When each phase ends, counted from the start of the splash.
local hold_start   = LOGO_FADE_IN
local fade_start   = hold_start + LOGO_HOLD
local logo_gone    = fade_start + LOGO_FADE_OUT
local title_start  = logo_gone + BLACK_PAUSE
local title_done   = title_start + TITLE_FADE_IN

local TRUE_COLOURS = 0.5           -- brightness 0.5 draws an image as-is; 0 is black

local LOGO_SIZE = 256
local LOGO_X = math.floor((W - LOGO_SIZE) / 2)
local LOGO_Y = math.floor((H - LOGO_SIZE) / 2)

-- Load both images now, so neither hitches halfway through a fade.
RA:resourceLoad("logo.png")
RA:resourceLoad("title.png")

-- 0 before `start`, rising to 1 over `length` seconds, then staying at 1.
local function ramp(t, start, length)
    local p = (t - start) / length
    if p < 0 then return 0 end
    if p > 1 then return 1 end
    return p
end

local frame = 0

local start_music = true

function update()
    frame = frame + 1
    local t = frame / FPS          -- seconds since the splash started

    if t < logo_gone then
        -- Fade in, hold, fade out.
        local brightness = TRUE_COLOURS * ramp(t, 0, LOGO_FADE_IN) * (1 - ramp(t, fade_start, LOGO_FADE_OUT))
        RA:uiDraw("logo.png", LOGO_X, LOGO_Y, 4, brightness)

    elseif t >= title_start then
        if start_music then
            RA:audioPlayBGM("title.wav", true, 0.9)
        end
        start_music = false

        RA:uiDraw("title.png", 0, 0, 1, TRUE_COLOURS * ramp(t, title_start, TITLE_FADE_IN))

        if t >= title_done then
            -- menu.lua draws the same image at full brightness from the next
            -- frame on, so the handover is seamless.
            RA:scriptAdd("menu.lua", 10)
            RA:scriptEndByName("splash.lua")
        end
    end
    -- Between the two (the black pause), nothing is drawn.
end
