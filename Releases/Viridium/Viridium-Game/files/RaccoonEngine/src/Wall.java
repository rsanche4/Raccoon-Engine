
public class Wall {
    float x1, z1, x2, z2;
	int sectorid;
    String wallTexture;
    float wallBrightness;
    
    public Wall(float x1, float z1, float x2, float z2, int sectorId, String wallTexture, float wallBrightness) {
    	this.x1 = x1;
    	this.z1 = z1;
    	this.x2 = x2;
    	this.z2 = z2;
    	this.sectorid = sectorId;
    	this.wallTexture = wallTexture;
    	this.wallBrightness = wallBrightness;
    }
}