/*
 * MIT License
 * Copyright (c) 2021 Johann N. Löfflmann
 * Forked into jace.hardware.tts for self-contained distribution.
 */
package jace.hardware.tts;

public class VoicePreferences {

    public enum Gender { FEMALE, MALE }
    public enum Age    { CHILD, ADULT }

    private String language;
    private String country;
    private Gender gender;
    private Age    age;

    public String getLanguage()        { return language; }
    public void   setLanguage(String v){ this.language = v; }

    public String getCountry()        { return country; }
    public void   setCountry(String v){ this.country = v; }

    public Gender getGender()        { return gender; }
    public void   setGender(Gender v){ this.gender = v; }

    public Age getAge()        { return age; }
    public void setAge(Age v)  { this.age = v; }
}
