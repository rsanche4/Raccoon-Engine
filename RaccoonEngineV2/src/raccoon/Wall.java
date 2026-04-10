package raccoon;

public class Wall {

	int x1, z1, x2, z2;
	int sector_a = -1;
    String wall_texture;
    int wall_brightness;
    boolean wall_tiled;
    boolean skip_wall_texture;
    
    public Wall(int x1, int z1, int x2, int z2, int sector_a, String wall_texture, int wall_brightness, boolean wall_tiled, boolean skip_wall_texture) {
    	this.x1 = x1;
    	this.z1 = z1;
    	this.x2 = x2;
    	this.z2 = z2;
    	this.sector_a = sector_a;
    	this.wall_texture = wall_texture;
    	this.wall_brightness = wall_brightness;
    	this.wall_tiled = wall_tiled;
    	this.skip_wall_texture = skip_wall_texture;
    }
}
