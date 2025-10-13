
public class Sprite {
	public float spriteXPos;
	public float spriteYPos;
	public float spriteZPos;
	public float spriteDir;
	public float spriteDist;
	public String spritename;
	public String spriteId;
	public String behaviorScript;

	public Sprite(float sx, float sy, float sz, float spriteDirection, String spritename, String spriteId, String behavior_script) {
		spriteXPos = sx;
		spriteYPos = sy;
		spriteZPos = sz;
		spriteDir = spriteDirection;
		this.spritename = spritename;
		this.spriteId = spriteId;
		behaviorScript = behavior_script;
	}
}