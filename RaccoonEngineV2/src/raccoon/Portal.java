package raccoon;

public class Portal {
	int x1, z1, x2, z2;
    int sector_a = -1;
    int sector_b = -1;
    String bottom_texture;
    int bottom_brightness;
    int bottom_tiled;
    boolean bottom_skip_texture;
    String middle_texture;
    int middle_brightness;
    int middle_tiled;
    boolean middle_skip_texture;
    String top_texture;
    int top_brightness;
    int top_tiled;
    boolean top_skip_texture;
    
    public Portal(int x1, int z1, int x2, int z2, int sector_a, int sector_b, String bottom_texture, int bottom_brightness, int bottom_tiled, boolean bottom_skip_texture, String middle_texture, int middle_brightness, int middle_tiled, boolean middle_skip_texture, String top_texture, int top_brightness, int top_tiled, boolean top_skip_texture) {
    	this.x1 = x1;
    	this.z1 = z1;
    	this.x2 = x2;
    	this.z2 = z2;
    	this.sector_a = sector_a;
    	this.sector_b = sector_b;
    	this.bottom_texture = bottom_texture;
    	this.bottom_brightness = bottom_brightness;
    	this.bottom_tiled = bottom_tiled;
    	this.bottom_skip_texture = bottom_skip_texture;
    	this.middle_texture = middle_texture;
    	this.middle_brightness = middle_brightness;
    	this.middle_tiled = middle_tiled;
    	this.middle_skip_texture = middle_skip_texture;
    	this.top_texture = top_texture;
    	this.top_brightness = top_brightness;
    	this.top_tiled = top_tiled;
    	this.top_skip_texture = top_skip_texture;
    }
}
