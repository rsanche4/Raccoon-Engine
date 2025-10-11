
public class Sprite {
	public double spriteXPos;
	public double spriteYPos;
	public double spriteZPos;
	public double spriteDir;
	public double spriteDist;
	public String spritename;
	public String spriteId;
	public String behaviorScript;

	public Sprite(double sx, double sy, double sz, double spriteDirection, String spritename, String spriteId, String behavior_script) {
		spriteXPos = sx;
		spriteYPos = sy;
		spriteZPos = sz;
		spriteDir = spriteDirection;
		this.spritename = spritename;
		this.spriteId = spriteId;
		behaviorScript = behavior_script;
	}
}