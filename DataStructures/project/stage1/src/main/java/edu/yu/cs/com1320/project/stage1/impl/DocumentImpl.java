package edu.yu.cs.com1320.project.stage1.impl;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;

public class DocumentImpl implements Document{
    private final URI uri;
    private String text;
    private byte[] binaryData;
    protected HashMap<String, String> metadata;
    public DocumentImpl(URI uri, byte[] binaryData){
        if(uri == null || binaryData == null) throw new IllegalArgumentException();
        this.uri = uri;
        this.binaryData = binaryData;
        this.metadata = new HashMap<>();
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


    public boolean equals(Document doc){
        return this.hashCode() == doc.hashCode();
    }

    /**
     * @param key   key of document metadata to store a value for
     * @param value value to store
     * @return old value, or null if there was no old value
     * @throws IllegalArgumentException if the key is null or blank
     */
    @Override
    public String setMetadataValue(String key, String value) {
        metadata.put(key, value);
        return key;
    }

    /**
     * @param key metadata key whose value we want to retrieve
     * @return corresponding value, or null if there is no such key
     * @throws IllegalArgumentException if the key is null or blank
     */
    @Override
    public String getMetadataValue(String key) {
        return metadata.get(key);
    }

    @Override
    public HashMap<String, String> getMetadata() {
        return this.metadata;
    }

    @Override
    public String getDocumentTxt() {

        return this.text;
    }

    @Override
    public byte[] getDocumentBinaryData() {
        return this.binaryData;
    }

    @Override
    public URI getKey() {
        return this.uri;
    }
}
