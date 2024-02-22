package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Document;
import edu.yu.cs.com1320.project.DocumentStore;
import edu.yu.cs.com1320.project.stage2.impl.DocumentStoreImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreImplTest {
    @Test
    void set_MetadataValidInputReturningNull() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        String key = "Key";
        String value = "Value";
        String oldValue = documentStore.setMetadata(file1URI, key, value);
        assertNull(oldValue, "The old value should be null for the first set.");
    }

    @Test
    void set_MetadataValidInputReturningOldValue() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        String key = "Key";
        String originalValue = "OGValue";
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        documentStore.setMetadata(file1URI, key, originalValue);
        String newValue = "new value";
        assertEquals(originalValue, documentStore.setMetadata(file1URI, key, newValue));
    }

    @Test
    void set_MetaDataInvalidInputThrowingException() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        File file2 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\anotherfile.txt");
        URI file2URI = file2.toURI();
        String key = "";
        String originalValue = "OGValue";
        assertThrows(IllegalArgumentException.class,
                () -> documentStore.setMetadata(file2URI, key, originalValue),
                "IllegalArgumentException should be thrown for null URI/Blank URI/null key/empty key/unknown URI.");
    }
    @Test
    void get_MetadataNullReturn() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        assertNull(documentStore.getMetadata(file1URI, "key"));
    }

    @Test
    void get_MetadataGoodReturn() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        documentStore.setMetadata(file1URI, "key", "value");
        assertEquals("value", documentStore.getMetadata(file1URI, "key"));
    }
    @Test
    void put_NewTXTDoc() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis1 = new FileInputStream(file1);
        URI file1URI = file1.toURI();
        assertEquals(0, documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT));
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
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo" +
                "\\Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        URI file1URI = file1.toURI();
        assertFalse(documentStore.delete(file1URI));
    }
}