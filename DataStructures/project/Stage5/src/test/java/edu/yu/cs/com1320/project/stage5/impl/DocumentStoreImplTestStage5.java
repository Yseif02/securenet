package edu.yu.cs.com1320.project.stage5.impl;

import edu.yu.cs.com1320.project.FileInput;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Random;

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
        assertTrue(150 > totalMemoryInBytes);
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
    void get_TXTGoodURI() {

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
    void search() {
    }

    @Test
    void searchByPrefix() {
    }

    @Test
    void deleteAll() {
    }

    @Test
    void deleteAllWithPrefix() {
    }

    @Test
    void searchByMetadata() {
    }

    @Test
    void searchByKeywordAndMetadata() {
    }

    @Test
    void searchByPrefixAndMetadata() {
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
    void setMaxDocumentCount() {
    }

    @Test
    void setMaxDocumentBytes() {
    }
}