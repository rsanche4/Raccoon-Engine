local script_index = ...

REAPI:add_script("splashscreen.lua")
REAPI:add_script("ui.lua")

-- GLOBAL VARIABLES
REAPI:writeVar("level", "menu")
REAPI:writeVar("splashScreenTime", 255) 
REAPI:writeVar("warningMesTime", 255)
REAPI:writeVar("menuScreenTime", 0)
REAPI:writeVar("menuOptionSelected", 0)
REAPI:writeVar("gameStart", 0)
REAPI:writeVar("levelHubFadeTime", 0)

REAPI:endme(script_index)