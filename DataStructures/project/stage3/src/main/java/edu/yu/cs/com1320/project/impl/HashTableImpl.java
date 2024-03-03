package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.HashTable;

import java.util.*;

public class HashTableImpl<Key, Value> implements HashTable<Key, Value> {
    private class Entry<K, V>{
        private final Key key;
        private Value value;
        private Entry<?,?> next;


        private Entry(Key k, Value v){
            if(k == null){
                throw new IllegalArgumentException();
            }
            this.key = k;
            this.value = v;
        }
        private Key getKey(){
            return this.key;
        }
        private Value getValue(){
            return this.value;
        }
        private boolean hasNext(){
            return next != null;
        }
    }
    private Entry<?,?>[] table;
    private final double LOAD_FACTOR;
    public HashTableImpl(){
        this.table = new Entry[5];
        this.LOAD_FACTOR = 0.75;
    }

    private int hashFunction(Key key, int tableLength){
        return (key.hashCode() & 0x7fffffff) % tableLength;
    }

    private boolean isFull(){
        return ((double) size()/table.length > this.LOAD_FACTOR);
    }

    private Entry<?, ?>[] doubleTable(){
        Entry<?,?>[] newTable = new Entry[this.table.length * 2];
        for(Entry<?,?> entry : this.table){
            if(entry == null) continue;
            if(entry.hasNext()){
                rehashCollisions(entry, newTable);
                continue;
            }
            addEntryToNewTable(entry, newTable);
        }
        this.table = newTable;
        return newTable;
    }

    private void addEntryToNewTable(Entry<?, ?> entry, Entry<?, ?>[] newTable) {
        int hashCodeForObject = hashFunction(entry.getKey(), newTable.length);
        //This
        Entry<?, ?> entryToCheck = newTable[hashCodeForObject];
        if(entryToCheck == null){
            newTable[hashCodeForObject] = new Entry<>(entry.getKey(), entry.getValue());
            return;
        }
        do {
            if(entryToCheck.next != null) entryToCheck = entryToCheck.next;
        }
        while (entryToCheck.next != null);
        entryToCheck.next = new Entry<>(entry.getKey(), entry.getValue());
    }

    private void rehashCollisions(Entry<?,?> entry, Entry<?,?>[] table){
        if(entry.hasNext()) rehashCollisions(entry.next, table);
        int hashCodeForObject = hashFunction(entry.getKey(), table.length);
        Entry<?,?> tableEntry = table[hashCodeForObject];
        if(tableEntry == null) {
            table[hashCodeForObject] = new Entry<>(entry.getKey(), entry.getValue());
            return;
        }
        while (tableEntry.next != null){
            tableEntry = tableEntry.next;
        }
        tableEntry.next = new Entry<>(entry.getKey(), entry.getValue());
    }

    /**
     * @param k the key whose value should be returned
     * @return the value that is stored in the HashTable for k, or null if there is no such key in the table
     */
    @Override
    public Value get(Key k) {
        int hashCodeForObject = hashFunction(k, this.table.length);
        Entry<?, ?> entryToSearch = this.table[hashCodeForObject];
        if(entryToSearch == null) return null;
        while (entryToSearch.next != null){
            if (entryToSearch.getKey().equals(k)) return entryToSearch.getValue();
            entryToSearch = entryToSearch.next;
        }
        if(entryToSearch.getKey().equals(k)) return entryToSearch.getValue();
        return null;
    }

    /**
     * @param k the key at which to store the value
     * @param v the value to store
     *          To delete an entry, put a null value.
     * @return if the key was already present in the HashTable, return the previous value stored for the key. If the key was not already present, return null.
     */
    @Override
    public Value put(Key k, Value v) {
        if(isFull()) doubleTable();
        if(v == null) return delete(k);
        int hashCodeForObject = hashFunction(k, this.table.length);
        Entry<?, ?> old = this.table[hashCodeForObject];
        if(old == null){
            this.table[hashCodeForObject] = new Entry<>(k,v);
            return null;
        }
        while (old.next != null){
            if(old.getKey().equals(k)){
                Value valueToReturn = old.getValue();
                old.value = v;
                return valueToReturn;
            }
            if (old.next == null) {
                break;
            }
            old = old.next;
        }
        if(old.getKey() == k){
            Value valueToReturn = old.getValue();
            old.value = v;
            return valueToReturn;
        }
        old.next = new Entry<>(k,v);
        return null;
    }

    private Value delete(Key key){
        if(get(key) == null) return null;
        int hashCodeForObject = hashFunction(key, this.table.length);
        Entry<?, ?> entryToSearch = this.table[hashCodeForObject];
        Entry<?,?> entryToReturn;
        if(entryToSearch.getKey().equals(key)){
            if(entryToSearch.next == null){
                entryToReturn = entryToSearch;
                table[hashCodeForObject] = null;
                return entryToReturn.getValue();
            }
            entryToReturn = entryToSearch;
            table[hashCodeForObject] = entryToSearch.next;
            entryToSearch.next = null;
            return entryToReturn.getValue();
        }
        Entry<?,?> previous = entryToSearch;
        Entry<?,?> current = entryToSearch.next;
        while (current != null){
            if(current.getKey().equals(key)){
                previous.next = current.next;
                current.next = null;
                return current.getValue();
            }
            previous = current;
            current = current.next;
        }
        return null;
    }

    /**
     * @param key the key whose presence in the hashtable we are inquiring about
     * @return true if the given key is present in the hashtable as a key, false if not
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public boolean containsKey(Key key) {
        if(key == null) throw new NullPointerException();
        int hashCodeForObject = hashFunction(key, this.table.length);
        Entry<?,?> entryToSearch = this.table[hashCodeForObject];
        while(entryToSearch.next != null){
            if(entryToSearch.getKey().equals(key)) return true;
            entryToSearch = entryToSearch.next;
        }
        return entryToSearch.getKey().equals(key);
    }

    /**
     * @return an unmodifiable set of all the keys in this HashTable
     * @see Collections#unmodifiableSet(Set)
     */
    @Override
    public Set<Key> keySet() {
        HashSet<Key> keySet = new HashSet<>();
        for (Entry<?, ?> entry : this.table) {
            if (entry == null) {
                continue;
            }
            Entry<?, ?> currentEntry = entry;
            if (currentEntry.next == null) {
                Key keyToAdd = currentEntry.getKey();
                keySet.add(keyToAdd);
            }
            while (currentEntry.next != null) {
                Key keyToAdd = currentEntry.getKey();
                keySet.add(keyToAdd);
                currentEntry = currentEntry.next;
            }
            Key keyToAdd = currentEntry.getKey();
            keySet.add(keyToAdd);
        }
        return Collections.unmodifiableSet(keySet);
    }

    /**
     * @return an unmodifiable collection of all the values in this HashTable
     * @see Collections#unmodifiableCollection(Collection)
     */
    @Override
    public Collection<Value> values() {
        List<Value> values = new ArrayList<>();
        for (Entry<?, ?> entry : this.table) {
            if (entry == null) {
                continue;
            }
            Entry<?, ?> currentEntry = entry;
            if (currentEntry.next == null) {
                Value valueToAdd = currentEntry.getValue();
                values.add(valueToAdd);
                continue;
            }
            while (currentEntry.next != null) {
                Value valueToAdd = currentEntry.getValue();
                values.add(valueToAdd);
                currentEntry = currentEntry.next;
            }
            Value valueToAdd = currentEntry.getValue();
            values.add(valueToAdd);
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * @return how entries there currently are in the HashTable
     */
    @Override
    public int size() {
        return keySet().size();
    }
}
