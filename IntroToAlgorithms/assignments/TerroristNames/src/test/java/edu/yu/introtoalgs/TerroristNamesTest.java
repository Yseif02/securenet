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
    void getWordsForId() {
    }

    @Test
    void checkIfWordExists() {
    }
    @Test
    void test100KAddsAnd1millSearches(){
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
    void test1MilAdds(){
        TerroristNamesBase terroristNamesBase = new TerroristNames();
        try {
            List<String> lines = Files.readAllLines(Paths.get("src/main/resources/unique_random_words.txt"));
            for (String line : lines) {
                terroristNamesBase.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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