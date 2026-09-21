
public class Sprite {
	public float spriteXPos;
	public float spriteYPos;
	public float spriteZPos;
	public float sprite_length;
	public float sprite_brightness;
	public String spritename;
	public String spriteId;
	public String behaviorScript;
	public float collision_radius; // 0 for non-collidable

	public Sprite(float sx, float sy, float sz, float sprite_length, float sprite_brightness, String spritename, String spriteId, String behavior_script, float collision_radius) {
		spriteXPos = sx;
		spriteYPos = sy;
		spriteZPos = sz;
		this.sprite_length = sprite_length;
		this.sprite_brightness = sprite_brightness;
		this.spritename = spritename;
		this.spriteId = spriteId;
		behaviorScript = behavior_script;
		this.collision_radius = collision_radius;
	}
}