package edu.yu.cs.com1320.project.stage5.impl;

import edu.yu.cs.com1320.project.HashTable;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import org.jetbrains.annotations.NotNull;

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
    private long timeLastUsed;
    private int byteSize;

    public DocumentImpl(URI uri, byte[] binaryData){
        if(uri == null || binaryData == null) throw new IllegalArgumentException();
        this.uri = uri;
        this.binaryData = binaryData;
        this.metadata = new HashTableImpl<>();
        this.documentFormat = DocumentStore.DocumentFormat.BINARY;
        this.byteSize = binaryData.length;
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
        this.byteSize = txt.getBytes().length;
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
        String newText = this.text.replaceAll("[^a-zA-Z0-9\\s]", "");
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
