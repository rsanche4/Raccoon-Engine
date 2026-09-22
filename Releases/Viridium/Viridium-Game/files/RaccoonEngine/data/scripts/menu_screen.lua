local script_index = ...

local total_time = 255
local menuTimeLightUp = REAPI:readVar("menuScreenTime")
local opacity = total_time - math.min(255, math.abs(menuTimeLightUp))
local sep = 25
local base = 240
local xstart = 256
local xcursor = 216
local mirrored_xcursor = xcursor + 152
local select_sound = "select.wav"
local enter_sound = "enter.wav"
local enter_vol = 0.87

local option_selected = REAPI:readVar("menuOptionSelected")
local game_started = REAPI:readVar("gameStart") == 1

-- Menu options: { label, x_offset }
local options
if game_started then
    options = {
        { label = "Continue", x = 0 },
        { label = "New Game", x = 0 },
        { label = "Options",  x = 8 },
        { label = "Quit",     x = 32 },
    }
else
    options = {
        { label = "New Game", x = 0 },
        { label = "Options",  x = 8 },
        { label = "Quit",     x = 32 },
    }
end

local max_ops = #options

local function start_new_game()
    REAPI:set_player_pos(76, 0, 0.3)
    REAPI:set_dir_player(4.712)
    REAPI:set_max_ray_steps(1000)
    REAPI:set_fog_settings(0, 0, 1, 5, 200)
    REAPI:toggle_glitched_sky(true)
    REAPI:load_map("hub.txt")
    local door_sign = 0
    for i = 4, 220, 10 do
        REAPI:change_world_wall_texture(i..".0,6.0,"..(i+1)..".0,6.0", "door_sign"..REAPI:padWithLeadingZeros(door_sign, 2)..".png", 0.2)
        REAPI:change_world_wall_texture((i+1)..".0,6.0,"..(i+2)..".0,6.0", "wooden_door_concretebg_left.png", 0.2)
        REAPI:change_world_wall_texture((i+2)..".0,6.0,"..(i+3)..".0,6.0", "wooden_door_concretebg_right.png", 0.2)
        door_sign = door_sign+1
    end
    REAPI:change_world_wall_texture("0.0,2.0,0.0,3.0", "mepic_left.png", 0.14)
    REAPI:change_world_wall_texture("0.0,3.0,0.0,4.0", "mepic_right.png", 0.14)
    REAPI:save_game()
    REAPI:stopBGM()
    REAPI:writeVar("level", "hub")
    REAPI:endme(script_index)
end

if menuTimeLightUp < 0 then
    REAPI:writeVar("menuScreenTime", menuTimeLightUp + 3)
else

    if REAPI:is_key_pressed_once("menudown") then
        REAPI:playSE(select_sound, false, 1)
        option_selected = (option_selected + 1) % max_ops
        REAPI:writeVar("menuOptionSelected", option_selected)
    elseif REAPI:is_key_pressed_once("menuup") then
        REAPI:playSE(select_sound, false, 1)
        option_selected = (option_selected - 1 + max_ops) % max_ops
        REAPI:writeVar("menuOptionSelected", option_selected)
    end

    local enter_pressed = REAPI:is_key_pressed_once("enter")
    if enter_pressed then
        REAPI:playSE(enter_sound, false, enter_vol)
    end

    local selected_label = options[option_selected + 1].label
    if enter_pressed then
        if selected_label == "New Game" then
            start_new_game()
        elseif selected_label == "Quit" then
            REAPI:quit()
        end
        -- "Continue" and "Options" can be handled here too when implemented
    end

    local button_variant = math.floor(REAPI:getFrameNumber() / 15) % 2
    REAPI:addUIToScreen("title" .. button_variant .. ".png", xstart, 120, 255, 1, 0)

    for i, opt in ipairs(options) do
        local row = i - 1 
        local y = base + sep * row
        local x = xstart + opt.x

        if row == option_selected then
            REAPI:addUIToScreen("cursor.png", xcursor, y, 255, 1, 0)
            REAPI:addUIToScreen("cursor.png", mirrored_xcursor, y, 255, 1, 0)
            local flash = 255 - (button_variant * 255)
            REAPI:displayText(opt.label, x, y, "font_16px.png", flash)
        else
            REAPI:displayText(opt.label, x, y, "font_16px.png", 255)
        end
    end
end

REAPI:displayText("(c) 2027 Raffy's Playground", 212, 472, "font_8px_code.png", opacity)