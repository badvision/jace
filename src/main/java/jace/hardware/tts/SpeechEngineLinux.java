/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

public class SpeechEngineLinux extends SpeechEngineAbstract {

    public SpeechEngineLinux() throws SpeechEngineCreationException {
        super();
    }

    @Override
    public String getSayExecutable() { return "spd-say"; }

    @Override
    public String[] getSayOptionsToGetSupportedVoices() { return new String[]{"-L"}; }

    @Override
    public String[] getSayOptionsToSayText(String text) {
        if (voice != null && !voice.isBlank()) {
            return new String[]{"-l", voice, "-r", String.valueOf(rate), text};
        }
        return new String[]{"-r", String.valueOf(rate), text};
    }

    @Override
    public Voice parse(String line) throws ParseException {
        String[] tokens = line.trim().split("  +");
        if (tokens.length != 3) {
            throw new ParseException("Unexpected line from spd-say: " + line);
        }
        if (tokens[0].equalsIgnoreCase("NAME")) return null; // header row
        Voice v = new Voice();
        v.setName(tokens[1]);
        v.setCulture(tokens[1]);
        v.setGender("?");
        v.setAge("?");
        v.setDescription(tokens[0]);
        return v;
    }
}
