
public class Portal {
	float x1, z1, x2, z2;
    int sectorA;
    int sectorB;
    String portalBottomTexture;
    float portalBottomBrightness;
    String portalMiddleTexture;
    float portalMiddleBrightness;
    String portalTopTexture;
    float portalTopBrightness;
    
    public Portal(float x1, float z1, float x2, float z2, int sectorA, int sectorB, String portalBottomTexture, float portalBottomBrightness, String portalMiddleTexture, float portalMiddleBrightness, String portalTopTexture, float portalTopBrightness) {
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
