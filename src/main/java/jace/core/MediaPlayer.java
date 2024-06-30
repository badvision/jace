package jace.core;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import jace.core.SoundMixer.SoundBuffer;
import jace.core.SoundMixer.SoundError;

public class MediaPlayer {

    double vol = 1.0;
    int repeats = 0;
    int maxRepetitions = 1;
    Status status = Status.NOT_STARTED;
    Media soundData;
    SoundBuffer playbackBuffer;
    Duration lastKnownDuration = Duration.ZERO;
    Executor executor = Executors.newSingleThreadExecutor();

    public static enum Status {
        NOT_STARTED, PLAYING, PAUSED, STOPPED
    }

    public static final int INDEFINITE = -1;

    public MediaPlayer(Media song) {
        this.soundData = song;
    }

    public Status getStatus() {
        return status;
    }

    public Duration getCurrentTime() {
        if (soundData == null) {
            return lastKnownDuration;
        } else {
            return soundData.getCurrentTime();
        }
    }

    public double getVolume() {
        return vol;
    }

    // NOTE: Once a song is stopped, it cannot be restarted.
    public void stop() {
        status = Status.STOPPED;
        try {
            if (playbackBuffer != null) {
                playbackBuffer.flush();
                playbackBuffer.shutdown();
                playbackBuffer = null;
            }
        } catch (InterruptedException | ExecutionException | SoundError e) {
            // Ignore exception on shutdown
        } finally {
            if (soundData != null) {
                lastKnownDuration = soundData.getCurrentTime();
                soundData.close();
            }
            soundData = null;
        }
    }

    public void setCycleCount(int i) {
        maxRepetitions = i;
    }

    public void setVolume(double d) {
        vol = Math.max(0.0, Math.min(1.0, d));
    }

    public void setStartTime(javafx.util.Duration millis) {
        soundData.seekToTime(millis);
    }

    public void pause() {
        status = Status.PAUSED;
    }

    public void play() {
        if (status == Status.STOPPED) {
            return;
        } else if (status == Status.NOT_STARTED) {
            repeats = 0;
            if (playbackBuffer == null || !playbackBuffer.isAlive()) {
                try {
                    playbackBuffer = SoundMixer.createBuffer(true);
                } catch (InterruptedException | ExecutionException | SoundError e) {
                    stop();
                    return;
                }
                if (playbackBuffer == null) {
                    stop();
                    return;
                }
            }
        }
        executor.execute(() -> {
            SoundBuffer theBuffer = playbackBuffer;
            status = Status.PLAYING;
            // System.out.println("Song playback thread started");
            Media theSoundData = soundData;
            while (status == Status.PLAYING && (maxRepetitions == INDEFINITE || repeats < maxRepetitions) && theSoundData != null && theBuffer != null) {
                if (theSoundData.isEnded()) {
                    if (maxRepetitions == INDEFINITE) {
                        theSoundData.restart();
                    } else {
                        repeats++;
                        if (repeats < maxRepetitions) {
                            theSoundData.restart();
                        } else {
                            System.out.println("Song ended");
                            this.stop();
                            break;
                        }
                    }
                }
                try {
                    theBuffer.playSample((short) (theSoundData.getNextLeftSample() * vol));
                    theBuffer.playSample((short) (theSoundData.getNextRightSample() * vol));
                } catch (InterruptedException | ExecutionException | SoundError e) {
                    e.printStackTrace();
                    this.stop();
                }
                theSoundData = soundData;
                theBuffer = playbackBuffer;
            }
        });
    }
}