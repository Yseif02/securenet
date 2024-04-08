package edu.yu.cs.com1320.project.stage4.impl;

import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentImplTest {
    Document document;
    URI uri1;


    private Document document1;
    private Document document2;

    @BeforeEach
    void setUp() throws IOException {
        // String text = "this is the text of the document123 ? hello yaakov" +
        //        " fine day it is her3 are s^o-me more w0rd&s";
        String text = "Good evening, it's  currently thursday night. The jytney costs $3. Does this work?";
        this.uri1 = URI.create("URI");
        this.document = new DocumentImpl(uri1, text);


        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        File file2 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\anotherfile.txt");
        URI file1URI = file1.toURI();
        URI file2URI = file2.toURI();
        FileInputStream fis1 = new FileInputStream(file1);
        FileInputStream fis2 = new FileInputStream(file2);
        byte[] file1Contents = fis1.readAllBytes();
        byte[] file2Contents = fis2.readAllBytes();
        document1 = new DocumentImpl(file1URI, file1Contents);
        document2 = new DocumentImpl(file2URI, file2Contents);
    }


    @Test
    void setMetadataValue() {
        String key = "key";
        String value = "Value";
        String newValue = "New Value";
        document1.setMetadataValue(key, value);
        assertEquals(value ,document1.setMetadataValue(key, newValue));
    }

    @Test
    void getMetadataValue() {
        String key = "key";
        String value = "Value";
        document1.setMetadataValue(key, value);
        assertNotNull(document1.getMetadataValue(key));
    }

    @Test
    void getMetadata() {
        assertNotNull(document1.getMetadata());
    }

    @Test
    void getDocumentTxt() throws IOException {
        DocumentStore store = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        URI file1URI = file1.toURI();
        FileInputStream fis1 = new FileInputStream(file1);
        store.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT);
        assertNotNull(store.get(file1URI).getDocumentTxt());
    }

    @Test
    void getDocumentBinaryData() throws IOException {
        DocumentStore store = new DocumentStoreImpl();
        File biaryFile = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\binaryDocTest.docx");
        URI file1URI = biaryFile.toURI();
        FileInputStream fis1 = new FileInputStream(biaryFile);
        store.put(fis1, file1URI, DocumentStore.DocumentFormat.BINARY);
        assertNotNull(store.get(file1URI).getDocumentBinaryData());
    }

    @Test
    void getKey() {
        assertNotNull(document1.getKey());
    }

    @Test
    void testEquals_good() throws IOException {
        File file1Copy = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\" +
                "Seif_Avraham_800699054\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        FileInputStream fis = new FileInputStream(file1Copy);
        URI uri = file1Copy.toURI();
        byte[] contents = fis.readAllBytes();
        Document document1Copy = new DocumentImpl(uri, contents);
        assertEquals(document1, document1Copy);
    }

    void testEquals_bad() throws IOException {
        assertNotEquals(document1, document2);
    }
    @Test
    void wordCount() {
        //Good evening, its  currently thursday night. The jytney costs $3. Does this work?
        assertEquals(1, this.document.wordCount("Good"));
        assertEquals(1, this.document.wordCount("evening"));
        assertEquals(1, this.document.wordCount("its"));
        assertEquals(1, this.document.wordCount("currently"));
        assertEquals(1, this.document.wordCount("thursday"));
        assertEquals(1, this.document.wordCount("night"));
        assertEquals(1, this.document.wordCount("The"));
        assertEquals(1, this.document.wordCount("jytney"));
        assertEquals(1, this.document.wordCount("costs"));
        assertEquals(1, this.document.wordCount("3"));
        assertEquals(1, this.document.wordCount("Does"));
        assertEquals(1, this.document.wordCount("this"));
        assertEquals(1, this.document.wordCount("work"));
        assertEquals(0, this.document.wordCount("the"));


    }

    @Test
    void getWords() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getWords = DocumentImpl.class.getDeclaredMethod("getDocumentWords");
        getWords.setAccessible(true);
        String[] words = (String[]) getWords.invoke(document);
        for(String word : words){
            System.out.print(word + " ");
        }
    }
}