local script_index = ...

-- Ok show a couple of settings like a couple of maps that are availble to us or the maps available
-- First Read the map.png or maps
-- Then after that and we select the map, select the color you want to be
-- Then after that, we are set to start
-- the game begins with the stuff. its fixed camera no need to zoom in. Just the regular pixels
-- we can however move cursor across the tiles and if we keep it hold, then we spam it across
-- enter to select a unit and shows us what kind of unit etc
-- on the top left we have a timer with 60 seconds

local map_name = "map.png"
local color_for_water = 0xff29adff
local color_for_land = 0xff00e436
local color_for_tree = 0xffab5236
local color_for_fruit = 0xff008751
local color_for_sheep = 0xfffff1e8
local color_for_stone = 0xffc2c3c7
local color_for_gold = 0xffffec27
local color_for_player1 = 0xff0000ff -- This is us
local color_for_player2 = 0xffff0000 -- This is the enemy ai for now which only skips

-- pass this to this function
REAPI:load_kingdom_play_map(map_name, color_for_water, color_for_land, color_for_tree, color_for_fruit, color_for_sheep, color_for_stone, color_for_gold, color_for_player1, color_for_player2)

-- actually start the game now for real
REAPI:add_script("gameplay.lua")
REAPI:endme(script_index)