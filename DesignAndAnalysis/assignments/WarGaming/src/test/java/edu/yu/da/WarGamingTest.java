package edu.yu.da;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class WarGamingTest {

    @Test
    void testGetThreatsToEvaluate_SmallSet() {
        WarGaming game = new WarGaming(Arrays.asList("a", "b", "c"));
        List<List<String>> expectedSubsets = List.of(
                List.of(),         // Empty set
                List.of("a"),
                List.of("a", "b"),
                List.of("a", "b", "c"),
                List.of("a", "c"),
                List.of("b"),
                List.of("b", "c"),
                List.of("c")
        );

        List<List<String>> actualSubsets = game.getThreatsToEvaluate();
        assertEquals(expectedSubsets, actualSubsets, "Subsets do not match expected order.");
    }

    @Test
    void testGetThreatsToEvaluate_SingleElement() {
        WarGaming game = new WarGaming(Arrays.asList("x"));
        List<List<String>> expectedSubsets = List.of(
                List.of(),
                List.of("x")
        );

        List<List<String>> actualSubsets = game.getThreatsToEvaluate();
        assertEquals(expectedSubsets, actualSubsets, "Failed with single-element input.");
    }

    @Test
    void testGetThreatsToEvaluate_EmptyList() {
        WarGaming game = new WarGaming(Arrays.asList());  // No threats
        List<List<String>> expectedSubsets = List.of(List.of()); // Only the empty set

        List<List<String>> actualSubsets = game.getThreatsToEvaluate();
        assertEquals(expectedSubsets, actualSubsets, "Should only return the empty subset.");
    }

    @Test
    void testGetThreatsToEvaluate_LargerSet() {
        WarGaming game = new WarGaming(Arrays.asList("w", "x", "y", "z"));
        List<List<String>> actualSubsets = game.getThreatsToEvaluate();

        int expectedSize = (int) Math.pow(2, 4); // 2^4 = 16 subsets
        assertEquals(expectedSize, actualSubsets.size(), "Incorrect number of subsets generated.");
    }

    @Test
    void testGetThreatsToEvaluate_MuchLargerSet() {
        WarGaming game = new WarGaming(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
                "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y"));
        List<List<String>> actualSubsets = game.getThreatsToEvaluate();

        int expectedSize = (int) Math.pow(2, 25); // 2^4 = 16 subsets
        assertEquals(expectedSize, actualSubsets.size(), "Incorrect number of subsets generated.");
    }

    @Test
    void testGetThreatsToEvaluate_OrderIsCanonical() {
        WarGaming game = new WarGaming(Arrays.asList("b", "a", "c")); // Unordered input
        List<List<String>> actualSubsets = game.getThreatsToEvaluate();

        List<List<String>> expectedSubsets = List.of(
                List.of(),
                List.of("a"),
                List.of("a", "b"),
                List.of("a", "b", "c"),
                List.of("a", "c"),
                List.of("b"),
                List.of("b", "c"),
                List.of("c")
        );

        assertEquals(expectedSubsets, actualSubsets, "Subsets should be sorted in canonical order.");
    }

    @Test
    void testThreatScore() {
        WarGamingBase wgb = new WarGaming(List.of("a", "b", "c"));

        // Create a ThreatScoring instance with the correct number of threat lists
        int totalLists = wgb.getThreatsToEvaluate().size();
        ThreatScoring ts = new ThreatScoring(totalLists);

        // Pass the instance's function to identifyTheUnexpectedThreat
        System.out.println(wgb.identifyTheUnexpectedThreat(ts.DEFAULT_THREAT_SCORE));
    }

    @Test
    void testThreatScore2() {
        WarGamingBase wgb = new WarGaming(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
                "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y"));

        // Create a ThreatScoring instance with the correct number of threat lists
        long startTime1 = System.nanoTime();
        int totalLists = wgb.getThreatsToEvaluate().size();
        long endTime1 = System.nanoTime();
        System.out.println("Execution time: " + (endTime1 - startTime1) / 1_000_000_000.0 + " seconds");

        ThreatScoring ts = new ThreatScoring(totalLists);

        System.out.println("Got all threats");
        // Pass the instance's function to identifyTheUnexpectedThreat
        long startTime = System.nanoTime();
        System.out.println(wgb.identifyTheUnexpectedThreat(ts.DEFAULT_THREAT_SCORE));
        long endTime = System.nanoTime();
        System.out.println("Execution time: " + (endTime - startTime) / 1_000_000_000.0 + " seconds");
    }


    public class ThreatScoring {
        private int numberOfLists;
        private int timesCalled = 0;

        public ThreatScoring(int numberOfLists) {
            this.numberOfLists = numberOfLists;
        }

        /**
         * Function that determines the magnitude of a threat.
         * By default, it returns the number of elements in the threat vector.
         */
        public final Function<List<String>, Integer> DEFAULT_THREAT_SCORE = threat -> {
            timesCalled++;  // Track how many times this function is called

            if (threat == null) {
                return 0; // Null threat has no danger
            }
            // Return a large score for the 2nd call
            if (timesCalled == 2) {
                return 25;
            }
            // Return a large score for the middle list
            if (timesCalled == (numberOfLists / 2)) {
                return 50;
            }
            return threat.size(); // Default score is the size of the threat list
        };
    }
}