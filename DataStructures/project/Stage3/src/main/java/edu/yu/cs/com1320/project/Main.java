package edu.yu.cs.com1320.project;

import edu.yu.cs.com1320.project.stage3.DocumentStore;
import edu.yu.cs.com1320.project.stage3.impl.DocumentStoreImpl;
import edu.yu.cs.com1320.project.stage3.impl.HashTableImpl;
import edu.yu.cs.com1320.project.stage3.impl.StackImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;

public class Main {
    public static void main(String[] args) throws IOException {
        //stackTest();
        //hashTableTest();
        documentStoreTest();
        System.out.println("done");
    }

    private static void stackTest(){
        Stack<Integer> stack = new StackImpl<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);
        stack.push(4);
        stack.push(5);
        stack.push(6);
        System.out.println(stack.peek());
        System.out.println(stack.size());
        System.out.println(stack.peek());
        System.out.println(stack.pop());
        System.out.println(stack.size());
    }

    private static void hashTableTest(){
        HashTableImpl<Integer, Integer> hashTable = new HashTableImpl<>();
        hashTable.put(0,0);
        hashTable.put(1,1);
        hashTable.put(2,2);
        hashTable.put(5,5);
        hashTable.put(10,10);
        hashTable.put(3,3);
        hashTable.put(4,4);
        hashTable.put(6,6);
        System.out.println(hashTable.values().toString());
        /*hashTable.put(7,7);
        hashTable.put(8,8);
        hashTable.put(9,9);
        hashTable.put(10,10);
        hashTable.put(11,11);
        hashTable.put(12,12);*/
    }

    private static void documentStoreTest() throws IOException {
        DocumentStore documentStore = new DocumentStoreImpl();
        File file1 = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054" +
                "\\DataStructures\\project\\stage1\\src\\main\\resources\\test.txt");
        URI file1URI = file1.toURI();
        FileInputStream fis1 = new FileInputStream(file1);
        System.out.println((documentStore.put(fis1, file1URI, DocumentStore.DocumentFormat.TXT)));
        documentStore.setMetadata(file1URI, "key", "value");
        documentStore.undo();

    }
}