package edu.yu.cs.com1320.project.stage4.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class DocumentImplTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void setMetadataValue() {
        String text = "this is the text of the document123 ? hello yaakov" +
                " fine day it is";
        URI uri1 = URI.create("URI");
        DocumentImpl document = new DocumentImpl(uri1, text);
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

    @Test
    void wordCount() {
    }

    @Test
    void getWords() {
    }
}