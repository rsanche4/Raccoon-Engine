package raccoon;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class Sound implements AutoCloseable {
    private Clip clip;
    private boolean looping;
    private float volume; 
    
    public Sound(File file, boolean loop, float volume) {
        this.looping = loop;
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        try {
            playStream(AudioSystem.getAudioInputStream(file));
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    /**
     * Plays straight from WAV bytes in memory. This is the path the engine
     * actually uses, because assets can come out of a data.rpk where there is
     * no File to point at.
     */
    public Sound(byte[] wav_bytes, boolean loop, float volume) {
        this.looping = loop;
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        try {
            // AudioSystem needs mark/reset to sniff the format, hence the buffer.
            playStream(AudioSystem.getAudioInputStream(
                    new BufferedInputStream(new ByteArrayInputStream(wav_bytes))));
        } catch (Exception e) {
            reportFailure(e);
        }
    }

    private void playStream(AudioInputStream audio_stream) {
        try {
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
        } catch (Exception e) {
            // Catch broadly on purpose. A machine with no sound device makes
            // AudioSystem.getClip() throw IllegalArgumentException, which is
            // not a LineUnavailableException - letting that escape would take
            // down whichever Lua script asked for the sound. A game with no
            // audio should keep running, just silently.
            reportFailure(e);
        }
    }

    private void reportFailure(Exception e) {
        System.err.println("[Sound] Could not play audio (" + e.getClass().getSimpleName()
                + ": " + e.getMessage() + "). Continuing without sound.");
        clip = null;
    }

    /** True while the clip is still playing. */
    public boolean isPlaying() {
        return clip != null && clip.isRunning();
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