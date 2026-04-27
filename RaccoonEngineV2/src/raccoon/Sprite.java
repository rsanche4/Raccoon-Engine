package raccoon;

public class Sprite {

	String ID;
	double sprite_x_pos;
	double sprite_y_pos;
	double sprite_z_pos;
	double sprite_length;
	int sprite_brightness;
	String spritename;
	String behavior_script;
	double collision_radius;
	double direction_rad;
	
	public Sprite(String ID, double sprite_x_pos, double sprite_y_pos, double sprite_z_pos, double sprite_length, String spritename, String behavior_script, int sprite_brightness, double collision_radius, double direction_rad) {
		this.ID = ID;
		this.sprite_x_pos = sprite_x_pos;
		this.sprite_y_pos = sprite_y_pos;
		this.sprite_z_pos = sprite_z_pos;
		this.sprite_length = sprite_length;
		this.sprite_brightness = sprite_brightness;
		this.spritename = spritename;
		this.behavior_script = behavior_script;
		this.collision_radius = collision_radius;
		this.direction_rad = direction_rad;
	}
	
}



