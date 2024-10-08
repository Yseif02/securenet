package edu.yu.introtoalgs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerroristNamesTest {

    @Test
    void add() {
    }

    @Test
    void search() {
    }

    @Test
    void test100KAddsAnd1BilSearches(){
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        try {
            int count = 0;
            List<String> lines = Files.readAllLines(Paths.get("src/main/resources/random_words_list.txt"));
            for (String line : lines) {
                //if (line.length() < 10 && line.length() > 1 && !terroristNamesBase.checkIfWordExists(line)) terroristNamesBase.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < 1_000_000_000; i++) {
            terroristNamesBase.search("acc");
        }
    }

    @Test
    void test1MilAddsAnd100MilSearch(){
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        try {
            List<String> lines = Files.readAllLines(Paths.get("src/main/resources/unique_random_words.txt"));
            for (String line : lines) {
                terroristNamesBase.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*for (int i = 0; i < 100_000_000; i++) {
            terroristNamesBase.search(String.valueOf(i));
        }*/

    }

    @Test
    void testAddingStringWithTab() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.add("hell\t0"));
    }

    @Test
    void testAddingStringWithSpace() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.add("hell 0"));
    }

    @Test
    void testAddingStringWithNumber() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        terroristNamesBase.add("hell0");
    }

    @Test
    void testSearchStringWithTab() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.search("hell\t0"));
    }

    @Test
    void testSearchStringWithSpace() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.search("hell 0"));
    }

    @Test
    void testSearchStringWithNumber() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        terroristNamesBase.search("hell0");
    }

    @Test
    void addNullId() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.add(null));
    }

    @Test
    void addEmptyId() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.add(""));
    }

    @Test
    void addWordThatIsAlreadyInBase() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> {
                    terroristNamesBase.add("hello");
                    terroristNamesBase.add("hello");
                });
    }

    @Test
    void searchNullId() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.search(null));
    }

    @Test
    void searchEmptyId() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        assertThrows(IllegalArgumentException.class,
                () -> terroristNamesBase.search(""));
    }


    @Test
    void test1MilAddsAnd1MilSearches(){
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        try {
            int count = 0;
            List<String> lines = Files.readAllLines(Paths.get("src/main/resources/unique_random_words.txt"));
            for (String line : lines) {
                //if (line.length() < 10 && line.length() > 1 && !terroristNamesBase.checkIfWordExists(line)) terroristNamesBase.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testSearch() {
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        List<String> treWords = List.of("gdrctrerx", "diktrezyt", "tregxhu", "tresttbbp", "treodrrr", "trexiwzk", "trejekd", "treyaa",
                "vitrezmx", "zxnptre", "zhnstre", "trehxbo", "vsasiltre", "bvictre", "treybmq", "ysbjitre",
                "lclwystre", "gitvtre", "wtktre", "hstrevnt", "uuatrevwd", "nytreuvg", "iriptresk",
                "fhptre", "treekt", "tredmboqa", "treexajbt", "wowsttre", "tretohn", "treehhahl", "fzytghtre",
                "treljdzxw", "mktregv", "zovutre", "trelfuhx", "trescpge", "treplcppx", "ixbbtre", "yiupmtre",
                "mfqtrewui", "treunu", "degxtre", "zpxtreae", "trezyexvk", "qojhtre", "trelae", "vdbtre",
                "attreeal", "kdotrevac", "rieqtret", "tre", "someTre", "noPattern", "tfrfef", "another");

        int count = 0;
        for (String string : treWords) {
            terroristNamesBase.add(string);
            count++;
        }
        System.out.println(count);
        assertEquals(50, terroristNamesBase.search("tre"));

    }
}