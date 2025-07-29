import json
import os
import pickle
from tqdm import tqdm
from random import sample, choice

def print_bsp_tree(node, prefix="", is_left=True):
    if node is None:
        return

    wall = node[0]
    left = node[1]
    right = node[2]

    # Format wall info
    wall_id = wall.get("wall_id", "??")
    sector = wall.get("sector_id", "unknown")
    label = f"[wall_id: {wall_id}, sector: {sector}]"

    # Print current node
    connector = "├── " if is_left else "└── "
    print(f"{prefix}{connector}{label}")

    # Determine the new prefix
    child_prefix = prefix + ("│   " if is_left else "    ")

    # Print left and right recursively
    if left or right:
        if left:
            print_bsp_tree(left, child_prefix, True)
        else:
            print(f"{child_prefix}├── None")

        if right:
            print_bsp_tree(right, child_prefix, False)
        else:
            print(f"{child_prefix}└── None")

UUID = 1
SPLITID = 0

def cost_func(num_splits, front_count, back_count):
    return (5 * num_splits) + abs(front_count - back_count)

def split_by_H(H, walls):
    global SPLITID
    global UUID
    num_splits = 0
    updated_walls = []
    for wall in walls:
        if H["front_face"]=="UP":
            v1_loc = H["wall_vertex_1"][1] < wall["wall_vertex_1"][1]
            v2_loc = H["wall_vertex_2"][1] < wall["wall_vertex_2"][1]
            if v1_loc!=v2_loc:
                num_splits+=1
                curr_id = 0
                if wall["wallsplit_ids"]==0:
                    SPLITID+=1
                    curr_id = SPLITID
                else:
                    curr_id = wall["wallsplit_ids"]
                wallA = {
                "sector_id": wall["sector_id"],
                "wall_id": wall["wall_id"],
                "wall_vertex_1": [wall["wall_vertex_1"][0], wall["wall_vertex_1"][1]], 
                "wall_vertex_2": [wall["wall_vertex_2"][0], H["wall_vertex_2"][1]],
                "front_face": wall["front_face"],
                "wallsplit_ids": curr_id
                }
                wallB = {
                "sector_id": wall["sector_id"],
                "wall_id": UUID,
                "wall_vertex_1": [wall["wall_vertex_1"][0], H["wall_vertex_1"][1]], 
                "wall_vertex_2": [wall["wall_vertex_2"][0], wall["wall_vertex_2"][1]],
                "front_face": wall["front_face"],
                "wallsplit_ids": curr_id
                }
                UUID+=1
                updated_walls.append(wallA)
                updated_walls.append(wallB)
            else:
                updated_walls.append(wall)
        elif H["front_face"]=="RIGHT":
            v1_loc = H["wall_vertex_1"][0] < wall["wall_vertex_1"][0]
            v2_loc = H["wall_vertex_2"][0] < wall["wall_vertex_2"][0]
            if v1_loc!=v2_loc:
                num_splits+=1
                
            else:
                updated_walls.append(wall)

    return updated_walls, num_splits

def is_wall_in_front(P, H):
    if H["front_face"]=="UP":
        return H["wall_vertex_1"][1] < P["wall_vertex_1"][1]
    elif H["front_face"]=="RIGHT":
        return H["wall_vertex_1"][0] < P["wall_vertex_1"][0]

def is_atomic_subspace(walls):
    return len(walls)==0

def recursive_bsp(walls):
    if is_atomic_subspace(walls):
        return
    else:
        if len(walls)<5:
            H = choice(walls)
            walls_split, num_splits = split_by_H(H, walls)
            subwalls_left = []
            subwalls_right = []
            for P in walls_split:
                is_front = is_wall_in_front(P, H)
                if P["wall_id"]!=H["wall_id"] and is_front:
                    subwalls_left.append(P)
                elif P["wall_id"]!=H["wall_id"] and not is_front:
                    subwalls_right.append(P)
            return (H, recursive_bsp(subwalls_left), recursive_bsp(subwalls_right))
        else:
            k = 5
            splitterwalls_candidates = sample(walls, k)
            subwalls_left_candidates = [[] for _ in range(k)]
            subwalls_right_candidates = [[] for _ in range(k)]
            costs = []
            for Hi in range(k):
                H = splitterwalls_candidates[Hi]
                walls_split, num_splits = split_by_H(H, walls)
                for P in walls_split:
                    is_front = is_wall_in_front(P, H)
                    if P["wall_id"]!=H["wall_id"] and is_front:
                        subwalls_left_candidates[Hi].append(P)
                    elif P["wall_id"]!=H["wall_id"] and not is_front:
                        subwalls_right_candidates[Hi].append(P)
                costs.append(cost_func(num_splits, len(subwalls_left_candidates[Hi]), len(subwalls_right_candidates[Hi])))
            best_Hi = costs.index(min(costs))
            return (splitterwalls_candidates[best_Hi], recursive_bsp(subwalls_left_candidates[best_Hi]), recursive_bsp(subwalls_right_candidates[best_Hi]))

def is_horizontal(vertex1, vertex2):
    if vertex1[0]!=vertex2[0]:
        return True
    elif vertex1[1]!=vertex2[1]:
        return False

def gen_bsp_tree(mapdata):
    global UUID
    global SPLITID
    walldefs = []
    for i in range(len(mapdata["sectors"])):
        for j in range(len(mapdata["sectors"][i]["vertices"])):
            if mapdata["sectors"][i]["properties"]["floor_height"]==0 and mapdata["sectors"][i]["properties"]["ceiling_height"] is None:
                continue
            x1, z1 = mapdata["sectors"][i]["vertices"][j][0], mapdata["sectors"][i]["vertices"][j][1] # x, z
            x2, z2 = mapdata["sectors"][i]["vertices"][(j+1)%len(mapdata["sectors"][i]["vertices"])][0], mapdata["sectors"][i]["vertices"][(j+1)%len(mapdata["sectors"][i]["vertices"])][1]
            walldefs.append({
                "sector_id": mapdata["sectors"][i]["properties"]["sector_id"],
                "wall_id": UUID,
                "wall_vertex_1": [x1, z1], 
                "wall_vertex_2": [x2, z2],
                "front_face": "UP" if is_horizontal([x1, z1], [x2, z2]) else "RIGHT",
                "wallsplit_ids": SPLITID
            })
            UUID+=1
    BSP_TREE = recursive_bsp(walldefs)
    return BSP_TREE

if __name__ == "__main__":
    for mapname in tqdm(os.listdir("maps")):
        with open(f'maps/{mapname}', 'r', encoding='utf-8') as file:
            mapdata = json.load(file)
        maprbt = gen_bsp_tree(mapdata)
        with open(f"bspt/{mapname.replace(".json", "")}.rbt", "wb") as f:
            pickle.dump(maprbt, f)