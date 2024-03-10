package edu.yu.cs.com1320.project.stage4.impl;

import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;
import edu.yu.cs.com1320.project.undo.Command;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class DocumentStoreImpl implements DocumentStore {
    private StackImpl<Command> commandStack;
    protected HashTableImpl<URI, Document> documentStore;
    protected TrieImpl<Document> documentWordsTrie;

    public DocumentStoreImpl(){
        this.commandStack = new StackImpl<>();
        this.documentStore = new HashTableImpl<>();
        this.documentWordsTrie = new TrieImpl<>();
    }
    /**
     * set the given key-value metadata pair for the document at the given uri
     *
     * @param uri - uri of the document to set metadata
     * @param key - metadata key
     * @param value - metadata value
     * @return the old value, or null if there was no previous value
     * @throws IllegalArgumentException if the uri is null or blank, if there is no document stored at that uri, or if the key is null or blank
     */
    @Override
    public String setMetadata(URI uri, String key, String value) {
        if(uri == null || uri.toString().isEmpty() || get(uri) == null || key == null || key.isEmpty()){
            throw new IllegalArgumentException();
        }
        String oldKey;
        String oldValue;
        if(getMetadata(uri, key) == null){
            oldKey = null;
        } else {
            oldKey = key;
        }
        Document doc = get(uri);
        oldValue = doc.getMetadataValue(key);
        doc.setMetadataValue(key, value);
        Consumer<URI> undo =
                HashTableImpl -> {
                    if(oldKey == null){
                        doc.setMetadataValue(key, null);
                    }else{
                        doc.setMetadataValue(key, oldValue);
                    }
                };
        this.commandStack.push(new Command(uri, undo));
        return oldValue;
    }

    /**
     * get the value corresponding to the given metadata key for the document at the given uri
     *
     * @param uri - uri of the document
     * @param key - key of the metadata to return
     * @return the value, or null if there was no value
     * @throws IllegalArgumentException if the uri is null or blank, if there is no document stored at that uri, or if the key is null or blank
     */
    @Override
    public String getMetadata(URI uri, String key) {
        if(uri == null || key == null || key.isEmpty() || get(uri) == null || uri.toString().isEmpty()) throw new IllegalArgumentException();
        return get(uri).getMetadataValue(key);
    }

    /**
     * @param input  the document being put
     * @param url    unique identifier for the document
     * @param format indicates which type of document format is being passed
     * @return if there is no previous doc at the given URI, return 0. If there is a previous doc, return the hashCode of the previous doc.
     * If InputStream is null, this is a delete, and thus return either the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException              if there is an issue reading input
     * @throws IllegalArgumentException if url or format are null
     */
    @Override
    public int put(InputStream input, URI url, DocumentFormat format) throws IOException {
        if (format == null || url == null || url.toString().isEmpty()) throw new IllegalArgumentException();
        boolean docExists;
        int previousHashCode = 0;
        Document previousDocument = null;
        if(get(url) != null){
            docExists = true;
            previousHashCode = get(url).hashCode();
            previousDocument = this.documentStore.get(url);
        } else {
            docExists = false;
        }
        if (input == null) return handleNullInput(url, docExists, previousHashCode);
        byte[] contents = input.readAllBytes();
        createAndAddDocument(url, format, contents);
        Consumer<URI> undo = getUndoConsumerForPut(url, docExists, previousDocument);
        this.commandStack.push(new Command(url, undo));
        return (docExists) ? previousHashCode : 0;
    }

    private void createAndAddDocument(URI url, DocumentFormat format, byte[] contents) {
        DocumentImpl document;
        if (format == DocumentFormat.BINARY) {
            document = new DocumentImpl(url, contents);
            this.documentStore.put(url, document);
        } else {
            document = new DocumentImpl(url, new String(contents));
            this.documentStore.put(url, document);
            addDocumentWordsToTrie(document);
        }
    }

    private void addDocumentWordsToTrie(DocumentImpl document){
        for (String word : document.getWordCountMap()){
            documentWordsTrie.put(word, document);
        }
    }

    private Consumer<URI> getUndoConsumerForPut(URI url, boolean docExists, Document previousDocument) {
        return HashTableImpl -> {
            if(docExists){
                this.documentStore.put(url, previousDocument);
            }else{
                this.documentStore.put(url, null);
            }
        };
    }


    private int handleNullInput(URI url, boolean docExists, int previousHashCode) {
        if (docExists) {
            delete(url);
            return previousHashCode;
        }
        return 0;
    }

    /**
     * @param url the unique identifier of the document to get
     * @return the given document
     */
    @Override
    public Document get(URI url) {
        return this.documentStore.get(url);
    }

    /**
     * @param url the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI url) {
        return false;
    }

    /**
     * undo the last put or delete command
     *
     * @throws IllegalStateException if there are no actions to be undone, i.e. the command stack is empty
     */
    @Override
    public void undo() throws IllegalStateException {

    }

    /**
     * undo the last put or delete that was done with the given URI as its key
     *
     * @param url
     * @throws IllegalStateException if there are no actions on the command stack for the given URI
     */
    @Override
    public void undo(URI url) throws IllegalStateException {

    }

    /**
     * Retrieve all documents whose text contains the given keyword.
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> search(String keyword) {
        return this.documentWordsTrie.getSorted(keyword, new DocumentComparatorForSearch(keyword));
    }

    /**
     * Retrieve all documents that contain text which starts with the given prefix
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByPrefix(String keywordPrefix) {
        return this.documentWordsTrie.getAllWithPrefixSorted(keywordPrefix, new DocumentComparatorForPrefix(keywordPrefix));
    }

    /**
     * Completely remove any trace of any document which contains the given keyword
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAll(String keyword) {
        return null;
    }

    /**
     * Completely remove any trace of any document which contains a word that has the given prefix
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithPrefix(String keywordPrefix) {
        return null;
    }

    /**
     * @param keysValues metadata key-value pairs to search for
     * @return a List of all documents whose metadata contains ALL OF the given values for the given keys. If no documents contain all the given key-value pairs, return an empty list.
     */
    @Override
    public List<Document> searchByMetadata(Map<String, String> keysValues) {
        return null;
    }

    /**
     * Retrieve all documents whose text contains the given keyword AND which has the given key-value pairs in its metadata
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @param keysValues
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        return null;
    }

    /**
     * Retrieve all documents that contain text which starts with the given prefix AND which has the given key-value pairs in its metadata
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @param keysValues
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        return null;
    }

    /**
     * Completely remove any trace of any document which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     *
     * @param keysValues
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithMetadata(Map<String, String> keysValues) {
        return null;
    }

    /**
     * Completely remove any trace of any document which contains the given keyword AND which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @param keysValues
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        return null;
    }

    /**
     * Completely remove any trace of any document which contains a word that has the given prefix AND which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @param keysValues
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        return null;
    }


    private record DocumentComparatorForPrefix(String prefix) implements Comparator<Document> {
        @Override
        public int compare(Document o1, Document o2) {
            return Integer.compare(getPrefixCount(o2), getPrefixCount(o1));
        }
        private int getPrefixCount(Document document) {
            int counter = 0;
            Set<String> words = document.getWordCountMap();
            for (String word : words) {
                String prefixSubstring = word.substring(0, prefix.length());
                if (prefixSubstring.equals(prefix)) counter++;
            }
            return counter;
        }
        }

    private record DocumentComparatorForSearch(String wordToSearch) implements Comparator<Document> {
        @Override
        public int compare(Document o1, Document o2) {
            int documentOneWordCount = o1.wordCount(wordToSearch);
            int documentTwoWordCount = o2.wordCount(wordToSearch);
            return Integer.compare(documentTwoWordCount ,documentOneWordCount);
        }
    }
}
