/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class SpeechEngineAbstract implements SpeechEngine {

    protected String voice;
    protected Process process;
    protected List<Voice> availableVoices;
    protected int rate = 0;

    public SpeechEngineAbstract() throws SpeechEngineCreationException {
        try {
            findAvailableVoices();
        } catch (IOException | InterruptedException e) {
            throw new SpeechEngineCreationException(e.getMessage());
        }
        if (availableVoices.isEmpty()) {
            throw new SpeechEngineCreationException("No TTS voices found on this system.");
        }
    }

    @Override
    public void setVoice(String voice) {
        this.voice = voice;
    }

    @Override
    public void setRate(int rate) throws IllegalArgumentException {
        if (rate < -100 || rate > 100)
            throw new IllegalArgumentException("Rate must be in [-100..100]");
        this.rate = rate;
    }

    @Override
    public List<Voice> getAvailableVoices() {
        return availableVoices;
    }

    @Override
    public Voice findVoiceByPreferences(VoicePreferences prefs) {
        for (Voice v : availableVoices) {
            if (v.matches(prefs)) return v;
        }
        return null;
    }

    @Override
    public void findAvailableVoices() throws IOException, InterruptedException {
        ArrayList<String> lines = ProcessHelper.startApplicationAndGetOutput(
                getSayExecutable(), getSayOptionsToGetSupportedVoices());
        availableVoices = new ArrayList<>();
        for (String line : lines) {
            try {
                Voice v = parse(line);
                if (v != null) availableVoices.add(v);
            } catch (ParseException ignored) {}
        }
    }

    /**
     * Speak {@code text}. Always speaks even if no explicit voice has been
     * set – platform subclasses must handle a null {@code voice} in
     * {@link #getSayOptionsToSayText}.
     */
    @Override
    public Process say(String text) throws IOException {
        process = ProcessHelper.startApplication(getSayExecutable(), getSayOptionsToSayText(text));
        return process;
    }

    @Override
    public void stopTalking() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    // ── Abstract platform hooks ──────────────────────────────────────────

    public abstract String getSayExecutable();

    public abstract String[] getSayOptionsToGetSupportedVoices();

    /** Must handle {@code voice == null} (use OS default). */
    public abstract String[] getSayOptionsToSayText(String text);

    public abstract Voice parse(String line) throws ParseException;
}
