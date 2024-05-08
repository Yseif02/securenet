package edu.yu.cs.com1320.project.stage6.impl;

import edu.yu.cs.com1320.project.FileInput;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Random;

class DocumentStoreImplTest {
    private File destinationDirectory;
    private DocumentStoreImpl documentStore;
    Random random = new Random();

    @BeforeEach
    void setUp() {
        this.destinationDirectory = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage6\\src\\main\\resources");
        assert destinationDirectory.isDirectory();
        this.documentStore = new DocumentStoreImpl(destinationDirectory);
    }

    @Test
    void testSerialize() throws IOException {
        documentStore.setMaxDocumentCount(1);
        /*FileInput file1 = createNewTXTFile("file1", "This is the text to file1");
        documentStore.setMetadata(file1.getUrl(), "key1", "value1");
        documentStore.setMetadata(file1.getUrl(), "key2", "value2");
        documentStore.setMetadata(file1.getUrl(), "key3", "value3");
        FileInput file2 = createNewBinaryFile("file2");*/
        String file1Name = "http://www.yu.edu/docStoreTest/doc1";
        URI uri1 = URI.create(file1Name);
        String text = "this is some text";
        //Document document1 = new DocumentImpl(uri1, text, null);
        InputStream inputStream = new StringBufferInputStream(text);
        documentStore.put(inputStream, uri1, DocumentStore.DocumentFormat.TXT);
        documentStore.setMetadata(uri1, "key1", "value1");
        documentStore.setMetadata(uri1, "key2", "value2");
        documentStore.setMetadata(uri1, "key3", "value3");


        String file2Name = "http://www.yu.edu/docStoreTest/doc2";
        URI uri2 = URI.create(file2Name);
        String text2 = "this is some text for doc 2";
        //Document document1 = new DocumentImpl(uri1, text, null);
        InputStream inputStream2 = new StringBufferInputStream(text2);
        documentStore.put(inputStream2, uri2, DocumentStore.DocumentFormat.TXT);
        System.out.println();
    }

    @Test
    void setMetadata() {
    }

    @Test
    void getMetadata() {
    }

    @Test
    void put() {
    }

    @Test
    void get() {
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

    private FileInput createNewTXTFile(String fileName, String fileTXT) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(fileName);
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
}