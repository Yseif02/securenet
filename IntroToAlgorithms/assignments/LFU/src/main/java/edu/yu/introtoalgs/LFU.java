package edu.yu.introtoalgs;

import java.util.*;

public class LFU<Key, Value> extends LFUBase<Key, Value>{
    public final HashMap<Key, Value> cacheMap;
    private final HashMap<Key, Integer> usageMap;
    private final TreeMap<Integer, LinkedHashSet<Key>> removalUsageMap;
    private final int maxSize;
    /**
     * Constructor: supplies the maximum size of the cache: when the cache is
     * full, the LFU eviction policy MUST be used to select a cache entry to swap
     * out to make room for the new cache entry.
     *
     * @param maxSize maximum size of the cache, must be greater than 0.
     * @throws IllegalArgumentException as appropriate.
     * @see #set
     */
    public LFU(int maxSize) {
        super(maxSize);
        if (maxSize < 1) {throw new IllegalArgumentException("maxsize must be grater than zero");}
        this.cacheMap = new HashMap<>();
        this.removalUsageMap = new TreeMap<>();
        this.usageMap = new HashMap<>();
        this.maxSize = maxSize;
    }

    /**
     * Caches the value, associating it with the key.  If the key is currently
     * associated with a different value, the new value overwrites the old value.
     *
     * @param key   used to retrieve the value, may not be null
     * @param value the value to be cached, may not be null
     * @return Returns true iff a cache entry with a previously non-existent key
     * is inserted into the cache, false otherwise
     * @throws IllegalArgumentException as appropriate.
     * @see #get
     */
    @Override
    public boolean set(Key key, Value value) {
        if (key == null || value == null) {throw new IllegalArgumentException("Key or Value may not be null");}
        if (this.cacheMap.containsKey(key)) {
            this.cacheMap.put(key, value);
            //int uses = this.usageMap.get(key);
            int timesAccessed = this.usageMap.remove(key);
            Set<Key> setOfKeysToRemoveKeyFrom = this.removalUsageMap.get(timesAccessed);
            setOfKeysToRemoveKeyFrom.remove(key);
            //remove list from usage map if size = 0 for given key;
            if(setOfKeysToRemoveKeyFrom.isEmpty()) {
                this.removalUsageMap.remove(timesAccessed);
            }
            this.usageMap.put(key, ++timesAccessed);
            if (this.removalUsageMap.get(timesAccessed) != null) {
                this.removalUsageMap.get(timesAccessed).add(key);
            } else {
                this.removalUsageMap.put(timesAccessed, new LinkedHashSet<>(Collections.singleton(key)));
            }

            return false;
        }
        if (this.size() == this.maxSize) {
            removeOldestElement();
        }
        this.cacheMap.put(key, value);
        this.usageMap.put(key, 1);
        LinkedHashSet<Key> setToAddKeyTo = this.removalUsageMap.get(1);
        if (setToAddKeyTo == null) {
            setToAddKeyTo = new LinkedHashSet<>(Collections.singleton(key));
            this.removalUsageMap.put(1,setToAddKeyTo);
        } else {
            setToAddKeyTo.add(key);
        }
        return true;
    }

    private void removeOldestElement() {
        LinkedHashSet<Key> setOfKeyToRemove = this.removalUsageMap.firstEntry().getValue();
        Iterator<Key> iterator = setOfKeyToRemove.iterator();
        Key keyToRemove = null;
        if (iterator.hasNext()) {
            keyToRemove = iterator.next();
            iterator.remove();
        }
        this.cacheMap.remove(keyToRemove);
        this.usageMap.remove(keyToRemove);
    }

    /**
     * Retrieve the value (if any) associated with the key, encapsulating the
     * value in an Optional.  If no value is associated with the key, the
     * Optional encapsulates null.
     *
     * @param key
     * @return the value associated with the key
     * @see #set
     */
    @Override
    public Optional<Value> get(Key key) {
        if (key == null) {throw new IllegalArgumentException("Key may not be null");}
        Value valueToReturn = this.cacheMap.get(key);
        if (valueToReturn != null) {
            int timesAccessed = this.usageMap.remove(key);
            // when all the keys have the same time used, retrieving a key will have to traverse the whole list to find the key if it's at the end
            // need a better way to find the key. Can use a hashmap but doesn't maintain order.
            LinkedHashSet<Key> setOfKeysToRemoveKeyFrom = this.removalUsageMap.get(timesAccessed);
            setOfKeysToRemoveKeyFrom.remove(key);
            //remove list from usage map if size = 0 for given key;
            if(setOfKeysToRemoveKeyFrom.isEmpty()) {
                this.removalUsageMap.remove(timesAccessed);
            }
            this.usageMap.put(key, ++timesAccessed);
            if (this.removalUsageMap.get(timesAccessed) != null) {
                this.removalUsageMap.get(timesAccessed).add(key);
            } else {
                this.removalUsageMap.put(timesAccessed, new LinkedHashSet<>(Collections.singleton(key)));
            }
        }
        return Optional.ofNullable(valueToReturn);
    }

    /**
     * Returns the current size of the cache.
     *
     * @returns number of elements in the cache
     */
    @Override
    public int size() {
        return this.cacheMap.size();
    }

    /**
     * Return true iff the cache has no entries, false otherwise.
     *
     * @return is the cache empty or not.
     */
    @Override
    public boolean isEmpty() {
        return (this.cacheMap.isEmpty());
    }

    /**
     * Empties the cache, such that isEmpty will return true after the method
     * completes.
     *
     * @see #isEmpty
     */
    @Override
    public void clear() {
        this.cacheMap.clear();
        this.usageMap.clear();
        this.removalUsageMap.clear();
    }
}
