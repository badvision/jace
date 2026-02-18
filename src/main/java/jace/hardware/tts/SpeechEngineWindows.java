/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

public class SpeechEngineWindows extends SpeechEngineAbstract {

    private static final String
        CODE_TOKEN_TTS_NAME = "##TTS_NAME##",
        CODE_TOKEN_RATE     = "##RATE##",
        CODE_TOKEN_TEXT     = "##TEXT##",
        PS_SAY = String.join("",
            "Add-Type -AssemblyName System.Speech;",
            "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer;",
            "$speak.SelectVoice('", CODE_TOKEN_TTS_NAME, "');",
            "$speak.Rate=", CODE_TOKEN_RATE, ";",
            "$speak.Speak('", CODE_TOKEN_TEXT, "');"),
        PS_VOICES = String.join("",
            "Add-Type -AssemblyName System.Speech;",
            "$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer;",
            "$speak.GetInstalledVoices() | ",
            "Select-Object -ExpandProperty VoiceInfo | ",
            "Select-Object -Property Name,Culture,Gender,Age,Description | ",
            "ConvertTo-Csv -NoTypeInformation | ",
            "Select-Object -Skip 1;");

    public SpeechEngineWindows() throws SpeechEngineCreationException {
        super();
    }

    @Override
    public String getSayExecutable() { return "PowerShell"; }

    @Override
    public String[] getSayOptionsToGetSupportedVoices() {
        return new String[]{"-Command", "\"" + PS_VOICES + "\""};
    }

    @Override
    public String[] getSayOptionsToSayText(String text) {
        // Resolve voice: use set voice, or fall back to first available
        String resolvedVoice = (voice != null && !voice.isBlank()) ? voice
                : (!availableVoices.isEmpty() ? availableVoices.get(0).getName() : "");
        String escapedText = text.replaceAll("'", "''''");
        String code = PS_SAY
                .replace(CODE_TOKEN_TTS_NAME, resolvedVoice)
                .replace(CODE_TOKEN_RATE,     String.valueOf((int) Math.round(rate / 10.0)))
                .replace(CODE_TOKEN_TEXT,     escapedText);
        return new String[]{"-Command", "\"" + code + "\""};
    }

    @Override
    public Voice parse(String csvLine) throws ParseException {
        String[] tokens = csvLine.split(",");
        if (tokens.length != 5) {
            throw new ParseException("Invalid CSV from PowerShell: " + csvLine);
        }
        Voice v = new Voice();
        v.setName(trimQuotes(tokens[0]));
        v.setCulture(trimQuotes(tokens[1]));
        v.setGender(trimQuotes(tokens[2]));
        v.setAge(trimQuotes(tokens[3]));
        v.setDescription(String.format("%s (%s, %s)",
                v.getName(), v.getCulture().replace('-', '_'), v.getGender()));
        return v;
    }

    private static String trimQuotes(String s) {
        return s.replaceAll("^\"|\"$", "");
    }
}
