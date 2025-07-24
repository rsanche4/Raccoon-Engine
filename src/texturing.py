# TODO: DEBUG Textuing, it works its just backwards and make sure it follows ur logic well
# TODO: Also try to draw two textures lets say, and lets see if we can asue Claude solution to prevent overdraws doing front to back BSP
# TODO: Also keep in mind that will only draw the closest walls to walls limited to a number. The closest 256 thats it we won't check past that.

import pygame
import cv2
import numpy as np
import sys
import math

pygame.init()
WID = 320
HEI = 240
screen = pygame.display.set_mode((WID, HEI))
clock = pygame.time.Clock()

# Load and prepare your texture
def load_texture():
    # Create a sample texture (replace with your image loading)
    img = np.zeros((64, 64, 3), dtype=np.uint8)
    # Checkerboard pattern
    for i in range(64):
        for j in range(64):
            if (i // 8 + j // 8) % 2 == 0:
                img[i, j] = [255, 100, 100]  # Red
            else:
                img[i, j] = [100, 100, 255]  # Blue
    return img

# Load texture
texture = cv2.imread("texture.png")#load_texture()
# To use your own image: texture = cv2.imread("your_image.png")

# A 3D quad (4 vertices)
triangle3D = [
    [9, -2, -11], 
    [11, -2, -11],  
    [11, 2, -11], 
    [9, 2, -11],  
]

# Movement speed
move_speed = 0.1
turn_speed = 0.04
zoom_speed = 0.1

angle = 0

def warp_texture_to_quad(texture, quad_points, output_size=(WID, HEI)):
    """Warp texture to fit the quad and return as pygame surface"""
    if len(quad_points) != 4:
        return None
    
    # Source corners of the texture
    src = np.array([[0, 0], [texture.shape[1]-1, 0], 
                   [texture.shape[1]-1, texture.shape[0]-1], [0, texture.shape[0]-1]], dtype=np.float32)
    
    # Destination quad points
    dst = np.array(quad_points, dtype=np.float32)
    
    try:
        # Get perspective transform
        M = cv2.getPerspectiveTransform(src, dst)
        
        # Apply transform
        warped = cv2.warpPerspective(texture, M, output_size)
        
        # Convert BGR to RGB for pygame
        warped_rgb = cv2.cvtColor(warped, cv2.COLOR_BGR2RGB)
        
        # Convert to pygame surface
        surface = pygame.surfarray.make_surface(warped_rgb.swapaxes(0, 1))
        return surface
    except:
        return None

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
            screen_points.append((screen_x, screen_y))
    
    # Draw textured quad if we have all 4 points
    if len(screen_points) == 4:
        # Create warped texture surface
        textured_surface = warp_texture_to_quad(texture, screen_points)
        
        if textured_surface:
            # Blit the textured surface
            screen.blit(textured_surface, (0, 0))
        else:
            # Fallback: draw wireframe
            pygame.draw.polygon(screen, (255, 0, 0), screen_points, 2)
        
        # Optional: Draw vertices as small circles
        for point in screen_points:
            pygame.draw.circle(screen, (255, 255, 0), point, 2)

    pygame.display.flip()
    clock.tick(60)

pygame.quit()
sys.exit()