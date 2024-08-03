package jace.core;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;

public class UtilityTest {

    @Test
    public void testLevenshteinDistance() {
        String s1 = "kitten";
        String s2 = "sitting";
        int distance = Utility.levenshteinDistance(s1, s2);
        assertEquals(3, distance);
    }

    @Test
    public void testAdjustedLevenshteinDistance() {
        String s1 = "kitten";
        String s2 = "sitting";
        int adjustedDistance = Utility.adjustedLevenshteinDistance(s1, s2);
        assertEquals(4, adjustedDistance);
    }

    @Test
    public void testRankMatch() {
        String s1 = "apple";
        String s2 = "banana";
        double score = Utility.rankMatch(s1, s2, 3);
        assertEquals(0, score, 0.001);
    }

    @Test
    public void testFindBestMatch() {
        String match = "apple";
        Collection<String> search = Arrays.asList("banana", "orange", "apple pie");
        String bestMatch = Utility.findBestMatch(match, search);
        assertEquals("apple pie", bestMatch);
    }
}