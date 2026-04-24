package raccoon;

import java.util.HashMap;
import java.util.PriorityQueue;

public class ResourceManager {

	public static boolean pack_into_rpk = true;
    public static String data_rpk = "data.rpk";
    public static String data_folder = "data";
    public static String data_folder_bgm = data_folder+"/bgm/";
    public static String data_folder_fonts = data_folder+"/fonts/";
    public static String data_folder_maps = data_folder+"/maps/";
    public static String data_folder_pics = data_folder+"/pics/";
    public static String data_folder_scripts = data_folder+"/scripts/";
    public static String data_folder_se = data_folder+"/se/";
    public static String data_folder_skybox = data_folder+"/skybox/";
    public static String data_folder_sprites = data_folder+"/sprites/";
    public static String data_folder_tex = data_folder+"/tex/";
    
    // ResourceManager.scripts.add(new Event("init.lua", 1));
	// TODO NOTE THIS IS IMPORTANT DO NOT EVEN ATTEMPT TO STORE ALL THAT. ONLY GET WHAT IS NECESSARY,A ND LOAD IT FROM THE FILE WHEN CALLED BY STRINGS ETC. DO NOT OVERLOAD UR MEMORY
	// Perhaps a better way is to store an index to where that info is stored outside or something. Instead of the raw info
    public static HashMap<String, Texture> textures = new HashMap<>(); 	// TODO here we need to actually store the colors that we are reading from the data and convert them to the closest ones in our allowed ANSI colors for our game engine
    // public static HashMap<String, LuaJ or something> all_scripts = etc // TODO like this
    public static PriorityQueue<Event> scripts = new PriorityQueue<>();
    // public static Sounds type of thing idk all Sounds so that we can recall them later and play them TODO
    // TODO Misc folder can go because we can simply use the RaccoonAPI to add stuff directly and use it as we see fit
    // TODO saves folder should be started when needed by an API if called, so yeah that we dont really check
    
    public static void unpackRPK() {
    	// TODO
    }
    
    public static void packRPK() {
    	
    }
    
    public static void manageResources() {
    }

	public static void loadData() {
		// So first step here: check if path "data/" basically exists. 
		// if the data/ does NOT exist, then that's ok! Now we check does "data.rpk" file exist?
		// if it does NOT we error out and say we don't have data.rpk OR data/
		
		// if the data.rpk file exist however, then we call unpackRPK()
		
		// If data/ the folder does exit then check also some basics and if we dont pass these, essentially error out warning user: (we are currently only supporting these formats but keep in mind maybe later we will support other extensions)
		// - we need to check if data/bgm/ exists. If it does, then check every file in there is .wav otherwise error out. For these, if the folder path itself does not exist, error out appropiately.
		// - if data/fonts/ exists and ensure they are: .ttf otherwise error out
		// - if data/maps/ exists and ensure they are .txt
		// - if data/pics/ exists and ensure they are .png
		// - if data/scripts/ exists and ensure they are all .lua. ALSO this needs to check if we have init.lua in there too. So init.lua as well MUST exist. Otherwise error out appropiately.
		// - if data/se/ exists and they are .wav
		// - if data/skybox/ exists. And also check here, if we have actual data in there of images, just ensure they are all the following dimensions: 4*game_width x game_height. And that they are .png
		// - if data/sprites/ exists. Also, ensure that the dimensions are as follows for each of them (and that they are .png): 8*the_sprite_heights x the_sprite_heights 
		// - if data/tex/ exists, and that they are .png. No other constraint here.
		
		// if we passed all these checks for the data folder, then we must now ask:
		// is pack_into_rpk true? If it is, then we must call packRPK()
		// if it is not true, then we instead call: manageResources()
	}
}
