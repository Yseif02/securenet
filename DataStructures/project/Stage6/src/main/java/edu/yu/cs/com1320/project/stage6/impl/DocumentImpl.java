package edu.yu.cs.com1320.project.stage6.impl;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

public class DocumentImpl implements Document {
    private final URI uri; //serialize
    private String text; //serialize
    private byte[] binaryData;  //serialize
    private HashMap<String,String> metadata; //serialize
    private HashMap<String, Integer> wordCountMap; //serialize

    private String[] documentWords;
    private final DocumentStore.DocumentFormat documentFormat;
    private final Set<String> documentWordSet;
    private long timeLastUsed;
    private int byteSize;

    public DocumentImpl(URI uri, byte[] binaryData){
        if(uri == null || binaryData == null) throw new IllegalArgumentException();
        this.uri = uri;
        this.binaryData = binaryData;
        this.metadata = new HashMap<>();
        this.documentFormat = DocumentStore.DocumentFormat.BINARY;
        this.byteSize = binaryData.length;
        this.wordCountMap = null;
        this.documentWordSet = null;
    }

    public DocumentImpl(URI uri, String text, Map<String, Integer> wordCountMap){
        if(uri == null || text.isEmpty()) throw new IllegalArgumentException();
        this.uri = uri;
        this.text = text;
        this.metadata = new HashMap<>();
        this.documentFormat = DocumentStore.DocumentFormat.TXT;
        if (wordCountMap != null) {
            this.setWordMap((HashMap<String, Integer>) wordCountMap);
            this.documentWords = wordCountMap.keySet().toArray(new String[0]);
        } else{
            this.wordCountMap = new HashMap<>();
            this.documentWords = getDocumentWords();
        }

        this.documentWordSet = new HashSet<>();
        this.byteSize = text.getBytes().length;
        if(documentWords != null) addWordsToHashMapAndSet(documentWords, wordCountMap);
    }

    private void addWordsToHashMapAndSet(String[] documentWords, Map<String, Integer> wordCountMap) {
        if (wordCountMap == null){
            for (String word : documentWords){
                addToMap(word);
            }
        }
        this.documentWordSet.addAll(Arrays.asList(documentWords));
    }

    private String[] getDocumentWords() {
        if(documentFormat.equals(DocumentStore.DocumentFormat.BINARY)) return null;
        String newText = this.text.replaceAll("[^a-zA-Z0-9\\s]", "");
        return Pattern.compile("[^a-zA-Z0-9']+")
                .splitAsStream(newText)
                .filter(word -> !word.isEmpty())
                .toArray(String[]::new);
    }

    private void addToMap(String word) {
        if(this.wordCountMap.get(word) == null){
            this.wordCountMap.put(word, 1);
        }else{
            this.wordCountMap.merge(word, 1, Integer::sum);
        }
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
     * @throws IllegalArgumentException if the key is null or blank
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
     * @throws IllegalArgumentException if the key is null or blank
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
    public HashMap<String, String> getMetadata() {
        HashMap<String, String> tableToReturn = new HashMap<>();
        for(String key:this.metadata.keySet()) {
            String valueToAddToTable = getMetadataValue(key);
            tableToReturn.put(key, valueToAddToTable);
        }
        return tableToReturn;
    }

    /**
     * @param metadata
     */
    @Override
    public void setMetadata(HashMap<String, String> metadata) {
        this.metadata = metadata;
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
        if(this.documentFormat.equals(DocumentStore.DocumentFormat.BINARY)) return 0;
        if(this.wordCountMap.entrySet().isEmpty()) return 0;
        if(this.wordCountMap.get(word) == null) return 0;
        return this.wordCountMap.get(word);
    }

    /**
     * @return all the words that appear in the document
     */
    @Override
    public Set<String> getWords() {
        return (this.documentFormat.equals(DocumentStore.DocumentFormat.TXT)) ? this.documentWordSet : null;
    }

    /**
     * return the last time this document was used, via put/get or via a search result
     * (for stage 4 of project)
     */
    @Override
    public long getLastUseTime() {
        return this.timeLastUsed;
    }

    /**
     * @param timeInNanoseconds
     */
    @Override
    public void setLastUseTime(long timeInNanoseconds) {
        this.timeLastUsed = timeInNanoseconds;
    }

    /**
     * @return a copy of the word to count map so it can be serialized
     */
    @Override
    public HashMap<String, Integer> getWordMap() {
        return this.wordCountMap;
    }

    /**
     * This must set the word to count map durlng deserialization
     *
     * @param wordMap
     */
    @Override
    public void setWordMap(HashMap<String, Integer> wordMap) {
        this.wordCountMap = wordMap;
    }

    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * <p>The implementor must ensure {@link Integer#signum
     * signum}{@code (x.compareTo(y)) == -signum(y.compareTo(x))} for
     * all {@code x} and {@code y}.  (This implies that {@code
     * x.compareTo(y)} must throw an exception if and only if {@code
     * y.compareTo(x)} throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * {@code (x.compareTo(y) > 0 && y.compareTo(z) > 0)} implies
     * {@code x.compareTo(z) > 0}.
     *
     * <p>Finally, the implementor must ensure that {@code
     * x.compareTo(y)==0} implies that {@code signum(x.compareTo(z))
     * == signum(y.compareTo(z))}, for all {@code z}.
     *
     * @param o the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     * @apiNote It is strongly recommended, but <i>not</i> strictly required that
     * {@code (x.compareTo(y)==0) == (x.equals(y))}.  Generally speaking, any
     * class that implements the {@code Comparable} interface and violates
     * this condition should clearly indicate this fact.  The recommended
     * language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     */
    @Override
    public int compareTo(@NotNull Document o) {
        return Long.compare(this.getLastUseTime(), o.getLastUseTime());
    }

    @Override
    public boolean equals(Object object) {
        if(this == object) return true;
        if(object == null || getClass() != object.getClass()) return false;
        return this.hashCode() == object.hashCode();
    }
}
