package edu.yu.introtoalgs;

import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        testCase1();
        testCase2();
    }

    private static void testCase1(){
        System.out.println("Case 1");
        LFU<String, String> lfu = new LFU<>(5);
        lfu.set("One", "One");
        lfu.set("Two", "Two");
        lfu.set("Three", "Three");
        lfu.set("Four", "Four");
        lfu.set("Five", "Five");
        System.out.println(lfu.get("One"));
        System.out.println(lfu.get("One"));
        System.out.println(lfu.get("Two"));
        System.out.println(lfu.get("Two"));
        System.out.println(lfu.get("Three"));
        System.out.println(lfu.get("Four"));
        System.out.println(lfu.get("Five"));
        lfu.set("Six", "Six");
        System.out.println(lfu.get("Three"));
        System.out.println();
        System.out.println();
    }

    private static void testCase2(){
        System.out.println("Case 2");
        final int maxSize = 5;
        final LFUBase<String, String> cache = new LFU<>(maxSize);
        cache.set("a", "valueA");
        final Optional<String> x = cache.get("a");
        int size = cache.size();
        boolean isEmpty = cache.isEmpty();
        System.out.println(x);
        System.out.println(size);
        System.out.println(isEmpty);
        cache.clear();
        isEmpty = cache.isEmpty();
        System.out.println(isEmpty);
        System.out.println();
    }
}
