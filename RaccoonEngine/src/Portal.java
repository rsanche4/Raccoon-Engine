
public class Portal {
	double x1, z1, x2, z2;
    int sectorA;
    int sectorB;
    String portalBottomTexture;
    double portalBottomBrightness;
    String portalMiddleTexture;
    double portalMiddleBrightness;
    String portalTopTexture;
    double portalTopBrightness;
    
    public Portal(double x1, double z1, double x2, double z2, int sectorA, int sectorB, String portalBottomTexture, double portalBottomBrightness, String portalMiddleTexture, double portalMiddleBrightness, String portalTopTexture, double portalTopBrightness) {
    	this.x1 = x1;
    	this.z1 = z1;
    	this.x2 = x2;
    	this.z2 = z2;
    	this.sectorA = sectorA;
    	this.sectorB = sectorB;
    	 this.portalBottomTexture = portalBottomTexture;
         this.portalBottomBrightness = portalBottomBrightness;
         this.portalMiddleTexture = portalMiddleTexture;
         this.portalMiddleBrightness = portalMiddleBrightness;
         this.portalTopTexture = portalTopTexture;
         this.portalTopBrightness = portalTopBrightness;
    }
}
