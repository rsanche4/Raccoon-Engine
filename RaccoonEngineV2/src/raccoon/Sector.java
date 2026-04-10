package raccoon;

public class Sector {
	
	int ID;
	int floor_height;
    int ceil_height;
    String floor_texture;
    int floor_brightness;
    int floor_tiled;
    boolean floor_skip_texture; 
    String ceil_texture;
    int ceil_brightness;
    int ceil_tiled;
    boolean ceil_skip_texture; 
    
    int[] boundary_coords = new int[4];
    int index_set_boundary_x = 0; 
    int index_set_boundary_z = 2;
    
    public Sector(int ID, int floor_height, int ceil_height, String floor_texture, int floor_brightness, int floor_tiled, boolean floor_skip_texture, String ceil_texture, int ceil_brightness, int ceil_tiled, boolean ceil_skip_texture) {
        this.ID = ID;
        this.floor_height = floor_height;
        this.ceil_height = ceil_height;
        this.floor_texture = floor_texture;
        this.floor_brightness = floor_brightness;
        this.floor_tiled = floor_tiled;
        this.floor_skip_texture = floor_skip_texture;
        this.ceil_texture = ceil_texture;
        this.ceil_brightness = ceil_brightness;
        this.ceil_tiled = ceil_tiled;
        this.ceil_skip_texture = ceil_skip_texture;
    }
}
