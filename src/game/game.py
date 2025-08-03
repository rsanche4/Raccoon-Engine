import pygame
import sys
import raccoonapi

pygame.init()

# Original resolution you render to
GAME_WIDTH, GAME_HEIGHT = 320, 240
game_surface = pygame.Surface((GAME_WIDTH, GAME_HEIGHT))

# Create the fullscreen window
screen = pygame.display.set_mode((0, 0), pygame.FULLSCREEN)
screen_width, screen_height = screen.get_size()

# Optional: Calculate a scale factor (e.g., 3x, 4x) to maintain aspect ratio
scale = min(screen_width // GAME_WIDTH, screen_height // GAME_HEIGHT)

# Main loop
clock = pygame.time.Clock()

REAPI = raccoonapi.REAPI(pygame)
REAPI.initialize()

while REAPI.running:
    for event in pygame.event.get():
        if event.type == pygame.QUIT or (event.type == pygame.KEYDOWN and event.key == pygame.K_ESCAPE):
            REAPI.exit_game()
        
        if event.type == pygame.KEYDOWN:
            if event.key == pygame.K_LEFT:
                REAPI.keys["turn_left"] = True
            elif event.key == pygame.K_RIGHT:
                REAPI.keys["turn_right"] = True
            elif event.key == pygame.K_UP:
                REAPI.keys["move_forward"] = True
            elif event.key == pygame.K_DOWN:
                REAPI.keys["move_back"] = True
            elif event.key == pygame.K_COMMA:
                REAPI.keys["strafe_left"] = True
            elif event.key == pygame.K_PERIOD:
                REAPI.keys["strafe_right"] = True
            elif event.key == pygame.K_LCTRL or event.key == pygame.K_RCTRL:
                REAPI.keys["shoot"] = True
            elif event.key == pygame.K_LALT or event.key == pygame.K_RALT:
                REAPI.keys["reload"] = True
            elif event.key == pygame.K_RETURN:
                REAPI.keys["interact"] = True
            elif event.key == pygame.K_SPACE:
                REAPI.keys["jump"] = True
            elif event.key == pygame.K_1:
                REAPI.keys["first"] = True
            elif event.key == pygame.K_2:
                REAPI.keys["second"] = True
            elif event.key == pygame.K_3:
                REAPI.keys["third"] = True

    # repeated keys
    keys = pygame.key.get_pressed()
    REAPI.keys_repeated["turn_left"] = keys[pygame.K_LEFT]
    REAPI.keys_repeated["turn_right"] = keys[pygame.K_RIGHT]
    REAPI.keys_repeated["move_forward"] = keys[pygame.K_UP]
    REAPI.keys_repeated["move_back"] = keys[pygame.K_DOWN]
    REAPI.keys_repeated["strafe_left"] = keys[pygame.K_COMMA]
    REAPI.keys_repeated["strafe_right"] = keys[pygame.K_PERIOD]
    REAPI.keys_repeated["shoot"] = keys[pygame.K_LCTRL] or keys[pygame.K_RCTRL]
    REAPI.keys_repeated["reload"] = keys[pygame.K_LALT] or keys[pygame.K_RALT]
    REAPI.keys_repeated["interact"] = keys[pygame.K_RETURN]
    REAPI.keys_repeated["jump"] = keys[pygame.K_SPACE]
    REAPI.keys_repeated["first"] = keys[pygame.K_1]
    REAPI.keys_repeated["second"] = keys[pygame.K_2]
    REAPI.keys_repeated["third"] = keys[pygame.K_3]
    REAPI.move_player()

    # --- Drawing to the game surface (320x240) ---
    game_surface.fill((0, 0, 0))

    # --- FRAME DRAW
    REAPI.render(game_surface)
    REAPI.render_sprites(game_surface)
    REAPI.run_events(REAPI)
    # --- FRAME DRAW
    
    # Reset keys for once
    REAPI.keys["turn_left"] = False
    REAPI.keys["turn_right"] = False
    REAPI.keys["move_forward"] = False
    REAPI.keys["move_back"] = False
    REAPI.keys["strafe_left"] = False
    REAPI.keys["strafe_right"] = False
    REAPI.keys["shoot"] = False
    REAPI.keys["reload"] = False
    REAPI.keys["interact"] = False
    REAPI.keys["jump"] = False
    REAPI.keys["first"] = False
    REAPI.keys["second"] = False
    REAPI.keys["third"] = False

    # --- Scaling the surface and drawing it to the screen ---
    scaled_surface = pygame.transform.scale(game_surface, (GAME_WIDTH * scale, GAME_HEIGHT * scale))
    # Center it
    x_offset = (screen_width - GAME_WIDTH * scale) // 2
    y_offset = (screen_height - GAME_HEIGHT * scale) // 2
    screen.fill((0, 0, 0)) # clear screen
    screen.blit(scaled_surface, (x_offset, y_offset))

    REAPI.update_frame()
    pygame.display.flip()
    clock.tick(REAPI.fps)

pygame.quit()
sys.exit()
