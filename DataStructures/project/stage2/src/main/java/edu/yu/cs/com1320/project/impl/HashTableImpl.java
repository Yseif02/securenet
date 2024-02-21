package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.HashTable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Instances of HashTable should be constructed with two type parameters, one for the type of the keys in the table and one for the type of the values
 *
 * @param <Key>
 * @param <Value>
 */

public class HashTableImpl<Key, Value> implements HashTable {
    private class Entry<K, V>{
        private Key key;
        private Value value;
        private Entry<?, ?> next;
        private int numberOfEntries;

        private Entry(Key k, Value v){
            if(k == null){
                throw new IllegalArgumentException();
            }
            this.key = k;
            this.value = v;
            this.numberOfEntries = 0;
        }
        private Key getKey(){
            return this.key;
        }
        private Value getValue(){
            return this.value;
        }
    }
    private final Entry<?,?>[] table;
    public HashTableImpl(){
        this.table = new Entry[5];
    }

    private int hashFunction(Object key){
        return (key.hashCode() & 0x7fffffff) % this.table.length;
    }

    /**
     * @param k the key whose value should be returned
     * @return the value that is stored in the HashTable for k, or null if there is no such key in the table
     */
    @Override
    public Object get(Object k) {
        int hashCodeForObject = hashFunction(k);
        Entry<?, ?> entryToSearch = this.table[hashCodeForObject];
        if(entryToSearch == null) return null;
        while(entryToSearch.next != null){
            if(entryToSearch.getKey().equals(k)) return entryToSearch.getValue();
            entryToSearch = entryToSearch.next;
        }
        if(entryToSearch.getValue().equals(k)) return entryToSearch.getValue();
        return null;
    }

    /**
     * @param k the key at which to store the value
     * @param v the value to store
     *          To delete an entry, put a null value.
     * @return if the key was already present in the HashTable, return the previous value stored for the key. If the key was not already present, return null.
     */
    @Override
    public Object put(Object k, Object v) {
        if(v == null) return delete(k);
        int hashCodeForObject = hashFunction(k);
        Entry<?, ?> old = this.table[hashCodeForObject];
        if(old == null){
            this.table[hashCodeForObject] = new Entry<>((Key) k,(Value) v);
            return null;
        }
        while (old.next != null){
            if(old.getKey().equals(k)){
                Object valueToReturn = old.getValue();
                old.value = (Value) v;
                return valueToReturn;
            }
            if (old.next == null) {
                break;
            }
            old = old.next;
        }
        if(old.getKey() == k){
            Object valueToReturn = old.getValue();
            old.value = (Value) v;
            return valueToReturn;
        }
        old.next = new Entry<Object, Object>((Key) k,(Value) v);
        return null;
    }

    private Object delete(Object key){
        if(get(key) == null) return false;
        int hashCodeForObject = hashFunction(key);
        Entry<?, ?> entryToSearch = this.table[hashCodeForObject];
        Entry<?,?> entryToReturn = null;
        if(entryToSearch.getKey().equals(key)){
            if(entryToSearch.next == null){
                entryToReturn = entryToSearch;
                table[hashCodeForObject] = null;
                return entryToReturn;
            }
            entryToReturn = entryToSearch;
            table[hashCodeForObject] = entryToSearch.next;
            entryToSearch.next = null;
            return entryToReturn;
        }
        Entry<?,?> previous = entryToSearch;
        Entry<?,?> current = entryToSearch.next;
            while (current != null){
                if(current.getKey().equals(key)){
                    previous.next = current.next;
                    current.next = null;
                    return current;
                }
                previous = current;
                current = current.next;
            }
        return null;
    }

    private int listSize(Object key){
        int hashCodeForObject = hashFunction(key);
        Entry<?, ?> entryToSearch = this.table[hashCodeForObject];
        int counter = 0;
        if(entryToSearch == null) return counter;
        if(entryToSearch.next == null) return 1;
        counter++;
        while (entryToSearch.next != null){
            entryToSearch = entryToSearch.next;
            counter++;
        }

        System.out.println(hashCodeForObject + " " + counter);
        return counter;
    }

    @Override
    public boolean containsKey(Object object) {
        int hashCodeForObject = hashFunction(object);
        Entry<?,?> entryToSearch = this.table[hashCodeForObject];
        boolean found = false;
        while(entryToSearch.next != null){
            if(entryToSearch.getKey().equals(object)) return true;
            entryToSearch = entryToSearch.next;
        }
        return entryToSearch.getKey().equals(object);
    }

    @Override
    public Set keySet() {
        HashSet<Key> keySet = new HashSet<>();
        for(int i = 0; i < this.table.length; i++){
            if(this.table[i] == null){
                continue;
            }
            Entry<?,?> currentEntry = this.table[i];
            if(currentEntry != null && currentEntry.next == null){
                Key keyToAdd = currentEntry.getKey();
                keySet.add(keyToAdd);
            }
            while (currentEntry.next != null){
                Key keyToAdd = currentEntry.getKey();
                keySet.add(keyToAdd);
                currentEntry = currentEntry.next;
            }
            Key keyToAdd = currentEntry.getKey();
            keySet.add(keyToAdd);
        }
        return Collections.unmodifiableSet(keySet);
    }

    @Override
    public Collection values() {
        HashSet<Value> values = new HashSet<>();
        for(int i = 0; i < this.table.length; i++){
            if(this.table[i] == null){
                continue;
            }
            Entry<?,?> currentEntry = this.table[i];
            if(currentEntry != null && currentEntry.next == null){
                Value valueToAdd = currentEntry.getValue();
                values.add(valueToAdd);
            }
            while (currentEntry.next != null){
                Value valueToAdd = currentEntry.getValue();
                values.add(valueToAdd);
                currentEntry = currentEntry.next;
            }
            Value valueToAdd = currentEntry.getValue();
            values.add(valueToAdd);
        }
        return Collections.unmodifiableSet(values);
    }

    @Override
    public int size() {
        return keySet().size();
    }
}
