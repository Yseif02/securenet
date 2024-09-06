package edu.yu.introtoalgs;

public class Main {
    public static void main(String[] args) {
        final int perServerMaxCapacity = 2;
        final DHashMapBase<String , Integer> dhm = new DHashMap<>(perServerMaxCapacity);
        dhm.addServer (12 , new SizedHashMap<String, Integer>(perServerMaxCapacity));
        dhm.addServer (18 , new SizedHashMap<String, Integer>(perServerMaxCapacity));
        dhm.put("foo", 1);
        dhm.put("bar", 2);
        final int v = dhm.get("bar");
        System.out.println(v);
        dhm.remove("foo");
        dhm.addServer(5 , new SizedHashMap<String , Integer > (perServerMaxCapacity));
        dhm.removeServer (12) ;
        final int v2 = dhm.get("bar");
        System.out.println(v2);
    }
}
