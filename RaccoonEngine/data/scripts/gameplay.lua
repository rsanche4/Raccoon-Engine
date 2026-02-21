local script_index = ...

local zoom = REAPI:readVar("zoom")
local cam_x = REAPI:readVar("cam_x")
local cam_y = REAPI:readVar("cam_y")
local step = math.floor(8 * zoom)/8

-- zoom in / out
-- if REAPI:is_key_pressed("space") then
--     if zoom < 3.0 then REAPI:writeVar("zoom", zoom + 1.0) end
-- end
-- if REAPI:is_key_pressed("ctrl") then
--     if zoom > 2.0 then REAPI:writeVar("zoom", zoom - 1.0) end
-- end

local arrow_left_offset = 0
local arrow_right_offset = 0
local arrow_down_offset = 0
local arrow_up_offset = 0
local gen_offset = 10

-- move camera
if REAPI:is_key_pressed("left") then
    REAPI:writeVar("cam_x", cam_x + step) 
    arrow_left_offset = -gen_offset
end
if REAPI:is_key_pressed("right") then 
    REAPI:writeVar("cam_x", cam_x - step)
    arrow_right_offset = gen_offset
end
if REAPI:is_key_pressed("up") then 
    REAPI:writeVar("cam_y", cam_y + step) 
    arrow_up_offset = -gen_offset
end
if REAPI:is_key_pressed("down") then  
    REAPI:writeVar("cam_y", cam_y - step) 
    arrow_down_offset = gen_offset
end

local zoom = REAPI:readVar("zoom")
local cam_x = REAPI:readVar("cam_x")
local cam_y = REAPI:readVar("cam_y")

REAPI:display_kingdom_map("map.png", "king.png", zoom, cam_x, cam_y)
local base_color = 0
local activated_color = 255
local color_left_arrow = base_color
local color_right_arrow = base_color
local color_up_arrow = base_color
local color_down_arrow = base_color
if (arrow_left_offset~=0) then
    color_left_arrow = activated_color
end
if (arrow_right_offset~=0) then
    color_right_arrow = activated_color
end
if (arrow_down_offset~=0) then
    color_down_arrow = activated_color
end
if (arrow_up_offset~=0) then
    color_up_arrow = activated_color
end

if REAPI:is_key_pressed_once("enter") then
    -- figure out what square did the cursor hit. its in the middle so
    REAPI:player_cursor_interact()
end

REAPI:addUIToScreen("arrow_up.png", 304, 10+arrow_up_offset, color_up_arrow, 1.0, 0x00000000)
REAPI:addUIToScreen("arrow_down.png", 304, 438+arrow_down_offset, color_down_arrow, 1.0, 0x00000000)
REAPI:addUIToScreen("arrow_left.png", 10+arrow_left_offset, 224, color_left_arrow, 1.0, 0x00000000)
REAPI:addUIToScreen("arrow_right.png", 598+arrow_right_offset, 224, color_right_arrow, 1.0, 0x00000000)

-- UI
REAPI:addUIToScreen("cursor.png", math.floor(320-(8*zoom)/2), math.floor(240-(8*zoom)/2), 255, zoom, 0x00000000)

local res_start = 10
local sep = 40
REAPI:addUIToScreen("gold_100.png", res_start, 450, 255, 3.0, 0x00000000)
REAPI:displayText(REAPI:readVar("Gold"), res_start+sep, 460, "font_16px_code.png", 255)

REAPI:addUIToScreen("stone_100.png", res_start, 420, 255, 3.0, 0x00000000)
REAPI:displayText(REAPI:readVar("Stone"), res_start+sep, 430, "font_16px_code.png", 255)

REAPI:addUIToScreen("tree_100.png", res_start, 390, 255, 3.0, 0x00000000)
REAPI:displayText(REAPI:readVar("Wood"), res_start+sep, 400, "font_16px_code.png", 255)

REAPI:addUIToScreen("fruit_100.png", res_start, 360, 255, 3.0, 0x00000000)
REAPI:displayText(REAPI:readVar("Food"), res_start+sep, 370, "font_16px_code.png", 255)

REAPI:addUIToScreen("house_100.png", res_start, 330, 255, 3.0, 0x00000000)
REAPI:displayText(REAPI:readVar("Popcount") .. "/" .. REAPI:readVar("Popcap"), res_start+sep, 340, "font_16px_code.png", 255)


REAPI:addUIToScreen("clock1.png", 10, 10, 255, 1.0, 0x00000000)
REAPI:displayText(REAPI:readVar("P1Timer"), 90, 25, "font_32px_code.png", 255)

REAPI:addUIToScreen("clock2.png", 566, 10, 255, 1.0, 0x00000000)
REAPI:displayText(REAPI:readVar("P2Timer"), 640-90-32-20, 25, "font_32px_code.png", 255)

