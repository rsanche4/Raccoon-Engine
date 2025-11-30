local script_index = ...

local cur_frame = REAPI:debug_stats("frame_num")
REAPI:set_skybox_offset(cur_frame*0.1)