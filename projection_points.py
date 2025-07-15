import pygame
import sys
import math

pygame.init()
WID = 1920
HEI = 1080
screen = pygame.display.set_mode((0, 0), pygame.FULLSCREEN)
clock = pygame.time.Clock()

# A 3D triangle (3 vertices)
triangle3D = [
    [ 9,  -2, -11], 
    [11,  -2, -11],  
    [11,  2, -11], 
    [11,  6, -11],  
    [ 9,  6, -11],  
    [ 9,  2, -11],  
]

# Movement speed
move_speed = 0.1
turn_speed = 0.04
zoom_speed = 0.1

angle = 0 # in radians

running = True
while running:
    for event in pygame.event.get():
        if event.type == pygame.QUIT:
            running = False

        
        if event.type == pygame.KEYDOWN:
            if event.key == pygame.K_ESCAPE:
                running = False

    # Get keys pressed
    keys = pygame.key.get_pressed()

    # Rotate left/right (Y-axis rotation)
    if keys[pygame.K_LEFT]:
        angle += turn_speed
    if keys[pygame.K_RIGHT]:
        angle -= turn_speed

    # Calculate camera-relative movement directions
    cos_a = math.cos(angle)
    sin_a = math.sin(angle)

    # Move closer/farther (relative to camera) - move all vertices
    if keys[pygame.K_UP]:
        for vertex in triangle3D:
            vertex[0] += zoom_speed * sin_a
            vertex[2] += zoom_speed * cos_a
    if keys[pygame.K_DOWN]:
        for vertex in triangle3D:
            vertex[0] -= zoom_speed * sin_a
            vertex[2] -= zoom_speed * cos_a

    screen.fill((0, 0, 0))

    # Project all vertices to screen coordinates
    screen_points = []
    
    for vertex in triangle3D:
        X, Y, Z = vertex
        
        # Apply rotation for rendering
        rotated_x = X * cos_a - Z * sin_a
        rotated_y = Y
        rotated_z = X * sin_a + Z * cos_a
        
        # Add a minimum Z distance
        min_z = 1
        if rotated_z < -min_z:  # Only render if behind camera and not too close
            focal_length = 400
            screen_x = int(WID//2 + (focal_length * rotated_x / -rotated_z))
            screen_y = int(HEI//2 - (focal_length * rotated_y / -rotated_z))
            
            if 0 <= screen_x < WID and 0 <= screen_y < HEI:
                screen_points.append((screen_x, screen_y))
    
    # Draw the triangle if we have all 3 points on screen
    if len(screen_points)>=3:
        pygame.draw.polygon(screen, (255, 0, 0), screen_points, 0)  # 2 = line width
        
        # Optional: Draw vertices as small circles
        for point in screen_points:
            pygame.draw.circle(screen, (255, 255, 0), point, 3)

    pygame.display.flip()
    clock.tick(60)

pygame.quit()
sys.exit()