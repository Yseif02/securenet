package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Trie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TrieImplTest {
    private Trie<String> trie;
    @BeforeEach
    void setUp() {
        this.trie = new TrieImpl<String>();
    }

    @Test
    void put() {
        this.trie.put("key", "value");
        assertTrue(this.trie.get("key").contains("value"));
    }

    @Test
    void put_nullKey() {
        assertThrows(IllegalArgumentException.class,
                () -> this.trie.put(null, "value"));
    }

    @Test
    void put_nullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> this.trie.put("key1", null));
    }

    @Test
    void put_emptyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> this.trie.put("", "value"));
    }

    @Test
    void get() {
        Set<String> setToTest = new HashSet<>(){{
            add("value1");
            add("value2");
            add("value3");
        }};
        this.trie.put("key1", "value1");
        this.trie.put("key1", "value2");
        this.trie.put("key1", "value3");
        Set<String> key1Values = this.trie.get("key1");
        assertEquals(key1Values, setToTest);
    }

    @Test
    void get_nonExistentKey() {
        Set<String> setToTest = new HashSet<>();
        assertEquals(setToTest, this.trie.get("key"));
    }
    @Test
    void get_nullKey() {
        Set<String> setToTest = new HashSet<>();
        assertThrows(IllegalArgumentException.class,
                () -> this.trie.get(null));
    }

    @Test
    void getSorted() {
        Comparator<String> stringComparator = (String1, String2) ->
                String2.toLowerCase().compareTo(String1.toLowerCase());
        this.trie.put("key1","apple");
        this.trie.put("key1","banana");
        this.trie.put("key1","cat");
        this.trie.put("key1","Dog");
        this.trie.put("key1","end");
        List<String> key1Sorted = this.trie.getSorted("key1", stringComparator);
        List<String> listToTest = new ArrayList<>();
        listToTest.add("end");
        listToTest.add("Dog");
        listToTest.add("cat");
        listToTest.add("banana");
        listToTest.add("apple");
        assertEquals(key1Sorted, listToTest);
    }

    @Test
    void getSorted_emptyKey() {
        Comparator<String> stringComparator = (String1, String2) ->
                String2.toLowerCase().compareTo(String1.toLowerCase());
        List<String> keys = this.trie.getSorted("", stringComparator);
        assertTrue(keys.isEmpty());
    }

    @Test
    void getSorted_nullKey() {
        Comparator<String> stringComparator = (String1, String2) ->
                String2.toLowerCase().compareTo(String1.toLowerCase());
        assertThrows(IllegalArgumentException.class, () ->
                this.trie.getSorted(null, stringComparator));
    }



    @Test
    void getAllWithPrefixSorted() {
        Comparator<String> stringComparator = Comparator.comparing(String::toLowerCase);
        this.trie.put("the","the");
        this.trie.put("then","then");
        this.trie.put("there","there");
        this.trie.put("their","their");
        this.trie.put("they","they");
        this.trie.put("they","they're");
        List<String> key1Sorted = this.trie.getAllWithPrefixSorted("th", stringComparator);
        List<String> listToTest = new ArrayList<>();
        listToTest.add("the");
        listToTest.add("their");
        listToTest.add("then");
        listToTest.add("there");
        listToTest.add("they");
        listToTest.add("they're");
        assertEquals(key1Sorted, listToTest);
    }

    @Test
    void getAllWithPrefixSorted_nullKey() {
        assertThrows(IllegalArgumentException.class,
            () -> this.trie.getAllWithPrefixSorted(null, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return o1.compareTo(o2);
                }
            }));
    }

    @Test
    void getAllWithPrefixSorted_nonExistingKey() {
        Comparator<String> stringComparator = Comparator.comparing(String::toLowerCase);
        this.trie.put("the","the");
        this.trie.put("then","then");
        this.trie.put("there","there");
        this.trie.put("their","their");
        this.trie.put("they","they");
        this.trie.put("they","they're");
        List<String> emptyList = new ArrayList<>();
        assertEquals(emptyList, this.trie.getAllWithPrefixSorted("to", stringComparator));
    }

    @Test
    void deleteAllWithPrefix() {
        this.trie.put("the","the");
        this.trie.put("then","then");
        this.trie.put("there","there");
        this.trie.put("their","their");
        this.trie.put("they","they");
        this.trie.put("they","they're");

        Set<String> setToTest = new HashSet<>(){{
            add("the");
            add("their");
            add("then");
            add("there");
            add("they");
            add("they're");
        }};
        assertEquals(setToTest, this.trie.deleteAllWithPrefix("th"));
    }

    @Test
    void deleteAll() {
        this.trie.put("key1","apple");
        this.trie.put("key1","banana");
        this.trie.put("key1","cat");
        this.trie.put("key1","Dog");
        this.trie.put("key1","end");
        Set<String> setToTest = new HashSet<>(){{
            add("apple");
            add("banana");
            add("cat");
            add("Dog");
            add("end");
        }};
        assertEquals(setToTest, this.trie.deleteAll("key1"));
    }

    @Test
    void delete() {
        this.trie.put("key1","apple");
        this.trie.put("key1","banana");
        this.trie.put("key1","cat");
        this.trie.put("key1","Dog");
        this.trie.put("key1","end");
        assertEquals("banana", this.trie.delete("key1", "banana"));
    }
}