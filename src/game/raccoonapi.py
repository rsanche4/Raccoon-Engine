import json
import pickle
import os
import cv2
import numpy as np
import sys
import math

class REAPI:
    def __init__(self, pygame):
        self.running = True
        self.fps = 30
        self.frame = 0
        self.map_index = -1
        self.maps = []
        self.events = [{
            "event_id": "init_event.py",
            "event_script": "init_event.py",
            "event_sprite": None,
            "event_location": (None, None, None)
        }]
        self.uuid = 0
        self.pygame = pygame
        self.keys = {
            "turn_left": False,
            "turn_right": False,
            "move_forward": False,
            "move_back": False,
            "strafe_left": False,
            "strafe_right": False,
            "shoot": False,
            "reload": False,
            "interact": False,
            "jump": False,
            "first": False,
            "second": False,
            "third": False
        }
        self.keys_repeated = {
            "turn_left": False,
            "turn_right": False,
            "move_forward": False,
            "move_back": False,
            "strafe_left": False,
            "strafe_right": False,
            "shoot": False,
            "reload": False,
            "interact": False,
            "jump": False,
            "first": False,
            "second": False,
            "third": False
        }
        self.player_pos = (0, 0, 0)
        self.player_dir = 0
        self.move_speed = 0.1
        self.turn_speed = 0.04
    
    def initialize(self):
        """
        Loads all the scripts, the information for the world, which map to load etc, reads all the binary trees all that
        """
        self.pygame.mixer.init()
        for mapname in os.listdir("maps"):
            with open(f'maps/{mapname}', 'r', encoding='utf-8') as file:
                mapdata = json.load(file)
            with open(f"bspt/{mapname.replace(".json", "")}.rbt", "rb") as f:
                maptree = pickle.load(f)
            self.maps.append({"mapdata": mapdata, "maptree": maptree})

    def change_player_speed(self, movespeed, turnspeed):
        """
        Changes the player move speed and turn speed
        """
        self.move_speed = movespeed
        self.turn_speed = turnspeed
    
    def change_player_pos_dir(self, newpos, newdir):
        """
        Changes the player position and direction
        """
        self.player_pos = newpos
        self.player_dir = newdir

    def get_player_info(self):
        """
        Get info of the player
        """
        return self.player_pos, self.player_dir, self.move_speed, self.turn_speed 

    def play_bgm(self, bgm, repeats):
        """
        Plays background music, on loop or not depending on what you choose
        """
        self.pygame.mixer.music.load(f"bgm/{bgm}")
        self.pygame.mixer.music.play(loops=repeats)

    def play_se(self, se):
        """
        Plays sound effects
        """
        self.pygame.mixer.Sound(f"se/{se}").play()

    def stop_bgm(self):
        """
        Stop background music that is playing
        """
        self.pygame.mixer.music.stop()

    def event_id_gen(self):
        """
        If you want to generate a UUID for an event instead of self naming, you can call this
        """
        self.uuid+=1
        return str(self.uuid)

    def add_event(self, event_id, event_script, event_sprite, event_location):
        """
        Adds an event to the world event list
        """
        self.events.append({
            "event_id": event_id,
            "event_script": event_script,
            "event_sprite": event_sprite,
            "event_location": event_location
        })

    def remove_event(self, event_id):
        """
        Removes an event from the world event list
        """
        removei = -1
        for i in range(len(self.events)):
            if self.events[i]["event_id"]==event_id:
                removei = i
                break
        if removei>=0:
            del self.events[removei]

    def find_event(self, event_id):
        """
        Finds an event from the world event list
        """
        for i in range(len(self.events)):
            if self.events[i]["event_id"]==event_id:
                return self.events[i]
        
    def run_events(self, REAPI):
        """
        Run the events of the user in our queue
        """
        for ev in self.events:
            behavior_script_name = ev["event_script"]
            with open(f"scripts/{behavior_script_name}") as f:
                exec(f.read())

    def render_sprites(self, game_surface):
        """
        Renders the sprites in the world
        """
        return

    def change_texture(self):
        """
        Given a texture id we want to swap it for a new one or change it etc ideally
        """
        return

    def draw_pic(self):
        """
        Given a picture to draw, we draw it on the screen
        """

    def write_screen_text(self):
        """
        Given a font file, we write that font to the screen at the specified location
        """
        return

    def convert_to_player_rel_coords(self, global_playerpos, vertex):
        """
        Given a vertex, it returns the relative coordinates to the player for rendering
        """
        newverx = vertex[0]-global_playerpos[0]
        newvery = vertex[1]-global_playerpos[1]
        newverz = vertex[2]-global_playerpos[2]
        return (newverx, newvery, newverz)

    def render(self, game_surface):
        """
        Renders the loaded world into the screen
        """
        if self.map_index>-1:
            current_map = self.maps[self.map_index]
            mapdata = current_map["mapdata"]
            maptree = current_map["maptree"]

            # read the maps data of where the player is and update the player position to that

            # 1: take the data from the world. This is absolute and convert it ready for rendering. The player is the camera always at 0, with 400 for FOV
            # Firest before that we should try to figure out which vertex and which quads we draw first in that particular world. Y is determined based on that. based on what are we on top of. and essentially. y matters to the camera so we should figure out where we are for that. Or maybe the y for the world is a bit different? 
            # I am stopping it here. 
            
            # 2: take the player position now updated etc and make it relative to the player. This is where the rendering will occur. Skybox all of it

    def move_player(self):
        """
        Once the keys are pressed, we have to update our player pos
        """
        if self.keys_repeated["turn_left"]:
            self.player_dir += self.turn_speed
        elif self.keys_repeated["turn_right"]:
            self.player_dir -= self.turn_speed

        cos_a = math.cos(self.player_dir)
        sin_a = math.sin(self.player_dir)

        if self.keys_repeated["move_forward"]:
            self.player_pos[0] += cos_a * self.move_speed
            self.player_pos[2] += sin_a * self.move_speed
        elif self.keys_repeated["move_back"]:
            self.player_pos[0] -= cos_a * self.move_speed
            self.player_pos[2] -= sin_a * self.move_speed

        if self.keys_repeated["strafe_left"]:
            cos_a_strafe = math.cos(self.player_dir+math.pi/2)
            sin_a_strafe = math.sin(self.player_dir+math.pi/2)
            self.player_pos[0] += cos_a_strafe * self.move_speed
            self.player_pos[2] += sin_a_strafe * self.move_speed
        elif self.keys_repeated["strafe_right"]:
            cos_a_strafe = math.cos(self.player_dir-math.pi/2)
            sin_a_strafe = math.sin(self.player_dir-math.pi/2)
            self.player_pos[0] += cos_a_strafe * self.move_speed
            self.player_pos[2] += sin_a_strafe * self.move_speed

    def get_keys_pressed(self, repeated=False):
        """
        Returns the current keys pressed by the player.
        """
        if repeated:
            return self.keys_repeated
        return self.keys

    def load_map(self, mapname):
        """
        Renders the given map, sets index and sets player spawn to that etc.
        """
        for i in range(len(self.maps)):
            if self.maps[i]["mapdata"]["map_properties"]["map_name"]==mapname:
                self.map_index = i
                
                return

    def get_frame_num(self):
        """
        Returns the current frame number.
        """
        return self.frame

    def update_frame(self):
        """
        Updates the frame by 1
        """
        self.frame+=1

    def set_fps(self, newfps):
        """
        Sets the game FPS
        """
        self.fps = newfps
    
    def get_fps(self):
        """
        Returns the current FPS
        """
        return self.fps

    def exit_game(self):
        """
        Exits the game.
        """
        self.running = False