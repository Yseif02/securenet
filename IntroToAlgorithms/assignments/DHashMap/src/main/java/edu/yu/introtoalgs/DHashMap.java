package edu.yu.introtoalgs;

import java.util.*;

public class DHashMap<Key, Value> extends DHashMapBase<Key, Value>{
    private final int perServerMaxCapacity;
    private final TreeMap<Integer, HashMap<Key, Value>> servers;
    // gets server hash based on id
    private final HashMap<Integer, Integer> serverLookupTable;
    // gets id based on server hash
    private final HashMap<Integer, Integer> reverseServerLookupTable;
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
        this.servers = new TreeMap<>();
        this.serverLookupTable = new HashMap<>();
        this.reverseServerLookupTable = new HashMap<>();
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
     * to rebalance the contents of the distributed hash map to incorporate the
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
        if (map == null) {throw new IllegalArgumentException("map is null");}
        if (id < 0 || serverLookupTable.containsKey(id)) {throw new IllegalArgumentException("Id is less than zero: " + id + ", or server Id already exists: " + serverLookupTable.containsKey(id));}
        int hashcodeForNewServer = id * 7;
        this.serverLookupTable.put(id, hashcodeForNewServer);
        this.reverseServerLookupTable.put(hashcodeForNewServer, id);
        // find the hashmap that is storing data that may need to move into the new hashmap
        Map.Entry<Integer, HashMap<Key, Value>> mapEntryToRebalance = servers.ceilingEntry(hashcodeForNewServer) != null
                ? servers.ceilingEntry(hashcodeForNewServer) // if there is a server with a higher hash value
                : servers.firstEntry(); // otherwise it becomes the first server
        servers.put(hashcodeForNewServer, map);
        if (servers.size() > 1 && mapEntryToRebalance != null) {
            this.reBalanceServer(mapEntryToRebalance, map, id, hashcodeForNewServer);
        }
    }

    private void reBalanceServer(Map.Entry<Integer, HashMap<Key, Value>> oldMapEntry, HashMap<Key,Value> newMap, int newMapId, int newServerHashcode) {
        Set<Map.Entry<Key, Value>> oldMapEntries = oldMapEntry.getValue().entrySet();
        if (newMap.hashCode() < oldMapEntry.hashCode()) {
            // case where the new map has a lower hash than the old map
            for(Map.Entry<Key, Value> entry : oldMapEntries){
                if(entry.getKey().hashCode() <= newServerHashcode) {
                    oldMapEntry.getValue().remove(entry.getKey());
                    newMap.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            // case where the new map has a higher hash meaning the old map looped around the ring
            for(Map.Entry<Key, Value> entry : oldMapEntries) {
                if (entry.getKey().hashCode() <= newServerHashcode && entry.getKey().hashCode() > oldMapEntry.getKey()) {
                    oldMapEntry.getValue().remove(entry.getKey());
                    newMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
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
        int hashcodeOfServerToRemove = serverLookupTable.get(id);
        HashMap<Key, Value> serverToRemove = servers.get(hashcodeOfServerToRemove);
        Set<Map.Entry<Key, Value>> entriesToReAddToServers = serverToRemove.entrySet();
        servers.remove(hashcodeOfServerToRemove);
        serverLookupTable.remove(id);
        reverseServerLookupTable.remove(hashcodeOfServerToRemove);
        for (Map.Entry<Key, Value> entry : entriesToReAddToServers){
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
        if (key == null) {throw new IllegalArgumentException("Key can't be null");}
        if (servers.isEmpty()) {throw new IllegalStateException("No Servers");}
        int hashcodeForKey = key.hashCode();
        int hashPosition = hashcodeForKey % servers.lastKey();
        boolean placed = false;
        int attempts = 3;

        Map.Entry<Integer, HashMap<Key, Value>> serverEntryToPlaceInto = servers.ceilingEntry(hashPosition);

        if (serverEntryToPlaceInto == null) {
            serverEntryToPlaceInto = servers.firstEntry();
        }
        while (attempts >= 0) {
            HashMap<Key, Value> serverToPlaceInto = serverEntryToPlaceInto.getValue();
            if (serverToPlaceInto.size() < getPerServerMaxCapacity()) {
                serverToPlaceInto.put(key, value);
                //placed = true;
                return value;


            } else {
                //map is full, go to the next map
                serverEntryToPlaceInto = servers.higherEntry(serverEntryToPlaceInto.getKey());
                if (serverEntryToPlaceInto == null) {
                    serverEntryToPlaceInto = servers.firstEntry(); // Wrap around to the first server
                }
                attempts--;

            }
        }
        throw new IllegalArgumentException("No Space available");
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
        if (key == null) {throw new IllegalArgumentException("Key can't be null");}
        if (servers.isEmpty()) {throw new IllegalStateException("No Servers");}
        int hashcodeForKey = key.hashCode();
        int hashPosition = hashcodeForKey % servers.lastKey();
        int attempts = 3;
        Map.Entry<Integer, HashMap<Key, Value>> serverEntryToSearch = servers.ceilingEntry(hashPosition);
        if (serverEntryToSearch == null) {
            serverEntryToSearch = servers.firstEntry();
        }
        if (serverEntryToSearch == null) {throw new IllegalStateException("no servers");}
        while (attempts >= 0) {
            //Value valueToReturn = serverEntryToSearch.getValue().get(key);
            if (!serverEntryToSearch.getValue().containsKey(key)) {
                serverEntryToSearch = servers.higherEntry(serverEntryToSearch.getKey());
                if (serverEntryToSearch == null) {
                    serverEntryToSearch = servers.firstEntry();
                }
                attempts--;
            } else {
                //placed = true;
                return serverEntryToSearch.getValue().get(key);
            }
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
        if (key == null) {throw new IllegalArgumentException("Key can't be null");}
        if (servers.isEmpty()) {throw new IllegalStateException("No Servers");}
        int hashcodeForKey = key.hashCode();
        int hashPosition = hashcodeForKey % servers.lastKey();
        int attempts = servers.size();
        Map.Entry<Integer, HashMap<Key, Value>> serverEntryToSearch = servers.ceilingEntry(hashPosition);
        if (serverEntryToSearch == null) {
            serverEntryToSearch = servers.firstEntry();
        }
        if (serverEntryToSearch == null) {throw new IllegalStateException("no servers");}
        while (attempts >= 0) {
            //Value valueToReturn = serverEntryToSearch.getValue().get(key);
            if (!serverEntryToSearch.getValue().containsKey(key)) {
                serverEntryToSearch = servers.higherEntry(serverEntryToSearch.getKey());
                if (serverEntryToSearch == null) {
                    serverEntryToSearch = servers.firstEntry();
                }
                attempts--;
            } else {
                //placed = true;
                return serverEntryToSearch.getValue().remove(key);
            }
        }
        return null;
    }

}
