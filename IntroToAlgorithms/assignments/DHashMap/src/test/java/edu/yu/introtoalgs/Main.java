package edu.yu.introtoalgs;

public class Main {
    public static void main(String[] args) {
        //testCase1();
        testCase2();
    }

    private static void testCase1() {
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

    private static void testCase2() {
        final int perServerMaxCapacity = 3;
        final DHashMap<Integer, Integer> dhm = new DHashMap<>(perServerMaxCapacity);
        dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(2, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(3, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(4, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(50, new SizedHashMap<>(perServerMaxCapacity));
        dhm.put(35, 35);
        dhm.put(40, 40);
        dhm.put(45, 45);
        dhm.put(50, 50);
        dhm.put(51, 51);
        dhm.put(52, 52);
        dhm.put(53, 53);
        dhm.put(54, 54);
        dhm.put(55, 55);
        dhm.put(56, 56);
        dhm.put(57, 57);
        dhm.put(58, 58);
        dhm.put(59, 59);
        dhm.put(61, 61);
        dhm.put(62, 62);
//        dhm.put(63, 63);
//        dhm.put(64, 64);
//        dhm.put(65, 65);
//        dhm.put(66, 66);
//        dhm.put(67, 67);
//        dhm.put(68, 68);
//        dhm.put(69, 69);
        System.out.println(dhm.get(62));
    }
}
