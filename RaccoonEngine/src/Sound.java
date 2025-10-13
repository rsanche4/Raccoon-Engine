import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class Sound implements AutoCloseable {
    private Clip clip;
    private boolean looping;
    private float volume;
    
    public Sound(String filePath, boolean loop, float volume) {
        this.looping = loop;
        this.volume = volume;
        playSound(filePath);
    }
    
    private void playSound(String filePath) {
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(filePath));
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            
            // Set volume
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                volumeControl.setValue(volume);
            }
            
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