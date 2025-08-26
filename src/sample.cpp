#include <SDL3/SDL.h>
#include <cmath>
#include <iostream>
#include <fstream>
#include <vector>
#include <string>

using namespace std;

// Globals
int screen_width = 320;
int screen_height = 240;
int application_width = screen_width*4;
int application_height = screen_height*4;

// Player Stats
float player_pos[3] = {0, 0, 400};
float player_dir = 4.7124;
float fp_dist = 400;

// read map file
int readLevel(const string& filename) {
    ifstream file(filename);
    string line;
    
    while (getline(file, line)) {
        cout << line << endl;
        // CONTINUE HERE
    }
    
    return 0;
}

// Get the euclid distance given 2 points in 2D plane
float euclid_dist(float v2x, float v2y, float v1x, float v1y) {
    float dist = sqrt((v2x - v1x) * (v2x - v1x) + (v2y - v1y) * (v2y - v1y));
    return dist;
}

// project Vertex onto screen and find where the Y pixel should be in the column. From bottom to top.
float projectY(float vertexy, float vertexz, float fpy, float fpz, float focal_point_dist, float screenheight) {
    float b = vertexy - fpy;
    float c = euclid_dist(vertexy, vertexz, fpy, fpz);
    float a = sqrt(c*c - b*b);
    float y = (b * focal_point_dist) / a;
    int pixelY = (int)((screenheight / 2) + y);
    return pixelY;
}

// Function to draw pixels directly per column
void drawPixels(Uint32* pixels, int pitch, unsigned int frames) {
    // Example: Draw some colored lines with gradients

    for (int x = 0; x < screen_width; x++) {
        for (int y = 0; y < screen_height; y++) {
            Uint32 color = 0xFF000000; //A R G B
            
            Uint32 a = 0xFF000000;
            Uint32 r = (x % 256) << 16;
            Uint32 g = (y % 256) << 8;
            Uint32 b = ((x+y) % 256);

            color = a | r | g | b;

            pixels[y * pitch + x] = color;
        }
    }
}

int main() {
    readLevel("level_test.txt");
    
    SDL_Init(SDL_INIT_VIDEO);
    SDL_Window* w = SDL_CreateWindow("Raccoon Engine", application_width, application_height, 0);
    SDL_Renderer* r = SDL_CreateRenderer(w, NULL);
    
    // Create surface at low resolution for fast pixel manipulation
    SDL_Surface* surface = SDL_CreateSurface(screen_width, screen_height, SDL_PIXELFORMAT_RGBA8888);
    SDL_Texture* texture = SDL_CreateTextureFromSurface(r, surface);
    
    float x = 200; // just boilerplate for now
    SDL_Event e;
    
    unsigned int frames = 0;
    while (1) {
        while (SDL_PollEvent(&e)) {
            if (e.type == SDL_EVENT_QUIT) break;
        }
        
        const bool* keys = SDL_GetKeyboardState(NULL);
        if (keys[SDL_SCANCODE_ESCAPE]) break;
        if (keys[SDL_SCANCODE_LEFT] && x > 0) x -= 5;
        if (keys[SDL_SCANCODE_RIGHT] && x < 350) x += 5;
        
        // Lock surface for direct pixel access
        SDL_LockSurface(surface);
        Uint32* pixels = (Uint32*)surface->pixels;
        int pitch = surface->pitch / 4; // pitch in pixels, not bytes
        
        // Call your pixel drawing function
        drawPixels(pixels, pitch, frames);
        
        SDL_UnlockSurface(surface);
        
        // Update texture and render scaled up to full application size
        SDL_UpdateTexture(texture, NULL, surface->pixels, surface->pitch);
        SDL_RenderClear(r);
        SDL_RenderTexture(r, texture, NULL, NULL); // This automatically scales up
        SDL_RenderPresent(r);
        SDL_Delay(16);

        frames++;
    }
    
    SDL_DestroySurface(surface);
    SDL_DestroyTexture(texture);
    SDL_Quit();
    return 0;
}