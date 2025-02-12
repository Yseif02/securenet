package edu.yu.da;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class MaxTheKiddushTest {

    @Test
    void testBasicCase() {
        int[] bookings = {2, 3, 1, 4, 2};
        int maxCapacity = 6;
        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(2, kiddush.maxIt());  // Expected max members that can be booked
    }

    @Test
    void testMaxCapacityReached() {
        int[] bookings = {1, 2, 3, 4};
        int maxCapacity = 10;
        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(4, kiddush.maxIt());  // All members fit within capacity
    }

    @Test
    void testNoValidGroups() {
        int[] bookings = {5, 6, 7, 8};
        int maxCapacity = 10;
        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(1, kiddush.maxIt());  // Only single members fit
    }

    @Test
    void testSingleMemberOnly() {
        int[] bookings = {7, 1, 8, 2};
        int maxCapacity = 8;
        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(1, kiddush.maxIt());  // Only individual members can be booked
    }

    @Test
    void testAllMembersFit() {
        int[] bookings = {1, 1, 1, 1, 1};
        int maxCapacity = 5;
        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(5, kiddush.maxIt());  // Whole array fits
    }

    @Test
    void testEdgeCaseEmptyArray() {
        int[] bookings = {};
        int maxCapacity = 5;
        assertThrows(IllegalArgumentException.class, () -> new MaxTheKiddush(bookings, maxCapacity));
    }

    @Test
    void testEdgeCaseSingleBooking() {
        int[] bookings = {4};
        int maxCapacity = 4;
        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(1, kiddush.maxIt());
    }

    @Test
    void testEdgeCaseMinimumCapacity() {
        int[] bookings = {2, 2, 2, 2};
        int maxCapacity = 2;
        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(1, kiddush.maxIt());  // Only single members fit
    }

    @Test
    void testLargeDataSetRandomValues() {
        int n = 100000;
        int maxCapacity = 50000;
        int[] bookings = new int[n];


        Random random = new Random(42); // Fixed seed for reproducibility
        for (int i = 0; i < n; i++) {
            bookings[i] = random.nextInt(500) + 1; // Random bookings (1-500)
        }

        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);

        long startTime = System.nanoTime();
        int result = kiddush.maxIt();
        long endTime = System.nanoTime();

        System.out.println("Large Random Dataset: " + result + " members booked.");
        System.out.println("Execution time (ms): " + (endTime - startTime) / 1_000_000.0);

        assertTrue(result > 0); // Ensuring the function returns a valid number
    }

    @Test
    void testLargeUniformDataSet1() {
        int n = 100000;
        int maxCapacity = 100000;
        int[] bookings = new int[n];

        for (int i = 0; i < n; i++) {
            bookings[i] = 1; // Uniform values (each booking = 1)
        }

        MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);

        long startTime = System.nanoTime();
        int result = kiddush.maxIt();
        long endTime = System.nanoTime();

        System.out.println("Large Uniform Dataset: " + result + " members booked.");
        System.out.println("Execution time (ms): " + (endTime - startTime) / 1_000_000.0);

        assertEquals(n, result); // All members should fit
    }

    @Test
    void testLargeUniformDataSet2() {
        int baseSize = 100000;

        for (int iteration = 0; iteration < 4; iteration++) {
            int n = baseSize * (1 << iteration); // Doubles n each iteration
            int maxCapacity = n; // Increase maxCapacity along with n
            int[] bookings = new int[n];

            for (int i = 0; i < n; i++) {
                bookings[i] = 1; // Uniform values (each booking = 1)
            }

            MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);

            long startTime = System.nanoTime();
            int result = kiddush.maxIt();
            long endTime = System.nanoTime();

            double executionTimeMs = (endTime - startTime) / 1_000_000.0;

            System.out.println("Large Uniform Dataset | n = " + n);
            System.out.println("Execution time (ms): " + executionTimeMs);
            System.out.println("Members booked: " + result);

            assertEquals(n, result); // Ensure all members fit
        }
    }

    @Test
    void testUniform3() {
        int size = 200000;
        int maxCapacity = 8000;
        int[] bookings = new int[size];
        int tablesNeeded = 1;
        Arrays.fill(bookings, tablesNeeded);
        MaxTheKiddushBase mtk = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(maxCapacity/tablesNeeded, mtk.maxIt());
    }

    @Test
    void testUniform4() {
        int size = 200000;
        int maxCapacity = 8000;
        int[] bookings = new int[size];
        int tablesNeeded = 2;
        Arrays.fill(bookings, tablesNeeded);
        MaxTheKiddushBase mtk = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(maxCapacity/tablesNeeded, mtk.maxIt());
    }

    @Test
    void testWorstCaseScenario() {
        int n = 100000;
        int maxCapacity = 10;

        for (int j = 1; j <= 8; j++) { // Run for increasing sizes
            n *= 2; // Double n each iteration
            int[] bookings = new int[n];

            for (int i = 0; i < n; i++) {
                bookings[i] = 11; // Every booking exceeds maxCapacity
            }

            MaxTheKiddush kiddush = new MaxTheKiddush(bookings, maxCapacity);

            long startTime = System.nanoTime();
            int result = kiddush.maxIt();
            long endTime = System.nanoTime();

            double executionTimeMs = (endTime - startTime) / 1_000_000.0;

            System.out.println("Worst Case Scenario | n = " + n);
            System.out.println("Execution time (ms): " + executionTimeMs);
            System.out.println("Result: " + result);

            assertEquals(0, result); // Expected output should be 0
        }
    }

    @Test
    void testMaxCapacityLessThanOne() {
        int[] bookings = {1, 2, 3};
        int maxCapacity = 0;
        assertThrows(IllegalArgumentException.class, () -> new MaxTheKiddush(bookings, maxCapacity));
    }

    @Test
    void testBookingsNull() {
        int[] bookings = null;
        int maxCapacity = 5;
        assertThrows(IllegalArgumentException.class, () -> new MaxTheKiddush(bookings, maxCapacity));
    }

    @Test
    void testNegativeValueInBookings() {
        int[] bookings = {1, -2, 3};
        int maxCapacity = 5;
        assertThrows(IllegalArgumentException.class, () -> new MaxTheKiddush(bookings, maxCapacity));
    }

    @Test
    void testMemberGreaterThanMaxCapacity() {
        int maxCapacity = 15;
        int[] bookings = new int[maxCapacity];

        for (int i = 0; i < maxCapacity; i++) {
            bookings[i] = i + 2;
        }

        // Correct way to shuffle
        Random random = new Random();
        for (int i = bookings.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = bookings[i];
            bookings[i] = bookings[j];
            bookings[j] = temp;

        }

        MaxTheKiddushBase mtk = new MaxTheKiddush(bookings, maxCapacity);
        assertEquals(0, mtk.maxIt());
    }
}