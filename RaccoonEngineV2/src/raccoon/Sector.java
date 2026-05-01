package raccoon;

public class Sector {
	
	int ID;
	double floor_height;
    double ceil_height;
    String floor_texture;
    int floor_brightness;
    double floor_tiled;
    boolean floor_skip_texture; 
    String ceil_texture;
    int ceil_brightness;
    double ceil_tiled;
    boolean ceil_skip_texture; 
    
    int[] boundary_coords = new int[4];
    int index_set_boundary_x = 0; 
    int index_set_boundary_z = 2;
    
    public Sector(int ID, double floor_height, double ceil_height, String floor_texture, int floor_brightness, double floor_tiled, boolean floor_skip_texture, String ceil_texture, int ceil_brightness, double ceil_tiled, boolean ceil_skip_texture) {
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
    
    public void update_sector_boundary(double val, int orientation) {
    	if (index_set_boundary_z+index_set_boundary_x >= 6) { // this should never happen
    		return; // TODO Wtf is this
    	}
    	if (orientation==0) {
    		boundary_coords[index_set_boundary_z]=val;
    		index_set_boundary_z++;
    		if (index_set_boundary_z==4 && (boundary_coords[2]>boundary_coords[3])) {    		
    			float temp = boundary_coords[2];
    			boundary_coords[2] = boundary_coords[3];
    			boundary_coords[3] = temp;
    		}
    	} else {
    		boundary_coords[index_set_boundary_x]=val;
    		index_set_boundary_x++;
    		if (index_set_boundary_x==2 && (boundary_coords[0]>boundary_coords[1])) {
    			float temp = boundary_coords[0];
    			boundary_coords[0] = boundary_coords[1];
    			boundary_coords[1] = temp;
    		}
    	}
    }
}
