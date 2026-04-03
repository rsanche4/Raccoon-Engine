local script_index = ...

if REAPI:getFrameNumber()==0 then
    REAPI:load_map("debug_map.txt")
end

REAPI:set_skybox("clear_sky.png", 1.0)
REAPI:set_fog_settings(0x00,0x00,0x01, 5, 100)
REAPI:set_max_ray_steps(1000)
REAPI:add_script("fps_tracker.lua")
REAPI:add_script("ui.lua")
REAPI:endme(script_index)
