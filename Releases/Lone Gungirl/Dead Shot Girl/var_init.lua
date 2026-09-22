local event_x = ...
local event_y = select(2, ...)
local event_name = select(3, ...)

REAPI:writeVar("isIntro", 0)
REAPI:writeVar("isMenu", 0)
REAPI:writeVar("SplashScreen", 1)
REAPI:writeVar("isGame", 0)
REAPI:writeVar("isGameOver", 0)
REAPI:writeVar("IntroDialogue", 1)

REAPI:writeVar("pressEnterOnce", 1)
REAPI:writeVar("pressSpaceOnce", 1)
REAPI:writeVar("pressCtrlOnce", 1)
REAPI:writeVar("ammo", 7)
REAPI:writeVar("health", 10)
REAPI:writeVar("score", 0)
REAPI:writeVar("splashScreenTime", -100)
REAPI:writeVar("dialogueIntroTime", -100)
REAPI:writeVar("menuFadeIn", 0)
REAPI:writeVar("introFadeIn", 0)
REAPI:writeVar("playReloadSEonce", 0)
REAPI:writeVar("playGameOverOnce", 1)

REAPI:writeVar("playerKillsCurrentWave", 0)
REAPI:writeVar("directorVarZInit", 1)
REAPI:writeVar("directorVarZ", 1)
REAPI:writeVar("directorVarWaveCount", 0)
REAPI:writeVar("directorVarAlpha", 30)
REAPI:writeVar("directorVarBeta", 1)

REAPI:writeVar("uuid", 0)

REAPI:playSE("splash.wav", false)
REAPI:endScript("var_init.lua")

