package edu.yu.cs.com1320.project.stage6.impl;

import edu.yu.cs.com1320.project.FileInput;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreImplTest {
    private File destinationDirectory;
    private DocumentStoreImpl documentStore;
    Random random = new Random();
    DocumentCreator documentCreator;

    @BeforeEach
    void setUp() {
        this.destinationDirectory = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage6\\src\\main\\resources");
        assert destinationDirectory.isDirectory();
        this.documentStore = new DocumentStoreImpl(destinationDirectory);
        this.documentCreator = new DocumentCreator();
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

        String binaryFile1Name = "http://www.yu.edu/docStoreTest/binary1";
        URI binary1URI = URI.create(binaryFile1Name);
        byte[] binary1bytes = generateRandomByteArray(random);
        InputStream binaryInputStream = new ByteArrayInputStream(binary1bytes);
        documentStore.put(binaryInputStream, binary1URI, DocumentStore.DocumentFormat.BINARY);

        String file2Name = "http://www.yu.edu/docStoreTest/doc2";
        URI uri2 = URI.create(file2Name);
        String text2 = "this is some text for doc 2";
        //Document document1 = new DocumentImpl(uri1, text, null);
        InputStream inputStream2 = new StringBufferInputStream(text2);
        documentStore.put(inputStream2, uri2, DocumentStore.DocumentFormat.TXT);
        //documentStore.setMaxDocumentCount(10);
        documentStore.setMetadata(uri1, "key1", "newValue");
        documentStore.setMetadata(binary1URI, "key1", "value1");
        System.out.println();
    }

    @Test
    void setMetadataInMemory() throws IOException {
        Document document = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testDoc1");
        documentStore.setMetadata(document.getKey(), "key1", "value1");
        assertEquals("value1", this.documentStore.getMetadata(document.getKey(), "key1"));
    }

    @Test
    void setMetadataOnDisk() throws IOException {
        Document SMDOD1TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SMDOD1TXT");
        Document SMDOD2Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "SMDOD2Binary");
        Document SMDOD3TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SMDOD3TXT");
        Document SMDOD4Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "SMDOD4Binary");
        documentStore.setMaxDocumentCount(2);
        documentStore.setMetadata(SMDOD1TXT.getKey(), "key1", "value1");
        documentStore.setMetadata(SMDOD2Binary.getKey(), "key1", "value1");
        assertEquals("value1", this.documentStore.getMetadata(SMDOD1TXT.getKey(), "key1"));
        assertEquals("value1", this.documentStore.getMetadata(SMDOD2Binary.getKey(), "key1"));
    }


    @Test
    void getMetadata() throws IOException {
        //in memory
        Document gmd1TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "gmd1TXT");
        Document gmd2Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "gmd2Binary");
        Document gmd3TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "gmd3TXT");
        Document gmd4Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "gmd4Binary");
        documentStore.setMetadata(gmd1TXT.getKey(), "key1", "value1");
        assertEquals("value1", documentStore.getMetadata(gmd1TXT.getKey(), "key1"));

        documentStore.setMetadata(gmd2Binary.getKey(), "key1", "value1");
        documentStore.setMetadata(gmd3TXT.getKey(), "key1", "value1");
        documentStore.setMetadata(gmd4Binary.getKey(), "key1", "value1");
        documentStore.setMaxDocumentCount(1);
        //in memory and on disk
        assertEquals("value1", documentStore.getMetadata(gmd4Binary.getKey(), "key1"));
        assertEquals("value1", documentStore.getMetadata(gmd1TXT.getKey(), "key1"));
    }

    @Test
    void undoSetMetadata(){

    }

    @Test
    void testBTreeSplitting() throws IOException {
        Document testBTreeBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary1");
        Document testBTreeBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary2");
        Document testBTreeTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT1");
        Document testBTreeBinary3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary3");
        Document testBTreeTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT2");
        Document testBTreeBinary4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary4");
        Document testBTreeTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT3");
        Document testBTreeBinary5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary5");
        Document testBTreeTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT4");
        Document testBTreeTXT5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT5");
        Document testBTreeBinary6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary6");
        Document testBTreeTXT6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT6");
        Document testBTreeTXT7 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT7");
        System.out.println();
        documentStore.setMaxDocumentCount(8);
        System.out.println();
    }

    @Test
    void put_nullInput() throws IOException {
        //in memory
        Document put1TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "put1TXT");
        assertNotNull(documentStore.get(put1TXT.getKey()));
        documentStore.put(null, put1TXT.getKey(), DocumentStore.DocumentFormat.TXT);
        assertNull(documentStore.get(put1TXT.getKey()));

        //out of memory
        Document put2TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "put2TXT");
        Document put3TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "put3TXT");
        documentStore.setMaxDocumentCount(1);
        documentStore.put(null, put2TXT.getKey(), DocumentStore.DocumentFormat.TXT);
        assertNull(this.documentStore.get(put2TXT.getKey()));
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
        //this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.TXT);
        return fileInput;
    }

    private FileInput createNewBinaryFile(String fileName) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        byte[] data = generateRandomByteArray(random);
        oos.writeObject(data);
        oos.close();
        FileInput fileInput = new FileInput(file.getPath());
        //this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.BINARY);
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

    private class DocumentCreator{
        private Document createAndAddNewDocumentToStore(DocumentStore.DocumentFormat format, String fileName) throws IOException {
            URI uri = URI.create(getPathToFile(fileName));
            if(format.equals(DocumentStore.DocumentFormat.TXT)){
                String text = "this is some text for " + fileName;
                InputStream inputStream = new StringBufferInputStream(text);
                documentStore.put(inputStream, uri, DocumentStore.DocumentFormat.TXT);
            } else {
                byte[] binary1bytes = generateRandomByteArray(random);
                InputStream binaryInputStream = new ByteArrayInputStream(binary1bytes);
                documentStore.put(binaryInputStream, uri, DocumentStore.DocumentFormat.BINARY);
            }
            return documentStore.get(uri);
        }
    }

    private Object reflectField(Object objectToReflect, String field) throws NoSuchFieldException, IllegalAccessException {
        Class<?> classObject = objectToReflect.getClass();
        Field classField = classObject.getDeclaredField(field);
        classField.setAccessible(true);
        return classField.get(documentStore);
    }

    private String getPathToFile(String fileName) {
        return "http://www.yu.edu/docStoreTest/" + fileName;
    }
}
