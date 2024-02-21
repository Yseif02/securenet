package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.DocumentStore;

import java.io.IOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class Main {
    public static void main(String[] args) throws IOException{
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        File file2 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\anotherfile.txt");

        File binaryFile = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\binaryDocTest.docx");
        URI file1URI = file1.toURI();
        URI file2URI = file2.toURI();
        URI binaryFileURI = binaryFile.toURI();
        FileInputStream fis1 = new FileInputStream(file1);
        FileInputStream fis2 = new FileInputStream(file2);
        FileInputStream fis3 = new FileInputStream(binaryFile);

        System.out.println(documentStore.put(fis3, binaryFileURI, DocumentStore.DocumentFormat.BINARY));
        System.out.println((documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis2, file2URI, DocumentStore.DocumentFormat.TXT)));
        System.out.println("Done");
    }
}
