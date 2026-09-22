import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class Sound implements AutoCloseable {
    private Clip clip;
    private boolean looping;
    private float volume; // 0.0 to 1.0 scale
    
    public Sound(String filePath, boolean loop, float volume) {
        this.looping = loop;
        this.volume = Math.max(0.0f, Math.min(1.0f, volume)); // Clamp to 0-1
        playSound(filePath);
    }
    
    private void playSound(String filePath) {
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(filePath));
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            
            // Set volume
            setVolume(volume);
            
            // Add listener to auto-close when done (if not looping)
            if (!looping) {
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP && !clip.isRunning()) {
                        close(); // Auto-close when sound finishes
                    }
                });
            }
            
            if (looping) {
                clip.loop(Clip.LOOP_CONTINUOUSLY);
            }
            
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Sets the volume using a 0.0 to 1.0 scale
     * @param volume 0.0 = mute, 1.0 = max volume
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = volumeToDecibels(this.volume, volumeControl.getMinimum(), volumeControl.getMaximum());
            volumeControl.setValue(dB);
        }
    }
    
    /**
     * Converts 0.0-1.0 volume scale to decibels
     */
    private float volumeToDecibels(float volume, float min, float max) {
        if (volume <= 0) {
            return min; // Mute
        }
        // Logarithmic scale for natural volume perception
        // Maps 0.0-1.0 to min-max dB range
        return (float) (min + (max - min) * Math.pow(volume, 2));
    }
    
    public float getVolume() {
        return volume;
    }
    
    public void stopSound() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }
    
    @Override
    public void close() {
        if (clip != null) {
            clip.close(); // Releases system resources
        }
    }
}