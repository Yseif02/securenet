package edu.yu.introtoalgs;

import com.sun.jdi.Value;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LFUTest {

    @Test
    void setNull() {
        LFU<String, String> lfu = new LFU<>(5);
        assertThrows(IllegalArgumentException.class, () -> lfu.set(null, "One"));
        assertThrows(IllegalArgumentException.class, () -> lfu.set("One", null));
    }

    @Test
    void set() {
        LFU<String, String> lfu = new LFU<>(5);
        assertTrue(lfu.set("One", "One"));
        assertTrue(lfu.set("Two", "Two"));
        assertTrue(lfu.set("Three", "Three"));
        assertTrue(lfu.set("Four", "Four"));
        assertTrue(lfu.set("Five", "Five"));
        assertFalse(lfu.set("One", "One1"));
        assertFalse(lfu.set("Two", "Two2"));
        assertFalse(lfu.set("Three", "Three3"));
        assertFalse(lfu.set("Four", "Four4"));
        assertFalse(lfu.set("Five", "Five5"));
        assertTrue(lfu.set("Six", "Six"));
    }

    @Test
    void getNullKey() {
        LFU<String, String> lfu = new LFU<>(5);
        assertThrows(IllegalArgumentException.class, () -> lfu.get(null));
    }

    @Test
    void get() {
        LFU<String, String> lfu = new LFU<>(5);
        lfu.set("One", "One");
        lfu.set("Two", "Two");
        lfu.set("Three", "Three");
        lfu.set("Four", "Four");
        lfu.set("Five", "Five");
        assertEquals("One", lfu.get("One").get());
        assertEquals("One", lfu.get("One").get());
        assertEquals("Two", lfu.get("Two").get());
        assertEquals("Two", lfu.get("Two").get());
        assertEquals("Three", lfu.get("Three").get());
        assertEquals("Four", lfu.get("Four").get());
        assertEquals("Five", lfu.get("Five").get());
        lfu.set("Six", "Six");
        // when a value would return null, instead it returns an empty Optional
        // ned to test if the optional is empty
        assertEquals(Optional.empty(), lfu.get("Three"));
    }

    @Test
    void testGetMillionValues() {
        LFU<String, String> lfu = new LFU<>(1000000);
        for (int i = 0; i < 1000000; i++) {
            lfu.set(String.valueOf(i), String.valueOf(i));
        }
        assertEquals("999999", lfu.get("999999").get());
    }

    @Test
    void kickOutWith1Mil(){
        Random random = new Random();
        LFU<String, String> lfu = new LFU<>(1000000);
        for (int i = 0; i < 1000000; i++) {
            lfu.set(String.valueOf(i), String.valueOf(i));
        }

        //get a random value from 0 to 999999
        int randomValue = random.nextInt(1000000);

        for (int i = 0; i < 1000000; i++) {
            if (i != randomValue) {
                lfu.get(String.valueOf(i));
            }
        }

        lfu.set("1000000", "1000000");
        assertEquals(Optional.empty(), lfu.get(String.valueOf(randomValue)));
    }

    @Test
    void timeTestFor1KTo1Mil(){
        LFU<String, String> lfu1 = new LFU<>(1000);
        for (int i = 0; i < 1000; i++) {
            lfu1.set(String.valueOf(i), String.valueOf(i));
        }

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            lfu1.set(String.valueOf(i), String.valueOf(i));
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Time to add 1 million values: " + (endTime - startTime) + " milliseconds");
    }


    @Test
    void randomOperationsOnRandomKeys(){
        LFU<String, String> lfu = new LFU<>(2500);
        Random random = new Random();
        int setOperations = random.nextInt(10000);
        for (int i = 0; i < setOperations; i++) {
            int data = random.nextInt(5000);
            lfu.set(String.valueOf(data), String.valueOf(data));
        }

        int getOperations = random.nextInt(10000);
        for (int i = 0; i < getOperations; i++) {
            int data = random.nextInt(5000);
            Optional<String> value = lfu.get(String.valueOf(data));
            if(value.isPresent()) {
                System.out.println((value.get()));
                continue;
            }
            if (lfu.cacheMap.containsKey(String.valueOf(data))){
                throw new IllegalStateException();
            }
            System.out.println(Optional.empty());
        }

    }

    @Test
    void testGetWith1Mil(){
        LFU<String, String> lfu = new LFU<>(100000000);
        for (int i = 0; i < 100000000; i++) {
            System.out.println(i);
            lfu.set(String.valueOf(i), String.valueOf(i));
        }
        assertEquals("999999", lfu.get("999999").get());
    }

    @Test
    void size() {
    }

    @Test
    void isEmpty() {
    }

    @Test
    void clear() {
    }
}