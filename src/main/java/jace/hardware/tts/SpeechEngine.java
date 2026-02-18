/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

import java.io.IOException;
import java.util.List;

public interface SpeechEngine {

    void findAvailableVoices() throws IOException, InterruptedException;

    List<Voice> getAvailableVoices();

    Voice findVoiceByPreferences(VoicePreferences voicePreferences);

    /** Set the voice by name. Pass null to use the system default. */
    void setVoice(String voice);

    /** Set the speech rate. Valid range is [-100..100]; 0 = normal. */
    void setRate(int rate) throws IllegalArgumentException;

    /** Speak {@code text} asynchronously. Returns the backing process. */
    Process say(String text) throws IOException;

    /** Stop any in-progress speech immediately. */
    void stopTalking();
}
