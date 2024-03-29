package edu.yu.cs.com1320.project.stage4.impl;

import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        /*this.url1 = addBinaryDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        URI url4 = addTXTDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        URI url6 = addTXTDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        this.documentStore.deleteAllWithPrefix("too");
        this.documentStore.setMetadata(url4, "Key1", "Value 1");
        this.documentStore.setMetadata(url4, "Key2", "Value 2");
        this.documentStore.setMetadata(url4, "Key3", "Value 3");*/
        this.metaDataTestPairs = new HashMap<>();
        metaDataTestPairs.put("Key1", "Value 1");
        metaDataTestPairs.put("Key2", "Value 2");
        metaDataTestPairs.put("Key3", "Value 3");
    }

    private static URI addTXTDocumentToStore(DocumentStoreImpl documentStore, FileInput fileInput) throws IOException {
        documentStore.put(fileInput.fis, fileInput.url, DocumentStore.DocumentFormat.TXT);
        return fileInput.url;
    }

    private static URI addBinaryDocumentToStore(DocumentStoreImpl documentStore, FileInput fileInput) throws IOException {
        documentStore.put(fileInput.fis, fileInput.url, DocumentStore.DocumentFormat.BINARY);
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
    void undo() throws IOException {
        this.url1 = addBinaryDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        this.documentStore.undo();
        System.out.println();
    }

    @Test
    void undoURI_SingleGenericCommand() throws IOException {
        this.url1 = addBinaryDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        URI url4 = addTXTDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        URI url6 = addTXTDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        this.documentStore.setMetadata(url4, "Key1", "Value 1");
        this.documentStore.setMetadata(url4, "Key2", "Value 2");
        this.documentStore.setMetadata(url4, "Key3", "Value 3");
        this.metaDataTestPairs = new HashMap<>();
        metaDataTestPairs.put("Key1", "Value 1");
        metaDataTestPairs.put("Key2", "Value 2");
        metaDataTestPairs.put("Key3", "Value 3");
        this.documentStore.delete(url1);
        this.documentStore.undo(url1);
        assertNotNull(this.documentStore.get(url1));
    }

    @Test
    void undoURI_middleOFCommandSet() throws IOException {
        URI doc1 = addTXTDocumentToStore(documentStore, createNewFile("doc1", "too, to, tooth,"));
        URI doc2 = addTXTDocumentToStore(documentStore, createNewFile("doc2", "ten, toe, tooth,"));
        this.url1 = addTXTDocumentToStore(documentStore, createNewFile("doc3", "yesterday, tooth"));
        this.documentStore.deleteAll("tooth");
        this.documentStore.undo(url1);
        assertNull(this.documentStore.get(doc1));
        assertNull(this.documentStore.get(doc2));
        assertNotNull(this.documentStore.get(this.url1));
    }

    @Test
    void undoURI_commandSetBottomOfCommandStack() throws IOException {
        URI excel = addTXTDocumentToStore(documentStore, createNewFile("excel", "excel"));
        URI expect = addTXTDocumentToStore(documentStore, createNewFile("expect", "expect"));
        URI expel = addTXTDocumentToStore(documentStore, createNewFile("expel", "expel"));
        URI too = addTXTDocumentToStore(documentStore, createNewFile("too", "too"));
        URI to = addTXTDocumentToStore(documentStore, createNewFile("to", "to"));
        URI t = addTXTDocumentToStore(documentStore, createNewFile("t", "t"));
        URI tool = addTXTDocumentToStore(documentStore, createNewFile("tool", "tool"));
        documentStore.deleteAllWithPrefix("ex");
        documentStore.deleteAllWithPrefix("to");
        documentStore.undo(excel);
        assertNotNull(documentStore.get(excel));
        assertNull(documentStore.get(expect));
        assertNull(documentStore.get(expel));
        assertNull(documentStore.get(to));
        assertNull(documentStore.get(too));
        assertNull(documentStore.get(tool));
    }



    @Test
    void undo_deleteAll(){
        this.documentStore.deleteAll("top");
        this.documentStore.undo();
        System.out.println();
    }

    @Test
    void undo_deleteAllWithPrefix() throws NoSuchFieldException {
        Field commandStack = documentStore.getClass().getDeclaredField("commandStack");
        commandStack.setAccessible(true);
//        Method[] methods = commandStack.getMethods();
//        for (Method method : methods){
//            System.out.println(method.getName());
//        }
    }

    @Test
    void undo_all() throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException {
        Field commandStack = documentStore.getClass().getDeclaredField("commandStack");
        commandStack.setAccessible(true);

        //while (commandStack.getInt(commandStack.getClass().getDeclaredMethod("size")) > 1)
    }

    @Test
    void test(){

    }


    @Test
    void undo_deleteAllWithMetaData() {
        documentStore.deleteAllWithMetadata(this.metaDataTestPairs);
        this.documentStore.undo();
        System.out.println();
    }
    @Test
    void testUndo() {
    }

    @Test
    void search() throws IOException {
        URI testSentence =  addTXTDocumentToStore(documentStore, createNewFile("testSentence",
                "Good evening, it's  currently thursday night. The jytney costs $3. Does this work?"));

        List<Document> documents = documentStore.search("it's");
        List<Document> documents2 = documentStore.search("3");
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }
        assertEquals(1, documents.size());
        assertEquals(1, documents2.size());
    }

    @Test
    void searchByPrefix() throws IOException {
        URI to = addTXTDocumentToStore(documentStore, createNewFile("to", "to too"));
        URI too = addTXTDocumentToStore(documentStore, createNewFile("too", "too top too top"));
        URI tool = addTXTDocumentToStore(documentStore, createNewFile("tool", "tool"));
        URI toe = addTXTDocumentToStore(documentStore, createNewFile("toe", "toe tomato tomorrow"));
        List<Document> documents = documentStore.searchByPrefix("to");
        for (Document document : documents) {
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void deleteAll() throws IOException {
        URI topple = addTXTDocumentToStore(documentStore, createNewFile("topple", "topple"));
        Set<URI> uris = documentStore.deleteAll("topple");
        for (URI uri:uris){
            System.out.println(uri.toString());
        }
    }

    @Test
    void deleteAllWithPrefix() throws IOException {
        URI to = addTXTDocumentToStore(documentStore, createNewFile("to", "to too"));
        URI too = addTXTDocumentToStore(documentStore, createNewFile("too", "too top too top"));
        URI tool = addTXTDocumentToStore(documentStore, createNewFile("tool", "tool"));
        URI toe = addTXTDocumentToStore(documentStore, createNewFile("toe", "toe tomato tomorrow"));
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
        //Document document1 = this.documentStore.get(url1);
        List<Document> documents = documentStore.searchByMetadata(metaDataTestPairs);
        for (Document document : documents) {
            System.out.println(document.getKey().toString());
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void searchByMetadata_emptyMap() throws IOException {
        URI one = addTXTDocumentToStore(documentStore, createNewFile("one", "one"));
        URI two = addTXTDocumentToStore(documentStore, createNewFile("two", "two"));
        URI three = addTXTDocumentToStore(documentStore, createNewFile("three", "three"));
        this.metaDataTestPairs = new HashMap<>();
        List<Document> documents = documentStore.searchByMetadata(this.metaDataTestPairs);
        for (Document document : documents){
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
    void deleteAllWithMetadata() throws IOException {
        URI url3 = addTXTDocumentToStore(documentStore, createNewFile("Test File 3", "toe top"));
        URI url4 = addTXTDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        URI url5 = addTXTDocumentToStore(documentStore, createNewFile("Test File 5", "ton"));
        URI url6 = addTXTDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        URI url7 = addTXTDocumentToStore(documentStore, createNewFile("Test File 7", "ton ton ton too top topple tomorrow"));
        URI url8 = addTXTDocumentToStore(documentStore, createNewFile("Test File 8", "top top top top topple"));
        this.documentStore.setMetadata(url5,"Key1", "Value 1");
        this.documentStore.setMetadata(url5,"Key2", "Value 2");
        this.documentStore.setMetadata(url5,"Key3", "Value 3");
        this.documentStore.setMetadata(url4,"Key1", "Value 1");
        this.documentStore.setMetadata(url4,"Key2", "Value 2");
        this.documentStore.setMetadata(url4,"Key3", "Value 3");
        this.documentStore.setMetadata(url7,"Key1", "Value 1");
        this.documentStore.setMetadata(url7,"Key2", "Value 2");
        this.documentStore.setMetadata(url7,"Key3", "Value 3");
        this.documentStore.deleteAllWithMetadata(metaDataTestPairs);
        assertNull(this.documentStore.get(url5));
        assertNull(this.documentStore.get(url4));
        assertNull(this.documentStore.get(url7));
    }

    @Test
    void deleteAllWithKeywordAndMetadata() throws IOException {
        URI url3 = addTXTDocumentToStore(documentStore, createNewFile("Test File 3", "toe top"));
        URI url4 = addTXTDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        URI url5 = addTXTDocumentToStore(documentStore, createNewFile("Test File 5", "ton"));
        URI url6 = addTXTDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        URI url7 = addTXTDocumentToStore(documentStore, createNewFile("Test File 7", "ton ton ton too top topple tomorrow"));
        URI url8 = addTXTDocumentToStore(documentStore, createNewFile("Test File 8", "top top top top topple"));
        this.documentStore.setMetadata(url5,"Key1", "Value 1");
        this.documentStore.setMetadata(url5,"Key2", "Value 2");
        this.documentStore.setMetadata(url5,"Key3", "Value 3");
        this.documentStore.setMetadata(url4,"Key1", "Value 1");
        this.documentStore.setMetadata(url4,"Key2", "Value 2");
        this.documentStore.setMetadata(url4,"Key3", "Value 3");
        this.documentStore.setMetadata(url7,"Key1", "Value 1");
        this.documentStore.setMetadata(url7,"Key2", "Value 2");
        this.documentStore.setMetadata(url7,"Key3", "Value 3");
        this.documentStore.deleteAllWithKeywordAndMetadata("ton", metaDataTestPairs);
        assertNull(this.documentStore.get(url5));
        assertNull(this.documentStore.get(url7));
        assertNotNull(this.documentStore.get(url4));
    }

    @Test
    void deleteAllWithPrefixAndMetadata() throws IOException {
        URI url3 = addTXTDocumentToStore(documentStore, createNewFile("Test File 3", "toe top"));
        URI url4 = addTXTDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        URI url5 = addTXTDocumentToStore(documentStore, createNewFile("Test File 5", "ton"));
        URI url6 = addTXTDocumentToStore(documentStore, createNewFile("Test File 6", "tooth together"));
        URI url7 = addTXTDocumentToStore(documentStore, createNewFile("Test File 7", "ton ton ton too top topple tomorrow"));
        URI url8 = addTXTDocumentToStore(documentStore, createNewFile("Test File 8", "top top top top topple"));
        this.documentStore.setMetadata(url5,"Key1", "Value 1");
        this.documentStore.setMetadata(url5,"Key2", "Value 2");
        this.documentStore.setMetadata(url5,"Key3", "Value 3");
        this.documentStore.setMetadata(url4,"Key1", "Value 1");
        this.documentStore.setMetadata(url4,"Key2", "Value 2");
        this.documentStore.setMetadata(url4,"Key3", "Value 3");
        this.documentStore.setMetadata(url7,"Key1", "Value 1");
        this.documentStore.setMetadata(url7,"Key2", "Value 2");
        this.documentStore.setMetadata(url7,"Key3", "Value 3");
    }
}