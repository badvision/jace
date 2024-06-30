/** 
* Copyright 2024 Brendan Robert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package jace.core;

import java.nio.BufferOverflowException;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;

import jace.Emulator;
import jace.config.ConfigurableField;

/**
 * Manages sound resources used by various audio devices (such as speaker and
 * mockingboard cards.) The plumbing is managed in this class so that the
 * consumers do not have to do a lot of work to manage mixer lines or deal with
 * how to reuse active lines if needed. It is possible that this class might be
 * used to manage volume in the future, but that remains to be seen.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
public class SoundMixer extends Device {
    public static boolean DEBUG_SOUND = false;

    /**
     * Bits per sample
     * Making this configurable requires too much effort and not a lot of benefit
     */
    // @ConfigurableField(name = "Bits per sample", shortName = "bits")
    public static int BITS = 16;
    /**
     * Sample playback rate
     */
    @ConfigurableField(name = "Playback Rate", shortName = "freq")
    public static int RATE = 44100;
    @ConfigurableField(name = "Mute", shortName = "mute")
    public static boolean MUTE = false;

    @ConfigurableField(name = "Buffer size", shortName = "buffer")
    //public static int BUFFER_SIZE = 1024;  Ok on MacOS but choppy on Windows!
    public static int BUFFER_SIZE = 2048;

    public static boolean PLAYBACK_ENABLED = false;
    // Innocent until proven guilty by a failed initialization
    public static boolean PLAYBACK_DRIVER_DETECTED = true;
    public static boolean PLAYBACK_INITIALIZED = false;
    
    private static String defaultDeviceName;
    private static long audioDevice = -1;
    private static long audioContext = -1;
    private static ALCCapabilities audioCapabilities;
    private static ALCapabilities audioLibCapabilities;
    // In case the OpenAL implementation wants to be run in a single thread, use a single thread executor
    protected static ExecutorService soundThreadExecutor = Executors.newSingleThreadExecutor();
    public SoundMixer() {
        if (!Utility.isHeadlessMode()) {
            defaultDeviceName = ALC10.alcGetString(0, ALC10.ALC_DEFAULT_DEVICE_SPECIFIER);        
        }
    }

    public static class SoundError extends Exception {
        private static final long serialVersionUID = 1L;
        public SoundError(String message) {
            super(message);
        }
    }

    public static <T> T performSoundFunction(Callable<T> operation, String action) throws SoundError {
        return performSoundFunction(operation, action, false);
    }

    public static <T> T performSoundFunction(Callable<T> operation, String action, boolean ignoreError) throws SoundError {
        Future<T> result = soundThreadExecutor.submit(operation);
        try {
            Future<Integer> error = soundThreadExecutor.submit(AL10::alGetError);
            int err;
            err = error.get();
            if (!ignoreError && DEBUG_SOUND) {
                if (err != AL10.AL_NO_ERROR) {
                    System.err.println(">>>SOUND ERROR " + AL10.alGetString(err) + " when performing action: " + action);
                    // throw new SoundError(AL10.alGetString(err));
                }
            }
            return result.get();
        } catch (ExecutionException e) {
            System.out.println("Error when executing sound action: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            // Do nothing: sound is probably being reset
        }
        return null;
    }

    public static void performSoundOperation(Runnable operation, String action) throws SoundError {        
        performSoundOperation(operation, action, false);
    }

    public static void performSoundOperation(Runnable operation, String action, boolean ignoreError) throws SoundError {
        performSoundFunction(()->{
            operation.run();
            return null;
        }, action, ignoreError);
    }

    public static void performSoundOperationAsync(Runnable operation, String action) {
        soundThreadExecutor.submit(operation, action);
    }

    protected static void initSound() {
        if (Utility.isHeadlessMode()) {
            return;
        }
        try {
            performSoundOperation(()->{
                if (!PLAYBACK_INITIALIZED) {                
                    audioDevice = ALC10.alcOpenDevice(defaultDeviceName);
                    audioContext = ALC10.alcCreateContext(audioDevice, new int[]{0});
                    ALC10.alcMakeContextCurrent(audioContext);
                    audioCapabilities = ALC.createCapabilities(audioDevice);
                    audioLibCapabilities = AL.createCapabilities(audioCapabilities);
                    if (!audioLibCapabilities.OpenAL10) {
                        PLAYBACK_DRIVER_DETECTED = false;
                        Logger.getLogger(SoundMixer.class.getName()).warning("OpenAL 1.0 not supported");
                        Emulator.withComputer(c->c.mixer.detach());
                    }
                    PLAYBACK_INITIALIZED = true;
                } else {
                    ALC10.alcMakeContextCurrent(audioContext);
                }
            }, "Initalize audio device");
        } catch (SoundError e) {
            PLAYBACK_DRIVER_DETECTED = false;
            Logger.getLogger(SoundMixer.class.getName()).warning("Error when initializing sound: " + e.getMessage());
            Emulator.withComputer(c->c.mixer.detach());
        }
    }

    // Lots of inspiration from https://www.youtube.com/watch?v=dLrqBTeipwg
    @Override
    public void attach() {
        if (Utility.isHeadlessMode()) {
            return;
        }
        if (!PLAYBACK_DRIVER_DETECTED) {
            Logger.getLogger(SoundMixer.class.getName()).warning("Sound driver not detected");
            return;
        }
        super.attach();
        initSound();
        PLAYBACK_ENABLED = true;
    }

    private static List<SoundBuffer> buffers = Collections.synchronizedList(new ArrayList<>());
    public static SoundBuffer createBuffer(boolean stereo) throws InterruptedException, ExecutionException, SoundError {
        if (!PLAYBACK_ENABLED) {
            System.err.println("Sound playback not enabled, buffer not created.");
            return null;
        }
        SoundBuffer buffer = new SoundBuffer(stereo);
        buffers.add(buffer);
        return buffer;
    }
    public static class SoundBuffer {
        public static int MAX_BUFFER_ID;
        private ShortBuffer currentBuffer;
        private ShortBuffer alternateBuffer;
        private int audioFormat;
        private int currentBufferId;
        private int alternateBufferId;
        private int sourceId;
        private boolean isAlive;
        private int buffersGenerated = 0;
        
        public SoundBuffer(boolean stereo) throws InterruptedException, ExecutionException, SoundError {
            initSound();
            currentBuffer = BufferUtils.createShortBuffer(BUFFER_SIZE * (stereo ? 2 : 1));
            alternateBuffer = BufferUtils.createShortBuffer(BUFFER_SIZE * (stereo ? 2 : 1));
            try {
                currentBufferId = performSoundFunction(AL10::alGenBuffers, "Initalize sound buffer: primary");
                alternateBufferId = performSoundFunction(AL10::alGenBuffers, "Initalize sound buffer: alternate");
                boolean hasSource = false;
                while (!hasSource) {
                    sourceId = performSoundFunction(AL10::alGenSources, "Initalize sound buffer: create source");
                    hasSource = performSoundFunction(()->AL10.alIsSource(sourceId), "Initalize sound buffer: Check if source is valid");
                }
                performSoundOperation(()->AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE), "Set looping to false");
            } catch (SoundError e) {
                Logger.getLogger(SoundMixer.class.getName()).warning("Error when creating sound buffer: " + e.getMessage());
                Thread.dumpStack();
                shutdown();
                throw e;
            }
            audioFormat = stereo ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
            isAlive = true;
        }

        public boolean isAlive() {
            return isAlive;
        }

        /* If stereo, call this once for left and then again for right sample */
        public void playSample(short sample) throws InterruptedException, ExecutionException, SoundError {
            if (!isAlive) {
                return;
            }
            if (!currentBuffer.hasRemaining()) {
                this.flush();
            }
            try {
                currentBuffer.put(sample);
            } catch (BufferOverflowException e) {
                if (DEBUG_SOUND) {
                    System.err.println("Buffer overflow, trying to compensate");
                }
                currentBuffer.clear();
                currentBuffer.put(sample);
            }
        }

        public void shutdown() throws InterruptedException, ExecutionException, SoundError {            
            if (!isAlive) {
                return;
            }
            isAlive = false;
            
            try {
                performSoundOperation(()->{if (AL10.alIsSource(sourceId)) AL10.alSourceStop(sourceId);}, "Shutdown: stop source");
            } finally {
                try {
                    performSoundOperation(()->{if (AL10.alIsSource(sourceId)) AL10.alDeleteSources(sourceId);}, "Shutdown: delete source");
                } finally {
                    sourceId = -1;
                    try {
                        performSoundOperation(()->{if (AL10.alIsBuffer(alternateBufferId)) AL10.alDeleteBuffers(alternateBufferId);}, "Shutdown: delete buffer 1");
                    } finally {
                        alternateBufferId = -1;
                        try {
                            performSoundOperation(()->{if (AL10.alIsBuffer(currentBufferId)) AL10.alDeleteBuffers(currentBufferId);}, "Shutdown: delete buffer 2");
                        } finally {
                            currentBufferId = -1;
                            buffers.remove(this);
                        }
                    }
                }
            }
        }

        public void flush() throws SoundError {
            buffersGenerated++;
            if (buffersGenerated > 2) {
                int[] unqueueBuffers = new int[]{currentBufferId};
                performSoundOperation(()->{
                    int buffersProcessed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
                    while (buffersProcessed < 1) {
                        Thread.onSpinWait();
                        buffersProcessed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
                    }
                }, "Flush: wait for buffers to finish playing");
                if (!isAlive) {
                    return;
                }
                // TODO: Figure out why we get Invalid Value on a new buffer
                performSoundOperation(()->AL10.alSourceUnqueueBuffers(sourceId, unqueueBuffers), "Flush: unqueue buffers");
            }
            if (!isAlive) {
                return;
            }
            // TODO: Figure out why we get Invalid Operation error on a new buffer after unqueue reports Invalid Value
            currentBuffer.flip();
            performSoundOperation(()->AL10.alBufferData(currentBufferId, audioFormat, currentBuffer, RATE), "Flush: buffer data");
            currentBuffer.clear();
            if (!isAlive) {
                return;
            }
            // TODO: Figure out why we get Invalid Operation error on a new buffer after unqueue reports Invalid Value
            performSoundOperation(()->AL10.alSourceQueueBuffers(sourceId, currentBufferId), "Flush: queue buffer");
            performSoundOperationAsync(()->{
                if (AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    AL10.alSourcePlay(sourceId);
                }
            }, "Flush: Start playing buffer");

            // Swap AL buffers
            int tempId = currentBufferId;
            currentBufferId = alternateBufferId;
            alternateBufferId = tempId;
            // Swap Java buffers
            ShortBuffer tempBuffer = currentBuffer;
            currentBuffer = alternateBuffer;
            alternateBuffer = tempBuffer;                
        }
    }

    public int getActiveBuffers() {
        return buffers.size();
    }

    @Override
    public void detach() {
        if (!PLAYBACK_ENABLED) {
            return;
        }   
        MUTE = true;
        PLAYBACK_ENABLED = false;
        
        while (!buffers.isEmpty()) {
            SoundBuffer buffer = buffers.remove(0);
            try {
                buffer.shutdown();
            } catch (InterruptedException | ExecutionException | SoundError e) {
                Logger.getLogger(SoundMixer.class.getName()).warning("Error when detaching sound mixer: " + e.getMessage());
            }
        }
        buffers.clear();
        PLAYBACK_INITIALIZED = false;
        try {
            performSoundOperation(()->ALC10.alcDestroyContext(audioContext), "Detach: destroy context", true);
            performSoundOperation(()->ALC10.alcCloseDevice(audioDevice), "Detach: close device", true);
        } catch (SoundError e) {
            // Shouldn't throw but have to catch anyway
        }
        super.detach();
    }

    @Override

    public String getDeviceName() {
        return "Sound Output";
    }

    @Override
    public String getShortName() {
        return "mixer";
    }

    @Override
    public synchronized void reconfigure() {
        PLAYBACK_ENABLED = PLAYBACK_DRIVER_DETECTED && !MUTE;
        if (PLAYBACK_ENABLED) {
            attach();
        } else {
            detach();
        }
    }

    @Override
    public void tick() {
    }
}
