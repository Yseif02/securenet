package edu.yu.introtoalgs;

import java.util.*;

public class DHashMap<Key, Value> extends DHashMapBase<Key, Value> {
    private final int perServerMaxCapacity;
    private final Map<Integer, HashMap<Key, Value>> servers;
    private int totalServers;
    private int totalEntries;
    private final List<Integer> ids;


    /**
     * Constructor: client specifies the per-server capacity of participating
     * servers (hash maps) in the distributed hash map.  (For simplicity, each
     * server has the same capacity.)  The system must throw an
     * IllegalArgumentException if clients attempt to store more than this amount
     * of data.
     *
     * @param perServerMaxCapacity per server maximum capacity, must be greater
     *                             than 0.
     * @throws IllegalArgumentException as appropriate.
     */
    public DHashMap(int perServerMaxCapacity) {
        super(perServerMaxCapacity);
        if (!(perServerMaxCapacity > 0)) throw new IllegalArgumentException("perServerMaxCapacity must be greater than 0");
        this.perServerMaxCapacity = perServerMaxCapacity;
        this.servers = new HashMap<>();
        this.totalServers = 0;
        this.totalEntries = 0;
        this.ids = new ArrayList<>();
    }

    /**
     * Returns the per server max capacity.
     *
     * @return per server max capacity.
     */
    @Override
    public int getPerServerMaxCapacity() {
        return this.perServerMaxCapacity;
    }

    /**
     * Adds a server to the distributed hash map.  The implementation may choose
     * to re-balance the contents of the distributed hash map to incorporate the
     * new server.
     *
     * @param id  uniquely identifies the server, can't be negative, can't
     *            currently be in the distributed hash map
     * @param map the server's hash map: all data maintained by the server must
     *            be stored in this map, can't be null.  It's the client's responsibility to
     *            ensure that all supplied maps have the specified perServerMaxCapacity.
     *            The implementation is responsible for ensuring that the map reference
     *            isn't modified.
     * @throws IllegalArgumentException as appropriate.
     */
    @Override
    public void addServer(int id, SizedHashMap<Key, Value> map) {
        if (id < 0 || (servers.containsKey(id))) {throw new IllegalArgumentException();}
        servers.put(id, map);
        ids.add(id);
        totalServers++;
    }

    /**
     * Removes the specified server from the distributed hash map.  The
     * implementation must relocate the server's current hash map to other
     * servers in the distributed hash map.
     *
     * @param id uniquely identifies the server, can't be negative, must
     *           currently be in the distributed hash map
     * @throws IllegalArgumentException as appropriate.
     */
    @Override
    public void removeServer(int id) {
        if (id < 0) {throw new IllegalArgumentException("Id can't be negative");}
        if (totalEntries > (totalServers - 1) * getPerServerMaxCapacity()) {throw new IllegalArgumentException("not enough memory to remove server");}
        HashMap<Key, Value> mapToRemove = servers.remove(id);
        if (mapToRemove == null) {throw new IllegalArgumentException("server doesn't exist");}
        Set<Map.Entry<Key, Value>> entriesToReAdd = mapToRemove.entrySet();
        ids.remove(id);
        this.addAll(entriesToReAdd);
        totalServers--;
    }

    private void addAll(Set<Map.Entry<Key, Value>> entriesToReAdd) {
        for (Map.Entry<Key, Value> entry : entriesToReAdd){
            this.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Adds the specified key and value association to the distributed hash map.
     *
     * @param key   can't be null
     * @param value
     * @throws IllegalArgumentException if size constraints prevent the Map entry
     *                                  from being stored
     * @throws IllegalStateException    if no server has been added to the
     *                                  distributed hash map
     * @see #addServer
     * @see Map#put
     */
    @Override
    public Value put(Key key, Value value) {
        if (totalServers == 0) {throw new IllegalStateException("no servers");}
        if (getMaxEntries() == totalEntries) {throw new IllegalArgumentException("no space available");}
        if (key == null) {throw new IllegalArgumentException("key is null");}
        int hashcode = getHashcode(key);
        boolean placed = false;
        while (!placed) {
            int IdOfServerToPlaceInto = ids.get(hashcode);
            HashMap<Key, Value> map = servers.get(IdOfServerToPlaceInto);
            if (map.size() != perServerMaxCapacity){
                map.put(key, value);
                totalEntries++;
                placed = true;
            } else if (hashcode != servers.size()) {
                hashcode++;
            } else {
                hashcode = 0;
            }
        }
        return value;
    }

    /**
     * Returns the value to which the specified key is mapped, or null if this
     * map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned, may not be null.
     * @return the value to which the specified key is mapped, or null if this
     * map contains no mapping for the key
     * @throws IllegalArgumentException if key is null
     * @see Map#get
     */
    @Override
    public Value get(Object key) {
        if (key == null) {throw new IllegalArgumentException("key is null");}
        int hashcode = getHashcode(key);
        int attemptsLeft = servers.size();
        while (attemptsLeft > 0){
            int idOfServerToCheck = ids.get(hashcode);
            HashMap<Key, Value> mapToCheck = servers.get(idOfServerToCheck);
            Value valueToCheck = mapToCheck.get(key);
            if (valueToCheck != null){
                return valueToCheck;
            }
            if (hashcode == servers.size()){
                hashcode = 0;
            } else {
                hashcode++;
            }
            attemptsLeft--;
        }
        return null;
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key whose mapping is to be removed from the map, may not be
     *            null.
     * @throws IllegalArgumentException as appropriate.
     * @returns the previous value associated with key, or null if there was no
     * mapping for key.
     * @see Map#remove
     */
    @Override
    public Value remove(Object key) {
        if (key == null) {throw new IllegalArgumentException("key is null");}
        int hashcode = getHashcode(key);
        int attemptsLeft = servers.size();
        while (attemptsLeft > 0){
            int idOfServerToCheck = ids.get(hashcode);
            HashMap<Key, Value> mapToCheck = servers.get(idOfServerToCheck);
            Value valueToCheck = mapToCheck.remove(key);
            if (valueToCheck != null){
                totalEntries--;
                return valueToCheck;
            }
            if (hashcode == servers.size()){
                hashcode = 0;
            } else {
                hashcode++;
            }
            attemptsLeft--;
        }
        return null;
    }

    private int getMaxEntries(){
        return totalServers * getPerServerMaxCapacity();
    }

    private int getHashcode(Object key){
        return key.hashCode() % servers.size();
    }

}
