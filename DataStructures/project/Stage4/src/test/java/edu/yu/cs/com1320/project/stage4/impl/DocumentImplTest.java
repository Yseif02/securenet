package edu.yu.cs.com1320.project.stage4.impl;

import edu.yu.cs.com1320.project.stage4.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class DocumentImplTest {
    Document document;
    URI uri1;

    @BeforeEach
    void setUp() {
        // String text = "this is the text of the document123 ? hello yaakov" +
        //        " fine day it is her3 are s^o-me more w0rd&s";
        String text = "?";
        this.uri1 = URI.create("URI");
        this.document = new DocumentImpl(uri1, text);
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

    @Test
    void wordCount() {
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