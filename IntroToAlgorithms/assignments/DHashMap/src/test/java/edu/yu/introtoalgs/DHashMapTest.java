package edu.yu.introtoalgs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DHashMapTest {
    final int perServerMaxCapacity = 1_000_000;
    DHashMapBase<Integer , Integer> dhm;

    @BeforeEach
    void setUp() {
        this.dhm = new DHashMap<>(perServerMaxCapacity);
    }

    @Test
    void getPerServerMaxCapacity() {
        assertEquals(perServerMaxCapacity, this.dhm.getPerServerMaxCapacity());
    }

    @Test
    void addServerNewId() {
        dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));

    }

    @Test
    void addServerExistingId() {
        dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        assertThrows(IllegalArgumentException.class, () -> dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity)));
    }

    @Test
    void addServerExistingIdOfBackupServer() {
        dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(2, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(3, new SizedHashMap<>(perServerMaxCapacity));
        assertThrows(IllegalArgumentException.class, () -> dhm.addServer(3, new SizedHashMap<>(perServerMaxCapacity)));
    }

    @Test
    void addFallbackRemoveFallbackAddNewFallback(){
        dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(2, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(3, new SizedHashMap<>(perServerMaxCapacity));

        dhm.removeServer(3);
        dhm.addServer(4, new SizedHashMap<>(perServerMaxCapacity));
    }

    @Test
    void removeServer() {
    }

    @Test
    void put() {
    }

    @Test
    void get() {
    }

    @Test
    void remove() {
        dhm.addServer(1, new SizedHashMap<>(5000));
        dhm.addServer(2, new SizedHashMap<>(5000));
        dhm.addServer(3, new SizedHashMap<>(5000));
        dhm.addServer(4, new SizedHashMap<>(5000));
        dhm.addServer(5, new SizedHashMap<>(5000));

        dhm.put(5, 5);
        assertEquals(5, dhm.remove(5));

        Random random = new Random();
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            int num = Math.abs(random.nextInt());
            dhm.put(num, num);
            keys.add(num);
        }
        int numToDelete = keys.get(random.nextInt(keys.size()));
        assertEquals(numToDelete, dhm.remove(numToDelete));

    }

    public static class StopWatch{
        private long start;

        public StopWatch(){
            start = 0;
        }
        public void start(){
            start = System.currentTimeMillis();
        }
        public double elapsedTime(){
            long now = System.currentTimeMillis();
            return (double) (now - start) / 10;
        }
    }

    @Test
    void OneKEntries(){
        //dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(5, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(10, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(15, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(20, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(25, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(30, new SizedHashMap<>(perServerMaxCapacity));

        Random random = new Random();
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            int num = random.nextInt();
            dhm.put(num, num);
            keys.add(num);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        int size = keys.size();
        int numToSearch = keys.get(random.nextInt(size));
        System.out.println(numToSearch);
        assertEquals(numToSearch, dhm.get(numToSearch));
        System.out.println(dhm.get(numToSearch));
        System.out.println(stopWatch.elapsedTime());

    }

    @Test
    void OneHunKEntries(){
        //dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(5, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(10, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(15, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(20, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(25, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(30, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(35, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(40, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(45, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(50, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(55, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(60, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(65, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(70, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(75, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(80, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(85, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(90, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(95, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(100, new SizedHashMap<>(perServerMaxCapacity));
        for (int i = 21; i < 100; i++) {
            dhm.addServer(i * 5, new SizedHashMap<>(perServerMaxCapacity));
        }

        Random random = new Random();
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            int num = random.nextInt();
            System.out.println(num);
            dhm.put(num, num);
            keys.add(num);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        int size = keys.size();
        int numToSearch = keys.get(random.nextInt(size));
        System.out.println(numToSearch);
        assertEquals(numToSearch, dhm.get(numToSearch));
        System.out.println(dhm.get(numToSearch));
        System.out.println(stopWatch.elapsedTime());

    }

    @Test
    void OneMilEntries(){
        //dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(5, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(10, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(15, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(20, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(25, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(30, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(35, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(40, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(45, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(50, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(55, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(60, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(65, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(70, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(75, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(80, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(85, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(90, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(95, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(100, new SizedHashMap<>(perServerMaxCapacity));

        Random random = new Random();
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            int num = random.nextInt();
            dhm.put(num, num);
            keys.add(num);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        int size = keys.size();
        int numToSearch = keys.get(random.nextInt(size));
        System.out.println(numToSearch);
        assertEquals(numToSearch, dhm.get(numToSearch));
        System.out.println(dhm.get(numToSearch));
        System.out.println(stopWatch.elapsedTime());

    }


    @Test
    void OneHunMilEntries(){
        //dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(5, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(10, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(15, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(20, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(25, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(30, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(35, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(40, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(45, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(50, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(55, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(60, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(65, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(70, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(75, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(80, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(85, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(90, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(95, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(100, new SizedHashMap<>(perServerMaxCapacity));
        for (int i = 21; i < 100; i++) {
            dhm.addServer(i * 5, new SizedHashMap<>(perServerMaxCapacity));
        }

        Random random = new Random();
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            int num = random.nextInt();
            dhm.put(num, num);
            keys.add(num);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        int size = keys.size();
        int numToSearch = keys.get(random.nextInt(size));
        System.out.println(numToSearch);
        assertEquals(numToSearch, dhm.get(numToSearch));
        System.out.println(dhm.get(numToSearch));
        System.out.println(stopWatch.elapsedTime());

    }

    @Test
    void OneBilEntries(){
        //dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(5, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(10, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(15, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(20, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(25, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(30, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(35, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(40, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(45, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(50, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(55, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(60, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(65, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(70, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(75, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(80, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(85, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(90, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(95, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(100, new SizedHashMap<>(perServerMaxCapacity));
        for (int i = 21; i < 1000; i++) {
            dhm.addServer(i * 5, new SizedHashMap<>(perServerMaxCapacity));
        }

        Random random = new Random();
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            int num = random.nextInt();
            System.out.println(i);
            dhm.put(num, num);
            keys.add(num);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        int size = keys.size();
        int numToSearch = keys.get(random.nextInt(size));
        System.out.println(numToSearch);
        assertEquals(numToSearch, dhm.get(numToSearch));
        System.out.println(dhm.get(numToSearch));
        System.out.println(stopWatch.elapsedTime());

    }
}