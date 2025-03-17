package edu.yu.da;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

public class MusicalChairsTest {

    @Test
    public void testBasicSeating() {
        // Test the basic example: 5 people, 3 chairs.
        // Expected behavior (using chairs 1,2,3):
        //   Person 0: sits in chair1 (1)
        //   Person 1: sits in chair2 (or chair3, depending on availability)
        //   Person 2: sits, and then no more moves are possible so
        //   Persons 3 and 4 should fail.
        MusicalChairs mc = new MusicalChairs(5, 3);
        assertTrue(mc.tryToSit(0, 1, 2), "Person 0 should sit successfully.");
        assertTrue(mc.tryToSit(1, 1, 3), "Person 1 should sit successfully.");
        assertTrue(mc.tryToSit(2, 1, 2), "Person 2 should sit successfully.");
        assertFalse(mc.tryToSit(3, 1, 3), "Person 3 should fail to sit.");
        assertFalse(mc.tryToSit(4, 1, 2), "Person 4 should fail to sit.");
    }

    @Test
    public void testEdgeCasesInvalidChairs() {
        MusicalChairs mc = new MusicalChairs(3, 3);
        // Test: chair number 0 is invalid.
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            mc.tryToSit(0, 0, 2);
        });
        assertNotNull(ex, "Using chair 0 should throw an exception.");

        // Test: chair number out of bounds (greater than nChairs).
        ex = assertThrows(IllegalArgumentException.class, () -> {
            mc.tryToSit(1, 1, 4);
        });
        assertNotNull(ex, "Using a chair number out of bounds should throw an exception.");
    }

    @Test
    public void testDuplicatePerson() {
        MusicalChairs mc = new MusicalChairs(3, 3);
        assertTrue(mc.tryToSit(0, 1, 2), "Person 0 should sit successfully.");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            mc.tryToSit(0, 2, 3);
        });
        assertNotNull(ex, "A duplicate person id should throw an exception.");
    }

    @Test
    public void testWorstCaseChain() {
        // Worst-case scenario: many people competing for very few chairs.
        // With 10 people and 2 chairs, only 2 people can ever be seated.
        MusicalChairs mc = new MusicalChairs(10, 2);
        int seatedCount = 0;
        for (int i = 0; i < 10; i++) {
            // All persons attempt to sit in chairs 1 and 2.
            if (mc.tryToSit(i, 1, 2)) {
                seatedCount++;
            }
        }
        assertEquals(2, seatedCount, "Only 2 people should be able to sit when there are 2 chairs.");
    }

    @Test
    public void testLargeDataSetPerformance() {
        // Test performance on a large dataset.
        int nPeople = 10000;
        int nChairs = 15000;
        MusicalChairs mc = new MusicalChairs(nPeople, nChairs);
        Random rand = new Random(42);
        int seatedCount = 0;
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < nPeople; i++) {
            int chair1 = rand.nextInt(nChairs) + 1;  // valid chairs: 1 to nChairs
            int chair2;
            do {
                chair2 = rand.nextInt(nChairs) + 1;
            } while (chair2 == chair1);
            if (mc.tryToSit(i, chair1, chair2)) {
                seatedCount++;
            }
        }
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Large dataset test duration: " + duration + "ms");
        // Since there are more chairs than people, expect everyone to be seated.
        assertEquals(nPeople, seatedCount, "All people should be seated when there are plenty of chairs.");
        // Check that the algorithm runs in a reasonable time (e.g., under 2000ms).
        assertTrue(duration < 2000, "Large dataset test should run quickly.");
    }

    @Test
    public void testUnionFindDirectly() throws Exception {
        // Use reflection to directly test the inner UnionFind class.
        MusicalChairs mc = new MusicalChairs(5, 5);
        Field ufField = MusicalChairs.class.getDeclaredField("unionFind");
        ufField.setAccessible(true);
        Object ufObject = ufField.get(mc);

        Class<?> ufClass = ufObject.getClass();
        // Test find(): initially, find(chair) should return the chair itself.
        Method findMethod = ufClass.getDeclaredMethod("find", int.class);
        findMethod.setAccessible(true);
        int result = (int) findMethod.invoke(ufObject, 3);
        assertEquals(3, result, "Initially, find(3) should return 3.");

        // Test union(): after union(3, 4), find(3) and find(4) should be equal.
        Method unionMethod = ufClass.getDeclaredMethod("union", int.class, int.class);
        unionMethod.setAccessible(true);
        unionMethod.invoke(ufObject, 3, 4);
        int root3 = (int) findMethod.invoke(ufObject, 3);
        int root4 = (int) findMethod.invoke(ufObject, 4);
        assertEquals(root3, root4, "After union(3,4), roots should match.");

        // Test occupy(): after occupying chair 3, isOccupied(3) should be true.
        Method occupyMethod = ufClass.getDeclaredMethod("occupy", int.class);
        occupyMethod.setAccessible(true);
        occupyMethod.invoke(ufObject, 3);

        Method isOccupiedMethod = ufClass.getDeclaredMethod("isOccupied", int.class);
        isOccupiedMethod.setAccessible(true);
        boolean occupied = (boolean) isOccupiedMethod.invoke(ufObject, 3);
        assertTrue(occupied, "Chair 3 should be marked as occupied.");
    }
}