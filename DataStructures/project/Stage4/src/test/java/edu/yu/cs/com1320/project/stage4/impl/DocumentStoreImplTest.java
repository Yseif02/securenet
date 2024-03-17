package edu.yu.cs.com1320.project.stage4.impl;

import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreImplTest {
    DocumentStoreImpl documentStore;
    HashMap<String, String> metaDataTestPairs;
    URI url1;
    URI url2;

    @BeforeEach
    void setUp() throws IOException {
        this.documentStore = new DocumentStoreImpl();
        /*this.url1 = addDocumentToStore(documentStore, createNewFile("Test File 1", "to"));
        this.url2 = addDocumentToStore(documentStore, createNewFile("Test File 2", "tow toggle"));
        URI url3 = addDocumentToStore(documentStore, createNewFile("Test File 3", "toe top"));
        URI url4 = addDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        URI url5 = addDocumentToStore(documentStore, createNewFile("Test File 5", "ton"));
        URI url6 = addDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        URI url7 = addDocumentToStore(documentStore, createNewFile("Test File 7", "ton ton ton too top topple tomorrow"));
        URI url8 = addDocumentToStore(documentStore, createNewFile("Test File 8", "top top top top topple"));*/
       /* documentStore.setMetadata(url1, "Key1", "Value 1");
        documentStore.setMetadata(url1, "Key2", "Value 2");
        documentStore.setMetadata(url1, "Key3", "Value 3");*/
        /*documentStore.setMetadata(url5, "Key1", "Value 1");
        documentStore.setMetadata(url5, "Key2", "Value 2");
        documentStore.setMetadata(url5, "Key3", "Value 3");*/

       /* documentStore.setMetadata(url7, "Key1", "Value 1");
        documentStore.setMetadata(url7, "Key2", "Value 2");
        documentStore.setMetadata(url7, "Key3", "Value 3");

        documentStore.setMetadata(url8, "Key1", "Value 1");
        documentStore.setMetadata(url8, "Key2", "Value 2");
        documentStore.setMetadata(url8, "Key3", "Value 3");*/
        URI url4 = addDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        this.metaDataTestPairs = new HashMap<>();
        metaDataTestPairs.put("Key1", "Value 1");
        metaDataTestPairs.put("Key2", "Value 2");
        metaDataTestPairs.put("Key3", "Value 3");
    }

    private static URI addDocumentToStore(DocumentStoreImpl documentStore, FileInput fileInput) throws IOException {
        documentStore.put(fileInput.fis, fileInput.url, DocumentStore.DocumentFormat.TXT);
        return fileInput.url;
    }

    private static FileInput createNewFile(String fileName, String fileTXT) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage4\\src\\main\\resources";
        File file = new File(destinationFolder, fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        myWriter.write(fileTXT);
        myWriter.close();
        return new FileInput(file.getPath());
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



    @Test
    void setMetadata() {
    }

    @Test
    void getMetadata() {
    }

    @Test
    void put() {
    }

    @Test
    void get() {
    }

    @Test
    void delete() {
    }

    @Test
    void undo() {
        this.documentStore.undo();
        System.out.println();
    }

    @Test
    void undo_deleteAll(){
        this.documentStore.deleteAll("top");
        this.documentStore.undo();
        System.out.println();
    }

    @Test
    void undo_deleteAllWithPrefix(){

    }

    @Test
    void undo_deleteAllWithMetaData(){
        documentStore.deleteAllWithMetadata(this.metaDataTestPairs);
        undo();
        System.out.println();
    }
    @Test
    void testUndo() {
    }

    @Test
    void search() {
        List<Document> documents = documentStore.search("there");
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void searchByPrefix() {
        List<Document> documents = documentStore.searchByPrefix("to");
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void deleteAll() {
        Set<URI> uris = documentStore.deleteAll("topple");
        for (URI uri:uris){
            System.out.println(uri.toString());
        }
    }

    @Test
    void deleteAllWithPrefix() {
        Set<URI> uris = documentStore.deleteAllWithPrefix("to");
        for (URI uri:uris){
            System.out.println(uri.toString());
        }
       /* Set<Document> documents = documentStore.documentWordsTrie.deleteAllWithPrefix("to");
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }*/

    }

    @Test
    void searchByMetadata() {
        Document document1 = this.documentStore.get(url1);
        List<Document> documents = documentStore.searchByMetadata(metaDataTestPairs);
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void searchByKeywordAndMetadata() {
        List<Document> documents = documentStore.searchByKeywordAndMetadata("ton", this.metaDataTestPairs);
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void searchByPrefixAndMetadata() {
        List<Document> documents = documentStore.searchByPrefixAndMetadata("to", this.metaDataTestPairs);
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void deleteAllWithMetadata() {

    }

    @Test
    void deleteAllWithKeywordAndMetadata() {
    }

    @Test
    void deleteAllWithPrefixAndMetadata() {
    }
}