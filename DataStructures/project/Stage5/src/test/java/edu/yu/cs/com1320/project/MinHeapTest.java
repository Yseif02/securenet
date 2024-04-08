package edu.yu.cs.com1320.project;

import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import edu.yu.cs.com1320.project.stage5.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage5.impl.DocumentStoreImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class MinHeapTest {
    private MinHeapImpl<Document> minHeap;
    private DocumentStoreImpl documentStore;

    @BeforeEach
    void setUp() {
        this.minHeap = new MinHeapImpl<>();
        this.documentStore = new DocumentStoreImpl();
    }

    private static URI addTXTDocumentToStore(DocumentStoreImpl documentStore, FileInput fileInput) throws IOException {
        documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.TXT);
        return fileInput.getUrl();
    }

    private static URI addBinaryDocumentToStore(DocumentStoreImpl documentStore, FileInput fileInput) throws IOException {
        documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.BINARY);
        return fileInput.getUrl();
    }

    private static FileInput createNewFile(String fileName, String fileTXT) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(destinationFolder, fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        myWriter.write(fileTXT);
        myWriter.close();
        return new FileInput(file.getPath());
    }

    @Test
    void insert() throws IOException {
        URI sentence1 = addTXTDocumentToStore(documentStore, createNewFile("sentence1", "This is the text to sentence one. Here are some prefixes for to; to, too, top"));
        URI binaryDoc = addBinaryDocumentToStore(documentStore, createNewFile("BinaryDoc", ""));
        URI sentence2 = addTXTDocumentToStore(documentStore, createNewFile("sentence2", "This sentence has no matching prefixes"));
        URI sentence3 = addTXTDocumentToStore(documentStore, createNewFile("sentence3", "Here is the text to sentence Three. These are other matching prefixes; tool, ton"));
        URI binaryDoc2 = addBinaryDocumentToStore(documentStore, createNewFile("BinaryDoc2", ""));
        URI sentence4 = addTXTDocumentToStore(documentStore, createNewFile("sentence4", "This sentence has another matching prefixes. totally"));
        Document document1 = documentStore.get(sentence1);
        Document document2 = documentStore.get(binaryDoc);
        this.minHeap.insert(document1);
        this.minHeap.insert(document2);
        this.minHeap.insert(this.documentStore.get(sentence2));
        this.minHeap.insert(this.documentStore.get(sentence3));
        this.minHeap.insert(this.documentStore.get(binaryDoc2));
        this.minHeap.insert(this.documentStore.get(sentence4));
        /*this.documentStore.setMetadata()
        this.documentStore.setMetadata()
        this.documentStore.setMetadata()
        this.documentStore.setMetadata()
        this.documentStore.setMetadata()*/
        Document document = this.minHeap.remove();
        //Document document3 = this.minHeap.elements[5];
        /*this.minHeap.insert(4); // 2
        this.minHeap.insert(2); // 1
        this.minHeap.insert(3); // 3
        this.minHeap.insert(9); // 4
        this.minHeap.insert(8); // 5*/
        System.out.println("done");
    }

    @Test
    void reHeapify() throws IOException, NoSuchFieldException {
        URI sentence1 = addTXTDocumentToStore(documentStore, createNewFile("sentence1", "This is the text to sentence one. Here are some prefixes for to; to, too, top"));
        URI binaryDoc = addBinaryDocumentToStore(documentStore, createNewFile("BinaryDoc", ""));
        URI sentence2 = addTXTDocumentToStore(documentStore, createNewFile("sentence2", "This sentence has no matching prefixes"));
        URI sentence3 = addTXTDocumentToStore(documentStore, createNewFile("sentence3", "Here is the text to sentence Three. These are other matching prefixes; tool, ton"));
        URI binaryDoc2 = addBinaryDocumentToStore(documentStore, createNewFile("BinaryDoc2", ""));
        URI sentence4 = addTXTDocumentToStore(documentStore, createNewFile("sentence4", "This sentence has another matching prefixes. totally"));
        Document document1 = documentStore.get(sentence1);
        Document document2 = documentStore.get(binaryDoc);
        this.documentStore.setMaxDocumentCount(5);
        this.documentStore.setMaxDocumentCount(4);
        this.documentStore.setMaxDocumentCount(3);
        this.documentStore.setMaxDocumentCount(2);
        this.documentStore.setMaxDocumentCount(1);
    }

    @Test
    void getArrayIndex() {
    }

    @Test
    void doubleArraySize() {
    }

    @Test
    void isEmpty() {
    }

    @Test
    void isGreater() {
    }

    @Test
    void swap() {
    }

    @Test
    void upHeap() {
    }

    @Test
    void downHeap() {
    }

    @Test
    void peek() {
    }

    @Test
    void remove() {
    }
}