package edu.yu.introtoalgs;

import org.junit.jupiter.api.Test;

public class Main {
    public static void main(String[] args) {
        //testCase1();
        //testCase2();
        //testCase3();
        testCase4();
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

    private static void testCase3(){
        final int perServerMaxCapacity = 3;
        final DHashMap<Integer, Integer> dhm = new DHashMap<>(perServerMaxCapacity);
        dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(2, new SizedHashMap<>(perServerMaxCapacity));
        dhm.addServer(4, new SizedHashMap<>(perServerMaxCapacity)); // backup server
        dhm.addServer(3, new SizedHashMap<>(perServerMaxCapacity)); // 14 - 21
        dhm.addServer(50, new SizedHashMap<>(perServerMaxCapacity));
        dhm.put(1, 1); // 1
        dhm.put(2, 2); // 1
        dhm.put(3, 3); // 1

        dhm.put(8, 8); // 2
        dhm.put(9, 9); // 2
        dhm.put(10, 10); // 2

        dhm.put(51, 51); // 50
        dhm.put(52, 52); // 50
        dhm.put(53, 53); // 50

        dhm.put(15, 15); // 3
        dhm.put(16, 16); // 3
        dhm.put(17, 17); // 3
        dhm.put(18, 18); // backup 4

        dhm.remove(18); // test removal from backup server
        dhm.put(18, 18);

        dhm.remove(17);

        // dhm.removeServer(50); //remove a server

        dhm.removeServer(4);; // remove backup server
    }

    private static void testCase4(){
        final int perServerMaxCapacity = 3;
        final DHashMap<Integer, Integer> dhm = new DHashMap<>(perServerMaxCapacity);
        dhm.addServer(1, new SizedHashMap<>(perServerMaxCapacity)); // 1 - 7
        dhm.addServer(2, new SizedHashMap<>(perServerMaxCapacity)); // 8 - 14

        dhm.addServer(1000, new SizedHashMap<>(perServerMaxCapacity)); // fallback

        dhm.addServer(3, new SizedHashMap<>(perServerMaxCapacity)); // 15 - 21
        dhm.addServer(4, new SizedHashMap<>(perServerMaxCapacity)); // 22 - 28
        dhm.addServer(5, new SizedHashMap<>(perServerMaxCapacity)); // 29 - 35
        dhm.addServer(6, new SizedHashMap<>(perServerMaxCapacity)); // 36 - 42
        dhm.addServer(7, new SizedHashMap<>(perServerMaxCapacity)); // 43 - 49
        dhm.addServer(8, new SizedHashMap<>(perServerMaxCapacity)); // 50 - 56
        dhm.addServer(9, new SizedHashMap<>(perServerMaxCapacity)); // 57 - 63
        dhm.addServer(50, new SizedHashMap<>(perServerMaxCapacity)); // 64 - 350

        dhm.put(1, 1);
        dhm.put(2, 2);
        dhm.put(3, 3);

        dhm.put(8, 8);
        dhm.put(9, 9);
        dhm.put(10, 10);

        dhm.put(15, 15);
        dhm.put(16, 16);
        dhm.put(17, 17);

        dhm.put(64, 64);
        dhm.put(65, 65);
        dhm.put(66, 66);

        dhm.put(67, 67);
        dhm.put(68, 68);
        dhm.put(69, 69);

        dhm.put(70, 70);

        System.out.println(dhm.get(70));
    }
}
