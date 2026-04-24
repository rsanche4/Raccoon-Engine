package raccoon;

import java.util.HashMap;
import java.util.PriorityQueue;

public class ResourceManager {

    // TODO NOTE THIS IS IMPORTANT DO NOT EVEN ATTEMPT TO STORE ALL THAT. ONLY GET WHAT IS NECESSARY,A ND LOAD IT FROM THE FILE WHEN CALLED BY STRINGS ETC. DO NOT OVERLOAD UR MEMORY
	// Perhaps a better way is to store an index to where that info is stored outside or something. Instead of the raw info
    public static HashMap<String, Texture> textures = new HashMap<>(); 	// TODO here we need to actually store the colors that we are reading from the data and convert them to the closest ones in our allowed ANSI colors for our game engine
    // public static HashMap<String, LuaJ or something> all_scripts = etc // TODO like this
    public static PriorityQueue<Event> scripts = new PriorityQueue<>();
    // public static Sounds type of thing idk all Sounds so that we can recall them later and play them TODO
    // TODO Misc folder can go because we can simply use the RaccoonAPI to add stuff directly and use it as we see fit
    // TODO saves folder should be started when needed by an API if called, so yeah that we dont really check
    
    public static void unpackRPK(String file_location) {
    	// TODO
    }
}
