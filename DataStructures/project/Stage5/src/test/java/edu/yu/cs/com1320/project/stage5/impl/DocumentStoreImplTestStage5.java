package edu.yu.cs.com1320.project.stage5.impl;

import edu.yu.cs.com1320.project.FileInput;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreImplTestStage5 {
    DocumentStoreImpl documentStore;
    Random random = new Random();


    @BeforeEach
    void setUp() throws FileNotFoundException {
        this.documentStore = new DocumentStoreImpl();
    }
    private static FileInput createNewTXTFile(String fileName, String fileTXT) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(destinationFolder, fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        myWriter.write(fileTXT);
        myWriter.close();
        return new FileInput(file.getPath());
    }

    private FileInput createNewBinaryFile(String fileName) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(destinationFolder, fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        byte[] data = generateRandomByteArray(random);
        oos.writeObject(data);
        oos.close();
        return new FileInput(file.getPath());
    }

    public static byte[] generateRandomByteArray(Random random) {
        int size = random.nextInt(100) + 1;
        byte[] byteArray = new byte[size];
        for (int i = 0; i < size; i++) {
            byteArray[i] = (byte) (random.nextInt(100) + 1);
        }
        return byteArray;
    }

    private Object reflectField(Object objectToReflect, String field) throws NoSuchFieldException, IllegalAccessException {
        Class<?> classObject = objectToReflect.getClass();
        Field classField = classObject.getDeclaredField(field);
        classField.setAccessible(true);
        return classField.get(documentStore);
    }

    @Test
    void put_binaryDoc() throws IOException {
        FileInput binaryDoc1 = createNewBinaryFile("binaryDoc1");
        this.documentStore.put(binaryDoc1.getFis(), binaryDoc1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        assertEquals(1, this.documentStore.documentStore.keySet().size());
        assertEquals(this.documentStore.get(binaryDoc1.getUrl()).getKey(), binaryDoc1.getUrl());
    }

    @Test
    void put_TXTDoc() throws IOException {
        FileInput TXTDoc1 = createNewTXTFile("binaryDoc1", "This is the text to Text Document 1.");
        this.documentStore.put(TXTDoc1.getFis(), TXTDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        assertEquals(1, this.documentStore.documentStore.keySet().size());
        assertEquals(this.documentStore.get(TXTDoc1.getUrl()).getKey(), TXTDoc1.getUrl());
    }

    @Test
    void put_multipleDocs() throws IOException {
        FileInput TXTDoc2 = createNewTXTFile("TXTDoc2", "This is the text to Text Document 2.");
        FileInput binaryDoc2 = createNewBinaryFile("binaryDoc2");
        FileInput TXTDoc3 = createNewTXTFile("TXTDoc3", "This is the text to Text Document 3.");
        FileInput binaryDoc3 = createNewBinaryFile("binaryDoc3");
        this.documentStore.put(TXTDoc2.getFis(), TXTDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc2.getFis(), binaryDoc2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(TXTDoc3.getFis(), TXTDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc3.getFis(), binaryDoc3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        assertEquals(4, this.documentStore.documentStore.keySet().size());
        assertEquals(this.documentStore.get(TXTDoc2.getUrl()).getKey(), TXTDoc2.getUrl());
        assertEquals(this.documentStore.get(TXTDoc3.getUrl()).getKey(), TXTDoc3.getUrl());
        assertEquals(this.documentStore.get(binaryDoc2.getUrl()).getKey(), binaryDoc2.getUrl());
        assertEquals(this.documentStore.get(binaryDoc3.getUrl()).getKey(), binaryDoc3.getUrl());
    }

    URI addSomeMixedDocs() throws IOException {
        FileInput TXTDoc7 = createNewTXTFile("TXTDoc7", "This is the text to Text Document 7. Zebra");
        FileInput binaryDoc5 = createNewBinaryFile("binaryDoc5");
        FileInput TXTDoc8 = createNewTXTFile("TXTDoc8", "This is the text to Text Document 8.");
        FileInput binaryDoc6 = createNewBinaryFile("binaryDoc6");
        this.documentStore.put(TXTDoc7.getFis(), TXTDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc5.getFis(), binaryDoc5.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(TXTDoc8.getFis(), TXTDoc8.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc6.getFis(), binaryDoc6.getUrl(), DocumentStore.DocumentFormat.BINARY);
        return binaryDoc6.getUrl();
    }

    @Test
    void put_replaceTXTDoc() throws IOException, NoSuchFieldException, IllegalAccessException {
        URI uriToReplace = addSomeMixedDocs();
        FileInput newTXTDoc = createNewTXTFile("newTXTDoc", "This is the text for a new TXT document that replaces an old document");
        this.documentStore.put(newTXTDoc.getFis(), uriToReplace, DocumentStore.DocumentFormat.TXT);
        assertEquals(4, this.documentStore.documentStore.keySet().size());
        Class<?> docStore = documentStore.getClass();
        Field totalMemoryInBytesField = docStore.getDeclaredField("totalMemoryInBytes");
        totalMemoryInBytesField.setAccessible(true);
        long totalMemoryInBytes = (long) totalMemoryInBytesField.get(documentStore);
    }

    @Test
    void put_withMaxBytesSetBeforePut() throws IOException, NoSuchFieldException, IllegalAccessException {
        documentStore.setMaxDocumentBytes(150);
        addSomeMixedDocs();
        FileInput anotherTXTDoc2 = createNewTXTFile("anotherTXTDoc2", "More text document. Hopefully this will have enough bytes to delete other docs. 123");
        this.documentStore.put(anotherTXTDoc2.getFis(), anotherTXTDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        long totalMemoryInBytes = (long) reflectField(documentStore, "totalMemoryInBytes");
        assertTrue(150 > totalMemoryInBytes);
    }



    @Test
    void put_withMaxBytesSetAfterPut1() throws IOException, NoSuchFieldException, IllegalAccessException {
        addSomeMixedDocs();
        documentStore.setMaxDocumentBytes(150);
        FileInput anotherTXTDoc1 = createNewTXTFile("anotherTXTDoc1", "More text document. Hopefully this will have enough bytes to delete other docs. 123");
        this.documentStore.put(anotherTXTDoc1.getFis(), anotherTXTDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        Class<?> docStore = documentStore.getClass();
        Field totalMemoryInBytesField = docStore.getDeclaredField("totalMemoryInBytes");
        totalMemoryInBytesField.setAccessible(true);
        long totalMemoryInBytes = (long) totalMemoryInBytesField.get(documentStore);
        assertTrue(150 > totalMemoryInBytes);
    }

    @Test
    void put_withMaxBytesSetAfterPut2() throws IOException {
        addSomeMixedDocs();
        FileInput anotherTXTDoc1 = createNewTXTFile("anotherTXTDoc1", "More text document. Hopefully this will have enough bytes to delete other docs. 123");
        this.documentStore.put(anotherTXTDoc1.getFis(), anotherTXTDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        documentStore.setMaxDocumentBytes(150);
    }


    @Test
    void get_TXTGoodURI() throws IOException {
        URI uri = addSomeMixedDocs();
        assertNotNull(this.documentStore.get(uri));
        assertEquals(uri, this.documentStore.get(uri).getKey());

    }

    @Test
    void get_BinaryGoodURI() throws IOException {
        URI uri = addSomeMixedDocs();
        FileInput binary1 = createNewBinaryFile("binary1");
        this.documentStore.put(binary1.getFis(), binary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        assertNotNull(this.documentStore.get(binary1.getUrl()));
        assertEquals(this.documentStore.get(binary1.getUrl()).getKey(), binary1.getUrl());
    }

    @Test
    void setMetadata() {
    }

    @Test
    void getMetadata() {
    }

    @Test
    void delete() {
    }

    @Test
    void undo() {
    }

    @Test
    void testUndo() {
    }

    @Test
    void search() throws IOException {
        FileInput searchDoc1 = createNewTXTFile("searchDoc1", "The crimson sun dipped hArSHlEy below the horizon, painting the sky with hues Of orange and of pink.");
        FileInput searchDoc2 = createNewTXTFile("searchDoc2", "With a flicker of hesitation, she pressed the send button, releasing her heartfelt message into the digital ether.");
        FileInput searchBinary1 = createNewBinaryFile("searchBinary1");
        FileInput searchDoc3 = createNewTXTFile("searchDoc3", "The ancient oak tree stood sentinel in the midst of the tranquil forest, its gnarled branches reaching towards the heavens.");
        FileInput searchDoc4 = createNewTXTFile("searchDoc4", "The aroma of freshly baked bread wafted through the air by the shore, enticing passersby with its irresistible allure.");
        FileInput searchBinary2 = createNewBinaryFile("searchBinary2");
        FileInput searchDoc5 = createNewTXTFile("searchDoc5", "As the waves crashed hArSHlEy against the rocky shore, seagulls soared effortlessly in the salty breeze.");
        FileInput searchDoc6 = createNewTXTFile("searchDoc6", "in the dimly lit alley, shadows danced to the rhythm of the flickering streetlamp.");
        FileInput searchBinary3 = createNewBinaryFile("searchBinary3");
        FileInput searchDoc7 = createNewTXTFile("searchDoc7", "Jack's dog's tail wagged excitedly as it eagerly awaited its" +
                " owner's return, while Sarah's cat's playful antics amused everyone in the room, making it a lively gathering despite the storm's relentless pounding against the windows.");
        this.documentStore.put(searchDoc1.getFis(), searchDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc2.getFis(), searchDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary1.getFis(), searchBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc3.getFis(), searchDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc4.getFis(), searchDoc4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary2.getFis(), searchBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc5.getFis(), searchDoc5.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc6.getFis(), searchDoc6.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary3.getFis(), searchBinary3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc7.getFis(), searchDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);
        //Search for uppercase at start of sentence
        List<Document> searchThe = this.documentStore.search("The");
        assertEquals(3, searchThe.size());
        assertTrue(searchThe.contains(this.documentStore.get(searchDoc1.getUrl())));
        assertTrue(searchThe.contains(this.documentStore.get(searchDoc3.getUrl())));
        assertTrue(searchThe.contains(this.documentStore.get(searchDoc4.getUrl())));
        assertFalse(searchThe.contains(this.documentStore.get(searchDoc2.getUrl())));
        assertFalse(searchThe.contains(this.documentStore.get(searchDoc5.getUrl())));
        assertFalse(searchThe.contains(this.documentStore.get(searchDoc6.getUrl())));
        assertFalse(searchThe.contains(this.documentStore.get(searchBinary1.getUrl())));
        assertFalse(searchThe.contains(this.documentStore.get(searchBinary2.getUrl())));
        assertFalse(searchThe.contains(this.documentStore.get(searchBinary3.getUrl())));
        //search for lowercase at start of sentence
        assertEquals(4, this.documentStore.search("in").size());
        //Search for words with mixed upper and lowercase
        List<Document> search_hArSHlEy = this.documentStore.search("hArSHlEy");
        assertEquals(2, search_hArSHlEy.size());
        //Search for words next to a comma
        List<Document> search_shore = this.documentStore.search("shore");
        assertEquals(2, search_shore.size());
        //Search for uppercase in middle of sentence
        List<Document> search_Of = this.documentStore.search("Of");
        assertEquals(1, search_Of.size());
        //search for words with apostrophes
        assertEquals(1, this.documentStore.search("Jack's").size());
        assertEquals(1, this.documentStore.search("dog's").size());
        assertEquals(1, this.documentStore.search("owner's").size());
        assertEquals(1, this.documentStore.search("Sarah's").size());
        assertEquals(1, this.documentStore.search("cat's").size());
        assertEquals(1, this.documentStore.search("storm's").size());

        //empty string
        assertEquals(0, this.documentStore.search("").size());
        //string with 1 special char
        assertEquals(0, this.documentStore.search("'").size());
        assertThrows(IllegalArgumentException.class, ()
                -> this.documentStore.search(null));
    }

    @Test
    void searchByPrefix() throws IOException {
        FileInput searchDoc1 = createNewTXTFile("searchDoc1", "The crimson sun dipped hArSHlEy below the horizon, painting the sky with its' hues Of orange and of pink.");
        FileInput searchDoc2 = createNewTXTFile("searchDoc2", "With a flicker of hesitation, she pressed the send button, releasing her heartfelt message into the digital ether.");
        FileInput searchBinary1 = createNewBinaryFile("searchBinary1");
        FileInput searchDoc3 = createNewTXTFile("searchDoc3", "The ancient oak tree stood sentinel in the midst of the tranquil forest, its gnarled branches reaching towards the heavens.");
        FileInput searchDoc4 = createNewTXTFile("searchDoc4", "The aroma of freshly baked bread wafted through the air by the shore, enticing passersby with its irresistible allure.");
        FileInput searchBinary2 = createNewBinaryFile("searchBinary2");
        FileInput searchDoc5 = createNewTXTFile("searchDoc5", "As the waves crashed hArSHlEy against the rocky shore, seagulls soared effortlessly in the salty breeze.");
        FileInput searchDoc6 = createNewTXTFile("searchDoc6", "in the dimly lit alley, shadows danced to the rhythm of the flickering streetlamp.");
        FileInput searchBinary3 = createNewBinaryFile("searchBinary3");
        FileInput searchDoc7 = createNewTXTFile("searchDoc7", "Jack's dog's tail wagged excitedly as it eagerly awaited its" +
                " owner's return, while Sarah's cat's playful antics amused everyone in the room, making it a lively gathering despite the storm's relentless pounding against the windows.");
        this.documentStore.put(searchDoc1.getFis(), searchDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc2.getFis(), searchDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary1.getFis(), searchBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc3.getFis(), searchDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc4.getFis(), searchDoc4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary2.getFis(), searchBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc5.getFis(), searchDoc5.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc6.getFis(), searchDoc6.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary3.getFis(), searchBinary3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc7.getFis(), searchDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);
        //search for "in" (doc 2 has into, 3,5,6,7 all have in)
        assertEquals(5, this.documentStore.searchByPrefix("in").size());
        assertEquals(7, this.documentStore.searchByPrefix("th").size());
        assertEquals(3, this.documentStore.searchByPrefix("Th").size());
        assertEquals(4, this.documentStore.searchByPrefix("it").size());
        //empty string
        assertEquals(0, this.documentStore.searchByPrefix("").size());
        //string with 1 special char
        assertEquals(0, this.documentStore.searchByPrefix("'").size());
        assertThrows(IllegalArgumentException.class, ()
        -> this.documentStore.searchByPrefix(null));
    }

    void addLotsOfDocs() throws IOException {
        FileInput searchDoc1 = createNewTXTFile("searchDoc1", "The crimson sun dipped hArSHlEy below the horizon, painting the sky with its' hues Of orange and of pink.");
        FileInput searchDoc2 = createNewTXTFile("searchDoc2", "With a flicker of hesitation, she pressed the send button, releasing her heartfelt message into the digital ether.");
        FileInput searchBinary1 = createNewBinaryFile("searchBinary1");
        FileInput searchDoc3 = createNewTXTFile("searchDoc3", "The ancient oak tree stood sentinel in the midst of the tranquil forest, its gnarled branches reaching towards the heavens.");
        FileInput searchDoc4 = createNewTXTFile("searchDoc4", "The aroma of freshly baked bread wafted through the air by the shore, enticing passersby with its irresistible allure.");
        FileInput searchBinary2 = createNewBinaryFile("searchBinary2");
        FileInput searchDoc5 = createNewTXTFile("searchDoc5", "As the waves crashed hArSHlEy against the rocky shore, seagulls soared effortlessly in the salty breeze.");
        FileInput searchDoc6 = createNewTXTFile("searchDoc6", "in the dimly lit alley, shadows danced to the rhythm of the flickering streetlamp.");
        FileInput searchBinary3 = createNewBinaryFile("searchBinary3");
        FileInput searchDoc7 = createNewTXTFile("searchDoc7", "Jack's dog's tail wagged excitedly as it eagerly awaited its" +
                " owner's return, while Sarah's cat's playful antics amused everyone in the room, making it a lively gathering despite the storm's relentless pounding against the windows.");
        this.documentStore.put(searchDoc1.getFis(), searchDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc2.getFis(), searchDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary1.getFis(), searchBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc3.getFis(), searchDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc4.getFis(), searchDoc4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary2.getFis(), searchBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc5.getFis(), searchDoc5.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc6.getFis(), searchDoc6.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary3.getFis(), searchBinary3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc7.getFis(), searchDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);
    }

    @Test
    void deleteAll() throws IOException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        FileInput deleteAllDoc1 = createNewTXTFile("deleteAllDoc1", "She decided to learn Spanish to better connect with her relatives in Barcelona.");
        this.documentStore.put(deleteAllDoc1.getFis(), deleteAllDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput deleteAllDoc2 = createNewTXTFile("deleteAllDoc2", "The restaurant down the street serves delicious Spanish tapas every Friday night.");
        this.documentStore.put(deleteAllDoc2.getFis(), deleteAllDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        assertEquals(2, this.documentStore.deleteAll("Spanish").size());
        TrieImpl<Document> trie = (TrieImpl<Document>) reflectField(documentStore, "documentWordsTrie");
        assertTrue(trie.get("She").isEmpty());
        assertTrue(trie.get("decided").isEmpty());
        assertTrue(trie.get("to").isEmpty());
        assertTrue(trie.get("learn").isEmpty());
        assertTrue(trie.get("Spanish").isEmpty());
        assertTrue(trie.get("better").isEmpty());
        assertTrue(trie.get("connect").isEmpty());
        assertTrue(trie.get("with").isEmpty());
        assertTrue(trie.get("her").isEmpty());
        assertTrue(trie.get("relatives").isEmpty());
        assertTrue(trie.get("in").isEmpty());
        assertTrue(trie.get("Barcelona.").isEmpty());
        assertTrue(trie.get("The").isEmpty());
        assertTrue(trie.get("restaurant").isEmpty());
        assertTrue(trie.get("down").isEmpty());
        assertTrue(trie.get("the").isEmpty());
        assertTrue(trie.get("street").isEmpty());
        assertTrue(trie.get("serves").isEmpty());
        assertTrue(trie.get("delicious").isEmpty());
        assertTrue(trie.get("Spanish").isEmpty());
        assertTrue(trie.get("tapas").isEmpty());
        assertTrue(trie.get("").isEmpty());
        assertTrue(trie.get("every").isEmpty());
        assertTrue(trie.get("Friday").isEmpty());
        assertTrue(trie.get("night.").isEmpty());
        assertTrue(this.documentStore.deleteAll("").isEmpty());
        assertThrows(IllegalArgumentException.class, ()
                -> this.documentStore.deleteAll(null));
    }

    @Test
    void deleteAllWithPrefix() throws IOException {
        FileInput deletePrefix1 = createNewTXTFile("deletePrefix1", "The cascade of colorful leaves danced in the autumn breeze, creating a captivating spectacle.");
        FileInput deletePrefix2 = createNewTXTFile("deletePrefix2", "The cafe on the corner serves aromatic coffee and delectable pastries, drawing in patrons from all walks of life.");
        FileInput deletePrefix3 = createNewTXTFile("deletePrefix3", "The cautious cat cautiously crept closer to the curious mouse, its whiskers twitching with anticipation.");
        FileInput deletePrefix4 = createNewTXTFile("deletePrefix4", "The majestic eagle soared high above the rugged mountain peaks, its keen eyes scanning the vast landscape below.");
        FileInput deletePrefix5 = createNewTXTFile("deletePrefix5", "With a gentle sigh, she closed her eyes and let the soothing melody of the piano wash over her, transporting her to a realm of tranquility.");
        this.documentStore.put(deletePrefix1.getFis(), deletePrefix1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(deletePrefix2.getFis(), deletePrefix2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(deletePrefix3.getFis(), deletePrefix3.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput deleteAllPrefixBinaryDoc = createNewBinaryFile("deleteAllPrefixBinaryDoc");
        this.documentStore.put(deleteAllPrefixBinaryDoc.getFis(), deleteAllPrefixBinaryDoc.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(deletePrefix4.getFis(), deletePrefix4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(deletePrefix5.getFis(), deletePrefix5.getUrl(), DocumentStore.DocumentFormat.TXT);
        Set<URI> documentSet = this.documentStore.deleteAllWithPrefix("ca");
        assertEquals(3, documentSet.size());
        assertFalse(documentSet.contains(deletePrefix4.getUrl()));
        assertFalse(documentSet.contains(deletePrefix5.getUrl()));
        assertFalse(documentSet.contains(deleteAllPrefixBinaryDoc.getUrl()));
        assertTrue(documentSet.contains(deletePrefix1.getUrl()));
        assertTrue(documentSet.contains(deletePrefix2.getUrl()));
        assertTrue(documentSet.contains(deletePrefix3.getUrl()));
    }

    @Test
    void searchByMetadata() throws IOException {
        FileInput searchByMetaDataTXT1 = createNewTXTFile("searchByMetaDataTXT1", "Here is some text");
        this.documentStore.put(searchByMetaDataTXT1.getFis(), searchByMetaDataTXT1.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput searchByMetaDataTXT2 = createNewTXTFile("searchByMetaDataTXT2", "The cascade of colorful leaves danced in the autumn breeze, creating a captivating spectacle.");
        this.documentStore.put(searchByMetaDataTXT2.getFis(), searchByMetaDataTXT2.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput searchByMetaDataBinary1 = createNewBinaryFile("searchByMetaDataBinary1");
        this.documentStore.put(searchByMetaDataBinary1.getFis(), searchByMetaDataBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        FileInput searchByMetaDataBinary2 = createNewBinaryFile("searchByMetaDataBinary2");
        this.documentStore.put(searchByMetaDataBinary2.getFis(), searchByMetaDataBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.setMetadata(searchByMetaDataTXT1.getUrl(), "key1","value1");
        this.documentStore.setMetadata(searchByMetaDataTXT1.getUrl(), "key2","value2");
        this.documentStore.setMetadata(searchByMetaDataBinary1.getUrl(), "key1","value1");
        this.documentStore.setMetadata(searchByMetaDataBinary1.getUrl(), "key2","value2");
        this.documentStore.setMetadata(searchByMetaDataTXT2.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(searchByMetaDataBinary2.getUrl(), "key2", "value2");
        HashMap<String, String > testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        List<Document> documentList = this.documentStore.searchByMetadata(testMap);
        assertEquals(2, documentList.size());
        assertTrue(documentList.contains(this.documentStore.get(searchByMetaDataTXT1.getUrl())));
        assertTrue(documentList.contains(this.documentStore.get(searchByMetaDataBinary1.getUrl())));
        assertFalse(documentList.contains(this.documentStore.get(searchByMetaDataTXT2.getUrl())));
        assertFalse(documentList.contains(this.documentStore.get(searchByMetaDataBinary2.getUrl())));
        assertTrue(this.documentStore.deleteAllWithPrefix("").isEmpty());
        assertThrows(IllegalArgumentException.class, ()
                -> this.documentStore.deleteAllWithPrefix(null));
    }

    @Test
    void searchByKeywordAndMetadata() throws IOException {
        FileInput searchByKeywordAndMetaDataTXT1 = createNewTXTFile("searchByKeywordTXT1", "how much wood can a woodchuck chuck if a woodchuck could chuck wood?");
        FileInput searchByKeywordAndMetaDataTXT2 = createNewTXTFile("searchByKeywordTXT2", "oogabooga booga can word here there up down");
        FileInput searchByKeywordAndMetaDataBinary = createNewBinaryFile("searchByKeywordAndMetaDataBinary");
        this.documentStore.put(searchByKeywordAndMetaDataTXT1.getFis(), searchByKeywordAndMetaDataTXT1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchByKeywordAndMetaDataTXT2.getFis(), searchByKeywordAndMetaDataTXT2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchByKeywordAndMetaDataBinary.getFis(), searchByKeywordAndMetaDataBinary.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.setMetadata(searchByKeywordAndMetaDataTXT1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(searchByKeywordAndMetaDataBinary.getUrl(),"key1", "value1");
        HashMap<String, String > testMap = new HashMap<>();
        testMap.put("key1", "value1");
        assertEquals(1, this.documentStore.searchByKeywordAndMetadata("can", testMap).size());
        assertTrue(this.documentStore.searchByKeywordAndMetadata("", new HashMap<>()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> this.documentStore.searchByKeywordAndMetadata(null, new HashMap<>()));
    }

    @Test
    void searchByPrefixAndMetadata() throws IOException {
        FileInput searchPrefixAndMD1 = createNewTXTFile("searchPrefixAndMD1", "The cascade of colorful leaves danced in the autumn breeze, creating a captivating spectacle.");
        FileInput searchPrefixAndMD2 = createNewTXTFile("searchPrefixAndMD2", "The cafe on the corner serves aromatic coffee and delectable pastries, drawing in patrons from all walks of life.");
        FileInput searchPrefixAndMD3 = createNewTXTFile("searchPrefixAndMD3", "The cautious cat cautiously crept closer to the curious mouse, its whiskers twitching with anticipation.");
        FileInput searchPrefixAndMD4 = createNewTXTFile("searchPrefixAndMD4", "The majestic eagle soared high above the rugged mountain peaks, its keen eyes scanning the vast landscape below.");
        FileInput searchPrefixAndMD5 = createNewTXTFile("searchPrefixAndMD5", "With a gentle sigh, she closed her eyes and let the soothing melody of the piano wash over her, transporting her to a realm of tranquility.");
        this.documentStore.put(searchPrefixAndMD1.getFis(), searchPrefixAndMD1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchPrefixAndMD2.getFis(), searchPrefixAndMD2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchPrefixAndMD3.getFis(), searchPrefixAndMD3.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput searchPrefixAndBD = createNewBinaryFile("searchPrefixAndBD");
        this.documentStore.put(searchPrefixAndBD.getFis(), searchPrefixAndBD.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchPrefixAndMD4.getFis(), searchPrefixAndMD4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchPrefixAndMD5.getFis(), searchPrefixAndMD5.getUrl(), DocumentStore.DocumentFormat.TXT);

        HashMap<String, String > testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");

        this.documentStore.setMetadata(searchPrefixAndMD1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(searchPrefixAndMD1.getUrl(), "key2", "value2");

        this.documentStore.setMetadata(searchPrefixAndMD2.getUrl(), "key1", "value1");

        this.documentStore.setMetadata(searchPrefixAndMD5.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(searchPrefixAndMD5.getUrl(), "key2", "value2");

        this.documentStore.setMetadata(searchPrefixAndBD.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(searchPrefixAndBD.getUrl(), "key2", "value2");

        assertEquals(1, this.documentStore.searchByPrefixAndMetadata("ca", testMap).size());
        assertTrue(this.documentStore.searchByPrefixAndMetadata("", new HashMap<>()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> this.documentStore.searchByKeywordAndMetadata(null, new HashMap<>()));
    }

    @Test
    void deleteAllWithMetadata() {
    }

    @Test
    void deleteAllWithKeywordAndMetadata() {
    }

    @Test
    void deleteAllWithPrefixAndMetadata() {
    }

    @Test
    void setMaxDocumentCount() throws NoSuchFieldException, IllegalAccessException {
        this.documentStore.setMaxDocumentCount(50);
        int documentCount = (int) reflectField(documentStore, "maxDocs");
        assertEquals(documentCount, 50);
    }

    @Test
    void setMaxDocumentCount_setLimitWhileOverLimit() throws IOException, NoSuchFieldException, IllegalAccessException {
        FileInput binaryFile = createNewBinaryFile("binaryFileForMaxDocCount");
        FileInput binaryFile2 = createNewBinaryFile("binaryFileForMaxDocCount2");
        this.documentStore.put(binaryFile.getFis(), binaryFile.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(binaryFile2.getFis(), binaryFile2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        addSomeMixedDocs();
        this.documentStore.setMaxDocumentCount(5);
        assertEquals(5, this.documentStore.documentStore.keySet().size());
        MinHeapImpl<Document> minHeap = (MinHeapImpl<Document>) reflectField(documentStore, "storage");
        assertNull(this.documentStore.get(binaryFile.getUrl()));
        assertEquals(binaryFile2.getUrl(), minHeap.peek().getKey());
    }

    @Test
    void setMaxDocumentCount_badInputNegativeInt() {
        assertThrows(IllegalArgumentException.class, ()
                -> this.documentStore.setMaxDocumentCount(-1));
    }

    @Test
    void setMaxDocumentBytes() throws NoSuchFieldException, IllegalAccessException {
        this.documentStore.setMaxDocumentBytes(50);
        long memoryInBytes = (long) reflectField(documentStore, "maxMemoryInBytes");
        assertEquals(memoryInBytes, 50);
    }

    @Test
    void setMaxDocumentBytes_negativeInt() {
        assertThrows(IllegalArgumentException.class, ()
                -> this.documentStore.setMaxDocumentBytes(-1));
    }
}