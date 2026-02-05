local script_index = ...

REAPI:add_script("splashscreen.lua")

-- GLOBAL VARIABLES
REAPI:writeVar("splashScreenTime", 0)
REAPI:writeVar("warningMesTime", -255)

REAPI:endme(script_index)