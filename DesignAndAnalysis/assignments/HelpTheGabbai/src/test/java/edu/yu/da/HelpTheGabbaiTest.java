package edu.yu.da;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class HelpTheGabbaiTest {

    @Test
    void testProportionalDistribution() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {100, 500, 2000, 3000};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        HashMap<String, Integer> actualCounts = new HashMap<>();
        for (String member : members) {
            actualCounts.put(member, 0);
        }

        Iterator<String> iterator = htg.iterator();
        int totalSelections = 0;

        while (iterator.hasNext()) {
            String selectedMember = iterator.next();
            actualCounts.put(selectedMember, actualCounts.get(selectedMember) + 1);
            totalSelections++;
        }

        // Verify total selections
        assertEquals(5600, totalSelections);

        // Verify proportional distribution
        for (int i = 0; i < members.length; i++) {
            double expectedPercentage = amudGrants[i] / 5600.0;
            double actualPercentage = actualCounts.get(members[i]) / (double) totalSelections;
            assertTrue(Math.abs(expectedPercentage - actualPercentage) < 0.01,
                    "Member " + members[i] + " is not within expected proportional range");
        }
    }

    @Test
    void testSmallDataset1() {
        String[] members = {"x", "y"};
        int[] amudGrants = {1, 2};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        HashMap<String, Integer> actualCounts = new HashMap<>();
        for (String member : members) {
            actualCounts.put(member, 0);
        }

        Iterator<String> iterator = htg.iterator();
        int totalSelections = 0;

        while (iterator.hasNext()) {
            String selectedMember = iterator.next();
            actualCounts.put(selectedMember, actualCounts.get(selectedMember) + 1);
            totalSelections++;
        }

        // Verify total selections
        assertEquals(3, totalSelections);

        // Verify counts
        assertEquals(1, actualCounts.get("x"));
        assertEquals(2, actualCounts.get("y"));
    }

    @Test
    void testEdgeCaseSingleMember1() {
        String[] members = {"a"};
        int[] amudGrants = {100};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        HashMap<String, Integer> actualCounts = new HashMap<>();
        actualCounts.put("a", 0);

        Iterator<String> iterator = htg.iterator();
        int totalSelections = 0;

        while (iterator.hasNext()) {
            String selectedMember = iterator.next();
            actualCounts.put(selectedMember, actualCounts.get(selectedMember) + 1);
            totalSelections++;
        }

        // Verify total selections
        assertEquals(100, totalSelections);

        // Verify counts
        assertEquals(100, actualCounts.get("a"));
    }

    @Test
    void testLargeDataset2() {
        int size = 1000;
        String[] members = new String[size];
        int[] amudGrants = new int[size];

        for (int i = 0; i < size; i++) {
            members[i] = "member" + i;
            amudGrants[i] = i + 1;
        }

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        Iterator<String> iterator = htg.iterator();
        int totalSelections = 0;

        while (iterator.hasNext()) {
            iterator.next();
            totalSelections++;
        }

        // Verify total selections
        assertEquals(500500, totalSelections); // Sum of first 1000 natural numbers
    }

    @Test
    void testUnevenDistribution() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {1, 1000, 5000, 9000};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        HashMap<String, Integer> actualCounts = new HashMap<>();
        for (String member : members) {
            actualCounts.put(member, 0);
        }

        Iterator<String> iterator = htg.iterator();
        int totalSelections = 0;

        while (iterator.hasNext()) {
            String selectedMember = iterator.next();
            actualCounts.put(selectedMember, actualCounts.get(selectedMember) + 1);
            totalSelections++;
        }

        // Verify total selections
        assertEquals(15001, totalSelections);

        // Verify proportional distribution
        for (int i = 0; i < members.length; i++) {
            double expectedPercentage = amudGrants[i] / 15001.0;
            double actualPercentage = actualCounts.get(members[i]) / (double) totalSelections;
            assertTrue(Math.abs(expectedPercentage - actualPercentage) < 0.01,
                    "Member " + members[i] + " is not within expected proportional range");
        }
    }

    @Test
    void testStatisticalFairness() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {100, 200, 300, 400};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        // Total iterations (simulate a large number of services)
        int totalSelections = 1_000_000;

        // Track counts for each member
        Map<String, Integer> actualCounts = new HashMap<>();
        for (String member : members) {
            actualCounts.put(member, 0);
        }

        // Use the iterator to simulate selections
        Iterator<String> iterator = htg.iterator();
        for (int i = 0; i < totalSelections; i++) {
            if (!iterator.hasNext()) {
                iterator = htg.iterator(); // Reset the iterator if exhausted
            }
            String member = iterator.next();
            actualCounts.put(member, actualCounts.get(member) + 1);
        }

        // Calculate expected distribution
        int totalGrants = 0;
        for (int grants : amudGrants) {
            totalGrants += grants;
        }

        // Compare actual vs expected percentages
        for (int i = 0; i < members.length; i++) {
            String member = members[i];
            double expectedPercentage = (amudGrants[i] / (double) totalGrants) * 100;
            double actualPercentage = (actualCounts.get(member) / (double) totalSelections) * 100;

            System.out.printf("Member %s: Expected = %.2f%%, Actual = %.2f%%\n",
                    member, expectedPercentage, actualPercentage);

            // Assert that the actual percentage is within 5% of the expected percentage
            assertTrue(Math.abs(expectedPercentage - actualPercentage) <= 5.0,
                    "Member " + member + " is outside the acceptable deviation range.");
        }
    }

    @Test
    void testEdgeCaseSingleMember() {
        String[] members = {"a"};
        int[] amudGrants = {1000};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        int count = 0;
        Iterator<String> iterator = htg.iterator();
        while (iterator.hasNext()) {
            assertEquals("a", iterator.next());
            count++;
        }

        // Verify the total count matches the amud grants
        assertEquals(1000, count);
    }

    @Test
    void testSmallDataset() {
        String[] members = {"x", "y"};
        int[] amudGrants = {1, 2};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        Map<String, Integer> actualCounts = new HashMap<>();
        for (String member : members) {
            actualCounts.put(member, 0);
        }

        Iterator<String> iterator = htg.iterator();
        while (iterator.hasNext()) {
            String member = iterator.next();
            actualCounts.put(member, actualCounts.get(member) + 1);
        }

        // Verify counts match expected values
        assertEquals(1, actualCounts.get("x"));
        assertEquals(2, actualCounts.get("y"));
    }

    @Test
    void testLargeDataset() {
        int size = 1000;
        String[] members = new String[size];
        int[] amudGrants = new int[size];

        for (int i = 0; i < size; i++) {
            members[i] = "member" + i;
            amudGrants[i] = i + 1;
        }

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        int totalGrants = (size * (size + 1)) / 2;
        Iterator<String> iterator = htg.iterator();

        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        // Verify total grants
        assertEquals(totalGrants, count);
    }

    @Test
    void testProportionalityAfterPartialSelections() {
        String[] members = {"a", "b", "c"};
        int[] amudGrants = {100, 200, 300};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        Map<String, Integer> actualCounts = new HashMap<>();
        for (String member : members) {
            actualCounts.put(member, 0);
        }

        Iterator<String> iterator = htg.iterator();
        int totalSelections = 300; // Test distribution after 300 selections
        int count = 0;

        while (count < totalSelections && iterator.hasNext()) {
            String member = iterator.next();
            actualCounts.put(member, actualCounts.get(member) + 1);
            count++;
        }

        // Verify proportionality after 300 selections
        for (int i = 0; i < members.length; i++) {
            double expectedRatio = amudGrants[i] / 600.0;
            double actualRatio = actualCounts.get(members[i]) / (double) totalSelections;
            assertTrue(Math.abs(expectedRatio - actualRatio) < 0.15,
                    "Member " + members[i] + " is outside acceptable proportional range after partial selections.");
        }
    }

    @Test
    void testNoMoreElementsAfterCompletion() {
        String[] members = {"a", "b"};
        int[] amudGrants = {1, 2};

        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);
        Iterator<String> iterator = htg.iterator();

        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        // Verify total grants
        assertEquals(3, count);

        // Verify that no more elements are returned
        assertThrows(NoSuchElementException.class, iterator::next);
    }
}