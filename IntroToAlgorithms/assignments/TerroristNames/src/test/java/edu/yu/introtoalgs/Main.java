package edu.yu.introtoalgs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        //testCase1();
        //testCase2();
        //test100KAddsAnd1millSearches();
    }

    /*private static void testCase1() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        try {
            int count = 0;
            List<String> lines = Files.readAllLines(Paths.get("src/main/resources/random_words_list.txt"));
            System.out.println("Number of lines read: " + lines.size());
            for (String line : lines) {
                ++count;
                //System.out.println("Adding: " + line + ":\t" + );
                if (line.length() < 10 && line.length() > 1 && !terroristNamesBase.checkIfWordExists(line)) terroristNamesBase.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Looking for 'acc': \tFound: " + terroristNamesBase.search("acc"));
        for (String string : terroristNamesBase.getWordsForId("acc")){
            System.out.println(string);
        }
        System.out.println(terroristNamesBase.getWordsForId("acc").size());

    }*/

    private static void testCase2() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        terroristNamesBase.add("hello");
        terroristNamesBase.add("alsohello");
        terroristNamesBase.add("hell");
        int times = terroristNamesBase.search("hell");
        System.out.println(times);
    }

    /*private static void test100KAddsAnd1millSearches(){
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        try {
            int count = 0;
            List<String> lines = Files.readAllLines(Paths.get("src/main/resources/random_words_list.txt"));
            for (String line : lines) {
                if (line.length() < 10 && line.length() > 1 && !terroristNamesBase.checkIfWordExists(line)) terroristNamesBase.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < 1_000_000; i++) {
            terroristNamesBase.search("acc");
        }
    }*/
}
