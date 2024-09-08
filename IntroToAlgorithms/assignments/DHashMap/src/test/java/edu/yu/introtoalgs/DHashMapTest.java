package edu.yu.introtoalgs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DHashMapTest {
    final int perServerMaxCapacity = 10;
    DHashMapBase<String , Integer> dhm;

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
    }
}