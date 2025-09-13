import java.util.HashMap;

// Sprite Class
// Description: This class is for sprites mainly. This will be good for sorting them and then drawing them on the screen.
public class Sprite implements Comparable<Sprite> {
	public double spriteXPos;
	public double spriteYPos;
	public double spriteDist;
	public String spritename;
	public String spriteId;
	public String behaviorScript;

	public Sprite(String spritename, double sx, double sy, String spriteId, String behavior_script) {
		spriteXPos = sx;
		spriteYPos = sy;
		this.spritename = spritename;
		this.spriteId = spriteId;
		behaviorScript = behavior_script;
	}

	public Texture getTexture(HashMap<String, Texture> tex) {
		return tex.get(spritename);
	}
	
	public String getSpriteId() {
		return spriteId;
	}
	
	public String getBehaviorScriptName() {
		return behaviorScript;
	}

	@Override
	public int compareTo(Sprite otherSprite) {
		if (otherSprite.spriteDist > this.spriteDist) {
			return 1;
		}
		if (otherSprite.spriteDist < this.spriteDist) {
			return -1;
		}
		;
		return (int) (otherSprite.spriteDist - this.spriteDist);
	}
}