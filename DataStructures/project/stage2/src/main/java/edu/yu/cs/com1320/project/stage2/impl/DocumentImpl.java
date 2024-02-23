package edu.yu.cs.com1320.project.stage2.impl;

import edu.yu.cs.com1320.project.Document;
import edu.yu.cs.com1320.project.HashTable;

import java.net.URI;
import java.util.Arrays;


public class DocumentImpl implements Document {
    private final URI uri;
    private String text;
    private byte[] binaryData;
    private HashTableImpl<String,String> metadata;

    public DocumentImpl(URI uri, byte[] binaryData){
        if(uri == null || binaryData == null) throw new IllegalArgumentException();
        this.uri = uri;
        this.binaryData = binaryData;
        this.metadata = new HashTableImpl<>();
    }

    public DocumentImpl(URI uri, String txt){
        if(uri == null || txt.isEmpty()) throw new IllegalArgumentException();
        this.uri = uri;
        this.text = txt;
        this.metadata = new HashTableImpl<>();
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
        return this.text;
    }

    /**
     * @return content of binary data document
     */
    @Override
    public byte[] getDocumentBinaryData() {
        return this.binaryData;
    }

    /**
     * @return URI which uniquely identifies this document
     */
    @Override
    public URI getKey() {
        return this.uri;
    }

    @Override
    public boolean equals(Object object) {
        if(this == object) return true;
        if(object == null || getClass() != object.getClass()) return false;
        return this.hashCode() == object.hashCode();
    }
}
