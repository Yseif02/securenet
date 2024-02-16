package edu.yu.cs.com1320.project.stage1.impl;

import edu.yu.cs.com1320.project.stage1.Document;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DocumentImpl implements Document {
    private final URI uri;
    private String text;
    private byte[] binaryData;
    private HashMap<String, String> metadata;
    public DocumentImpl(URI uri, byte[] binaryData){
        if(uri == null || binaryData == null) throw new IllegalArgumentException();
        this.uri = uri;
        this.binaryData = binaryData;
        this.metadata = new HashMap<>();
        // test
    }

    public DocumentImpl(URI uri, String txt){
        if(uri == null || txt.isEmpty()) throw new IllegalArgumentException("txt = " + txt + "uri = " + uri);
        this.uri = uri;
        this.text = txt;
        this.metadata = new HashMap<>();
    }



    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(binaryData);
        return result;
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
        return metadata.get(key);
    }

    /**
     * @return a COPY of the metadata saved in this document
     */
    @Override
    public HashMap<String, String> getMetadata() {
        HashMap<String, String> newHashMap = new HashMap<>();
        for(HashMap.Entry<String,String> entry : this.metadata.entrySet()){
            String key = entry.getKey();
            String value = entry.getValue();
            newHashMap.put(key, value);
        }
        return newHashMap;
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
        return getClass() == object.getClass() && object.hashCode() == this.hashCode();
    }
}
