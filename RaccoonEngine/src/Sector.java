
public class Sector {
    int sectorId;
	double floor_height;
    double ceil_height;
    String floorTexture;
    double floorBrightness;
    String ceilTexture;
    double ceilBrightness;
    
    double[] boundary_coords = new double[4]; // { horizontal_value1, horizontal_value2, vertical_value1, vertical_value2 }
    int index_set_boundary_x = 0; 
    int index_set_boundary_z = 2;
    
    // Parameterized constructor
    public Sector(int sectorId, double floor_height, double ceil_height, String floorTexture, double floorBrightness, String ceilTexture, double ceilBrightness) {
        this.sectorId = sectorId;
        this.floor_height = floor_height;
        this.ceil_height = ceil_height;
        this.floorTexture = floorTexture;
        this.floorBrightness = floorBrightness;
        this.ceilTexture = ceilTexture;
        this.ceilBrightness = ceilBrightness;
    }
    
    // 0 - horizontal, 1 - vertical
    public void update_sector_boundary(double val, int orientation) {
    	if (index_set_boundary_z+index_set_boundary_x >= 6) { // this should never happen
    		return;
    	}
    	if (orientation==0) {
    		boundary_coords[index_set_boundary_z]=val;
    		index_set_boundary_z++;
    		if (index_set_boundary_z==4 && (boundary_coords[2]>boundary_coords[3])) {    		
    			double temp = boundary_coords[2];
    			boundary_coords[2] = boundary_coords[3];
    			boundary_coords[3] = temp;
    		}
    	} else {
    		boundary_coords[index_set_boundary_x]=val;
    		index_set_boundary_x++;
    		if (index_set_boundary_x==2 && (boundary_coords[0]>boundary_coords[1])) {
    			double temp = boundary_coords[0];
    			boundary_coords[0] = boundary_coords[1];
    			boundary_coords[1] = temp;
    		}
    	}
    }
    
    
    
}
