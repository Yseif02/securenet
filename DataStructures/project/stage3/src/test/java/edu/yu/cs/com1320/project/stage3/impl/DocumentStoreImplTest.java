package edu.yu.cs.com1320.project.stage3.impl;

import edu.yu.cs.com1320.project.HashTable;
import edu.yu.cs.com1320.project.Stack;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.stage3.Document;
import edu.yu.cs.com1320.project.stage3.DocumentStore;
import edu.yu.cs.com1320.project.undo.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreImplTest {
    private DocumentStoreImpl documentStore;
    private URI file1URI;

    @BeforeEach
    void setUp() throws IOException {
        this.documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        this.file1URI = file1.toURI();
        FileInputStream fis1 = new FileInputStream(file1);
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        addDocuments();
    }
    void addDocuments() throws IOException {
        int fileNumber = 1;
        for(int i = 1; i <= 10; i++){
            FileInput fileInput = getFileInput(fileNumber);
            if(fileNumber++ % 2 == 0) {
                documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.TXT);
            }else {
                documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.BINARY);
            }
        }
        System.out.println();
    }

    private FileInput getFileInput(int fileNumber) throws IOException {
        String resourcesPath = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage3\\src\\main\\resources";
        String fileName = "file" + fileNumber;
        String fileTXT = "This is the text for file #" + fileNumber;
        File file = new File(resourcesPath, fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        myWriter.write(fileTXT);
        myWriter.close();
        return new FileInput(file.getPath());
    }

    @Test
    void set_MetadataValidInputReturningOldValue() throws IOException {
        File file = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\" +
                "DataStructures\\project\\stage2\\src\\main\\resources\\one");
        FileInputStream fis1 = new FileInputStream(file);
        URI fileURI = file.toURI();
        String key = "Key";
        String originalValue = "OGValue";
        documentStore.put(fis1, fileURI, DocumentStore.DocumentFormat.TXT);
        documentStore.setMetadata(fileURI, key, originalValue);
        String newValue = "new value";
        assertEquals(originalValue, documentStore.setMetadata(fileURI, key, newValue));
    }

    @Test
    void set_MetaDataInvalidInputThrowingException() throws IOException {
        File file2 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\anotherfile.txt");
        URI file2URI = file2.toURI();
        String key = "";
        String originalValue = "OGValue";
        assertThrows(IllegalArgumentException.class,
                () -> this.documentStore.setMetadata(file2URI, key, originalValue),
                "IllegalArgumentException should be thrown for null URI/Blank URI/null key/empty key/unknown URI.");
    }
    @Test
    void get_MetadataNullReturn() throws IOException {
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        URI file1URI = file1.toURI();
        assertNull(documentStore.getMetadata(file1URI, "key"));
    }

    @Test
    void get_MetadataGoodReturn() throws IOException {
        File file = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\" +
        "DataStructures\\project\\stage2\\src\\main\\resources\\two");
        FileInputStream fis1 = new FileInputStream(file);
        URI fileURI = file.toURI();
        documentStore.put(fis1, fileURI, DocumentStore.DocumentFormat.TXT);
        documentStore.setMetadata(fileURI, "key", "value");
        assertEquals("value", documentStore.getMetadata(fileURI, "key"));
    }
    @Test
    void put_NewTXTDoc() throws IOException {
        File file = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\" +
                "DataStructures\\project\\stage2\\src\\main\\resources\\three");
        FileInputStream fis1 = new FileInputStream(file);
        URI fileURI = file.toURI();
        assertEquals(0, documentStore.put(fis1, fileURI, DocumentStore.DocumentFormat.TXT));
    }
    @Test
    void put_ReplaceTXTDoc() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        FileInputStream fis2 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        assertEquals(documentStore.get(file1URI).hashCode(), documentStore.put(fis2, file1URI, DocumentStore.DocumentFormat.TXT));
    }

    @Test
    void put_nullStream() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        assertEquals(documentStore.get(file1URI).hashCode(), documentStore.put(null, file1URI, DocumentStore.DocumentFormat.TXT));
    }

    @Test
    void get_GoodURI() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        Document document = documentStore.get(file1URI);
        assertEquals(document, documentStore.get(file1URI));
    }

    @Test
    void get_BadURI() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        URI file1URI = file1.toURI();
        assertNull(documentStore.get(file1URI));
    }

    @Test
    void delete_GoodURI() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        assertTrue(documentStore.delete(file1URI));
    }

    @Test
    void delete_InvalidURI() throws IOException {
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\three");
        URI file1URI = file1.toURI();
        assertFalse(documentStore.delete(file1URI));
    }

    @Test
    void undo() throws IOException {
        FileInput newFile = new FileInput("C:\\Users\\heich\\Desktop\\" +
                "code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\eleven");
        this.documentStore.put(newFile.getFis(), newFile.getUrl(), DocumentStore.DocumentFormat.TXT);
        this.documentStore.undo();
        //assertEquals(1, this.documentStore.documentStore.size());
    }

    @Test
    void undoPut_URIinMiddleOfStack() throws IOException {
        FileInput[] fileInputs = new FileInput[4];
        fileInputs[0] = new FileInput("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\" +
                "DataStructures\\project\\stage2\\src\\main\\resources\\one");
        fileInputs[1] = new FileInput("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\" +
                "DataStructures\\project\\stage2\\src\\main\\resources\\two");
        fileInputs[2] = new FileInput("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\" +
                "DataStructures\\project\\stage2\\src\\main\\resources\\three");
        fileInputs[3] = new FileInput("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\" +
                "DataStructures\\project\\stage2\\src\\main\\resources\\four");
        for (FileInput fileInput : fileInputs){
            this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.TXT);
        }
        int originalSize = this.documentStore.documentStore.size();
        this.documentStore.undo(this.file1URI);
        assertEquals(originalSize-1, this.documentStore.documentStore.size());
    }

    @Test
    void getMetaDataValues(){
        Document document = documentStore.get(this.file1URI);
        document.setMetadataValue("key1", "value1");
        document.setMetadataValue("key2", "value2");
        document.setMetadataValue("key3", "value3");
        document.getMetadata();
        document.getDocumentTxt();
        document.getDocumentBinaryData();
    }

    @Test
    void undo1() throws IOException {
        File resources = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources");
        File[] resourceFiles = resources.listFiles();
        for (File resourceFile : resourceFiles) {
            FileInput fileInput = new FileInput(resourceFile.getPath());
            this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.TXT);
        }
        System.out.println();
    }
    @Test
    void createFiles() throws IOException {

    }

    @Test
    void undoAll() {
        for (int i = 0; this.documentStore.documentStore.size() > 0; i++) {
            int size = this.documentStore.documentStore.size();
            this.documentStore.undo();
        }
        System.out.println();
    }

    @Test
    void undoAll_URL(){
        System.out.println();
        for(Document document : this.documentStore.documentStore.values()){
            documentStore.undo(document.getKey());
        }
        System.out.println();
    }

    @Test
    void undo_SetMetaData(){
        for(Document document : documentStore.documentStore.values()){
            documentStore.setMetadata(document.getKey(), "key", "value");
        }
        System.out.println();
        undoAll();
        System.out.println();
    }

    private static class FileInput{
        private final URI url;
        private final FileInputStream fis;
        private final File file;

        private FileInput(String pathname) throws FileNotFoundException {
            this.file = new File(pathname);
            this.url = this.file.toURI();
            this.fis = new FileInputStream(this.file);
        }

        public FileInputStream getFis() {
            return fis;
        }

        public URI getUrl() {
            return url;
        }

        public File getFile(){
            return this.file;
        }
    }
}