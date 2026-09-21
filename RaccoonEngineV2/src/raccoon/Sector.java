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
    
    double[] boundary_coords = new double[4];
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
    
    public void updateSectorBoundary(double val, int orientation) {
    	// Every sector is one Manhattan-partition cell: exactly 2 X values and
    	// 2 Z values. A third on either axis means the map is not valid editor
    	// output, so refuse it instead of silently ignoring or overflowing.
    	if ((orientation==0 && index_set_boundary_z >= 4) || (orientation!=0 && index_set_boundary_x >= 2)) {
    		throw new IllegalStateException("Sector " + ID + " got a third " + (orientation==0 ? "X" : "Z")
    				+ " boundary value (" + val + "). Each sector must be a single partition cell with exactly "
    				+ "2 X and 2 Z values. Re-export this map from the editor.");
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
