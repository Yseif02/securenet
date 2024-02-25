package edu.yu.cs.com1320.project.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HashTableImplTest {
    private HashTableImpl<String,String> hashTable;

    @BeforeEach
    void setUp() {
        this.hashTable = new HashTableImpl<>();
    }

    @Test
    void get() {
        String key = "key";
        String value = "value";
        this.hashTable.put(key,value);
        assertEquals(value, this.hashTable.get(key));
    }

    @Test
    void put_newKey() {
        String key = "key";
        String value = "value";
        assertNull(this.hashTable.put(key, value));
    }

    @Test
    void put_replaceExistingValue() {
        String key = "key";
        String value = "value";
        this.hashTable.put(key, value);
        String newValue = "newValue";
        assertEquals(value, this.hashTable.put(key, newValue));
    }

    @Test
    void delete_nonExistingKey(){
        String key = "key";
        assertNull(this.hashTable.put(key, null));
    }

    @Test
    void delete_existingKey(){
        String key = "key";
        String value = "value";
        this.hashTable.put(key, value);
        assertEquals(value, this.hashTable.put(key, null));
    }

    @Test
    void containsKey() {
        String key = "key";
        String value = "value";
        this.hashTable.put(key, value);
        assertTrue(this.hashTable.containsKey(key));
    }

    @Test
    void containsKey_nullKey() {
        assertThrows(NullPointerException.class,
                () -> this.hashTable.containsKey(null));
    }

    @Test
    void keySet() {
        HashTableImpl<Integer, Integer> integerHashTable = new HashTableImpl<>();
        HashSet<Integer> setToCompare = new HashSet<>();
        for(int i = 0; i < 1000; i++){
            setToCompare.add(i);
            integerHashTable.put(i, i);
        }
        assertEquals(setToCompare, integerHashTable.keySet());
    }

    @Test
    void values() {
        HashTableImpl<Integer, Integer> integerHashTable = new HashTableImpl<>();
        List<Integer> listToCompare = new LinkedList<>();
        for(int i = 0; i < 10; i++){
            listToCompare.add(i);
            listToCompare.add(i);
            integerHashTable.put(i, i);
            integerHashTable.put(i + 10, i);
        }
        assertEquals(listToCompare, integerHashTable.values());
    }

    @Test
    void size() {
        HashTableImpl<Integer, Integer> integerHashTable = new HashTableImpl<>();
        List<Integer> listToCompare = new ArrayList<>();
        for(int i = 0; i < 10; i++){
            listToCompare.add(i);
            integerHashTable.put(i, i);
        }
        assertEquals(listToCompare.size(), integerHashTable.size());
    }
}