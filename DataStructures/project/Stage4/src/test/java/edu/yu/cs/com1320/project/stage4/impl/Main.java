package edu.yu.cs.com1320.project.stage4.impl;

import edu.yu.cs.com1320.project.Trie;
import edu.yu.cs.com1320.project.impl.TooSimpleTrie;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;

import java.io.*;
import java.net.URI;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws IOException {
        DocumentStoreImpl documentStore = new DocumentStoreImpl();
        addDocumentToStore(documentStore, createNewFile("Test File 1", "to"));
        addDocumentToStore(documentStore, createNewFile("Test File 6", "tow toggle"));
        addDocumentToStore(documentStore, createNewFile("Test File 3", "toe"));
        addDocumentToStore(documentStore, createNewFile("Test File 4", "tool tool tool tough tore"));
        addDocumentToStore(documentStore, createNewFile("Test File 2", "ton"));
        addDocumentToStore(documentStore, createNewFile("Test File 5", "tooth together"));
        addDocumentToStore(documentStore, createNewFile("Test File 7", "ton ton ton too top topple tomorrow"));
        addDocumentToStore(documentStore, createNewFile("Test File 8", "top top top top topple"));
        List<Document> documents = documentStore.search("ton");
        //List<Document> documents = documentStore.searchByPrefix("to");
        for (Document document : documents)
            System.out.println(document.getDocumentTxt());
    }

    private void test1(){
        TooSimpleTrie<String> trie = new TooSimpleTrie<String>() {};
        trie.put("The", "The");
        trie.put("Thin", "Thin");
        trie.put("Thinner", "Thinner");
        trie.put("This", "This");

        trie.deleteAll("The");
        trie.put("The", "The");

        trie.deleteAll("Thin");
        trie.put("Thin", "Thin");

        trie.deleteAll("Thinner");
        trie.put("Thinner", "Thinner");

        trie.deleteAll("This");
        trie.put("This", "This");

    }

    private static void addDocumentToStore(DocumentStoreImpl documentStore, FileInput fileInput) throws IOException {
        documentStore.put(fileInput.fis, fileInput.url, DocumentStore.DocumentFormat.TXT);
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
}
