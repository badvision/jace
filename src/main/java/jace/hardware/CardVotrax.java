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

import jace.Emulator;
import jace.config.ConfigurableField;
import jace.config.DynamicSelection;
import jace.config.Name;
import jace.core.RAMEvent;
import jace.hardware.tts.SpeechEngine;
import jace.hardware.tts.SpeechEngineCreationException;
import jace.hardware.tts.SpeechEngineNative;
import jace.hardware.tts.Voice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // ── Static voice cache (populated once, async) ────────────────────────

    private static final TreeSet<String> AVAILABLE_VOICES = new TreeSet<>();
    private static final AtomicBoolean voiceLoadStarted = new AtomicBoolean(false);

    private static void startVoiceEnumeration() {
        if (!voiceLoadStarted.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                SpeechEngineNative.getInstance().getAvailableVoices()
                        .stream().map(Voice::getName).forEach(AVAILABLE_VOICES::add);
            } catch (Exception e) {
                Logger.getLogger(CardVotrax.class.getName())
                        .log(Level.WARNING, "Votrax: voice enumeration failed – {0}", e.getMessage());
            }
        });
        t.setDaemon(true);
        t.setName("Votrax-voice-enum");
        t.start();
    }

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
        description  = "TTS voice. 'System Default' uses the OS default voice.")
    public DynamicSelection<String> voice = new DynamicSelection<>(null) {
        @Override
        public boolean allowNull() { return true; }

        @Override
        public LinkedHashMap<String, String> getSelections() {
            LinkedHashMap<String, String> options = new LinkedHashMap<>();
            options.put(null, "System Default");
            AVAILABLE_VOICES.forEach(name -> options.put(name, name));
            return options;
        }
    };

    @ConfigurableField(
        name        = "Speech Rate",
        shortName   = "rate",
        defaultValue = "0",
        description  = "Speech rate from -100 (slowest) to 100 (fastest). 0 = normal system rate.")
    public int speechRate = 0;

    // ── Flush-trigger characters ──────────────────────────────────────────

    // '?' is intentionally absent: it is the phoneme-block end delimiter.
    private static final Set<Character> FLUSH_CHARS = Set.of(
            '\r', '\n', '.', '!', ';', ':'
    );

    // ── Votrax SC-01 phoneme → macOS ARPABET table ────────────────────────
    // Index = (ASCII byte) - 0x40.  null = STOP (end utterance).
    // Empty string = pause (PA0 / PA1).
    // Passed to `say` as "[[inpt PHON]][[P0][P1]...]" on macOS.
    private static final String[] PHONEME_ARPABET = {
        "EH",  // 0  EH3   "jacket"
        "EH",  // 1  EH2   "enlist"
        "EH",  // 2  EH1   "heavy"
        "",    // 3  PA0   (short pause)
        "DX",  // 4  DT    "butter" (flap)
        "EY",  // 5  A1    "enable"
        "EY",  // 6  A2    "made"
        "ZH",  // 7  ZH    "measure"
        "AA",  // 8  AH2   "honest"
        "IH",  // 9  I3    "inhibit"
        "IH",  // 10 I2    "inhibit"
        "IH",  // 11 I1    "inhibit"
        "M",   // 12 M     "mat"
        "N",   // 13 N     "sun"
        "B",   // 14 B     "bag"
        "V",   // 15 V     "van"
        "CH",  // 16 CH    "chip"
        "SH",  // 17 SH    "shop"
        "Z",   // 18 Z     "zoo"
        "AO",  // 19 AW1   "awful"
        "NG",  // 20 NG    "thing"
        "AA",  // 21 AH1   "father"
        "UH",  // 22 OO1   "looking"
        "UH",  // 23 OO    "book"
        "L",   // 24 L     "land"
        "K",   // 25 K     "kitten"
        "JH",  // 26 J     "judge"
        "HH",  // 27 H     "hello"
        "G",   // 28 G     "get"
        "F",   // 29 F     "fast"
        "D",   // 30 D     "paid"
        "S",   // 31 S     "pass"
        "EY",  // 32 A     "maid"
        "AY",  // 33 AY    "aide"
        "Y",   // 34 Y1    "yard"
        "AX",  // 35 UH3   "mission" (unstressed)
        "AA",  // 36 AH    "got"
        "P",   // 37 P     "past"
        "OW",  // 38 O     "more"
        "IH",  // 39 I     "pin"
        "UW",  // 40 U     "tune"
        "Y",   // 41 Y     "yet"
        "T",   // 42 T     "tap"
        "R",   // 43 R     "red"
        "IY",  // 44 E     "meet"
        "W",   // 45 W     "win"
        "AE",  // 46 AE    "dad"
        "AE",  // 47 AE1   "after"
        "AO",  // 48 AW2   "salty"
        "AX",  // 49 UH2   "tradition"
        "AX",  // 50 UH1   "about"
        "AH",  // 51 UH    "cup"
        "AO",  // 52 O2    "for"
        "OW",  // 53 O1    "aboard"
        "IY",  // 54 IU    "you"
        "UW",  // 55 U1    "you"
        "DH",  // 56 THV   "the" (voiced)
        "TH",  // 57 TH    "thing"
        "ER",  // 58 ER    "bird"
        "EH",  // 59 EH    "get"
        "EH",  // 60 E1    "before"
        "AO",  // 61 AW    "call"
        "",    // 62 PA1   (pause)
        null,  // 63 STOP  (end utterance)
    };

    // ── Runtime state (not serialised) ────────────────────────────────────

    private transient SpeechEngine              speechEngine;
    private transient final StringBuilder       textBuffer    = new StringBuilder();
    private transient final List<Integer>       phonemeBuffer = new ArrayList<>();
    private transient boolean                   phonemeMode   = false;
    private transient final BlockingQueue<String> speechQueue = new LinkedBlockingQueue<>();
    private transient Thread                    speechThread;
    private volatile  boolean                   speaking      = false;

    // ── Construction ──────────────────────────────────────────────────────

    public CardVotrax() {
        super();
        startVoiceEnumeration();
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

    // ── Hardware handshaking ──────────────────────────────────────────────

    /**
     * Intercepts register reads to emulate Votrax RTS/CTS handshaking.
     *
     * The real Type-N-Talk drives its RTS line HIGH while busy translating
     * text to phonemes, which the host sees as CTS inactive on the SSC.
     * SW2_CTS bit 0 = !CTS: 0 = ready, 1 = busy.
     *
     * We also suppress the receive-ready bit in ACIA_Status because the
     * Votrax is a one-way output device — it never sends data back.
     * Without this, the SSC's newInputAvailable flag (used for CTS) would
     * also incorrectly fire receive IRQs.
     */
    /**
     * Returns the echo byte set by sendOutputByte without touching directInput
     * (which is null — the Votrax has no TCP socket).
     */
    @Override
    protected int getInputByte() {
        if (newInputAvailable.get()) {
            synchronized (newInputAvailable) {
                newInputAvailable.set(false);
            }
        }
        return lastInputByte;
    }

    @Override
    protected void handleIOAccess(int register, RAMEvent.TYPE type, int value, RAMEvent e) {
        // Votrax is a one-way output device: it never sends data back, so it
        // must never fire a receive IRQ regardless of how the game configured
        // ACIA_Command. Force the flag off before super runs so the base-class
        // ACIA_Status handler cannot generate a spurious interrupt from our
        // echo byte.
        RECV_IRQ_ENABLED = false;

        super.handleIOAccess(register, type, value, e);
        if (type == RAMEvent.TYPE.WRITE) return;

        if (register == SW2_CTS) {
            boolean busy = speaking || !speechQueue.isEmpty();
            int v = e.getNewValue();
            e.setNewValue(busy ? (v | 0x01) : (v & ~0x01));
        }
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
        phonemeBuffer.clear();
        phonemeMode = false;
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
        int b = i & 0x7F;
        char c = (char) b;

        // ── Phoneme block start: ~ (0x7E) ────────────────────────────────
        if (b == 0x7E) {
            // Flush any pending text first so ordering is preserved.
            flushTextBuffer();
            phonemeMode = true;
            phonemeBuffer.clear();
        } else if (phonemeMode) {
            // ── Inside a phoneme block ────────────────────────────────────
            if (b == 0x3F) {
                // '?' = end of phoneme block; convert and queue for speech.
                phonemeMode = false;
                String speech = phonemesToSpeech();
                phonemeBuffer.clear();
                if (!speech.isEmpty()) speechQueue.offer(speech);
            } else if (b >= 0x40) {
                // Characters 0x40–0x7F map to SC-01 phoneme indices 0–63.
                phonemeBuffer.add(b - 0x40);
            }
            // Bytes below 0x40 (other than '?') inside a phoneme block
            // are ignored – they have no defined meaning in the TNT protocol.
        } else {
            // ── Normal ASCII text mode ────────────────────────────────────
            if (FLUSH_CHARS.contains(c)) {
                if (c != '\r' && c != '\n') textBuffer.append(c);
                flushTextBuffer();
            } else if (c >= ' ') {
                textBuffer.append(c);
            }
        }

        // ── Echo byte back into ACIA receive register ─────────────────────
        // The real Votrax echoes each sent byte back to the host so the
        // Apple II sees ACIA_Status bit 3 (RDRF) go high before it sends
        // the next character.
        lastInputByte = b;
        newInputAvailable.set(true);
    }

    private void flushTextBuffer() {
        String text = textBuffer.toString().trim();
        textBuffer.setLength(0);
        if (!text.isEmpty()) speechQueue.offer(text);
    }

    /**
     * Converts accumulated SC-01 phoneme indices to a macOS-style
     * [[inpt PHON]] ARPABET string for the native TTS engine.
     * Pauses (PA0/PA1) become [[slnc 150]] commands; STOP (63) ends
     * the utterance early.
     */
    private String phonemesToSpeech() {
        StringBuilder sb = new StringBuilder("[[inpt PHON]]");
        for (int idx : phonemeBuffer) {
            if (idx < 0 || idx >= PHONEME_ARPABET.length) continue;
            String p = PHONEME_ARPABET[idx];
            if (p == null) break;            // STOP phoneme – end utterance
            if (p.isEmpty()) {
                sb.append("[[slnc 150]]");   // PA0 / PA1 pause
            } else {
                sb.append("[[").append(p).append("]]");
            }
        }
        String result = sb.toString();
        return result.equals("[[inpt PHON]]") ? "" : result;
    }

    // ── TTS engine management ─────────────────────────────────────────────

    private void initSpeechEngine() {
        try {
            speechEngine = SpeechEngineNative.getInstance();
            speechEngine.setRate(Math.max(-100, Math.min(100, speechRate)));

            String selectedVoice = voice.getValue();
            if (selectedVoice != null) {
                speechEngine.setVoice(selectedVoice);
            } else {
                // Use the first available voice so say() always works.
                List<Voice> voices = speechEngine.getAvailableVoices();
                if (!voices.isEmpty()) {
                    speechEngine.setVoice(voices.get(0).getName());
                }
            }

            Logger.getLogger(CardVotrax.class.getName()).log(Level.INFO,
                    "Votrax TTS ready (voice={0}, rate={1})",
                    new Object[]{selectedVoice != null ? selectedVoice : "(default)", speechRate});

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
                        speaking = true;
                        try {
                            Process p = speechEngine.say(text);
                            if (p != null) p.waitFor();
                        } catch (IOException e) {
                            Logger.getLogger(CardVotrax.class.getName()).log(
                                    Level.WARNING, "Votrax TTS error: {0}", e.getMessage());
                        }
                    } else if (speaking) {
                        // Queue drained — Votrax is idle; deassert busy and notify host
                        speaking = false;
                        if (TRANS_IRQ_ENABLED) {
                            IRQ_TRIGGERED = true;
                            Emulator.withComputer(c -> c.getCpu().generateInterrupt());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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
