package edu.yu.cs.com1320.project.stage1.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
public class DocumentStoreImpl implements DocumentStore{
    protected HashMap<URI, Document> documentStore;

    public DocumentStoreImpl(){
        this.documentStore = new HashMap<>();
    }

    /**
     * set the given key-value metadata pair for the document at the given uri
     * @param uri
     * @param key
     * @param value
     * @return the old value, or null if there was no previous value
     * @throws IllegalArgumentException if the uri is null or blank,
     * or if there is no document stored at that uri, or if the key
     * is null or blank
     */
    @Override
    public String setMetadata(URI uri, String key, String value) {
        if(uri == null || key == null || key.isEmpty()){
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
        if(uri == null || key == null || key.isEmpty() || get(uri) == null) throw new IllegalArgumentException();
        return get(uri).getMetadataValue(key);
    }

    /**
     * @param input  the document being put
     * @param uri    unique identifier for the document
     * @param format indicates which type of document format is being passed
     * @return if there is no previous doc at the given URI, return 0.
     * If there is a previous doc, return the hashCode of the previous
     * doc. If InputStream is null, this is a delete, and thus return either
     * the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException              if there is an issue reading input
     * @throws IllegalArgumentException if uri is null or empty, or format is null
     */
    @Override
    public int put(InputStream input, URI uri, DocumentFormat format) throws IOException {
        boolean docExists = false;
        int previousHashCode = 0;
        if(get(uri) != null){
            docExists = true;
            previousHashCode = get(uri).hashCode();
            //documentStore.remove(uri);
        }
        if (input == null) {
            if (docExists) {
                delete(uri);
                return previousHashCode;
            }
            return 0;
        }
        byte[] text = input.readAllBytes();
        BasicFileAttributes metaData = Files.readAttributes(Paths.get(uri), BasicFileAttributes.class);
        if (format == DocumentFormat.BINARY) {
            Document document = new DocumentImpl(uri, text);
            documentStore.put(uri, document);
        } else if (format == DocumentFormat.TXT) {
            System.out.println("1)URI: " + uri);
            System.out.println("2)File Text: " + new String(text));
            Document document = new DocumentImpl(uri, new String(text));
            addAllMetaData(document, metaData);
            documentStore.put(uri, document);
        } else if (format == null) throw new IllegalArgumentException();
        return (docExists) ? previousHashCode : 0;
    }

    private void addAllMetaData(Document document, BasicFileAttributes metaData) {
        Path path = Paths.get(document.getKey());
        document.setMetadataValue("File Name", path.getFileName().toString());
        document.setMetadataValue("File Size", String.valueOf(metaData.size()));
        document.setMetadataValue("Creation Time", metaData.creationTime().toString());
        document.setMetadataValue("Is Directory", String.valueOf(metaData.isDirectory()));
        document.setMetadataValue("Is Other", String.valueOf(metaData.isOther()));
        document.setMetadataValue("Is Regular File", String.valueOf(metaData.isRegularFile()));
        document.setMetadataValue("Is Symbolic Link", String.valueOf(metaData.isSymbolicLink()));
        document.setMetadataValue("Last Access Time", metaData.lastAccessTime().toString());
        document.setMetadataValue("Last Modified Time", metaData.lastModifiedTime().toString());
    }


    /**
     * @param url the unique identifier of the document to get
     * @return the given document
     */
    @Override
    public Document get(URI url) {
        return documentStore.get(url);
    }

    /**
     * @param url the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI url) {
        if(documentStore.get(url) == null) {
            return false;
        }else{
            documentStore.remove(url);
            return true;
        }
    }
}
