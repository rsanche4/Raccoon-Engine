package raccoon;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class Sound implements AutoCloseable {
    private Clip clip;
    private boolean looping;
    private float volume; 
    
    public Sound(File file, boolean loop, float volume) {
        this.looping = loop;
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        playSound(file);
    }
    
    private void playSound(File file) {
        try {
            AudioInputStream audio_stream = AudioSystem.getAudioInputStream(file);
            clip = AudioSystem.getClip();
            clip.open(audio_stream);
            
            setVolume(volume);
            if (!looping) {
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP && !clip.isRunning()) {
                        close(); 
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
    
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volume_control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = volumeToDecibels(this.volume, volume_control.getMinimum(), volume_control.getMaximum());
            volume_control.setValue(dB);
        }
    }
    
    private float volumeToDecibels(float volume, float min, float max) {
        if (volume <= 0) {
            return min;
        }
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
            clip.close();
        }
    }
}