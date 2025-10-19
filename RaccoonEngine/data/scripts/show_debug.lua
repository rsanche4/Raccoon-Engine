local script_index = ...

local font = "font_8px.png"

-- Function to update cached stats
local stats_cache = {
    -- Memory stats
    used_mem = REAPI:debug_stats("used_mem"),
    total_mem = REAPI:debug_stats("total_mem"),
    max_mem = REAPI:debug_stats("max_mem"),
    free_mem = REAPI:debug_stats("free_mem"),
    mem_usage_percent = REAPI:debug_stats("mem_usage_percent"),
    
    -- Performance stats
    fps = REAPI:debug_stats("fps"),
    max_fps = REAPI:debug_stats("max_fps"),
    
    -- System stats
    cpu_cores = REAPI:debug_stats("cpu_cores"),
    active_threads = REAPI:debug_stats("active_threads"),

    -- Resource counts
    texture_count = REAPI:debug_stats("texture_count"),
    sprite_count = REAPI:debug_stats("sprite_count"),
    sector_count = REAPI:debug_stats("sector_count"),
    wall_count = REAPI:debug_stats("wall_count"),
    portal_count = REAPI:debug_stats("portal_count"),
    active_scripts = REAPI:debug_stats("active_scripts"),
    
    -- Screen info
    screen_width = REAPI:debug_stats("screen_width"),
    screen_height = REAPI:debug_stats("screen_height"),
    total_pixels = REAPI:debug_stats("total_pixels"),
    
    -- Advanced stats
    heap_memory_used = REAPI:debug_stats("heap_memory_used"),
    heap_memory_max = REAPI:debug_stats("heap_memory_max"),
    non_heap_memory_used = REAPI:debug_stats("non_heap_memory_used"),
    thread_peak_count = REAPI:debug_stats("thread_peak_count"),
    thread_total_started = REAPI:debug_stats("thread_total_started"),
    classes_loaded = REAPI:debug_stats("classes_loaded"),
    classes_total_loaded = REAPI:debug_stats("classes_total_loaded"),
    classes_unloaded = REAPI:debug_stats("classes_unloaded")
}

-- Get current frame number
local current_frame = REAPI:debug_stats("frame_num")

-- Display player info (updated every frame)
local y = 10
REAPI:displayText(string.format("Pos: (%.2f, %.2f, %.2f) Dir: %.2f", 
    REAPI:get_player_pos_x(), 
    REAPI:get_player_pos_y(), 
    REAPI:get_player_pos_z(), 
    REAPI:get_dir_player()), 10, y, font)
y = y + 10

REAPI:displayText("Sector ID: " .. REAPI:get_player_sector(), 10, y, font)
y = y + 10

-- Separator
y = y + 5
REAPI:displayText("=== PERFORMANCE ===", 10, y, font)
y = y + 10

-- Performance stats
REAPI:displayText(string.format("FPS: %.1f / %.1f", stats_cache.fps or 0, stats_cache.max_fps or 0), 10, y, font)
y = y + 10

REAPI:displayText(string.format("Frame: %d", current_frame), 10, y, font)
y = y + 10

-- Separator
y = y + 5
REAPI:displayText("=== MEMORY (MB) ===", 10, y, font)
y = y + 10

-- Memory stats
REAPI:displayText(string.format("Used: %d / %d MB (%d%%)", 
    stats_cache.used_mem or 0, 
    stats_cache.max_mem or 0,
    stats_cache.mem_usage_percent or 0), 10, y, font)
y = y + 10

REAPI:displayText(string.format("Allocated: %d MB | Free: %d MB", 
    stats_cache.total_mem or 0, 
    stats_cache.free_mem or 0), 10, y, font)
y = y + 10

REAPI:displayText(string.format("Heap: %d / %d MB", 
    stats_cache.heap_memory_used or 0, 
    stats_cache.heap_memory_max or 0), 10, y, font)
y = y + 10

REAPI:displayText(string.format("Non-Heap: %d MB", 
    stats_cache.non_heap_memory_used or 0), 10, y, font)
y = y + 10

-- Separator
y = y + 5
REAPI:displayText("=== SYSTEM ===", 10, y, font)
y = y + 10

-- System stats
REAPI:displayText(string.format("CPU Cores: %d | Threads: %d (Peak: %d)", 
    stats_cache.cpu_cores or 0, 
    stats_cache.active_threads or 0,
    stats_cache.thread_peak_count or 0), 10, y, font)
y = y + 10

REAPI:displayText(string.format("Total Threads Started: %d", 
    stats_cache.thread_total_started or 0), 10, y, font)
y = y + 10

-- Separator
y = y + 5
REAPI:displayText("=== RESOURCES ===", 10, y, font)
y = y + 10

-- Resource counts
REAPI:displayText(string.format("Textures: %d | Sprites: %d", 
    stats_cache.texture_count or 0, 
    stats_cache.sprite_count or 0), 10, y, font)
y = y + 10

REAPI:displayText(string.format("Sectors: %d | Walls: %d | Portals: %d", 
    stats_cache.sector_count or 0,
    stats_cache.wall_count or 0,
    stats_cache.portal_count or 0), 10, y, font)
y = y + 10

REAPI:displayText(string.format("Active Scripts: %d", 
    stats_cache.active_scripts or 0), 10, y, font)
y = y + 10

-- Separator
y = y + 5
REAPI:displayText("=== CLASSES ===", 10, y, font)
y = y + 10

-- Class loading stats
REAPI:displayText(string.format("Loaded: %d | Total: %d | Unloaded: %d", 
    stats_cache.classes_loaded or 0,
    stats_cache.classes_total_loaded or 0,
    stats_cache.classes_unloaded or 0), 10, y, font)
y = y + 10

-- Separator
y = y + 5
REAPI:displayText("=== DISPLAY ===", 10, y, font)
y = y + 10

-- Display info
REAPI:displayText(string.format("Resolution: %dx%d (%d pixels)", 
    stats_cache.screen_width or 0, 
    stats_cache.screen_height or 0,
    stats_cache.total_pixels or 0), 10, y, font)
y = y + 10

local game_width = REAPI:debug_stats("game_width")
REAPI:set_skybox_offset(current_frame%(game_width*4))