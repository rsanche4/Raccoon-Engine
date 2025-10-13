
public class Sector {
    int sectorId;
	float floor_height;
    float ceil_height;
    String floorTexture;
    float floorBrightness;
    String ceilTexture;
    float ceilBrightness;
    
    boolean[] collision_data; // before u do the index, do sector id -1 to get the correct value 
    
    float[] boundary_coords = new float[4]; // { horizontal_value1, horizontal_value2, vertical_value1, vertical_value2 }
    int index_set_boundary_x = 0; 
    int index_set_boundary_z = 2;
    
    // Parameterized constructor
    public Sector(int sectorId, float floor_height, float ceil_height, String floorTexture, float floorBrightness, String ceilTexture, float ceilBrightness) {
        this.sectorId = sectorId;
        this.floor_height = floor_height;
        this.ceil_height = ceil_height;
        this.floorTexture = floorTexture;
        this.floorBrightness = floorBrightness;
        this.ceilTexture = ceilTexture;
        this.ceilBrightness = ceilBrightness;
    }
    
    public void init_collisions(int size) {
    	collision_data = new boolean[size];
    }
    
    // 0 - horizontal, 1 - vertical
    public void update_sector_boundary(float val, int orientation) {
    	if (index_set_boundary_z+index_set_boundary_x >= 6) { // this should never happen
    		return;
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
