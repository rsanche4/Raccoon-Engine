
public class Sector {
    int sectorId;
	double floor_height;
    double ceil_height;
    String floorTexture;
    double floorBrightness;
    String ceilTexture;
    double ceilBrightness;
    
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
}
