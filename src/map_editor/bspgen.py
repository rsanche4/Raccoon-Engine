import json
import os
import pickle
from tqdm import tqdm
from random import randint

BSP_TREE = {}
WALLDEFS = []

def is_atomic_subspace(walls):
    return len(walls)==1

def recursive_bsp(walls):
    global BSP_TREE
    global WALLDEFS
    if is_atomic_subspace(walls):
        return
    else:
        splitterwall = randint(0, len(walls)-1) # This should change to try 5 different candidates and weight in the best score for splitting and balance tree but its fine
        # So now the question is who goes to the left and who goes to the right
        # The index IS the wall basically. Lot less space so all good. The only question is how do we decide who goes to the left and who goes to to the right
        # TODO

def is_horizontal(vertex1, vertex2):
    if vertex1[0]!=vertex2[0]:
        return True
    elif vertex1[1]!=vertex2[1]:
        return False

def gen_bsp_tree(mapdata):
    global BSP_TREE
    global WALLDEFS
    walldefs = []
    for i in range(len(mapdata["sectors"])):
        for j in range(len(mapdata["sectors"][i]["vertices"])):
            if mapdata["sectors"][i]["properties"]["floor_height"]==0 and mapdata["sectors"][i]["properties"]["ceiling_height"] is None:
                continue
            x1, z1 = mapdata["sectors"][i]["vertices"][j][0], mapdata["sectors"][i]["vertices"][j][1] # x, z
            x2, z2 = mapdata["sectors"][i]["vertices"][(j+1)%len(mapdata["sectors"][i]["vertices"])][0], mapdata["sectors"][i]["vertices"][(j+1)%len(mapdata["sectors"][i]["vertices"])][1]
            walldefs.append({
                "sector_id": mapdata["sectors"][i]["properties"]["sector_id"],
                "wall_vertex_1": [x1, z1], 
                "wall_vertex_2": [x2, z2],
                "front_face": "UP" if is_horizontal([x1, z1], [x2, z2]) else "RIGHT"
            })
    recursive_bsp(walldefs)
    return {"bsp_tree": BSP_TREE, "walldefs": WALLDEFS}
    
if __name__ == "__main__":
    for mapname in tqdm(os.listdir("maps")):
        with open(f'maps/{mapname}', 'r', encoding='utf-8') as file:
            mapdata = json.load(file)
        maprbt = gen_bsp_tree(mapdata)
        with open(f"bspt/{mapname.replace(".json", "")}.rbt", "wb") as f:
            pickle.dump(maprbt, f)