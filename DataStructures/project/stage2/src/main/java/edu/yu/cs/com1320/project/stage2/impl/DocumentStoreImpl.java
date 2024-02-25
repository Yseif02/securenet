package edu.yu.cs.com1320.project.stage2.impl;

import edu.yu.cs.com1320.project.Document;
import edu.yu.cs.com1320.project.DocumentStore;
import edu.yu.cs.com1320.project.impl.HashTableImpl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;


public class DocumentStoreImpl implements DocumentStore {
    protected HashTableImpl<URI, Document> documentStore;

    public DocumentStoreImpl(){
        this.documentStore = new HashTableImpl<>();
    }


    /**
     * set the given key-value metadata pair for the document at the given uri
     *
     * @param uri
     * @param key
     * @param value
     * @return the old value, or null if there was no previous value
     * @throws IllegalArgumentException if the uri is null or blank, if there is no document stored at that uri, or if the key is null or blank
     */
    @Override
    public String setMetadata(URI uri, String key, String value) {
        if(uri == null || uri.toString().isEmpty() || get(uri) == null || key == null || key.isEmpty()){
            throw new IllegalArgumentException();
        }
        Document doc = get(uri);
        String oldValue = doc.getMetadataValue(key);
        doc.setMetadataValue(key, value);
        return oldValue;
    }

    /**
     * get the value corresponding to the given metadata key for the document at the given uri
     *
     * @param uri
     * @param key
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
     * @return if there is no previous doc at the given URI, return 0. If there is a previous doc, return the hashCode of the previous doc. If InputStream is null, this is a delete, and thus return either the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException              if there is an issue reading input
     * @throws IllegalArgumentException if url or format are null
     */
    @Override
    public int put(InputStream input, URI url, DocumentFormat format) throws IOException {
        if (format == null || url == null || url.toString().isEmpty()) throw new IllegalArgumentException();
        boolean docExists = false;
        int previousHashCode = 0;
        if(get(url) != null){
            docExists = true;
            previousHashCode = get(url).hashCode();
        }
        if (input == null) {
            if (docExists) {
                delete(url);
                return previousHashCode;
            }
            return 0;
        }

        byte[] contents = input.readAllBytes();
        input.close();
        if (format == DocumentFormat.BINARY) {
            Document document = new DocumentImpl(url, contents);
            this.documentStore.put(url, document);
        } else if (format == DocumentFormat.TXT) {
            Document document = new DocumentImpl(url, new String(contents));
            this.documentStore.put(url, document);
        }
        return (docExists) ? previousHashCode : 0;
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
        if(this.documentStore.get(url) == null) {
            return false;
        }else{
            this.documentStore.put(url, null);
            return true;
        }
    }
}
