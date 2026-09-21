local script_index = ...
local FONT = "system_font.ttf"
RA:worldLoadMapAsync("world.txt", "LOADING WORLD")
RA:playerSetPosition(10, 2, 10, 0)
RA:playerSetWalk(1.0, 0, 0)
RA:worldSetSkybox("default_sky.png", 0.5)
local function fmt(v) return string.format("%.1f", v) end
function update()
    if RA:loadingIsActive() then return end
    local x, y, z = RA:playerGetPosition(0), RA:playerGetPosition(1), RA:playerGetPosition(2)
    RA:uiFillRect(6, 6, 150, 56, 0, 0.35)
    RA:uiText("X " .. fmt(x), 12, 10, FONT, 0.6)
    RA:uiText("Y " .. fmt(y), 12, 26, FONT, 0.6)
    RA:uiText("Z " .. fmt(z), 12, 42, FONT, 0.6)
    if RA:inputGetKeyStatus(true, "esc") then RA:systemQuit() end
end
