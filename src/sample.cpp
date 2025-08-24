#include <SDL3/SDL.h>
#include <cmath>
#include <iostream>

using namespace std;

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

int main() {
    //int answer = projectY(20, -5, 0, 5, 5, 8);
    //cout << answer;

    SDL_Init(SDL_INIT_VIDEO);
    SDL_Window* w = SDL_CreateWindow("Box", 400, 300, 0);
    SDL_Renderer* r = SDL_CreateRenderer(w, NULL);
    
    float x = 200;
    SDL_Event e;
    
    while (1) {
        while (SDL_PollEvent(&e)) {
            if (e.type == SDL_EVENT_QUIT) break;
        }
        
        const bool* keys = SDL_GetKeyboardState(NULL);
        if (keys[SDL_SCANCODE_ESCAPE]) break;
        if (keys[SDL_SCANCODE_LEFT] && x > 0) x -= 5;
        if (keys[SDL_SCANCODE_RIGHT] && x < 350) x += 5;
        
        SDL_SetRenderDrawColor(r, 0, 0, 0, 255);
        SDL_RenderClear(r);
        SDL_SetRenderDrawColor(r, 255, 255, 255, 255);
        SDL_FRect box = {x, 150, 50, 50};
        SDL_RenderFillRect(r, &box);
        SDL_RenderPresent(r);
        SDL_Delay(16);
    }
    
    SDL_Quit();
    return 0;
}