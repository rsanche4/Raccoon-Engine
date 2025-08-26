#include <SDL3/SDL.h>
#include <cmath>
#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <unordered_map>
#include <sstream>
#include <algorithm>

using namespace std;

// Globals
int screen_width = 320;
int screen_height = 240;
int application_width = screen_width*4;
int application_height = screen_height*4;

// Level Stats
string levelname;
int atomic_segment_size;
string skybox_img;
int skybox_brightness;
struct Sector {
    int floor_height;
    int ceil_height;
    string floorTexture;
    int floorBrightness;
    string ceilTexture;
    int ceilBrightness;
};
unordered_map<int, Sector> sectors;
struct Wall {
    int sectorid;
    string wallTexture;
    int wallBrightness;
};
unordered_map<string, Wall> walls;
struct Portal {
    int sectorA;
    int sectorB;
};
unordered_map<string, Portal> portals;

// Player Stats
float player_pos[3] = {0, 0, 400};
float first_ray_direction = 4.3319;
float ray_inc = 0.002379;
float fp_dist = 400;

// Turn wall integers into a string key we can use, order matters so beware
string makeWallKey(int x1, int z1, int x2, int z2) {
    return to_string(x1) + " " + to_string(z1) + " " + to_string(x2) + " " + to_string(z2);
}

// read map file
int readLevel(const string& filename) {

    ifstream file(filename);
    string line;

    while (getline(file, line)) {

        if (line.empty()) continue;
        string header_type; 
        if (line=="[LEVEL]" || line=="[SECTORS]" || line=="[WALLS]" || line=="[PORTALS]") {
            header_type = line;
            continue;
        }

        istringstream iss(line);
        if (header_type=="[LEVEL]") {
            iss >> levelname >> atomic_segment_size >> skybox_img >> skybox_brightness;
        } else if (header_type=="[SECTORS]") {
            int sectorid;
            int flh;
            int clh;
            string floorimg;
            int floorbright;
            string ceilimg;
            int ceilbright;
            iss >> sectorid >> flh >> clh >> floorimg >> floorbright >> ceilimg >> ceilbright;
            sectors[sectorid] = {flh, clh, floorimg, floorbright, ceilimg, ceilbright};
        } else if (header_type=="[WALLS]") {
            int x1;
            int z1;
            int x2;
            int z2;
            int sectorid;
            string wallimg;
            int wallbright;
            iss >> x1 >> z1 >> x2 >> z2 >> sectorid >> wallimg >> wallbright;
            // Normalize coordinates so we always go left->right or bottom->top
            if (x1 > x2) {
                swap(x1, x2);
            } else if (x1 == x2 && z1 > z2) {
                swap(z1, z2);
            }
            // Horizontal wall (z fixed, x changes)
            if (z1 == z2) {
                for (int x = x1; x < x2; ++x) {
                    string key = makeWallKey(x, z1, x + 1, z1);
                    walls[key] = { sectorid, wallimg, wallbright };
                }
            }
            // Vertical wall (x fixed, z changes)
            else if (x1 == x2) {
                for (int z = z1; z < z2; ++z) {
                    string key = makeWallKey(x1, z, x1, z + 1);
                    walls[key] = { sectorid, wallimg, wallbright };
                }
            }
        } else if (header_type=="[PORTALS]") {
            int x1;
            int z1;
            int x2;
            int z2;
            int sectorA;
            int sectorB;
            iss >> x1 >> z1 >> x2 >> z2 >> sectorA >> sectorB;
            // Normalize coordinates so we always go left->right or bottom->top
            if (x1 > x2) {
                swap(x1, x2);
            } else if (x1 == x2 && z1 > z2) {
                swap(z1, z2);
            }
            if (sectorA > sectorB) {
                swap(sectorA, sectorB);
            }
            // Horizontal wall (z fixed, x changes)
            if (z1 == z2) {
                for (int x = x1; x < x2; ++x) {
                    string key = makeWallKey(x, z1, x + 1, z1);
                    portals[key] = { sectorA, sectorB };
                }
            }
            // Vertical wall (x fixed, z changes)
            else if (x1 == x2) {
                for (int z = z1; z < z2; ++z) {
                    string key = makeWallKey(x1, z, x1, z + 1);
                    portals[key] = { sectorA, sectorB };
                }
            }
        }
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
            
            // Now here all we need to do is run DDA through the map following all the math we laid out
            // So we need to do a thing right here is fine where we run everytime the thing and we go slowly, 
            // the ray has a limit dont forget also to check if its closer than the screen in which case nah
            // but anyways we start there and we go and go and that tells us once we get to the place we need to project this thing
            // but again we need to start at the origin, keep going etc. 
            
            
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