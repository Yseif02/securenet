package edu.yu.introtoalgs;

import java.util.*;

public class DHashMap<Key, Value> extends DHashMapBase<Key, Value>{
    private final int perServerMaxCapacity;
    private final TreeMap<Integer, HashMap<Key, Value>> servers;
    // gets server hash based on id
    private final HashMap<Integer, Integer> serverLookupTable;
    // gets id based on server hash
    private final HashMap<Integer, Integer> reverseServerLookupTable;
    private int totalEntries;


    private final HashMap<Integer, HashMap<Key, Value>> fallbackServerLookup;
    private HashMap<Key, Value> fallbackServer;

    private final List<Integer> otherServerIdsToSearch;



    private final List<HashMap<Key, Value>> serverList;
    private final HashMap<Integer, List<Integer>> serverVirtualNodes;

    boolean virtualNodes;
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
        this.serverList = new ArrayList<>();
        this.otherServerIdsToSearch = new ArrayList<>();

        this.serverVirtualNodes = new HashMap<>();
        this.virtualNodes = false;


        this.totalEntries = 0;
        this.fallbackServerLookup = new HashMap<>();
        this.fallbackServer = null;
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
        if (id < 0 || this.serverLookupTable.containsKey(id) || this.fallbackServerLookup.containsKey(id)) {throw new IllegalArgumentException("Id is less than zero: " + id + ", or server Id already exists: " + this.serverLookupTable.containsKey(id));}
        if (this.fallbackServer == null && this.serverList.size() > 1) {
            this.fallbackServerLookup.put(id, map);
            this.fallbackServer = map;
            this.serverList.add(map);
            return;
        }


        int hashcodeForNewServer = id * 7;
        this.serverLookupTable.put(id, hashcodeForNewServer);
        this.reverseServerLookupTable.put(hashcodeForNewServer, id);
        // find the hashmap that is storing data that may need to move into the new hashmap
        Map.Entry<Integer, HashMap<Key, Value>> mapEntryToRebalance = this.servers.ceilingEntry(hashcodeForNewServer) != null
                ? this.servers.ceilingEntry(hashcodeForNewServer) // if there is a server with a higher hash value
                : this.servers.firstEntry(); // otherwise it becomes the first server
        this.servers.put(hashcodeForNewServer, map);
        this.serverList.add(map);
        /*List<Integer> virtualNodes = new ArrayList<>();
        virtualNodes.add(hashcodeForNewServer);
        this.serverVirtualNodes.put(id, virtualNodes);
        if(servers.size() > 1 && needsMoreServerNodes()){
            addMoreNodes();
            this.virtualNodes = true;
        }*/

