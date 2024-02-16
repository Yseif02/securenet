package edu.yu.cs.com1320.project.stage1.impl;

import edu.yu.cs.com1320.project.stage1.Document;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class DocumentImplTest {

    @Test
    void testEquals() throws IOException {
        File file = new File("C:\\Users\\heich\\Desktop\\code\\My Repository\\DataStructures" +
                "\\project\\stage1\\src\\main\\java\\edu\\yu\\cs\\com1320\\project\\stage1\\test.txt");
        FileInputStream fis = new FileInputStream(file);
        URI uri = file.toURI();
        byte[] contents = fis.readAllBytes();
        Document document1 = new DocumentImpl(uri, contents);
        Document document2 = new DocumentImpl(uri, contents);
        assertTrue(document1.equals(document2));
    }
    @Test
    void testEquals_bad() throws IOException {
        File file = new File("C:\\Users\\heich\\Desktop\\code\\My Repository\\DataStructures" +
                "\\project\\stage1\\src\\main\\java\\edu\\yu\\cs\\com1320\\project\\stage1\\test.txt");
        File otherFile = new File("C:\\Users\\heich\\Desktop\\code\\My Repository\\DataStructures" +
                "\\project\\stage1\\src\\main\\java\\edu\\yu\\cs\\com1320\\project\\stage1\\anotherfile.txt");
        FileInputStream fis = new FileInputStream(file);
        FileInputStream otherFis = new FileInputStream(otherFile);
        URI uri = file.toURI();
        URI otherURI = otherFile.toURI();
        byte[] contents = fis.readAllBytes();
        byte[] otherContents = otherFis.readAllBytes();
        Document document1 = new DocumentImpl(uri, contents);
        Document document2 = new DocumentImpl(uri, contents);
        Document document3 = new DocumentImpl(otherURI, otherContents);
        assertFalse(document1.equals(document3));
    }

    @Test
    void setMetadataValue() {
    }

    @Test
    void getMetadataValue() {
    }

    @Test
    void getMetadata() {
    }

    @Test
    void getDocumentTxt() {
    }

    @Test
    void getDocumentBinaryData() {
    }

    @Test
    void getKey() {
    }
}