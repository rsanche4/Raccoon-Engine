
public class Wall {
    double x1, z1, x2, z2;
	int sectorid;
    String wallTexture;
    double wallBrightness;
    
    public Wall(double x1, double z1, double x2, double z2, int sectorId, String wallTexture, double wallBrightness) {
    	this.x1 = x1;
    	this.z1 = z1;
    	this.x2 = x2;
    	this.z2 = z2;
    	this.sectorid = sectorId;
    	this.wallTexture = wallTexture;
    	this.wallBrightness = wallBrightness;
    }
}