        if (this.servers.size() > 1 && mapEntryToRebalance != null) {
            this.reBalanceServer(mapEntryToRebalance, map, id, hashcodeForNewServer);
        }


    }

    private void addMoreNodes() {
        int lastServerHashValue = serverLookupTable.get(this.servers.lastEntry().getKey());
        int secondLastServerHashValue = this.servers.floorKey(lastServerHashValue);
        Map.Entry<Integer, HashMap<Key, Value>> serverEntryForNewNode;
        int positionAdder = lastServerHashValue/secondLastServerHashValue;
        int position = secondLastServerHashValue + positionAdder; //hash value of virtual node position
        for (int i = 0; i < this.servers.size(); i++) {
            if (i == 0) {
                // get last server id
                int serverId = this.reverseServerLookupTable.get(lastServerHashValue);
                // add a new virtual node at the next position
                this.servers.put(position, this.servers.get(this.serverLookupTable.get(serverId)));

                this.reverseServerLookupTable.put(serverId, position);
                // add this position to the servers virtual nodes list
                this.serverVirtualNodes.get(serverId).add(position);
                // move to next position
                position += positionAdder;
                // get entry of first server
                serverEntryForNewNode = this.servers.lowerEntry(lastServerHashValue);

            }

        }
    }

    private boolean needsMoreServerNodes() {
        int lastServerHashValue = serverLookupTable.get(this.servers.lastEntry().getKey());
        int secondLastServerHashValue = this.servers.floorKey(lastServerHashValue);
        return (2 * secondLastServerHashValue < secondLastServerHashValue);
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
        if (id < 0 || (!this.serverLookupTable.containsKey(id) && !this.fallbackServerLookup.containsKey(id))) {throw new IllegalArgumentException("Id is less than zero: " + id + ", or server Id doesn't exists: " + this.serverLookupTable.containsKey(id));}
        if (this.fallbackServerLookup.containsKey(id)){
            if (this.totalEntries - this.fallbackServer.size() > (this.serverList.size() - 1) * getPerServerMaxCapacity()) {
                throw new IllegalArgumentException("No space available to redistribute");
            }
            removeFallbackServer(id);
            return;
        }
        int hashcodeOfServerToRemove = this.serverLookupTable.get(id);
        HashMap<Key, Value> serverToRemove = this.servers.get(hashcodeOfServerToRemove);
        if (this.totalEntries - serverToRemove.size() > (this.serverList.size() - 1) * getPerServerMaxCapacity()) {
            throw new IllegalArgumentException("No space available to redistribute");
        }
        Set<Map.Entry<Key, Value>> entriesToReAddToServers = serverToRemove.entrySet();
        this.servers.remove(hashcodeOfServerToRemove);
        this.serverLookupTable.remove(id);
        this.reverseServerLookupTable.remove(hashcodeOfServerToRemove);
        this.serverList.remove(serverToRemove);
        this.totalEntries -= entriesToReAddToServers.size();
        for (Map.Entry<Key, Value> entry : entriesToReAddToServers){
            this.put(entry.getKey(), entry.getValue());
        }
    }

    private void removeFallbackServer(int id) {
        Set<Map.Entry<Key, Value>> entriesToReAddToServers = this.fallbackServer.entrySet();
        this.totalEntries -= entriesToReAddToServers.size();
        this.serverList.remove(this.fallbackServerLookup.remove(id));
        this.fallbackServer = null;
        this.fallbackServerLookup.remove(id);
        Set<Map.Entry<Key, Value>> notPlaced = new HashSet<>();
        for (Map.Entry<Key, Value> entry : entriesToReAddToServers){
            if (this.put(entry.getKey(), entry.getValue()) == null) {
                notPlaced.add(entry);
            }
        }
        for (Map.Entry<Key, Value> entry : notPlaced) {
            int hashcodeForKey = entry.getKey().hashCode();
            int hashPosition = hashcodeForKey % this.servers.lastKey();
            int attempts = this.serverList.size();

            Map.Entry<Integer, HashMap<Key, Value>> serverEntryToPlaceInto = this.servers.ceilingEntry(hashPosition);

            if (serverEntryToPlaceInto == null) {
                serverEntryToPlaceInto = this.servers.firstEntry();
            }
            while (attempts > 0) {
                HashMap<Key, Value> serverToPlaceInto = serverEntryToPlaceInto.getValue();
                if (serverToPlaceInto.size() < getPerServerMaxCapacity()) {
                    serverToPlaceInto.put(entry.getKey(), entry.getValue());
                    this.totalEntries++;
                    //placed = true;


                } else {
                    //map is full, go to the next map
                    serverEntryToPlaceInto = this.servers.higherEntry(serverEntryToPlaceInto.getKey());
                    if (serverEntryToPlaceInto == null) {
                        serverEntryToPlaceInto = this.servers.firstEntry(); // Wrap around to the first server
                    }
                    attempts--;
                }
            }
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
        if (this.totalEntries == (this.serverList.size() * getPerServerMaxCapacity())) {throw new IllegalArgumentException("No Space available");}
        if (key == null) {throw new IllegalArgumentException("Key can't be null");}
        if (this.servers.isEmpty()) {throw new IllegalStateException("No Servers");}
        int hashcodeForKey = Math.abs(key.hashCode());
        int hashPosition = hashcodeForKey % this.servers.lastKey();
        boolean placed = false;
        int attempts = 3;
        Set< Map.Entry<Integer, HashMap<Key, Value>>> serversEntriesAttempted = new HashSet<>();

        Map.Entry<Integer, HashMap<Key, Value>> serverEntryToPlaceInto = this.servers.ceilingEntry(hashPosition);

        if (serverEntryToPlaceInto == null) {
            serverEntryToPlaceInto = this.servers.firstEntry();
        }
        while (attempts > 0) {
            HashMap<Key, Value> serverToPlaceInto = serverEntryToPlaceInto.getValue();
            if (serverToPlaceInto.size() < getPerServerMaxCapacity()) {
                serverToPlaceInto.put(key, value);
                this.totalEntries++;
                //placed = true;
                return value;


            } else {
                //map is full, go to the next map
                serversEntriesAttempted.add(serverEntryToPlaceInto);
                serverEntryToPlaceInto = this.servers.higherEntry(serverEntryToPlaceInto.getKey());
                if (serverEntryToPlaceInto == null) {
                    serverEntryToPlaceInto = this.servers.firstEntry(); // Wrap around to the first server
                }
                attempts--;

            }
        }

        // 3 consecutive Servers are full, place in fallback server
        if (this.fallbackServer != null && this.fallbackServer.size() < getPerServerMaxCapacity()) {
            this.fallbackServer.put(key, value);
            totalEntries++;
            return value;
        }

        // Fallback server is full, worst case scenario
        //give up
        //throw new IllegalArgumentException("No Space available");
        Set<Map.Entry<Integer, HashMap<Key, Value>>> serverEntriesNotAttempted = new HashSet<>(this.servers.entrySet());
        serverEntriesNotAttempted.removeAll(serversEntriesAttempted);
        for(Map.Entry<Integer, HashMap<Key, Value>> mapEntry : serverEntriesNotAttempted){
            if (mapEntry.getValue().size() == getPerServerMaxCapacity()) {continue;}
            HashMap<Key, Value> serverToPlaceInto = this.servers.get(mapEntry.getKey());
            serverToPlaceInto.put(key, value);
            totalEntries++;
            this.otherServerIdsToSearch.add(mapEntry.getKey());
            return value;
        }


        return null;
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
        if (this.servers.isEmpty()) {throw new IllegalStateException("No Servers");}
        int hashcodeForKey = Math.abs(key.hashCode());
        int hashPosition = hashcodeForKey % this.servers.lastKey();
        int attempts = 3;
        Map.Entry<Integer, HashMap<Key, Value>> serverEntryToSearch = this.servers.ceilingEntry(hashPosition);
        if (serverEntryToSearch == null) {
            serverEntryToSearch = this.servers.firstEntry();
        }
        if (serverEntryToSearch == null) {throw new IllegalStateException("no servers");}
        while (attempts >= 0) {
            //Value valueToReturn = serverEntryToSearch.getValue().get(key);
            if (!serverEntryToSearch.getValue().containsKey(key)) {
                serverEntryToSearch = this.servers.higherEntry(serverEntryToSearch.getKey());
                if (serverEntryToSearch == null) {
                    serverEntryToSearch = this.servers.firstEntry();
                }
                attempts--;
            } else {
                //placed = true;
                return serverEntryToSearch.getValue().get(key);
            }
        }

        if (this.fallbackServer != null) {
            Value value = this.fallbackServer.get(key);
            if (value != null) {return this.fallbackServer.get(key);}
        }

        // check otherServerList
        Set<Integer> searched = new HashSet<>();
        for (Integer id : this.otherServerIdsToSearch){
            if (searched.contains(id)){continue;}
            HashMap<Key, Value> mapToSearch = this.servers.get(id);
            Value value = mapToSearch.get(key);
            if (value != null) {
                otherServerIdsToSearch.remove(id);
                return value;
            }
            searched.add(id);
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
        if (this.servers.isEmpty()) {throw new IllegalStateException("No Servers");}
        int hashcodeForKey = Math.abs(key.hashCode());
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
                Value valueToReturn = serverEntryToSearch.getValue().remove(key);
                this.totalEntries--;
            }
        }
//        List<HashMap<Key, Value>> notSearched = new ArrayList<>(this.serverList);
//        notSearched.removeAll(serversSearched);
//        for (HashMap<Key, Value> server : notSearched) {
//            if (server.containsKey(key)) {
//                return server.get(key);
//            }
//        }

        if (this.fallbackServer != null) {
            Value value = this.fallbackServer.remove(key);
            if (value != null) {
                this.totalEntries--;
                return value;
            }
        }
        return null;
    }

}
