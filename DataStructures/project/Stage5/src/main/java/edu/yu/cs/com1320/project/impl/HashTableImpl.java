package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.HashTable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class HashTableImpl<Key, Value> implements HashTable<Key, Value> {

    /**
     * @param k the key whose value should be returned
     * @return the value that is stored in the HashTable for k, or null if there is no such key in the table
     */
    public Value get(Key k) {
        return null;
    }

    /**
     * @param k the key at which to store the value
     * @param v the value to store
     *          To delete an entry, put a null value.
     * @return if the key was already present in the HashTable, return the previous value stored for the key. If the key was not already present, return null.
     */
    public Value put(Key k, Value v) {
        return null;
    }

    /**
     * @param key the key whose presence in the hashtabe we are inquiring about
     * @return true if the given key is present in the hashtable as a key, false if not
     * @throws NullPointerException if the specified key is null
     */
    public boolean containsKey(Key key) {
        return false;
    }

    /**
     * @return an unmodifiable set of all the keys in this HashTable
     * @see Collections#unmodifiableSet(Set)
     */
    public Set<Key> keySet() {
        return null;
    }

    /**
     * @return an unmodifiable collection of all the values in this HashTable
     * @see Collections#unmodifiableCollection(Collection)
     */
    public Collection<Value> values() {
        return null;
    }

    /**
     * @return how entries there currently are in the HashTable
     */
    public int size() {
        return 0;
    }
}
