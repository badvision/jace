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

package jace.hardware;

import jace.config.ConfigurableField;
import jace.config.Name;
import jace.hardware.tts.SpeechEngine;
import jace.hardware.tts.SpeechEngineCreationException;
import jace.hardware.tts.SpeechEngineNative;
import jace.hardware.tts.Voice;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Votrax Type-n-Talk emulation.
 *
 * Receives characters from the Apple II via the Super Serial Card protocol
 * and speaks them using the host OS native TTS engine. Text is buffered and
 * flushed to the TTS engine when a sentence-ending punctuation mark or
 * carriage-return / line-feed is received.
 *
 * No TCP socket is opened. All SSC baud-rate, port, and flow-control
 * settings are irrelevant and are hidden from the configuration UI.
 *
 * @author Brendan Robert (BLuRry) brendan.robert@gmail.com
 */
@Name("Votrax Type-n-Talk")
public class CardVotrax extends CardSSC {

    // ── Suppress inherited CardSSC config fields ──────────────────────────
    // Declaring these fields here (without @ConfigurableField) causes
    // Configuration.java to skip the annotated superclass versions.
    public short   IP_PORT      = 0;
    public int     livenessCheck = 0;
    public boolean RECV_STRIP_LF = false;
    public boolean TRANS_ADD_LF  = false;

    // ── TTS configuration ─────────────────────────────────────────────────

    @ConfigurableField(
        name        = "Voice",
        shortName   = "voice",
        defaultValue = "",
        description  = "TTS voice name. Leave blank for the system default. "
                     + "macOS examples: 'Samantha', 'Alex'. "
                     + "Windows example: 'Microsoft David Desktop'.")
    public String voiceName = "";

    @ConfigurableField(
        name        = "Speech Rate",
        shortName   = "rate",
        defaultValue = "0",
        description  = "Speech rate from -100 (slowest) to 100 (fastest). 0 = normal system rate.")
    public int speechRate = 0;

    // ── Flush-trigger characters ──────────────────────────────────────────

    private static final Set<Character> FLUSH_CHARS = Set.of(
            '\r', '\n', '.', '!', '?', ';', ':'
    );

    // ── Runtime state (not serialised) ────────────────────────────────────

    private transient SpeechEngine              speechEngine;
    private transient final StringBuilder       textBuffer   = new StringBuilder();
    private transient final BlockingQueue<String> speechQueue = new LinkedBlockingQueue<>();
    private transient Thread                    speechThread;

    // ── Construction ──────────────────────────────────────────────────────

    public CardVotrax() {
        super();
    }

    @Override
    public String getDeviceName() {
        return "Votrax Type-n-Talk";
    }

    // ── Disable the SSC TCP socket ────────────────────────────────────────

    /**
     * Overridden to prevent the SSC from opening a TCP listener.
     * The parent's resume() starts this on a daemon thread; it exits
     * immediately so no port is ever opened.
     */
    @Override
    public void socketMonitor() {
        // No network socket for the Votrax – speech is one-way outbound only.
    }

    /**
     * The card is always "connected": the TTS engine is always ready to
     * accept text. The Apple II sees the transmit register as perpetually
     * empty and clear-to-send.
     */
    @Override
    public boolean isConnected() {
        return true;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void resume() {
        PORT_CONNECTED = true;
        initSpeechEngine();
        startSpeechThread();
        super.resume(); // starts the listen thread → runs socketMonitor() → exits immediately
    }

    @Override
    public boolean suspend() {
        stopSpeechThread();
        if (speechEngine != null) {
            speechEngine.stopTalking();
        }
        textBuffer.setLength(0);
        PORT_CONNECTED = false;
        return super.suspend();
    }

    // ── Output interception ───────────────────────────────────────────────

    /**
     * Called by CardSSC when the Apple II writes a byte to the ACIA data
     * register. Characters are buffered and dispatched to the TTS engine
     * asynchronously when a flush trigger (CR, LF, or sentence-ending
     * punctuation) is received.
     */
    @Override
    protected void sendOutputByte(int i) {
        // Apple II text uses high-bit ASCII – strip bit 7 to get plain ASCII.
        char c = (char) (i & 0x7F);

        if (FLUSH_CHARS.contains(c)) {
            // Include the punctuation in the spoken text (but not CR/LF).
            if (c != '\r' && c != '\n') {
                textBuffer.append(c);
            }
            String text = textBuffer.toString().trim();
            textBuffer.setLength(0);
            if (!text.isEmpty()) {
                speechQueue.offer(text);
            }
        } else if (c >= ' ') {
            // Printable character – accumulate.
            textBuffer.append(c);
        }
        // Other control characters are discarded silently.
    }

    // ── TTS engine management ─────────────────────────────────────────────

    private void initSpeechEngine() {
        try {
            speechEngine = SpeechEngineNative.getInstance();
            speechEngine.setRate(Math.max(-100, Math.min(100, speechRate)));

            if (voiceName != null && !voiceName.isBlank()) {
                speechEngine.setVoice(voiceName);
            } else {
                // Use the first available voice so say() always works.
                List<Voice> voices = speechEngine.getAvailableVoices();
                if (!voices.isEmpty()) {
                    speechEngine.setVoice(voices.get(0).getName());
                }
            }

            Logger.getLogger(CardVotrax.class.getName()).log(Level.INFO,
                    "Votrax TTS ready (voice={0}, rate={1})",
                    new Object[]{voiceName.isBlank() ? "(default)" : voiceName, speechRate});

        } catch (SpeechEngineCreationException e) {
            Logger.getLogger(CardVotrax.class.getName()).log(Level.WARNING,
                    "Votrax: could not initialise TTS engine – {0}", e.getMessage());
            speechEngine = null;
        }
    }

    private void startSpeechThread() {
        if (speechThread != null && speechThread.isAlive()) return;
        speechThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String text = speechQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (text != null && speechEngine != null) {
                        Process p = speechEngine.say(text);
                        if (p != null) p.waitFor();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    Logger.getLogger(CardVotrax.class.getName()).log(
                            Level.WARNING, "Votrax TTS error: {0}", e.getMessage());
                }
            }
        });
        speechThread.setDaemon(true);
        speechThread.setName("Votrax-TTS-slot" + getSlot());
        speechThread.start();
    }

    private void stopSpeechThread() {
        speechQueue.clear();
        if (speechThread != null) {
            speechThread.interrupt();
            try { speechThread.join(200); } catch (InterruptedException ignored) {}
            speechThread = null;
        }
    }
}
