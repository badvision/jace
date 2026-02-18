/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

import java.util.Locale;

public class Voice {
    private String name;
    private String culture;
    private String gender;
    private String age;
    private String description;

    public String getName()        { return name; }
    public void   setName(String v){ this.name = v; }

    public String getCulture()        { return culture; }
    public void   setCulture(String v){ this.culture = v; }

    public String getGender()        { return gender; }
    public void   setGender(String v){ this.gender = v; }

    public String getAge()        { return age; }
    public void   setAge(String v){ this.age = v; }

    public String getDescription()        { return description; }
    public void   setDescription(String v){ this.description = v; }

    @Override
    public String toString() {
        return String.format("name='%s', culture='%s', gender='%s', age='%s', description='%s'",
                name, culture, gender, age, description);
    }

    public boolean matches(VoicePreferences prefs) {
        VoicePreferences mine = toVoicePreferences();
        if (prefs.getLanguage() != null && mine.getLanguage() != null
                && !prefs.getLanguage().equalsIgnoreCase(mine.getLanguage())) return false;
        if (prefs.getCountry() != null && mine.getCountry() != null
                && !prefs.getCountry().equalsIgnoreCase(mine.getCountry())) return false;
        if (prefs.getGender() != null && mine.getGender() != null
                && !prefs.getGender().equals(mine.getGender())) return false;
        if (prefs.getAge() != null && mine.getAge() != null
                && !prefs.getAge().equals(mine.getAge())) return false;
        return true;
    }

    private VoicePreferences toVoicePreferences() {
        VoicePreferences vp = new VoicePreferences();
        if (culture != null) {
            String[] tokens = culture.toLowerCase(Locale.US).replaceAll("_", "-").split("-");
            if (tokens.length > 0) vp.setLanguage(tokens[0]);
            if (tokens.length > 1) vp.setCountry(tokens[1].toUpperCase(Locale.US));
        }
        if (gender != null) switch (gender.toLowerCase(Locale.US)) {
            case "male"   -> vp.setGender(VoicePreferences.Gender.MALE);
            case "female" -> vp.setGender(VoicePreferences.Gender.FEMALE);
        }
        if (age != null) switch (age.toLowerCase(Locale.US)) {
            case "adult" -> vp.setAge(VoicePreferences.Age.ADULT);
            case "child" -> vp.setAge(VoicePreferences.Age.CHILD);
        }
        return vp;
    }
}
