package edu.yu.cs.com1320.project.stage6.impl;

import edu.yu.cs.com1320.project.FileInput;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.print.Doc;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreImplTest {
    private File destinationDirectory;
    private DocumentStoreImpl documentStore;
    Random random = new Random();
    DocumentCreator documentCreator;
    HashSet<URI> docStoreURIs;
    int fullDocStoreSize;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        this.destinationDirectory = new File("C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage6\\src\\main\\resources");
        //assert destinationDirectory.isDirectory();
        this.documentStore = new DocumentStoreImpl(destinationDirectory);
        this.documentCreator = new DocumentCreator();
        this.docStoreURIs = (HashSet<URI>) reflectField(documentStore, "docStoreURIs");
        this.fullDocStoreSize = (Integer) reflectField(documentStore, "fullDocStoreSize");
    }

    @Test
    void testBaseDir() throws IOException {
        DocumentStore documentStore2 = new DocumentStoreImpl();
        String file1Name = "file1Name";
        URI uri1 = URI.create(getPathToFile(file1Name));
        {
            String text = "this is some text for " + file1Name;
            InputStream inputStream = new StringBufferInputStream(text);
            documentStore2.put(inputStream, uri1, DocumentStore.DocumentFormat.TXT);
        }
        {
            String file2name = "file2name";
            URI uri2 = URI.create(getPathToFile(file2name));
            byte[] binary1bytes = generateRandomByteArray(random);
            InputStream binaryInputStream = new ByteArrayInputStream(binary1bytes);
            documentStore2.put(binaryInputStream, uri2, DocumentStore.DocumentFormat.BINARY);
        }

        documentStore2.setMaxDocumentCount(1);
    }

    @Test
    void deleteEmptyDirectories() throws IOException {
        Document dedTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dedTXT1");
        Document dedTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dedTXT2");
        this.documentStore.setMaxDocumentCount(1);
        this.documentStore.setMaxDocumentCount(2);
        this.documentStore.get(dedTXT1.getKey());

    }

    @Test
    void metadataMapTest() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document mdmt1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "mdmt");
        this.documentStore.setMetadata(mdmt1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(mdmt1.getKey(), "key2", "value2");
        List<Document> returnList1 = this.documentStore.searchByMetadata(testMap);
        assertEquals(1, returnList1.size());
        Document mdmt1Replaced = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "mdmt");
        List<Document> returnList2 = this.documentStore.searchByMetadata(testMap);
        assertEquals(0, returnList2.size());
        this.documentStore.undo(mdmt1Replaced.getKey());
        List<Document> returnList3 = this.documentStore.searchByMetadata(testMap);
        assertEquals(1, returnList3.size());
    }

    @Test
    void testSerialize() throws IOException {
        documentStore.setMaxDocumentCount(1);
        /*FileInput file1 = createNewTXTFile("file1", "This is the text to file1");
        documentStore.setMetadata(file1.getUrl(), "key1", "value1");
        documentStore.setMetadata(file1.getUrl(), "key2", "value2");
        documentStore.setMetadata(file1.getUrl(), "key3", "value3");
        FileInput file2 = createNewBinaryFile("file2");*/
        String file1Name = "http://www.yu.edu/docStoreTest/doc1";
        URI uri1 = URI.create(file1Name);
        String text = "this is some text";
        //Document document1 = new DocumentImpl(uri1, text, null);
        InputStream inputStream = new StringBufferInputStream(text);
        documentStore.put(inputStream, uri1, DocumentStore.DocumentFormat.TXT);
        documentStore.setMetadata(uri1, "key1", "value1");
        documentStore.setMetadata(uri1, "key2", "value2");
        documentStore.setMetadata(uri1, "key3", "value3");

        String binaryFile1Name = "http://www.yu.edu/docStoreTest/binary1";
        URI binary1URI = URI.create(binaryFile1Name);
        byte[] binary1bytes = generateRandomByteArray(random);
        InputStream binaryInputStream = new ByteArrayInputStream(binary1bytes);
        documentStore.put(binaryInputStream, binary1URI, DocumentStore.DocumentFormat.BINARY);



        String file2Name = "http://www.yu.edu/docStoreTest/doc2";
        URI uri2 = URI.create(file2Name);
        String text2 = "this is some text for doc 2";
        //Document document1 = new DocumentImpl(uri1, text, null);
        InputStream inputStream2 = new StringBufferInputStream(text2);
        documentStore.put(inputStream2, uri2, DocumentStore.DocumentFormat.TXT);
        //documentStore.setMaxDocumentCount(10);
        documentStore.setMetadata(uri1, "key1", "newValue");
        documentStore.setMetadata(binary1URI, "key1", "value1");
    }

    @Test
    void setMetadataInMemory() throws IOException {
        Document document = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testDoc1");
        documentStore.setMetadata(document.getKey(), "key1", "value1");
        assertEquals("value1", this.documentStore.getMetadata(document.getKey(), "key1"));
    }

    @Test
    void setMetadataOnDisk() throws IOException {
        Document SMDOD1TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SMDOD1TXT");
        Document SMDOD2Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "SMDOD2Binary");
        Document SMDOD3TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SMDOD3TXT");
        Document SMDOD4Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "SMDOD4Binary");
        documentStore.setMaxDocumentCount(2);
        documentStore.setMetadata(SMDOD1TXT.getKey(), "key1", "value1");
        documentStore.setMetadata(SMDOD2Binary.getKey(), "key1", "value1");
        assertEquals("value1", this.documentStore.getMetadata(SMDOD1TXT.getKey(), "key1"));
        assertEquals("value1", this.documentStore.getMetadata(SMDOD2Binary.getKey(), "key1"));
    }

    @Test
    void undoSetMetadataOnDisk() throws IOException {
        Document USMDOD1TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDOD1TXT");
        documentStore.setMetadata(USMDOD1TXT.getKey(), "key1", "value1");
        Document USMDOD2Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "USMDOD2Binary");
        Document USMDOD3TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDOD3TXT");
        Document USMDOD4Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "USMDOD4Binary");
        documentStore.setMaxDocumentCount(3);
        documentStore.setMetadata(USMDOD1TXT.getKey(), "key2", "value2");
        //documentStore.setMetadata(USMDOD2Binary.getKey(), "key1", "value1");
        assertEquals("value2", this.documentStore.getMetadata(USMDOD1TXT.getKey(), "key2"));
        //assertEquals("value1", this.documentStore.getMetadata(USMDOD2Binary.getKey(), "key1"));
        documentStore.undo();
        assertEquals("value1", this.documentStore.getMetadata(USMDOD1TXT.getKey(), "key1"));
        assertEquals(3, docStoreURIs.size());
        documentStore.setMaxDocumentCount(2);
        assertEquals(2, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(USMDOD2Binary.getKey()));
        System.out.println();
    }


    @Test
    void getMetadata() throws IOException {
        //in memory
        Document gmd1TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "gmd1TXT");
        Document gmd2Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "gmd2Binary");
        Document gmd3TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "gmd3TXT");
        Document gmd4Binary = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "gmd4Binary");
        documentStore.setMetadata(gmd1TXT.getKey(), "key1", "value1");
        assertEquals("value1", documentStore.getMetadata(gmd1TXT.getKey(), "key1"));

        documentStore.setMetadata(gmd2Binary.getKey(), "key1", "value1");
        documentStore.setMetadata(gmd3TXT.getKey(), "key1", "value1");
        documentStore.setMetadata(gmd4Binary.getKey(), "key1", "value1");
        documentStore.setMaxDocumentCount(1);
        //in memory and on disk
        assertEquals("value1", documentStore.getMetadata(gmd4Binary.getKey(), "key1"));
        assertEquals("value1", documentStore.getMetadata(gmd1TXT.getKey(), "key1"));
    }


    @Test
    void undoSetMetadataInMemory1() throws IOException {
        Document USMDIM1TXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDIM1TXT1");
        Document USMDIM1TXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDIM1TXT2");
        this.documentStore.setMetadata(USMDIM1TXT1.getKey(), "key1", "value1");
        this.documentStore.undo();
        assertNull(this.documentStore.getMetadata(USMDIM1TXT1.getKey(), "key1"));
    }

    @Test
    void undoSetMetadataInMemory2() throws IOException, NoSuchFieldException, IllegalAccessException {
        Document USMDIM2TXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDIM2TXT1");
        Document USMDIM2TXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDIM2TXT2");
        //set md in memory
        this.documentStore.setMetadata(USMDIM2TXT1.getKey(), "key1", "value1");
        this.documentStore.get(USMDIM2TXT2.getKey());
        //kick out document
        this.documentStore.setMaxDocumentCount(1);
        this.documentStore.setMaxDocumentCount(2);
        //HashSet<URI> docStoreURIs = (HashSet<URI>) reflectField(documentStore, "docStoreURIs");
        assertEquals(1, docStoreURIs.size());
        this.documentStore.undo();
        assertNull(this.documentStore.getMetadata(USMDIM2TXT1.getKey(), "key1"));
        assertEquals(2, docStoreURIs.size());
    }

    @Test
    void undoSetMetadataOnDisk1() throws IOException, NoSuchFieldException, IllegalAccessException {
        Document USMDOD1TXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDOD1TXT1");
        Document USMDOD1TXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDOD1TXT2");
        //set metadata while doc is on disk
        this.documentStore.setMaxDocumentCount(1);
        this.documentStore.setMaxDocumentCount(2);
        this.documentStore.setMetadata(USMDOD1TXT1.getKey(), "key1", "value1");

        HashSet<URI> docStoreURIs = (HashSet<URI>) reflectField(documentStore, "docStoreURIs");
        assertEquals(2, docStoreURIs.size());
        //undo while doc is in memory
        this.documentStore.undo();
        assertFalse(docStoreURIs.contains(USMDOD1TXT1.getKey()));
        //doc is moved back to disk
        assertEquals(1, docStoreURIs.size());
        assertNull(this.documentStore.getMetadata(USMDOD1TXT1.getKey(), "key1"));
    }

    @Test
    void undoSetMetadataOnDisk2() throws IOException, NoSuchFieldException, IllegalAccessException {
        Document USMDOD2TXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDOD1TXT1");
        Document USMDOD2TXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "USMDOD1TXT2");
        this.documentStore.setMaxDocumentCount(1);
        this.documentStore.setMaxDocumentCount(2);
        //set doc md while on disk
        this.documentStore.setMetadata(USMDOD2TXT1.getKey(), "key1", "value1");
        Document doc2 = this.documentStore.get(USMDOD2TXT2.getKey());
        //kick doc out
        this.documentStore.setMaxDocumentCount(1);
        assertTrue(this.docStoreURIs.contains(doc2.getKey()));
        this.documentStore.undo();
        assertFalse(this.docStoreURIs.contains(USMDOD2TXT1.getKey()));
    }




    @Test
    void testBTreeSplitting() throws IOException {
        Document testBTreeBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary1");
        Document testBTreeBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary2");
        Document testBTreeTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT1");
        Document testBTreeBinary3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary3");
        Document testBTreeTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT2");
        Document testBTreeBinary4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary4");
        Document testBTreeTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT3");
        Document testBTreeBinary5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary5");
        Document testBTreeTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT4");
        Document testBTreeTXT5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT5");
        Document testBTreeBinary6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "testBTreeBinary6");
        Document testBTreeTXT6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT6");
        Document testBTreeTXT7 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "testBTreeTXT7");
        documentStore.setMaxDocumentCount(8);

    }

    @Test
    void put_nullInput() throws IOException {
        //in memory
        Document put1TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "put1TXT");
        assertNotNull(documentStore.get(put1TXT.getKey()));
        documentStore.put(null, put1TXT.getKey(), DocumentStore.DocumentFormat.TXT);
        assertNull(documentStore.get(put1TXT.getKey()));

        //out of memory
        Document put2TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "put2TXT");
        Document put3TXT = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "put3TXT");
        documentStore.setMaxDocumentCount(1);
        documentStore.put(null, put2TXT.getKey(), DocumentStore.DocumentFormat.TXT);
        assertNull(this.documentStore.get(put2TXT.getKey()));
    }

    @Test
    void putReplaceDocInMemory() throws IOException {
        Document prdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "prdimTXT1");
        Document prdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "prdimTXT2");
        Document newPrdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "prdimTXT1", "This is new text");
        assertEquals(2, docStoreURIs.size());
        assertEquals("This is new text", this.documentStore.get(prdimTXT1.getKey()).getDocumentTxt());

    }

    @Test
    void undoPutReplaceDocInMemory() throws IOException {
        Document uprdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "uprdimTXT1");
        Document uprdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "uprdimTXT2");
        Document newUprdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "uprdimTXT1", "This is new text");
        assertEquals(2, docStoreURIs.size());
        assertEquals("This is new text", this.documentStore.get(uprdimTXT1.getKey()).getDocumentTxt());
        this.documentStore.undo();
        assertEquals("this is some text for uprdimTXT1", this.documentStore.get(uprdimTXT1.getKey()).getDocumentTxt());
        assertEquals(2, docStoreURIs.size());
    }

    @Test
    void putReplaceDocOnDisk() throws IOException {
        Document prdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "prdimTXT1");
        Document prdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "prdimTXT2");
        this.documentStore.setMaxDocumentCount(1);
        this.documentStore.setMaxDocumentCount(2);
        Document newPrdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "prdimTXT1", "This is new text");
        assertEquals(2, docStoreURIs.size());
        assertEquals("This is new text", this.documentStore.get(prdimTXT1.getKey()).getDocumentTxt());

    }

    @Test
    void undoPutReplaceDocOnDisk() throws IOException {
        Document uprdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "uprdimTXT1");
        Document uprdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "uprdimTXT2");
        this.documentStore.setMaxDocumentCount(1);
        this.documentStore.setMaxDocumentCount(2);
        Document newUprdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "uprdimTXT1", "This is new text");
        assertEquals(2, docStoreURIs.size());
        assertEquals("This is new text", this.documentStore.get(uprdimTXT1.getKey()).getDocumentTxt());
        this.documentStore.undo();
        assertEquals(1, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(uprdimTXT1.getKey()));
    }


    @Test
    void deleteInMemory() throws IOException {
        Document dimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dimTXT1");
        Document dimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dimTXT2");
        this.documentStore.delete(dimTXT1.getKey());
        assertEquals(1, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(dimTXT2.getKey()));
    }

    @Test
    void undoDeleteInMemory() throws IOException {
        Document dimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dimTXT1");
        Document dimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dimTXT2");
        this.documentStore.delete(dimTXT1.getKey());
        assertEquals(1, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(dimTXT2.getKey()));
        this.documentStore.undo();
        assertEquals(2, docStoreURIs.size());
        this.documentStore.setMaxDocumentCount(1);
        assertEquals(1, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(dimTXT2.getKey()));
    }

    @Test
    void deleteOnDisk() throws IOException {
        Document dodTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dodTXT1");
        Document dodTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dodTXT1");
        documentStore.setMaxDocumentCount(1);
        assertTrue(documentStore.delete(dodTXT1.getKey()));
        documentStore.undo();
        assertEquals(1, docStoreURIs.size());
    }

    @Test
    void deleteOnDisk2() throws IOException {
        Document dod2TXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dod2TXT1");
        Document dod2TXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dod2TXT2");
        Document dod2TXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dod2TXT3");
        documentStore.setMaxDocumentCount(2);
        this.documentStore.delete(dod2TXT1.getKey());
    }

    @Test
    void undoDeleteOnDisk2() throws IOException {
        Document udod2TXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udod2TXT1");
        Document udod2TXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udod2TXT2");
        Document udod2TXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udod2TXT3");
        documentStore.setMaxDocumentCount(2);
        this.documentStore.delete(udod2TXT1.getKey());
        assertEquals(2, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(udod2TXT1.getKey()));

        this.documentStore.undo();
        assertEquals(2, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(udod2TXT1.getKey()));

        this.documentStore.get(udod2TXT1.getKey());
        assertEquals(2, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(udod2TXT2.getKey()));
        assertTrue(docStoreURIs.contains(udod2TXT1.getKey()));
        assertTrue(docStoreURIs.contains(udod2TXT3.getKey()));
    }

    @Test
    void pushToDiskViaMaxDocCountViaUndoDelete() throws IOException {
        Document document1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "document1");
        //Document document2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "document2");
        documentStore.setMaxDocumentCount(1);
        documentStore.delete(document1.getKey());
        Document document3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "document3");
        this.documentStore.undo(document1.getKey());
        assertFalse(docStoreURIs.contains(document1.getKey()));
    }

    @Test
    void undo() {
    }

    @Test
    void testUndo() {
    }

    @Test
    void search_InMemory() throws IOException {
        String SDTXT1 = "SDTXT1";
        URI SDTXT1URI = URI.create(SDTXT1);
        String SDTXT1FileText = "This is sentence 1.";
        this.documentStore.put(new StringBufferInputStream(SDTXT1FileText), SDTXT1URI, DocumentStore.DocumentFormat.TXT);

        String SDTXT2 = "SDTXT2";
        URI SDTXT2URI = URI.create(SDTXT2);
        String SDTXT2FileText = "Here is another sentence.";
        this.documentStore.put(new StringBufferInputStream(SDTXT2FileText), SDTXT2URI, DocumentStore.DocumentFormat.TXT);

        String SDTXT3 = "SDTXT3";
        URI SDTXT3URI = URI.create(SDTXT3);
        String SDTXT3FileText = "This doc doesn't have the specified word.";
        this.documentStore.put(new StringBufferInputStream(SDTXT3FileText), SDTXT3URI, DocumentStore.DocumentFormat.TXT);

        List<Document> documents = this.documentStore.search("sentence");
        assertEquals(2, documents.size());
        assertTrue(documents.contains(this.documentStore.get(SDTXT1URI)));
        assertTrue(documents.contains(this.documentStore.get(SDTXT2URI)));
    }

    @Test
    void search_InMemoryAndDisk() throws IOException {
        String SDAMTXT1FileText = "This is sentence 1.";
        Document SDAMTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SDAMTXT1", SDAMTXT1FileText);

        String SDAMTXT2FileText = "Here is another sentence sentence.";
        Document SDAMTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SDAMTXT2", SDAMTXT2FileText);

        String SDAMTXT3FileText = "This doc doesn't have the specified word.";
        Document SDAMTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SDAMTXT3", SDAMTXT3FileText);


        this.documentStore.setMaxDocumentCount(2);
        List<Document> documents = this.documentStore.search("sentence");
        assertEquals(2, documents.size());
        assertTrue(documents.contains(this.documentStore.get(SDAMTXT1.getKey())));
        assertTrue(documents.contains(this.documentStore.get(SDAMTXT2.getKey())));
        assertTrue(this.docStoreURIs.contains(SDAMTXT1.getKey()));
        assertTrue(this.docStoreURIs.contains(SDAMTXT2.getKey()));
        for (Document document : documents){
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void searchByPrefix_inMemory() throws IOException {
        Document sbpimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpimTXT3"); //1
        Document sbpimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpimTXT2", "This sentence does not have the given prefix"); //0
        Document sbpimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpimTXT1", "This test we are looking for words that have the prefix te"); //2

        List<Document> sbp = this.documentStore.searchByPrefix("te");
        assertEquals(2, sbp.size());
        assertTrue(sbp.contains(sbpimTXT1));
        assertTrue(sbp.contains(sbpimTXT3));
        for (Document document : sbp){
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void searchByPrefix_InMemoryAndDisk() throws IOException {
        Document sbpimaodTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpimaodTXT1");

        String sbpimaodTXT2FileText = "Here is another sentence.";
        Document sbpimaodTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpimaodTXT2", sbpimaodTXT2FileText);

        String sbpimaodTXT3FileText = "This doc does have the specified prefix te ten.";
        Document sbpimaodTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "SDAMTXT3", sbpimaodTXT3FileText);

        this.documentStore.setMaxDocumentCount(2);
        List<Document> documents = this.documentStore.searchByPrefix("te");
        assertEquals(2, documents.size());
        assertTrue(documents.contains(sbpimaodTXT1));
        assertTrue(documents.contains(sbpimaodTXT3));
        assertTrue(this.docStoreURIs.contains(sbpimaodTXT1.getKey()));
        assertTrue(this.docStoreURIs.contains(sbpimaodTXT3.getKey()));
        for (Document document : documents){
            System.out.println(document.getDocumentTxt());
        }
    }

    @Test
    void deleteAll_inMemory() throws IOException {
        Document daimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimTXT1");
        Document daimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimTXT2", "This doc doesn't have the specified word");
        Document daimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "daimBinary1");
        Document daimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimTXT3");

        Set<URI> deletedDocs = this.documentStore.deleteAll("is");
        assertEquals(2, deletedDocs.size());
        assertTrue(deletedDocs.contains(daimTXT1.getKey()));
        assertTrue(deletedDocs.contains(daimTXT3.getKey()));
    }

    @Test
    void undoDeleteAllInMemory() throws IOException {
        Document udaimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimTXT1");
        Document udaimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimTXT2", "This doc doesn't have the specified word");
        Document udaimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "daimBinary1");
        Document udaimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimTXT3");

        Set<URI> deletedDocs = this.documentStore.deleteAll("is");
        assertFalse(docStoreURIs.contains(udaimTXT1.getKey()));
        assertFalse(docStoreURIs.contains(udaimTXT3.getKey()));
        this.documentStore.undo();
        assertTrue(docStoreURIs.contains(udaimTXT1.getKey()));
        assertTrue(docStoreURIs.contains(udaimTXT2.getKey()));
        assertTrue(docStoreURIs.contains(udaimBinary1.getKey()));
        assertTrue(docStoreURIs.contains(udaimTXT3.getKey()));
    }

    @Test
    void deleteAllInMemoryAndDisk() throws IOException {
        Document daimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimadTXT1");
        Document daimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimadTXT2", "This doc doesn't have the specified word");
        Document daimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "daimadBinary1");
        Document daimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimadTXT3");
        this.documentStore.setMaxDocumentCount(2);
        Set<URI> deletedDocs = this.documentStore.deleteAll("is");
        assertEquals(2, deletedDocs.size());
        assertTrue(deletedDocs.contains(daimadTXT1.getKey()));
        assertTrue(deletedDocs.contains(daimadTXT3.getKey()));
        assertFalse(docStoreURIs.contains(daimadTXT1.getKey()));
        assertFalse(docStoreURIs.contains(daimadTXT2.getKey()));
        assertTrue(docStoreURIs.contains(daimadBinary1.getKey()));
        assertFalse(docStoreURIs.contains(daimadTXT3.getKey()));
    }

    @Test
    void undoDeleteAllInMemoryAndDisk() throws IOException {
        Document udaimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimadTXT1");
        Document udaimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimadTXT2", "This doc doesn't have the specified word");
        Document udaimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "daimadBinary1");
        Document udaimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "daimadTXT3");
        this.documentStore.setMaxDocumentCount(2);
        this.documentStore.setMaxDocumentCount(4);
        Set<URI> deletedDocs = this.documentStore.deleteAll("is");
        this.documentStore.undo();
        assertEquals(2, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(udaimadTXT3.getKey()));
        assertTrue(docStoreURIs.contains(udaimadBinary1.getKey()));
    }

    @Test
    void undoSingleDocFromDeleteAllOnDisk() throws IOException {
        Document usdfdaTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "usdfdaTXT1", "jump");
        Document usdfdaTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "usdfdaTXT2", "jump again");
        Document usdfdaTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "usdfdaTXT3", "don't again");
        this.documentStore.setMaxDocumentCount(2);
        this.documentStore.setMaxDocumentCount(3);
        Set<URI> uris = this.documentStore.deleteAll("jump");
        assertEquals(1, docStoreURIs.size());
        assertEquals(2, uris.size());
        this.documentStore.undo(usdfdaTXT1.getKey());
        assertEquals(1, docStoreURIs.size());
        Set<URI> uris2 = this.documentStore.deleteAll("jump");
        assertEquals(1, uris2.size());
    }

    @Test
    void undoSingleDocFromDeleteAllInMemory() throws IOException {
        Document usdfdaIMTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "usdfdaIMTXT1", "jump");
        Document usdfdaIMTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "usdfdaIMTXT2", "jump again");
        Document usdfdaIMTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "usdfdaIMTXT3", "don't again");
        this.documentStore.setMaxDocumentCount(2);
        Set<URI> uris = this.documentStore.deleteAll("jump");
        assertEquals(1, docStoreURIs.size());
        assertEquals(2, uris.size());
        this.documentStore.undo(usdfdaIMTXT2.getKey());
        assertEquals(2, docStoreURIs.size());
        Set<URI> uris2 = this.documentStore.deleteAll("jump");
        assertEquals(1, uris2.size());
    }

    @Test
    void finishCommandSetAfterUndoSingleDocFromDeleteAllInMemory() throws IOException {
        Document FCSAusdfdaIMTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "FCSAusdfdaIMTXT1", "jump");
        Document FCSAusdfdaIMTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "FCSAusdfdaIMTXT2", "jump again");
        Document FCSAusdfdaIMTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "FCSAusdfdaIMTXT3", "don't again");
        //this.documentStore.setMaxDocumentCount(2);
        Set<URI> uris = this.documentStore.deleteAll("jump");
        assertEquals(1, docStoreURIs.size());
        assertEquals(2, uris.size());
        this.documentStore.undo(FCSAusdfdaIMTXT2.getKey());
        assertEquals(2, docStoreURIs.size());
        assertEquals(1, this.documentStore.search("jump").size());
        this.documentStore.undo();
        assertEquals(2, this.documentStore.search("jump").size());
    }


    @Test
    void deleteAllWithPrefixInMemory() throws IOException {
        Document dapimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimTXT1");
        Document dapimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimTXT2", "This doc doesn't have the specified word");
        Document dapimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapimBinary1");
        Document dapimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimTXT3");

        Set<URI> deletedDocs = this.documentStore.deleteAllWithPrefix("so");
        assertEquals(2, deletedDocs.size());
        assertTrue(deletedDocs.contains(dapimTXT1.getKey()));
        assertTrue(deletedDocs.contains(dapimTXT3.getKey()));
        assertEquals(2, docStoreURIs.size());
    }

    @Test
    void undoDeleteAllWithPrefixInMemory() throws IOException {
        Document udapimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapimTXT1");
        Document udapimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapimTXT2", "This doc doesn't have the specified word");
        Document udapimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udapimBinary1");
        Document udapimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapimTXT3");

        Set<URI> deletedDocs = this.documentStore.deleteAllWithPrefix("so");
        assertEquals(2, deletedDocs.size());
        assertTrue(deletedDocs.contains(udapimTXT1.getKey()));
        assertTrue(deletedDocs.contains(udapimTXT3.getKey()));
        assertEquals(2, docStoreURIs.size());
        this.documentStore.undo();
        assertTrue(docStoreURIs.contains(udapimTXT1.getKey()));
        assertTrue(docStoreURIs.contains(udapimTXT2.getKey()));
        assertTrue(docStoreURIs.contains(udapimBinary1.getKey()));
        assertTrue(docStoreURIs.contains(udapimTXT3.getKey()));
    }

    @Test
    void deleteAllWithPrefixInMemoryAndOnDisk() throws IOException {
        Document dapimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimadTXT1");
        Document dapimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimadTXT2", "This doc doesn't have the specified word");
        Document dapimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapimadBinary1");
        Document dapimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimadTXT3");
        this.documentStore.setMaxDocumentCount(2);
        Set<URI> deletedDocs = this.documentStore.deleteAll("some");
        assertEquals(2, deletedDocs.size());
        assertTrue(deletedDocs.contains(dapimadTXT1.getKey()));
        assertTrue(deletedDocs.contains(dapimadTXT3.getKey()));
        assertFalse(docStoreURIs.contains(dapimadTXT1.getKey()));
        assertFalse(docStoreURIs.contains(dapimadTXT2.getKey()));
        assertTrue(docStoreURIs.contains(dapimadBinary1.getKey()));
        assertFalse(docStoreURIs.contains(dapimadTXT3.getKey()));
    }

    @Test
    void undoDeleteAllWithPrefixInMemoryAndOnDisk() throws IOException {
        Document udapimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimadTXT1");
        Document udapimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimadTXT2", "This doc doesn't have the specified word");
        Document udapimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapimadBinary1");
        Document udapimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapimadTXT3");
        this.documentStore.setMaxDocumentCount(2);
        this.documentStore.setMaxDocumentCount(4);
        Set<URI> deletedDocs = this.documentStore.deleteAll("some");
        this.documentStore.undo();
        assertEquals(2, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(udapimadTXT3.getKey()));
        assertTrue(docStoreURIs.contains(udapimadBinary1.getKey()));
    }

    @Test
    void searchByMetadataInMemory() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document sbmimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbmimTXT1");
        Document sbmimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbmimTXT2");
        Document sbmimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbmimBinary1");
        Document sbmimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbmimTXT3");
        Document sbmimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbmimBinary2");
        this.documentStore.setMetadata(sbmimTXT1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(sbmimTXT1.getKey(), "key2", "value2");
        this.documentStore.setMetadata(sbmimTXT2.getKey(), "key1", "value1");
        this.documentStore.setMetadata(sbmimBinary1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(sbmimBinary1.getKey(), "key2", "value2");
        List<Document> returnedDocumentList = this.documentStore.searchByMetadata(testMap);
        assertEquals(2, returnedDocumentList.size());
        assertTrue(returnedDocumentList.contains(sbmimTXT1));
        assertTrue(returnedDocumentList.contains(sbmimBinary1));
    }
    @Test
    void searchByMetadataInMemoryAndDisk() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document sbmimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbmimadTXT1");
        Document sbmimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbmimadTXT2");
        Document sbmimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbmimadBinary1");
        Document sbmimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbmimadTXT3");
        Document sbmimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbmimadBinary2");
        this.documentStore.setMetadata(sbmimadTXT1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(sbmimadTXT1.getKey(), "key2", "value2");
        this.documentStore.setMetadata(sbmimadTXT2.getKey(), "key1", "value1");
        this.documentStore.setMetadata(sbmimadBinary1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(sbmimadBinary1.getKey(), "key2", "value2");
        this.documentStore.get(sbmimadTXT1.getKey());//has data
        this.documentStore.get(sbmimadTXT2.getKey());
        this.documentStore.get(sbmimadTXT3.getKey());
        this.documentStore.get(sbmimadBinary1.getKey());
        this.documentStore.get(sbmimadBinary2.getKey());//has data
        this.documentStore.setMaxDocumentCount(1);
        List<Document> returnedDocumentList = this.documentStore.searchByMetadata(testMap);
        assertEquals(2, returnedDocumentList.size());
        assertTrue(returnedDocumentList.contains(sbmimadTXT1));
        assertTrue(returnedDocumentList.contains(sbmimadBinary1));
        assertEquals(1, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(sbmimadBinary1.getKey()));

    }

    @Test
    void searchByKeywordAndMetadataIM() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document sbkamdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbkamdimTXT1", "Here is some text for txt 1. The word is bananas");
        Document sbkamdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbkamdimBinary1");
        Document sbkamdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbkamdimTXT2");
        Document sbkamdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbkamdimBinary2");
        Document sbkamdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbkamdimTXT3", "Monkeys eat bananas");
        this.documentStore.setMetadata(sbkamdimTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbkamdimTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(sbkamdimBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbkamdimBinary1.getKey(),"key2", "value2");
        List<Document> documents = this.documentStore.searchByKeywordAndMetadata("bananas", testMap);
        assertEquals(1, documents.size());
        assertTrue(documents.contains(sbkamdimTXT1));
    }

    @Test
    void searchByKeywordAndMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document sbkamdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbkamdimadTXT1", "Here is some text for txt 1. The word is bananas");
        Document sbkamdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbkamdimadBinary1");
        Document sbkamdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbkamdimadTXT2");
        Document sbkamdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbkamdimadBinary2");
        Document sbkamdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbkamdimadTXT3", "Monkeys eat bananas");
        Document sbkamdimadTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbkamdimadTXT4", "Here is some text for txt 4. The word is bananas");
        this.documentStore.setMetadata(sbkamdimadTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbkamdimadTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(sbkamdimadTXT4.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbkamdimadTXT4.getKey(),"key2", "value2");
        this.documentStore.setMetadata(sbkamdimadBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbkamdimadBinary1.getKey(),"key2", "value2");
        this.documentStore.get(sbkamdimadTXT1.getKey());
        this.documentStore.get(sbkamdimadBinary1.getKey());
        this.documentStore.get(sbkamdimadTXT2.getKey());
        this.documentStore.get(sbkamdimadBinary2.getKey());
        this.documentStore.get(sbkamdimadTXT3.getKey());
        this.documentStore.get(sbkamdimadTXT4.getKey());
        documentStore.setMaxDocumentCount(5);
        List<Document> documents = this.documentStore.searchByKeywordAndMetadata("bananas", testMap);
        assertEquals(2, documents.size());
        assertTrue(documents.contains(sbkamdimadTXT1));
        assertTrue(documents.contains(sbkamdimadTXT4));
        assertFalse(docStoreURIs.contains(sbkamdimadBinary1.getKey()));
        assertFalse(docStoreURIs.contains(sbkamdimadBinary1.getKey()));assertEquals(5, docStoreURIs.size());
    }

    @Test
    void searchByPrefixAndMetadataIM() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document sbpamdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpamdimTXT1", "Here is some text for txt 1. The word is bananas");
        Document sbpamdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbpamdimBinary1");
        Document sbpamdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpamdimTXT2");
        Document sbpamdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbpamdimBinary2");
        Document sbpamdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpamdimTXT3", "Monkeys eat bananas");
        this.documentStore.setMetadata(sbpamdimTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbpamdimTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(sbpamdimBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbpamdimBinary1.getKey(),"key2", "value2");
        List<Document> documents = this.documentStore.searchByPrefixAndMetadata("banan", testMap);
        assertEquals(1, documents.size());
        assertTrue(documents.contains(sbpamdimTXT1));
    }

    @Test
    void searchByPrefixAndMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document sbpamdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpamdimadTXT1", "Here is some text for txt 1. The word is bananas");
        Document sbpamdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbpamdimadBinary1");
        Document sbpamdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpamdimadTXT2");
        Document sbpamdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "sbpamdimadBinary2");
        Document sbpamdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpamdimadTXT3", "Monkeys eat bananas");
        Document sbpamdimadTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "sbpamdimadTXT4", "Here is some text for txt 4. The word is bananas");
        this.documentStore.setMetadata(sbpamdimadTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbpamdimadTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(sbpamdimadTXT4.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbpamdimadTXT4.getKey(),"key2", "value2");
        this.documentStore.setMetadata(sbpamdimadBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(sbpamdimadBinary1.getKey(),"key2", "value2");
        this.documentStore.get(sbpamdimadTXT1.getKey());
        this.documentStore.get(sbpamdimadBinary1.getKey());
        this.documentStore.get(sbpamdimadTXT2.getKey());
        this.documentStore.get(sbpamdimadBinary2.getKey());
        this.documentStore.get(sbpamdimadTXT3.getKey());
        this.documentStore.get(sbpamdimadTXT4.getKey());
        documentStore.setMaxDocumentCount(5);
        List<Document> documents = this.documentStore.searchByPrefixAndMetadata("banan", testMap);
        assertEquals(2, documents.size());
        assertTrue(documents.contains(sbpamdimadTXT1));
        assertTrue(documents.contains(sbpamdimadTXT4));
        assertFalse(docStoreURIs.contains(sbpamdimadBinary1.getKey()));
        assertFalse(docStoreURIs.contains(sbpamdimadBinary1.getKey()));
        assertEquals(5, docStoreURIs.size());
    }

    @Test
    void deleteAllWithMetadataInMemory() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dawmdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dawmdimTXT1");
        Document dawmdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dawmdimTXT2");
        Document dawmdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dawmdimBinary1");
        Document dawmdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dawmdimTXT3");
        Document dawmdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dawmdimBinary2");
        this.documentStore.setMetadata(dawmdimTXT1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(dawmdimTXT1.getKey(), "key2", "value2");
        this.documentStore.setMetadata(dawmdimTXT2.getKey(), "key1", "value1");
        this.documentStore.setMetadata(dawmdimBinary1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(dawmdimBinary1.getKey(), "key2", "value2");
        Set<URI> deletedDocs = this.documentStore.deleteAllWithMetadata(testMap);
        assertEquals(2, deletedDocs.size());
        assertTrue(deletedDocs.contains(dawmdimTXT1.getKey()));
        assertTrue(deletedDocs.contains(dawmdimBinary1.getKey()));
        assertEquals(3, docStoreURIs.size());
    }

    @Test
    void undoDeleteAllWithMetadataInMemory() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document udawmdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udawmdimTXT1");
        Document udawmdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udawmdimTXT2");
        Document udawmdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udawmdimBinary1");
        Document udawmdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udawmdimTXT3");
        Document udawmdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udawmdimBinary2");
        this.documentStore.setMetadata(udawmdimTXT1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(udawmdimTXT1.getKey(), "key2", "value2");
        this.documentStore.setMetadata(udawmdimTXT2.getKey(), "key1", "value1");
        this.documentStore.setMetadata(udawmdimBinary1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(udawmdimBinary1.getKey(), "key2", "value2");
        Set<URI> deletedDocs = this.documentStore.deleteAllWithMetadata(testMap);
        assertEquals(2, deletedDocs.size());
        assertTrue(deletedDocs.contains(udawmdimTXT1.getKey()));
        assertTrue(deletedDocs.contains(udawmdimBinary1.getKey()));
        assertEquals(3, docStoreURIs.size());
        this.documentStore.undo();
        assertEquals(5, docStoreURIs.size());
    }

    @Test
    void deleteAllWithMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dawmdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dawmdimadTXT1");
        Document dawmdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dawmdimadTXT2");
        Document dawmdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dawmdimadBinary1");
        Document dawmdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dawmdimadTXT3");
        Document dawmdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dawmdimadBinary2");
        this.documentStore.setMetadata(dawmdimadTXT1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(dawmdimadTXT1.getKey(), "key2", "value2");
        this.documentStore.setMetadata(dawmdimadTXT2.getKey(), "key1", "value1");
        this.documentStore.setMetadata(dawmdimadBinary1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(dawmdimadBinary1.getKey(), "key2", "value2");
        this.documentStore.get(dawmdimadTXT3.getKey());
        this.documentStore.get(dawmdimadBinary2.getKey());
        this.documentStore.setMaxDocumentCount(3);
        Set<URI> returnedDocs = this.documentStore.deleteAllWithMetadata(testMap);
        assertEquals(2, returnedDocs.size());
        assertEquals(2, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(dawmdimadTXT2.getKey()));
        /*this.documentStore.setMaxDocumentBytes(5);
        this.documentStore.undo();
        assertEquals(3, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(dawmdimadBinary1.getKey()));
        assertTrue(docStoreURIs.contains(dawmdimadBinary2.getKey()));
        assertTrue(docStoreURIs.contains(dawmdimadTXT3.getKey()));*/
    }

    @Test
    void undoDeleteAllWithMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document udawmdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udawmdimadTXT1");
        Document udawmdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udawmdimadTXT2");
        Document udawmdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udawmdimadBinary1");
        Document udawmdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udawmdimadTXT3");
        Document udawmdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udawmdimadBinary2");
        this.documentStore.setMetadata(udawmdimadTXT1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(udawmdimadTXT1.getKey(), "key2", "value2");
        this.documentStore.setMetadata(udawmdimadTXT2.getKey(), "key1", "value1");
        this.documentStore.setMetadata(udawmdimadBinary1.getKey(), "key1", "value1");
        this.documentStore.setMetadata(udawmdimadBinary1.getKey(), "key2", "value2");
        this.documentStore.get(udawmdimadTXT3.getKey());
        this.documentStore.get(udawmdimadBinary2.getKey());
        this.documentStore.setMaxDocumentCount(3);
        Set<URI> returnedDocs = this.documentStore.deleteAllWithMetadata(testMap);
        assertEquals(2, returnedDocs.size());
        assertEquals(2, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(udawmdimadTXT2.getKey()));
        this.documentStore.setMaxDocumentCount(5);
        this.documentStore.undo();
        assertEquals(3, docStoreURIs.size());
        assertTrue(docStoreURIs.contains(udawmdimadBinary1.getKey()));
        assertTrue(docStoreURIs.contains(udawmdimadBinary2.getKey()));
        assertTrue(docStoreURIs.contains(udawmdimadTXT3.getKey()));
    }

    @Test
    void deleteAllWithKeywordAndMetadataIM() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dakamdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimBinary1"); //yes md
        Document dakamdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimBinary2"); //no md
        Document dakamdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT1", "Here is some text for txt 1. The word is bananas");
        Document dakamdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT2", "bananas");
        Document dakamdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT3");
        Document dakamdimTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT4");
        this.documentStore.setMetadata(dakamdimTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dakamdimTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimTXT3.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimTXT4.getKey());
        this.documentStore.setMetadata(dakamdimBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimBinary1.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimBinary2.getKey());


        Set<URI> documents = this.documentStore.deleteAllWithKeywordAndMetadata("bananas", testMap);
        assertEquals(1, documents.size());
        assertTrue(documents.contains(dakamdimTXT1.getKey()));
    }

    @Test
    void undoDeleteAllWithKeywordAndMetadataIM() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dakamdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimBinary1"); //yes md
        Document dakamdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimBinary2"); //no md
        Document dakamdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT1", "Here is some text for txt 1. The word is bananas");
        Document dakamdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT2", "bananas");
        Document dakamdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT3");
        Document dakamdimTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimTXT4");
        this.documentStore.setMetadata(dakamdimTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dakamdimTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimTXT3.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimTXT4.getKey());
        this.documentStore.setMetadata(dakamdimBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimBinary1.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimBinary2.getKey());

        Set<URI> documents = this.documentStore.deleteAllWithKeywordAndMetadata("bananas", testMap);
        assertEquals(5, docStoreURIs.size());
        this.documentStore.undo();
        assertEquals(6, docStoreURIs.size());
    }

    @Test
    void deleteAllWithKeywordAndMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dakamdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimadBinary1"); //yes md
        Document dakamdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimadBinary2"); //no md
        Document dakamdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT1", "Here is some text for txt 1. The word is bananas");
        Document dakamdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT2", "bananas");
        Document dakamdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT3");
        Document dakamdimadTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT4");
        this.documentStore.setMetadata(dakamdimadTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dakamdimadTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadTXT3.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimadTXT4.getKey());
        this.documentStore.setMetadata(dakamdimadBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadBinary1.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimadBinary2.getKey());

        Document dakamdimadBinary3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimadBinary3");
        Document dakamdimadBinary4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dakamdimadBinary4");
        Document dakamdimadTXT5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT5", "Here is some text for txt 1. The word is bananas");
        Document dakamdimadTXT6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT6", "bananas");
        Document dakamdimadTXT7 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT7");
        Document dakamdimadTXT8 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dakamdimadTXT8");
        this.documentStore.setMetadata(dakamdimadTXT5.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadTXT5.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dakamdimadTXT6.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadTXT7.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadTXT7.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimadTXT8.getKey());
        this.documentStore.setMetadata(dakamdimadBinary3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dakamdimadBinary3.getKey(),"key2", "value2");
        this.documentStore.get(dakamdimadBinary4.getKey());

        this.documentStore.setMaxDocumentCount(6);
        Set<URI> documents = this.documentStore.deleteAllWithKeywordAndMetadata("bananas", testMap);
        assertEquals(2, documents.size());
        assertTrue(documents.contains(dakamdimadTXT1.getKey()));
        assertTrue(documents.contains(dakamdimadTXT5.getKey()));
        assertEquals(5, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(dakamdimadTXT5.getKey()));
        assertTrue(docStoreURIs.contains(dakamdimadTXT6.getKey()));
        assertTrue(docStoreURIs.contains(dakamdimadTXT7.getKey()));
        assertTrue(docStoreURIs.contains(dakamdimadTXT8.getKey()));
        assertTrue(docStoreURIs.contains(dakamdimadBinary3.getKey()));
        assertTrue(docStoreURIs.contains(dakamdimadBinary4.getKey()));
    }

    @Test
    void undoDeleteAllWithKeywordAndMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document udakamdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "duakamdimadBinary1"); //yes md
        Document udakamdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udakamdimadBinary2"); //no md
        Document udakamdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT1", "Here is some text for txt 1. The word is bananas");
        Document udakamdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT2", "bananas");
        Document udakamdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT3");
        Document udakamdimadTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT4");
        this.documentStore.setMetadata(udakamdimadTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(udakamdimadTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadTXT3.getKey(),"key2", "value2");
        this.documentStore.get(udakamdimadTXT4.getKey());
        this.documentStore.setMetadata(udakamdimadBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadBinary1.getKey(),"key2", "value2");
        this.documentStore.get(udakamdimadBinary2.getKey());

        Document udakamdimadBinary3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udakamdimadBinary3");
        Document udakamdimadBinary4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udakamdimadBinary4");
        Document udakamdimadTXT5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT5", "Here is some text for txt 1. The word is bananas");
        Document udakamdimadTXT6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT6", "bananas");
        Document udakamdimadTXT7 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT7");
        Document udakamdimadTXT8 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udakamdimadTXT8");
        this.documentStore.setMetadata(udakamdimadTXT5.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadTXT5.getKey(),"key2", "value2");
        this.documentStore.setMetadata(udakamdimadTXT6.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadTXT7.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadTXT7.getKey(),"key2", "value2");
        this.documentStore.get(udakamdimadTXT8.getKey());
        this.documentStore.setMetadata(udakamdimadBinary3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udakamdimadBinary3.getKey(),"key2", "value2");
        this.documentStore.get(udakamdimadBinary4.getKey());

        this.documentStore.setMaxDocumentCount(6);
        Set<URI> documents = this.documentStore.deleteAllWithKeywordAndMetadata("bananas", testMap);
        assertEquals(5, this.docStoreURIs.size());
        assertEquals(2, documents.size());
        this.documentStore.setMaxDocumentCount(12);
        this.documentStore.undo();
        assertEquals(6, this.docStoreURIs.size());
        assertTrue(docStoreURIs.contains(udakamdimadTXT5.getKey()));
        assertTrue(docStoreURIs.contains(udakamdimadTXT6.getKey()));
        assertTrue(docStoreURIs.contains(udakamdimadTXT7.getKey()));
        assertTrue(docStoreURIs.contains(udakamdimadTXT8.getKey()));
        assertTrue(docStoreURIs.contains(udakamdimadBinary3.getKey()));
        assertTrue(docStoreURIs.contains(udakamdimadBinary4.getKey()));
    }

    @Test
    void deleteAllWithPrefixAndMetadataIM() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dapamdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimBinary1"); //yes md
        Document dapamdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimBinary2"); //no md
        Document dapamdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT1", "Here is some text for txt 1. The word is bananas");
        Document dapamdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT2", "bananas");
        Document dapamdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT3");
        Document dapamdimTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT4");
        this.documentStore.setMetadata(dapamdimTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dapamdimTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimTXT3.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimTXT4.getKey());
        this.documentStore.setMetadata(dapamdimBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimBinary1.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimBinary2.getKey());


        Set<URI> documents = this.documentStore.deleteAllWithPrefixAndMetadata("banan", testMap);
        assertEquals(1, documents.size());
        assertTrue(documents.contains(dapamdimTXT1.getKey()));
    }

    @Test
    void undoDeleteAllWithPrefixAndMetadataIM() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dapamdimBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimBinary1"); //yes md
        Document dapamdimBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimBinary2"); //no md
        Document dapamdimTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT1", "Here is some text for txt 1. The word is bananas");
        Document dapamdimTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT2", "bananas");
        Document dapamdimTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT3");
        Document dapamdimTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimTXT4");
        this.documentStore.setMetadata(dapamdimTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dapamdimTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimTXT3.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimTXT4.getKey());
        this.documentStore.setMetadata(dapamdimBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimBinary1.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimBinary2.getKey());

        Set<URI> documents = this.documentStore.deleteAllWithPrefixAndMetadata("banan", testMap);
        assertEquals(5, docStoreURIs.size());
        this.documentStore.undo();
        assertEquals(6, docStoreURIs.size());
    }

    @Test
    void deleteAllWithPrefixAndMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document dapamdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimadBinary1"); //yes md
        Document dapamdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimadBinary2"); //no md
        Document dapamdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT1", "Here is some text for txt 1. The word is bananas");
        Document dapamdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT2", "bananas");
        Document dapamdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT3");
        Document dapamdimadTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT4");
        this.documentStore.setMetadata(dapamdimadTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dapamdimadTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadTXT3.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimadTXT4.getKey());
        this.documentStore.setMetadata(dapamdimadBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadBinary1.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimadBinary2.getKey());

        Document dapamdimadBinary3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimadBinary3");
        Document dapamdimadBinary4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "dapamdimadBinary4");
        Document dapamdimadTXT5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT5", "Here is some text for txt 1. The word is bananas");
        Document dapamdimadTXT6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT6", "bananas");
        Document dapamdimadTXT7 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT7");
        Document dapamdimadTXT8 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "dapamdimadTXT8");
        this.documentStore.setMetadata(dapamdimadTXT5.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadTXT5.getKey(),"key2", "value2");
        this.documentStore.setMetadata(dapamdimadTXT6.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadTXT7.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadTXT7.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimadTXT8.getKey());
        this.documentStore.setMetadata(dapamdimadBinary3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(dapamdimadBinary3.getKey(),"key2", "value2");
        this.documentStore.get(dapamdimadBinary4.getKey());

        this.documentStore.setMaxDocumentCount(6);
        Set<URI> documents = this.documentStore.deleteAllWithPrefixAndMetadata("banan", testMap);
        assertEquals(2, documents.size());
        assertTrue(documents.contains(dapamdimadTXT1.getKey()));
        assertTrue(documents.contains(dapamdimadTXT5.getKey()));
        assertEquals(5, docStoreURIs.size());
        assertFalse(docStoreURIs.contains(dapamdimadTXT5.getKey()));
        assertTrue(docStoreURIs.contains(dapamdimadTXT6.getKey()));
        assertTrue(docStoreURIs.contains(dapamdimadTXT7.getKey()));
        assertTrue(docStoreURIs.contains(dapamdimadTXT8.getKey()));
        assertTrue(docStoreURIs.contains(dapamdimadBinary3.getKey()));
        assertTrue(docStoreURIs.contains(dapamdimadBinary4.getKey()));
    }

    @Test
    void undoDeleteAllWithPrefixAndMetadataIMAD() throws IOException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        testMap.put("key2", "value2");
        Document udapamdimadBinary1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udapamdimadBinary1"); //yes md
        Document udapamdimadBinary2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udapamdimadBinary2"); //no md
        Document udapamdimadTXT1 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT1", "Here is some text for txt 1. The word is bananas");
        Document udapamdimadTXT2 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT2", "bananas");
        Document udapamdimadTXT3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT3");
        Document udapamdimadTXT4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT4");
        this.documentStore.setMetadata(udapamdimadTXT1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadTXT1.getKey(),"key2", "value2");
        this.documentStore.setMetadata(udapamdimadTXT2.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadTXT3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadTXT3.getKey(),"key2", "value2");
        this.documentStore.get(udapamdimadTXT4.getKey());
        this.documentStore.setMetadata(udapamdimadBinary1.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadBinary1.getKey(),"key2", "value2");
        this.documentStore.get(udapamdimadBinary2.getKey());

        Document udapamdimadBinary3 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udapamdimadBinary3");
        Document udapamdimadBinary4 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.BINARY, "udapamdimadBinary4");
        Document udapamdimadTXT5 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT5", "Here is some text for txt 1. The word is bananas");
        Document udapamdimadTXT6 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT6", "bananas");
        Document udapamdimadTXT7 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT7");
        Document udapamdimadTXT8 = documentCreator.createAndAddNewDocumentToStore(DocumentStore.DocumentFormat.TXT, "udapamdimadTXT8");
        this.documentStore.setMetadata(udapamdimadTXT5.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadTXT5.getKey(),"key2", "value2");
        this.documentStore.setMetadata(udapamdimadTXT6.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadTXT7.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadTXT7.getKey(),"key2", "value2");
        this.documentStore.get(udapamdimadTXT8.getKey());
        this.documentStore.setMetadata(udapamdimadBinary3.getKey(),"key1", "value1");
        this.documentStore.setMetadata(udapamdimadBinary3.getKey(),"key2", "value2");
        this.documentStore.get(udapamdimadBinary4.getKey());

        this.documentStore.setMaxDocumentCount(6);
        Set<URI> documents = this.documentStore.deleteAllWithKeywordAndMetadata("bananas", testMap);
        assertEquals(5, this.docStoreURIs.size());
        assertEquals(2, documents.size());
        this.documentStore.setMaxDocumentCount(12);
        this.documentStore.undo();
        assertEquals(6, this.docStoreURIs.size());
        assertTrue(docStoreURIs.contains(udapamdimadTXT5.getKey()));
        assertTrue(docStoreURIs.contains(udapamdimadTXT6.getKey()));
        assertTrue(docStoreURIs.contains(udapamdimadTXT7.getKey()));
        assertTrue(docStoreURIs.contains(udapamdimadTXT8.getKey()));
        assertTrue(docStoreURIs.contains(udapamdimadBinary3.getKey()));
        assertTrue(docStoreURIs.contains(udapamdimadBinary4.getKey()));
    }


    @Test
    void setMaxDocumentCount() {
    }

    @Test
    void setMaxDocumentBytes() {
    }


    private FileInput createNewTXTFile(String fileName, String fileTXT) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        myWriter.write(fileTXT);
        myWriter.close();
        FileInput fileInput = new FileInput(file.getPath());
        //this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.TXT);
        return fileInput;
    }

    private FileInput createNewBinaryFile(String fileName) throws IOException {
        String destinationFolder = "C:\\Users\\heich\\Desktop\\code\\MyRepo\\Seif_Avraham_800699054\\DataStructures\\project\\Stage5\\src\\main\\resources";
        File file = new File(fileName);
        file.createNewFile();
        FileWriter myWriter = new FileWriter(file);
        FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        byte[] data = generateRandomByteArray(random);
        oos.writeObject(data);
        oos.close();
        FileInput fileInput = new FileInput(file.getPath());
        //this.documentStore.put(fileInput.getFis(), fileInput.getUrl(), DocumentStore.DocumentFormat.BINARY);
        return fileInput;
    }

    public static byte[] generateRandomByteArray(Random random) {
        int size = random.nextInt(100) + 1;
        byte[] byteArray = new byte[size];
        for (int i = 0; i < size; i++) {
            byteArray[i] = (byte) (random.nextInt(100) + 1);
        }
        return byteArray;
    }

    private class DocumentCreator{
        private Document createAndAddNewDocumentToStore(DocumentStore.DocumentFormat format, String fileName) throws IOException {
            URI uri = URI.create(getPathToFile(fileName));
            if(format.equals(DocumentStore.DocumentFormat.TXT)){
                String text = "this is some text for " + fileName;
                InputStream inputStream = new StringBufferInputStream(text);
                documentStore.put(inputStream, uri, DocumentStore.DocumentFormat.TXT);
            } else {
                byte[] binary1bytes = generateRandomByteArray(random);
                InputStream binaryInputStream = new ByteArrayInputStream(binary1bytes);
                documentStore.put(binaryInputStream, uri, DocumentStore.DocumentFormat.BINARY);
            }
            return documentStore.get(uri);
        }

        private Document createAndAddNewDocumentToStore(DocumentStore.DocumentFormat format, String fileName, String fileText) throws IOException {
            URI uri = URI.create(getPathToFile(fileName));
            if(format.equals(DocumentStore.DocumentFormat.TXT)){
                InputStream inputStream = new StringBufferInputStream(fileText);
                documentStore.put(inputStream, uri, DocumentStore.DocumentFormat.TXT);
            } else {
                byte[] binary1bytes = generateRandomByteArray(random);
                InputStream binaryInputStream = new ByteArrayInputStream(binary1bytes);
                documentStore.put(binaryInputStream, uri, DocumentStore.DocumentFormat.BINARY);
            }
            return documentStore.get(uri);
        }
    }
    /*
     * @param objectToReflect - the class in which the field you
     * field = the data structure or field you want to reflect
     */
    private Object reflectField(Object objectToReflect, String field) throws NoSuchFieldException, IllegalAccessException {
        Class<?> classObject = objectToReflect.getClass();
        Field classField = classObject.getDeclaredField(field);
        classField.setAccessible(true);
        return classField.get(documentStore);
    }

    private String getPathToFile(String fileName) {
        return "http://www.yu.edu/docStoreTest/" + fileName;
    }
}
