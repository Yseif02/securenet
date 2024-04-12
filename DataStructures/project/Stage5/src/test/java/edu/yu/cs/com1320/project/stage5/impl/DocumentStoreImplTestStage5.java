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
    private FileInput createNewTXTFile(String fileName, String fileTXT) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(destinationFolder, fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        myWriter.write(fileTXT);
        myWriter.close();
        FileInput fileInput = new FileInput(file.getPath());
        this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.TXT);
        return fileInput;
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
        FileInput fileInput = new FileInput(file.getPath());
        this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.BINARY);
        return fileInput;
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
        this.documentStore.put(TXTDoc1.getNewFis(), TXTDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        assertEquals(1, this.documentStore.documentStore.keySet().size());
        assertEquals(this.documentStore.get(TXTDoc1.getUrl()).getKey(), TXTDoc1.getUrl());
    }

    @Test
    void put_multipleDocs() throws IOException {
        FileInput TXTDoc2 = createNewTXTFile("TXTDoc2", "This is the text to Text Document 2.");
        FileInput binaryDoc2 = createNewBinaryFile("binaryDoc2");
        FileInput TXTDoc3 = createNewTXTFile("TXTDoc3", "This is the text to Text Document 3.");
        FileInput binaryDoc3 = createNewBinaryFile("binaryDoc3");
        /*this.documentStore.put(TXTDoc2.getFis(), TXTDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc2.getFis(), binaryDoc2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(TXTDoc3.getFis(), TXTDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc3.getFis(), binaryDoc3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        assertEquals(4, this.documentStore.documentStore.keySet().size());*/
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
       /* this.documentStore.put(TXTDoc7.getFis(), TXTDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc5.getFis(), binaryDoc5.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(TXTDoc8.getFis(), TXTDoc8.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(binaryDoc6.getFis(), binaryDoc6.getUrl(), DocumentStore.DocumentFormat.BINARY);*/
        return binaryDoc6.getUrl();
    }

    @Test
    void put_replaceTXTDoc() throws IOException, NoSuchFieldException, IllegalAccessException {
        URI uriToReplace = addSomeMixedDocs();
        FileInput newTXTDoc = createNewTXTFile("newTXTDoc", "This is the text for a new TXT document that replaces an old document");
//        this.documentStore.put(newTXTDoc.getFis(), uriToReplace, DocumentStore.DocumentFormat.TXT);
        assertEquals(5, this.documentStore.documentStore.keySet().size());
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
//        this.documentStore.put(anotherTXTDoc2.getFis(), anotherTXTDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        long totalMemoryInBytes = (long) reflectField(documentStore, "totalMemoryInBytes");
        assertTrue(150 > totalMemoryInBytes);
    }





    @Test
    void put_withMaxBytesSetAfterPut1() throws IOException, NoSuchFieldException, IllegalAccessException {
        addSomeMixedDocs();
        documentStore.setMaxDocumentBytes(150);
        FileInput anotherTXTDoc1 = createNewTXTFile("anotherTXTDoc1", "More text document. Hopefully this will have enough bytes to delete other docs. 123");
//        this.documentStore.put(anotherTXTDoc1.getFis(), anotherTXTDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
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
//        this.documentStore.put(anotherTXTDoc1.getFis(), anotherTXTDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
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
//        this.documentStore.put(binary1.getFis(), binary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
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
    void undo_setMetaData() throws IOException {
        FileInput undo1 = createNewBinaryFile("undo1");
        this.documentStore.setMetadata(undo1.getUrl(), "key1", "value1");
        HashMap<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        assertEquals(1, this.documentStore.searchByMetadata(testMap).size());
        this.documentStore.undo();
        assertEquals(0, this.documentStore.searchByMetadata(testMap).size());
        this.documentStore.setMetadata(undo1.getUrl(), "key1", "value1");
        assertEquals(1, this.documentStore.searchByMetadata(testMap).size());
        this.documentStore.undo(undo1.getUrl());
        assertEquals(0, this.documentStore.searchByMetadata(testMap).size());
    }

    @Test
    void undo_put() throws IOException {
        //regular undo
        FileInput undoPut1 = createNewBinaryFile("undoPut1");
        assertNotNull(this.documentStore.get(undoPut1.getUrl()));
        this.documentStore.undo();
        assertNull(this.documentStore.get(undoPut1.getUrl()));

        //undo url
        FileInput undoPut2 = createNewBinaryFile("undoPut1");
        assertNotNull(this.documentStore.get(undoPut2.getUrl()));
        this.documentStore.undo(undoPut2.getUrl());
        assertNull(this.documentStore.get(undoPut2.getUrl()));
    }

    @Test
    void undo_delete() throws IOException {
        FileInput undoDeleteTXT1 = createNewTXTFile("undoDeleteTXT1", "This doc has 21 bytes");
        assertNotNull(this.documentStore.get(undoDeleteTXT1.getUrl()));
        this.documentStore.delete(undoDeleteTXT1.getUrl());
        assertNull(this.documentStore.get(undoDeleteTXT1.getUrl()));
        this.documentStore.undo();
        assertNotNull(this.documentStore.get(undoDeleteTXT1.getUrl()));
    }

    @Test
    void undo_deleteAll() throws IOException, NoSuchFieldException, IllegalAccessException {
        FileInput undoDeleteAll1 = createNewTXTFile("undoDeleteAll1", "This doc has 21 bytes");
        FileInput undoDeleteAll2 = createNewTXTFile("undoDeleteAll2", "This document has 26 bytes");
        assertNotNull(this.documentStore.get(undoDeleteAll1.getUrl()));
        assertNotNull(this.documentStore.get(undoDeleteAll2.getUrl()));
        this.documentStore.deleteAll("has");
        long totalBytes = (long) reflectField(documentStore, "totalMemoryInBytes");
        assert (totalBytes == 0);
        assertNull(this.documentStore.get(undoDeleteAll1.getUrl()));
        assertNull(this.documentStore.get(undoDeleteAll2.getUrl()));
        this.documentStore.undo();
        assertNotNull(this.documentStore.get(undoDeleteAll1.getUrl()));
        assertNotNull(this.documentStore.get(undoDeleteAll2.getUrl()));
    }

    @Test
    void undo_deleteAllWithPrefix() throws IOException, NoSuchFieldException, IllegalAccessException {
        FileInput undoDeleteAllWithPrefix1 = createNewTXTFile("undoDeleteAllWithPrefix1", "This doc has 21 bytes");
        FileInput undoDeleteAllWithPrefix2 = createNewTXTFile("undoDeleteAllWithPrefix2", "This document has 26 bytes");
        assertNotNull(this.documentStore.get(undoDeleteAllWithPrefix1.getUrl()));
        assertNotNull(this.documentStore.get(undoDeleteAllWithPrefix2.getUrl()));
        long beforeDelete = (long) reflectField(documentStore, "totalMemoryInBytes");
        assert (beforeDelete == 47);
        this.documentStore.deleteAllWithPrefix("do");
        long afterDelete = (long) reflectField(documentStore, "totalMemoryInBytes");
        assert (afterDelete == 0);
        assertNull(this.documentStore.get(undoDeleteAllWithPrefix1.getUrl()));
        assertNull(this.documentStore.get(undoDeleteAllWithPrefix2.getUrl()));
        this.documentStore.undo();
        assertNotNull(this.documentStore.get(undoDeleteAllWithPrefix1.getUrl()));
        assertNotNull(this.documentStore.get(undoDeleteAllWithPrefix2.getUrl()));
        long afterUndo = (long) reflectField(documentStore, "totalMemoryInBytes");
        assert (afterUndo == 47);

    }

    @Test
    void undo_deleteAllWithMetadata() throws IOException {
        FileInput undoDeleteAllWithMetaDataTXT = createNewTXTFile("undoDeleteAllWithMetaDataTXT", "some text");
        FileInput undoDeleteAllWithMetaDataBinary = createNewBinaryFile("undoDeleteAllWithMetaDataBinary");
        this.documentStore.setMetadata(undoDeleteAllWithMetaDataTXT.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(undoDeleteAllWithMetaDataBinary.getUrl(), "key1", "value1");
        HashMap<String, String> testMap= new HashMap<>();
        testMap.put("key1", "value1");
        assertNotNull(this.documentStore.get(undoDeleteAllWithMetaDataTXT.getUrl()));
        assertNotNull(this.documentStore.get(undoDeleteAllWithMetaDataBinary.getUrl()));
        this.documentStore.deleteAllWithMetadata(testMap);
        assertNull(this.documentStore.get(undoDeleteAllWithMetaDataTXT.getUrl()));
        assertNull(this.documentStore.get(undoDeleteAllWithMetaDataBinary.getUrl()));
        this.documentStore.undo();
        assertNotNull(this.documentStore.get(undoDeleteAllWithMetaDataTXT.getUrl()));
        assertNotNull(this.documentStore.get(undoDeleteAllWithMetaDataBinary.getUrl()));
    }

    @Test
    void undo_deleteAllWithKeywordAndMetadata(){

    }

    @Test
    void undo_deleteAllWithPrefixAndMetadata(){

    }

    @Test
    void undo_urlInStack() throws IOException, NoSuchFieldException, IllegalAccessException {
        FileInput txtForUndoURLInStack1 = createNewTXTFile("txtForUndoURLInStack1", "some text");
        FileInput txtForUndoURLInStack2 = createNewTXTFile("txtForUndoURLInStack2", "some text");
        FileInput binaryForUndoURLInStack = createNewBinaryFile("binaryForUndoURLInStack");
        this.documentStore.deleteAll("some");
        this.documentStore.undo(txtForUndoURLInStack2.getUrl());
        MinHeapImpl<Document> heap = (MinHeapImpl<Document>) reflectField(documentStore, "storage");
        assertEquals(heap.peek(), this.documentStore.get(binaryForUndoURLInStack.getUrl()));

    }

    @Test
    void undo_urlInStackMultipleStacks() throws IOException, NoSuchFieldException, IllegalAccessException {
        FileInput txtForUndoURLInStack1 = createNewTXTFile("txtForUndoURLInStack1", "some text");
        FileInput txtForUndoURLInStack2 = createNewTXTFile("txtForUndoURLInStack2", "some text");
        FileInput txtForUndoURLInStack3 = createNewTXTFile("txtForUndoURLInStack3", "more text");
        FileInput txtForUndoURLInStack4 = createNewTXTFile("txtForUndoURLInStack4", "more text");
        FileInput binaryForUndoURLInStack = createNewBinaryFile("binaryForUndoURLInStack");
        this.documentStore.deleteAll("some");
        this.documentStore.deleteAll("more");
        this.documentStore.undo(txtForUndoURLInStack2.getUrl());
        assertNull(this.documentStore.get(txtForUndoURLInStack1.getUrl()));
        assertNull(this.documentStore.get(txtForUndoURLInStack3.getUrl()));
        assertNull(this.documentStore.get(txtForUndoURLInStack4.getUrl()));
        assertNotNull(this.documentStore.get(txtForUndoURLInStack2.getUrl()));
        MinHeapImpl<Document> heap = (MinHeapImpl<Document>) reflectField(documentStore, "storage");
        assertEquals(heap.peek(), this.documentStore.get(binaryForUndoURLInStack.getUrl()));

    }

    @Test
    void undo_addCommandStackBackWithMemoryLimit() throws IOException {
        FileInput txtForUndoWithMemoryLimit1 = createNewTXTFile("txtForUndoWithMemoryLimit1", "This doc has 21 bytes");
        FileInput txtForUndoWithMemoryLimit2 = createNewTXTFile("txtForUndoWithMemoryLimit2", "This document has 26 bytes");
        this.documentStore.deleteAll("This");
        this.documentStore.setMaxDocumentBytes(25);
        assertThrows(IllegalArgumentException.class,
                () -> this.documentStore.undo());
    }

    @Test
    void undo_addCommandStackBackWithDocumentLimit() throws IOException {
        FileInput binary1 = createNewBinaryFile("binary1");
        FileInput binary2 = createNewBinaryFile("binary2");
        FileInput binary3 = createNewBinaryFile("binary3");
        FileInput binary4 = createNewBinaryFile("binary4");
        FileInput txt1 = createNewTXTFile("txt1", "The");
        FileInput txt2 = createNewTXTFile("txt2", "The");
        FileInput txt3 = createNewTXTFile("txt3", "The");
        FileInput txt4 = createNewTXTFile("txt4", "The");
        this.documentStore.deleteAll("The");
        this.documentStore.setMaxDocumentCount(4);
        this.documentStore.undo();
        assertNull(this.documentStore.get(binary1.getUrl()));
        assertNull(this.documentStore.get(binary2.getUrl()));
        assertNull(this.documentStore.get(binary3.getUrl()));
        assertNull(this.documentStore.get(binary4.getUrl()));
        assertNotNull(this.documentStore.get(txt1.getUrl()));
        assertNotNull(this.documentStore.get(txt2.getUrl()));
        assertNotNull(this.documentStore.get(txt3.getUrl()));
        assertNotNull(this.documentStore.get(txt4.getUrl()));
    }

    @Test
    void testUndo() throws IOException, NoSuchFieldException, IllegalAccessException {
        FileInput textForNanoTimeTest1 = createNewTXTFile("textForNanoTimeTest1", "Here is some text");
        FileInput textForNanoTimeTest2 = createNewTXTFile("textForNanoTimeTest2", "Here is some text");
        FileInput binaryForNanoTime1 = createNewBinaryFile("binaryForNanoTime1");
        FileInput binaryForNanoTime2 = createNewBinaryFile("binaryForNanoTime2");
        FileInput textForNanoTimeTest3 = createNewTXTFile("textForNanoTimeTest3", "Here is some text");
        this.documentStore.setMetadata(binaryForNanoTime1.getUrl(), "key", "value");
        this.documentStore.deleteAll("Here");
        this.documentStore.undo();
        MinHeapImpl<Document> heap = (MinHeapImpl<Document>) reflectField(documentStore, "storage");
        assertEquals(heap.peek(), this.documentStore.get(binaryForNanoTime2.getUrl()));
        assertEquals(heap.peek(), this.documentStore.get(binaryForNanoTime1.getUrl()));
    }

    @Test
    void undo_memoryTest2() throws IOException {
        FileInput TXTForUndo_memoryTest1 = createNewTXTFile("TXTForUndo_memoryTest1", "this doc has 21 bytes");
        FileInput TXTForUndo_memoryTest2 = createNewTXTFile("TXTForUndo_memoryTest2", "this doc has 21 bytes");
        FileInput TXTForUndo_memoryTest3 = createNewTXTFile("TXTForUndo_memoryTest3", "this doc has 21 bytes");
        this.documentStore.deleteAll("this");
        this.documentStore.setMaxDocumentBytes(60);
        this.documentStore.undo();
        assertEquals(2, this.documentStore.documentStore.keySet().size());
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
        /*this.documentStore.put(searchDoc1.getFis(), searchDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc2.getFis(), searchDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary1.getFis(), searchBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc3.getFis(), searchDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc4.getFis(), searchDoc4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary2.getFis(), searchBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc5.getFis(), searchDoc5.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc6.getFis(), searchDoc6.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary3.getFis(), searchBinary3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc7.getFis(), searchDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);*/
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
        /*this.documentStore.put(searchDoc1.getFis(), searchDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc2.getFis(), searchDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary1.getFis(), searchBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc3.getFis(), searchDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc4.getFis(), searchDoc4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary2.getFis(), searchBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc5.getFis(), searchDoc5.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc6.getFis(), searchDoc6.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary3.getFis(), searchBinary3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc7.getFis(), searchDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);*/
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
       /* this.documentStore.put(searchDoc1.getFis(), searchDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc2.getFis(), searchDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary1.getFis(), searchBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc3.getFis(), searchDoc3.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc4.getFis(), searchDoc4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary2.getFis(), searchBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc5.getFis(), searchDoc5.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchDoc6.getFis(), searchDoc6.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchBinary3.getFis(), searchBinary3.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchDoc7.getFis(), searchDoc7.getUrl(), DocumentStore.DocumentFormat.TXT);*/
    }

    @Test
    void deleteAll() throws IOException, NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        FileInput deleteAllDoc1 = createNewTXTFile("deleteAllDoc1", "She decided to learn Spanish to better connect with her relatives in Barcelona.");
//        this.documentStore.put(deleteAllDoc1.getFis(), deleteAllDoc1.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput deleteAllDoc2 = createNewTXTFile("deleteAllDoc2", "The restaurant down the street serves delicious Spanish tapas every Friday night.");
//        this.documentStore.put(deleteAllDoc2.getFis(), deleteAllDoc2.getUrl(), DocumentStore.DocumentFormat.TXT);
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
        //empty keyword
        assertTrue(this.documentStore.deleteAll("").isEmpty());
        //null keyword
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
        /*this.documentStore.put(deletePrefix1.getFis(), deletePrefix1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(deletePrefix2.getFis(), deletePrefix2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(deletePrefix3.getFis(), deletePrefix3.getUrl(), DocumentStore.DocumentFormat.TXT);*/
        FileInput deleteAllPrefixBinaryDoc = createNewBinaryFile("deleteAllPrefixBinaryDoc");
        /*this.documentStore.put(deleteAllPrefixBinaryDoc.getFis(), deleteAllPrefixBinaryDoc.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(deletePrefix4.getFis(), deletePrefix4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(deletePrefix5.getFis(), deletePrefix5.getUrl(), DocumentStore.DocumentFormat.TXT);*/
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
//        this.documentStore.put(searchByMetaDataTXT1.getFis(), searchByMetaDataTXT1.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput searchByMetaDataTXT2 = createNewTXTFile("searchByMetaDataTXT2", "The cascade of colorful leaves danced in the autumn breeze, creating a captivating spectacle.");
//        this.documentStore.put(searchByMetaDataTXT2.getFis(), searchByMetaDataTXT2.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput searchByMetaDataBinary1 = createNewBinaryFile("searchByMetaDataBinary1");
//        this.documentStore.put(searchByMetaDataBinary1.getFis(), searchByMetaDataBinary1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        FileInput searchByMetaDataBinary2 = createNewBinaryFile("searchByMetaDataBinary2");
        //this.documentStore.put(searchByMetaDataBinary2.getFis(), searchByMetaDataBinary2.getUrl(), DocumentStore.DocumentFormat.BINARY);
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
        //empty map
        HashMap<String, String> emptyMap = new HashMap<>();
        List<Document> emptyList = this.documentStore.searchByMetadata(emptyMap);
        assertTrue(emptyList.isEmpty());
        //null map
        assertTrue(this.documentStore.searchByMetadata(null).isEmpty());
    }

    @Test
    void searchByKeywordAndMetadata() throws IOException {
        FileInput searchByKeywordAndMetaDataTXT1 = createNewTXTFile("searchByKeywordTXT1", "how much wood can a woodchuck chuck if a woodchuck could chuck wood?");
        FileInput searchByKeywordAndMetaDataTXT2 = createNewTXTFile("searchByKeywordTXT2", "oogabooga booga can word here there up down");
        FileInput searchByKeywordAndMetaDataBinary = createNewBinaryFile("searchByKeywordAndMetaDataBinary");
        /*this.documentStore.put(searchByKeywordAndMetaDataTXT1.getFis(), searchByKeywordAndMetaDataTXT1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchByKeywordAndMetaDataTXT2.getFis(), searchByKeywordAndMetaDataTXT2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchByKeywordAndMetaDataBinary.getFis(), searchByKeywordAndMetaDataBinary.getUrl(), DocumentStore.DocumentFormat.BINARY);*/
        this.documentStore.setMetadata(searchByKeywordAndMetaDataTXT1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(searchByKeywordAndMetaDataBinary.getUrl(),"key1", "value1");
        HashMap<String, String > testMap = new HashMap<>();
        testMap.put("key1", "value1");
        assertEquals(1, this.documentStore.searchByKeywordAndMetadata("can", testMap).size());
        //empty keyword
        assertTrue(this.documentStore.searchByKeywordAndMetadata("", new HashMap<>()).isEmpty());
        //null keyword
        assertThrows(IllegalArgumentException.class,
                () -> this.documentStore.searchByKeywordAndMetadata(null, new HashMap<>()));
        //empty map
        assertTrue(this.documentStore.searchByKeywordAndMetadata("can", new HashMap<>()).isEmpty());
        //null map
        assertTrue(this.documentStore.searchByKeywordAndMetadata("can", null).isEmpty());
    }

    @Test
    void searchByPrefixAndMetadata() throws IOException {
        FileInput searchPrefixAndMD1 = createNewTXTFile("searchPrefixAndMD1", "The cascade of colorful leaves danced in the autumn breeze, creating a captivating spectacle.");
        FileInput searchPrefixAndMD2 = createNewTXTFile("searchPrefixAndMD2", "The cafe on the corner serves aromatic coffee and delectable pastries, drawing in patrons from all walks of life.");
        FileInput searchPrefixAndMD3 = createNewTXTFile("searchPrefixAndMD3", "The cautious cat cautiously crept closer to the curious mouse, its whiskers twitching with anticipation.");
        FileInput searchPrefixAndMD4 = createNewTXTFile("searchPrefixAndMD4", "The majestic eagle soared high above the rugged mountain peaks, its keen eyes scanning the vast landscape below.");
        FileInput searchPrefixAndMD5 = createNewTXTFile("searchPrefixAndMD5", "With a gentle sigh, she closed her eyes and let the soothing melody of the piano wash over her, transporting her to a realm of tranquility.");
       /* this.documentStore.put(searchPrefixAndMD1.getFis(), searchPrefixAndMD1.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchPrefixAndMD2.getFis(), searchPrefixAndMD2.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchPrefixAndMD3.getFis(), searchPrefixAndMD3.getUrl(), DocumentStore.DocumentFormat.TXT);*/
        FileInput searchPrefixAndBD = createNewBinaryFile("searchPrefixAndBD");
        /*this.documentStore.put(searchPrefixAndBD.getFis(), searchPrefixAndBD.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.put(searchPrefixAndMD4.getFis(), searchPrefixAndMD4.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.put(searchPrefixAndMD5.getFis(), searchPrefixAndMD5.getUrl(), DocumentStore.DocumentFormat.TXT);*/

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
        //empty map
        assertTrue(this.documentStore.searchByPrefixAndMetadata("can", new HashMap<>()).isEmpty());
        //null map
        assertTrue(this.documentStore.searchByPrefixAndMetadata("can", null).isEmpty());
    }

    @Test
    void deleteAllWithMetadata() throws IOException {
        FileInput DAWMDTXT1 = createNewTXTFile("DAWMDTXT1", "Some text");
        //this.documentStore.put(DAWMDTXT1.getFis(), DAWMDTXT1.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput DAWMDTXT2 = createNewTXTFile("DAWMDTXT2", "More text");
//        this.documentStore.put(DAWMDTXT2.getFis(), DAWMDTXT2.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput DAWMDB1 = createNewBinaryFile("DAWMDB1");
//        this.documentStore.put(DAWMDB1.getFis(), DAWMDB1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        FileInput DAWMDB2 = createNewBinaryFile("DAWMDB2");
//        this.documentStore.put(DAWMDB2.getFis(), DAWMDB2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.setMetadata(DAWMDTXT1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWMDB1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWMDTXT2.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWMDB2.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWMDTXT1.getUrl(), "key2", "value2");
        this.documentStore.setMetadata(DAWMDB1.getUrl(), "key2", "value2");
        HashMap<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Set<URI> deleted =  this.documentStore.deleteAllWithMetadata(testMap);
        assertEquals(2, deleted.size());
        assertTrue(deleted.contains(DAWMDTXT1.getUrl()));
        assertTrue(deleted.contains(DAWMDB1.getUrl()));
        //empty map
        assertTrue(this.documentStore.deleteAllWithMetadata(new HashMap<>()).isEmpty());
        //null map
        assertTrue(this.documentStore.deleteAllWithMetadata(null).isEmpty());
    }

    @Test
    void deleteAllWithKeywordAndMetadata() throws IOException {
        FileInput DAWKAMDTXT1 = createNewTXTFile("DAWMDTXT1", "Some text");
        //this.documentStore.put(DAWKAMDTXT1.getFis(), DAWKAMDTXT1.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput DAWKAMDTXT2 = createNewTXTFile("DAWMDTXT2", "More text");
        //this.documentStore.put(DAWKAMDTXT2.getFis(), DAWKAMDTXT2.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput DAWKAMDB1 = createNewBinaryFile("DAWKAMDB1");
        //this.documentStore.put(DAWKAMDB1.getFis(), DAWKAMDB1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        FileInput DAWKAMDB2 = createNewBinaryFile("DAWKAMDB2");
        //this.documentStore.put(DAWKAMDB2.getFis(), DAWKAMDB2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.setMetadata(DAWKAMDTXT1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWKAMDB1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWKAMDTXT2.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWKAMDB2.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWKAMDTXT1.getUrl(), "key2", "value2");
        this.documentStore.setMetadata(DAWKAMDB1.getUrl(), "key2", "value2");
        HashMap<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Set<URI> deleted =  this.documentStore.deleteAllWithKeywordAndMetadata("text", testMap);
        assertEquals(1, deleted.size());
        assertTrue(deleted.contains(DAWKAMDTXT1.getUrl()));
        //empty map
        assertTrue(this.documentStore.deleteAllWithKeywordAndMetadata("text", new HashMap<>()).isEmpty());
        //null map
        assertTrue(this.documentStore.deleteAllWithKeywordAndMetadata("text", null).isEmpty());
        //empty string
        assertTrue(this.documentStore.deleteAllWithKeywordAndMetadata("", testMap).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> this.documentStore.deleteAllWithKeywordAndMetadata(null, testMap));

    }

    @Test
    void deleteAllWithPrefixAndMetadata() throws IOException {
        FileInput DAWPAMDTXT1 = createNewTXTFile("DAWMDTXT1", "Some text");
        //this.documentStore.put(DAWPAMDTXT1.getFis(), DAWPAMDTXT1.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput DAWPAMDTXT2 = createNewTXTFile("DAWMDTXT2", "More text");
        //this.documentStore.put(DAWPAMDTXT2.getFis(), DAWPAMDTXT2.getUrl(), DocumentStore.DocumentFormat.TXT);
        FileInput DAWPAMDB1 = createNewBinaryFile("DAWKAMDB1");
        //this.documentStore.put(DAWPAMDB1.getFis(), DAWPAMDB1.getUrl(), DocumentStore.DocumentFormat.BINARY);
        FileInput DAWPAMDB2 = createNewBinaryFile("DAWKAMDB2");
        //this.documentStore.put(DAWPAMDB2.getFis(), DAWPAMDB2.getUrl(), DocumentStore.DocumentFormat.BINARY);
        this.documentStore.setMetadata(DAWPAMDTXT1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWPAMDB1.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWPAMDTXT2.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWPAMDB2.getUrl(), "key1", "value1");
        this.documentStore.setMetadata(DAWPAMDTXT1.getUrl(), "key2", "value2");
        this.documentStore.setMetadata(DAWPAMDB1.getUrl(), "key2", "value2");
        HashMap<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Set<URI> deleted =  this.documentStore.deleteAllWithPrefixAndMetadata("te", testMap);
        assertEquals(1, deleted.size());
        assertTrue(deleted.contains(DAWPAMDTXT1.getUrl()));
        //empty map
        assertTrue(this.documentStore.deleteAllWithPrefixAndMetadata("te", new HashMap<>()).isEmpty());
        //null map
        assertTrue(this.documentStore.deleteAllWithPrefixAndMetadata("te", null).isEmpty());
        //empty string
        assertTrue(this.documentStore.deleteAllWithPrefixAndMetadata("", testMap).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> this.documentStore.deleteAllWithPrefixAndMetadata(null, testMap));
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
//        this.documentStore.put(binaryFile.getFis(), binaryFile.getUrl(), DocumentStore.DocumentFormat.BINARY);
//        this.documentStore.put(binaryFile2.getFis(), binaryFile2.getUrl(), DocumentStore.DocumentFormat.BINARY);
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

    @Test
    void test_heapProperties() throws IOException, NoSuchFieldException, IllegalAccessException {
        MinHeapImpl<Document> storage = (MinHeapImpl<Document>) reflectField(documentStore, "storage");
        FileInput heapTestTextFile1 = createNewTXTFile("heapTestTextFile1", "Here are some words");
        FileInput heapTestTextFile2 = createNewTXTFile("heapTestTextFile2", "This is the text for heapTestTextFile2");
        FileInput heapTestTextDoc3 = createNewTXTFile("heapTestTextDoc3", "This is the third text doc");
        FileInput heapTestBinaryDoc = createNewBinaryFile("heapTestBinaryDoc");
        Document doc1 = this.documentStore.get(heapTestTextFile1.getUrl());
        Document doc2 = this.documentStore.get(heapTestTextFile2.getUrl());
        Document doc3 = this.documentStore.get(heapTestTextDoc3.getUrl());
        Document doc4binary = this.documentStore.get(heapTestBinaryDoc.getUrl());
        assertEquals(storage.peek(), doc1);
        this.documentStore.setMetadata(doc1.getKey(), "key", "value");

        assertEquals(storage.peek(), doc2);
        this.documentStore.get(doc2.getKey());

        assertEquals(storage.peek(), doc3);
        this.documentStore.search("third");

        assertEquals(storage.peek(), doc4binary);
        this.documentStore.setMetadata(doc4binary.getKey(), "key1", "value1");

        assertEquals(storage.peek(), doc1);
        HashMap<String, String > testMap = new HashMap<>();
        testMap.put("key", "value");
        this.documentStore.searchByKeywordAndMetadata("Here", testMap);

        assertEquals(storage.peek(), doc2);
        this.documentStore.searchByPrefix("fo");

        assertEquals(storage.peek(), doc3);
        this.documentStore.get(doc3.getKey());

        assertEquals(storage.peek(), doc4binary);
        HashMap<String, String > testMap2 = new HashMap<>();
        testMap2.put("key1", "value1");
        this.documentStore.searchByMetadata(testMap2);

        assertEquals(storage.peek(), doc1);
        this.documentStore.searchByPrefixAndMetadata("He", testMap);

        assertEquals(storage.peek(), doc2);
    }
}