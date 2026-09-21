local script_index = ...

local FONT = "system_font.ttf"
local W, H = RA:uiScreenWidth(), RA:uiScreenHeight()

-- NOTE: LuaJ 3.0.2 is Lua 5.2 -- there is no "//" operator. Use math.floor.
local function centered(text, y, brightness)
    local x = math.floor((W - RA:uiTextWidth(text, FONT)) / 2)
    RA:uiText(text, x, y, FONT, brightness)
end

function update()
    RA:uiFillRect(0, 0, W, H, 0, 0.5)                       -- black backdrop
    centered("MY GAME", math.floor(H / 2) - 60, 0.5)

    local bw, bh = 190, 30
    RA:uiFillRect(math.floor(W / 2) - math.floor(bw / 2), math.floor(H / 2) - 10, bw, bh, 12, 0.5)
    centered("[ ENTER ]  START", math.floor(H / 2) - 2, 0.8)
    centered("ESC to quit", math.floor(H / 2) + 50, 0.45)

    if RA:inputGetKeyStatus(true, "enter") then
        RA:scriptAdd("game.lua", 10)
        RA:scriptEndByName("menu.lua")
    end
    if RA:inputGetKeyStatus(true, "esc") then
        RA:systemQuit()
    end
end
