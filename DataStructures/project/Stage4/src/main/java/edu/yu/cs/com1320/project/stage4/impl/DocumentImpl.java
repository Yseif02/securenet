package edu.yu.cs.com1320.project.stage4.impl;

import edu.yu.cs.com1320.project.HashTable;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.stage4.Document;
import edu.yu.cs.com1320.project.stage4.DocumentStore;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class DocumentImpl implements Document {
    private final URI uri;
    private String text;
    private byte[] binaryData;
    private final HashTableImpl<String,String> metadata;
    private final DocumentStore.DocumentFormat documentFormat;
    private final HashMap<String, Integer> wordCountMap;
    private final Set<String> documentWordSet;

    public DocumentImpl(URI uri, byte[] binaryData){
        if(uri == null || binaryData == null) throw new IllegalArgumentException();
        this.uri = uri;
        this.binaryData = binaryData;
        this.metadata = new HashTableImpl<>();
        this.documentFormat = DocumentStore.DocumentFormat.BINARY;
        this.wordCountMap = null;
        this.documentWordSet = null;
    }

    public DocumentImpl(URI uri, String txt){
        if(uri == null || txt.isEmpty()) throw new IllegalArgumentException();
        this.uri = uri;
        this.text = txt;
        this.metadata = new HashTableImpl<>();
        this.documentFormat = DocumentStore.DocumentFormat.TXT;
        this.wordCountMap = new HashMap<>();
        this.documentWordSet = new HashSet<>();
        String[] documentWords = getDocumentWords();
        if(documentWords != null) addWordsToHashMapAndSet(documentWords);
    }

    private void addWordsToHashMapAndSet(String[] documentWords) {
        for (String word : documentWords){
            if(this.wordCountMap.get(word) == null){
                wordCountMap.put(word, 1);
            }else{
                wordCountMap.merge(word, 1, Integer::sum);
            }
            this.documentWordSet.add(word);
        }
    }

    private String[] getDocumentWords() {
        if(documentFormat.equals(DocumentStore.DocumentFormat.BINARY)) return null;
        String newText = this.text.replaceAll("[^a-zA-Z0-9'\\s]", "");;
        return Pattern.compile("[^a-zA-Z0-9']+")
                .splitAsStream(newText)
                .filter(word -> !word.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(binaryData);
        return Math.abs(result);
    }

    /**
     * @param key   key of document metadata to store a value for
     * @param value value to store
     * @return old value, or null if there was no old value
     */
    @Override
    public String setMetadataValue(String key, String value) {
        if(key == null || key.isEmpty()) throw new IllegalArgumentException();
        String oldValue = this.metadata.get(key);
        this.metadata.put(key, value);
        return oldValue;
    }

    /**
     * @param key metadata key whose value we want to retrieve
     * @return corresponding value, or null if there is no such key
     */
    @Override
    public String getMetadataValue(String key) {
        if(key == null || key.isEmpty()) throw new IllegalArgumentException();
        return this.metadata.get(key);
    }

    /**
     * @return a COPY of the metadata saved in this document
     */
    @Override
    public HashTable<String, String> getMetadata() {
        HashTable<String, String> tableToReturn = new HashTableImpl<>();
        for(String key:this.metadata.keySet()) {
            String valueToAddToTable = getMetadataValue(key);
            tableToReturn.put(key, valueToAddToTable);
        }
        return tableToReturn;
    }

    /**
     * @return content of text document
     */
    @Override
    public String getDocumentTxt() {
        return (documentFormat.equals(DocumentStore.DocumentFormat.TXT)) ? this.text : null;
    }

    /**
     * @return content of binary data document
     */
    @Override
    public byte[] getDocumentBinaryData() {
        return (documentFormat.equals(DocumentStore.DocumentFormat.BINARY)) ? this.binaryData : null;
    }

    /**
     * @return URI which uniquely identifies this document
     */
    @Override
    public URI getKey() {
        return this.uri;
    }

    /**
     * how many times does the given word appear in the document?
     *
     * @param word
     * @return the number of times the given words appears in the document. If it's a binary document, return 0.
     */
    @Override
    public int wordCount(String word) {
        return (this.documentFormat.equals(DocumentStore.DocumentFormat.TXT)) ? this.wordCountMap.get(word) : 0;
    }

    /**
     * @return all the words that appear in the document
     */
    @Override
    public Set<String> getWordCountMap() {
        return (this.documentFormat.equals(DocumentStore.DocumentFormat.TXT)) ? this.documentWordSet : null;
    }
}
