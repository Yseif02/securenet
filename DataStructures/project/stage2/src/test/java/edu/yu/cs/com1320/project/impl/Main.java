package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.stage2.DocumentStore;
import edu.yu.cs.com1320.project.stage2.impl.DocumentStoreImpl;

import java.io.*;

import java.io.IOException;
import java.net.URI;


public class Main {
    public static void main(String[] args) throws IOException{
        test2();

        System.out.println("Done");
    }
    private static final DocumentStore documentStore = new DocumentStoreImpl();
    static void test1() throws IOException {
        File one = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\one");
        File two = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\two");
        File three = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\three");
        File four = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\four");
        File five = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\five");
        File six = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\six");
        File seven = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\seven");
        File eight = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\eight");
        File nine = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\nine");
        File ten = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\ten");
        File eleven = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\eleven");
        File twelve = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\stage2\\src\\main\\resources\\twelve");

        File binaryFile = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\binaryDocTest.docx");

        URI URIOne = one.toURI();
        URI URITwo = two.toURI();
        URI URIThree = three.toURI();
        URI URIFour = four.toURI();
        URI URIFive = five.toURI();
        URI URISix = six.toURI();
        URI URISeven = seven.toURI();
        URI URIEight = eight.toURI();
        URI URINine = nine.toURI();
        URI URITen = ten.toURI();
        URI URIEleven = eleven.toURI();
        URI URITwelve = twelve.toURI();
        URI binaryFileURI = binaryFile.toURI();

        FileInputStream fis1 = new FileInputStream(one);
        FileInputStream fis2 = new FileInputStream(two);
        FileInputStream fis3 = new FileInputStream(three);
        FileInputStream fis4 = new FileInputStream(four);
        FileInputStream fis5 = new FileInputStream(five);
        FileInputStream fis6 = new FileInputStream(six);
        FileInputStream fis7 = new FileInputStream(seven);
        FileInputStream fis8 = new FileInputStream(eight);
        FileInputStream fis9 = new FileInputStream(nine);
        FileInputStream fis10 = new FileInputStream(ten);
        FileInputStream fis11 = new FileInputStream(eleven);
        FileInputStream fis12 = new FileInputStream(twelve);
        FileInputStream fisBinary = new FileInputStream(binaryFile);

        System.out.println(documentStore.put(fisBinary, binaryFileURI, DocumentStore.DocumentFormat.BINARY));
        System.out.println((documentStore.put(fis1, URIOne, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis2, URITwo, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis3, URIThree, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis4, URIFour, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis5, URIFive, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis6, URISix, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis7, URISeven, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis8, URIEight, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis9, URINine, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis10, URITen, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis11, URIEleven, DocumentStore.DocumentFormat.TXT)));
        System.out.println((documentStore.put(fis12, URITwelve, DocumentStore.DocumentFormat.TXT)));

        documentStore.setMetadata(URIOne, "key", "value");
    }
    static void test2(){
        HashTableImpl<Integer, Integer> table = new HashTableImpl<>();
        table.put(1,1);
        table.put(2,2);
        table.put(3,3);
        table.put(4,4);
    }
}
