# So if i understand, the first thing we need to do is look at the maps in the "maps" folder. 
# Then, we have to go through all the .json files in there. Each .json is a map. 
# So then, we need to create a "binary space partition tree" with this data. 
# How do we do that? well, by reading the contents of each json!
# the json are structured as follows:
# paste the json structure here
# Notice that the walls are simply where the vertices connect! So basically each line connects between each vertex. There is a wall between those. 
# so vertex 1 is connected through a line with vertex 2. Vertex 2 to vertex 3. Etc. 
# That is a "wall" for us so we need to run bsp on those in 2D is fine basically. We have the x and z coordinates of where they are so that's good.
# There is "front" or "back" for the walls so feel free to label that yourself at random per wall if it makes things easier. Unless u need it specified then let me know.
# Please include some sort of counter or something to help us keep track of where we are on the partition. This can take a while to do so it helps us keep track.
# Finally, once you are done, output the same name for the map but with the name .rbt for each map, and drop each seperate bsp tree in these binary files that i can later extract somewere else. 
# So for example map1.json and map2.json -> this outputs under bspt/ folder we put map1.rbt and map2.rbt