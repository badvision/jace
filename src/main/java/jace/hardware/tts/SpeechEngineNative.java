/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

import java.util.Locale;

/**
 * Factory that creates a platform-appropriate {@link SpeechEngine}.
 * Each call to {@link #getInstance()} creates a fresh engine instance
 * so that multiple cards can maintain independent voice/rate settings.
 */
public class SpeechEngineNative {

    private SpeechEngineNative() {}

    public static SpeechEngine getInstance() throws SpeechEngineCreationException {
        String os = System.getProperty("os.name", "").replaceAll("\\s", "").toLowerCase(Locale.US);
        if (os.startsWith("windows")) return new SpeechEngineWindows();
        if (os.startsWith("macos"))   return new SpeechEngineMacOS();
        if (os.startsWith("linux"))   return new SpeechEngineLinux();
        throw new SpeechEngineCreationException("Unsupported OS for TTS: " + System.getProperty("os.name"));
    }
}
