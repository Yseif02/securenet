package edu.yu.da;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        //t1();
        //t3();
        //t4();
        //t5();
        t6();
        t7();
    }

    public static void t1() {
        long startTime = System.currentTimeMillis();

        final String[] members = {"a", "b", "c", "d"};
        final int[] amudGrants = {100, 590, 2000, 1000};
        final HelpTheGabbaiBase htg = new HelpTheGabbai(members, amudGrants);
        final Iterator<String> iterator = htg.iterator();
        int nAwarded = 0;
        while (iterator.hasNext()) {
            final String next = iterator.next();
            System.out.println(next);
            System.out.println(nAwarded++);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("Test duration: " + duration + " ms");
    }

    public static void t2() {
        for (int size = 1000; size <= 10000000; size *= 10) {
            long startTime = System.currentTimeMillis();

            final String[] members = new String[size];
            final int[] amudGrants = new int[size];
            for (int i = 0; i < size; i++) {
                members[i] = "member" + i;
                amudGrants[i] = 1;
            }
            final HelpTheGabbaiBase htg = new HelpTheGabbai(members, amudGrants);
            final Iterator<String> iterator = htg.iterator();
            while (iterator.hasNext()) {
                iterator.next();
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.println("Test size: " + size + " duration: " + duration + " ms");
        }
    }

    public static void t3() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {100, 500, 2000, 3000};

        // Create an instance of HelpTheGabbai
        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        // Use the iterator to count the actual distribution
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

        // Calculate the total number of amud grants
        int totalAmudGrants = 0;
        for (int grant : amudGrants) {
            totalAmudGrants += grant;
        }

        // Calculate and print the expected distribution
        System.out.println("Expected Distribution:");
        for (int i = 0; i < members.length; i++) {
            double expectedPercentage = (amudGrants[i] / (double) totalAmudGrants) * 100;
            System.out.printf("Member %s: %.2f%%\n", members[i], expectedPercentage);
        }

        // Calculate and print the actual distribution
        System.out.println("\nActual Distribution:");
        for (String member : members) {
            int actualCount = actualCounts.get(member);
            double actualPercentage = (actualCount / (double) totalSelections) * 100;
            System.out.printf("Member %s: %.2f%%\n", member, actualPercentage);
        }
        calculateDeviation(members, amudGrants, actualCounts, totalSelections);
    }

    public static void t4() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {1, 1000, 5000, 9000}; // Uneven distribution

        // Create an instance of HelpTheGabbai
        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        // Use the iterator to count the actual distribution
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

        // Calculate the total number of amud grants
        int totalAmudGrants = 0;
        for (int grant : amudGrants) {
            totalAmudGrants += grant;
        }

        // Calculate and print the expected distribution
        System.out.println("Expected Distribution:");
        for (int i = 0; i < members.length; i++) {
            double expectedPercentage = (amudGrants[i] / (double) totalAmudGrants) * 100;
            System.out.printf("Member %s: %.2f%%\n", members[i], expectedPercentage);
        }

        // Calculate and print the actual distribution
        System.out.println("\nActual Distribution:");
        for (String member : members) {
            int actualCount = actualCounts.get(member);
            double actualPercentage = (actualCount / (double) totalSelections) * 100;
            System.out.printf("Member %s: %.2f%%\n", member, actualPercentage);
        }
        calculateDeviation(members, amudGrants, actualCounts, totalSelections);
    }

    public static void calculateDeviation(String[] members, int[] amudGrants, HashMap<String, Integer> actualCounts, int totalSelections) {
        // Calculate the total number of amud grants
        int totalAmudGrants = 0;
        for (int grant : amudGrants) {
            totalAmudGrants += grant;
        }

        System.out.println("\nDeviation from Expected Distribution:");
        double totalDeviation = 0;

        for (int i = 0; i < members.length; i++) {
            // Expected count for this member
            double expectedCount = (amudGrants[i] / (double) totalAmudGrants) * totalSelections;

            // Actual count for this member
            int actualCount = actualCounts.get(members[i]);

            // Calculate deviation
            double deviation = Math.abs((actualCount - expectedCount) / expectedCount) * 100;

            // Print deviation
            System.out.printf("Member %s: Deviation = %.2f%%\n", members[i], deviation);

            // Add to total deviation
            totalDeviation += deviation;
        }

        // Print average deviation
        double averageDeviation = totalDeviation / members.length;
        System.out.printf("\nAverage Deviation: %.2f%%\n", averageDeviation);
    }

    public static void t5() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {100, 200, 300, 400};

        // Create an instance of HelpTheGabbai
        HelpTheGabbai htg = new HelpTheGabbai(members, amudGrants);

        // Use the iterator to count the actual distribution
        HashMap<String, Integer> actualCounts = new HashMap<>();
        for (String member : members) {
            actualCounts.put(member, 0);
        }

        Iterator<String> iterator = htg.iterator();
        int totalSelections = 0;

        // Calculate checkpoints for 25%, 50%, and 75%
        int totalAmudGrants = 0;
        for (int grant : amudGrants) {
            totalAmudGrants += grant;
        }
        int checkpoint25 = totalAmudGrants / 4;
        int checkpoint50 = totalAmudGrants / 2;
        int checkpoint75 = (3 * totalAmudGrants) / 4;

        // Iterate and track the distribution at checkpoints
        while (iterator.hasNext()) {
            String selectedMember = iterator.next();
            actualCounts.put(selectedMember, actualCounts.get(selectedMember) + 1);
            totalSelections++;

            // Check distribution at checkpoints
            if (totalSelections == checkpoint25 || totalSelections == checkpoint50 || totalSelections == checkpoint75) {
                System.out.printf("\nDistribution after %d%%:\n", (totalSelections * 100) / totalAmudGrants);
                printDistribution(members, amudGrants, actualCounts, totalSelections, totalAmudGrants);
            }
        }

        // Final distribution
        System.out.println("\nFinal Distribution:");
        printDistribution(members, amudGrants, actualCounts, totalSelections, totalAmudGrants);
    }

    public static void printDistribution(String[] members, int[] amudGrants, HashMap<String, Integer> actualCounts, int totalSelections, int totalAmudGrants) {
        // Print expected distribution
        System.out.println("Expected Distribution:");
        for (int i = 0; i < members.length; i++) {
            double expectedPercentage = (amudGrants[i] / (double) totalAmudGrants) * 100;
            System.out.printf("Member %s: %.2f%%\n", members[i], expectedPercentage);
        }

        // Print actual distribution
        System.out.println("\nActual Distribution:");
        for (String member : members) {
            int actualCount = actualCounts.get(member);
            double actualPercentage = (actualCount / (double) totalSelections) * 100;
            System.out.printf("Member %s: %.2f%%\n", member, actualPercentage);
        }

        // Calculate deviation
        calculateDeviation(members, amudGrants, actualCounts, totalSelections);
    }

    public static void t6() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {10, 20, 30, 40};
        HelpTheGabbaiBase htg = new HelpTheGabbai(members, amudGrants);
        Iterator<String> iterator1 = htg.iterator();
        Iterator<String> iterator2 = htg.iterator();
        HashMap<String, Integer> hashMap1 = new HashMap<>();
        HashMap<String, Integer> hashMap2 = new HashMap<>();
        iterator1.forEachRemaining(s ->  hashMap1.put(s, hashMap1.getOrDefault(s, 0) + 1));
        while (iterator2.hasNext()) {
            String winner = iterator2.next();
            hashMap2.put(winner, hashMap2.getOrDefault(winner, 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : hashMap1.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println();
        System.out.println("-------------");
        System.out.println();
        for (Map.Entry<String, Integer> entry : hashMap2.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println();
        System.out.println("--------");
        System.out.println();
    }

    public static void t7() {
        String[] members = {"a", "b", "c", "d"};
        int[] amudGrants = {100, 20000, 30, 40000};
        HelpTheGabbaiBase htg = new HelpTheGabbai(members, amudGrants);
        HashMap<String, Integer> hashMap1 = new HashMap<>();
        HashMap<String, Integer> hashMap2 = new HashMap<>();

        int tries = 0;
        Iterator<String> iterator1 = htg.iterator();
        Iterator<String> iterator2 = htg.iterator();
        do {
            hashMap1.clear();
            hashMap2.clear();
            iterator1 = htg.iterator();
            iterator2 = htg.iterator();

            iterator1.forEachRemaining(s -> hashMap1.put(s, hashMap1.getOrDefault(s, 0) + 1));
            while (iterator2.hasNext()) {
                String winner = iterator2.next();
                hashMap2.put(winner, hashMap2.getOrDefault(winner, 0) + 1);
            }
            System.out.println(tries++);
        } while (!hashMap1.equals(hashMap2));

        for (Map.Entry<String, Integer> entry : hashMap1.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println();
        System.out.println("-------------");
        System.out.println();
        for (Map.Entry<String, Integer> entry : hashMap2.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println();
        System.out.println(tries);
    }
}
