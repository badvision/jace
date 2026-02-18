/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpeechEngineMacOS extends SpeechEngineAbstract {

    // Gender lookup tables sourced from the original jAdapterForNativeTTS project.
    private static final String MALE_NAMES =
            "Albert,Eddy,Grandpa,Jester,Jacques,Majed,Reed,Rishi,Rocko,Sinji," +
            "Alex,Bruce,Carlos,Cem,Daniel,Diego,Felipe,Fred,Henrik,Jorge,Juan,Junior,Juri,Lee,Luca,Maged,Magnus,Markus,Neel,Nicolas,Nicos,Oliver,Oskar,Otoya,Ralph,Tarik,Thomas,Tom,Xander,Yannick,Yuri,";

    private static final String FEMALE_NAMES =
            "Amélie,Amira,Daria,Grandma,Lana,Lesya,Linh,Tünde,Meijia,Mónica,Montse,Sandy,Shelley,Tingting," +
            "Alva,Agnes,Alice,Allison,Andrea,Angelica,Anna,Amelie,Aurelie,Ava,Catarina,Carmit,Chantal,Claire,Damayanti,Ellen,Ewa,Fiona,Frederica,Ioana,Iveta,Joana,Kanya,Karen,Kate,Kathy,Katja,Klara,Kyoko,Laila,Laura,Lekha,Luciana,Mariska,Milena,Mei-Jia,Melina,Moira,Monica,Nora,Paola,Paulina,Petra,Princess,Samantha,Sara,Satu,Serena,Sin-ji,Soledad,Susan,Tessa,Ting-Ting,Veena,Vicki,Victoria,Yelda,Yuna,Zosia,Zuzana,";

    public SpeechEngineMacOS() throws SpeechEngineCreationException {
        super();
    }

    @Override
    public String getSayExecutable() { return "say"; }

    @Override
    public String[] getSayOptionsToGetSupportedVoices() { return new String[]{"-v", "?"}; }

    @Override
    public String[] getSayOptionsToSayText(String text) {
        String ratePrefix = formatRate();
        if (voice != null && !voice.isBlank()) {
            return new String[]{"-v", voice, ratePrefix + text};
        }
        // No explicit voice – let macOS use its default
        if (!ratePrefix.isEmpty()) {
            return new String[]{ratePrefix + text};
        }
        return new String[]{text};
    }

    private String formatRate() {
        if (rate == 0) return "";
        // Map [-100..100] → [50..310], midpoint 180
        return String.format("[[rate %d]]", (int) Math.round(rate * 1.3 + 180));
    }

    @Override
    public Voice parse(String line) throws ParseException {
        Pattern pattern = Pattern.compile("^(.+?)\\s+([^ ]+)\\s+#.*$");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find() && matcher.groupCount() == 2) {
            String name    = matcher.group(1);
            String culture = matcher.group(2);
            Voice v = new Voice();
            v.setName(name);
            v.setCulture(culture);
            String gender = genderFor(stripBrackets(name));
            v.setGender(gender);
            v.setAge("?");
            v.setDescription(String.format("%s (%s, %s)", name, culture, gender));
            return v;
        }
        throw new ParseException("Unexpected line from say: " + line);
    }

    private static String stripBrackets(String s) {
        Matcher m = Pattern.compile("^([^(]+)\\s+\\(.*\\).*$").matcher(s);
        return m.find() ? m.group(1) : s;
    }

    private static String genderFor(String name) {
        if (FEMALE_NAMES.contains(name + ",")) return "female";
        if (MALE_NAMES.contains(name + ","))   return "male";
        return "?";
    }
}
