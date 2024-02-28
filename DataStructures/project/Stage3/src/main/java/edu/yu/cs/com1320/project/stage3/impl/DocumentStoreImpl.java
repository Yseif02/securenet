package edu.yu.cs.com1320.project.stage3.impl;



import edu.yu.cs.com1320.project.HashTable;
import edu.yu.cs.com1320.project.Stack;
import edu.yu.cs.com1320.project.stage3.Document;
import edu.yu.cs.com1320.project.stage3.DocumentStore;
import edu.yu.cs.com1320.project.undo.Command;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.function.Consumer;

public class DocumentStoreImpl implements DocumentStore {
    private StackImpl<Command> commandStack;
    protected HashTableImpl<URI, Document> documentStore;


    public DocumentStoreImpl(){
        this.commandStack = new StackImpl<>();
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
        boolean docExists;
        int previousHashCode = 0;
        byte[] previousContentsInBytes;
        String previousContentsInString;
        if(get(url) != null){
            docExists = true;
            previousHashCode = get(url).hashCode();
            if(get(url).getDocumentTxt() == null){
                previousContentsInString = null;
                previousContentsInBytes = get(url).getDocumentBinaryData();
            }else {
                previousContentsInBytes = null;
                previousContentsInString = get(url).getDocumentTxt();
            }
        } else {
            previousContentsInString = null;
            previousContentsInBytes = null;
            docExists = false;
        }
        if (input == null) {
            if (docExists) {
                delete(url);
                return previousHashCode;
            }
            return 0;
        }
        boolean documentInBytes;
        Document document;
        byte[] contents = input.readAllBytes();
        if (format == DocumentFormat.BINARY) {
            documentInBytes = true;
            document = new DocumentImpl(url, contents);
            this.documentStore.put(url, document);
        } else {
            documentInBytes = false;
            document = new DocumentImpl(url, new String(contents));
            this.documentStore.put(url, document);
        }
        Consumer<URI> undo =
                HashTableImpl -> {
            if(docExists && documentInBytes){
                this.documentStore.put(url, new DocumentImpl(url, previousContentsInBytes));
                this.commandStack.pop();
            }else if(docExists){
                this.documentStore.put(url, new DocumentImpl(url, previousContentsInString));
                this.commandStack.pop();
            }else{
                this.documentStore.put(url, null);
            }
        };
        this.commandStack.push(new Command(url, undo));
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
            Document deletedDocument = this.documentStore.get(url);
            this.documentStore.put(url, null);
            Consumer<URI> undo =
                    HashTableImpl -> {
                    if(deletedDocument.getDocumentTxt() == null){
                        this.documentStore.put(url ,new DocumentImpl(url, deletedDocument.getDocumentBinaryData()));
                        HashTable<String, String> deletedMetaData = deletedDocument.getMetadata();
                        for (String key : deletedMetaData.keySet()){
                            documentStore.get(url).setMetadataValue(key, deletedDocument.getMetadataValue(key));
                        }
                    }else{
                        this.documentStore.put(url ,new DocumentImpl(url, deletedDocument.getDocumentTxt()));
                        HashTable<String, String> deletedMetaData = deletedDocument.getMetadata();
                        for (String key : deletedMetaData.keySet()){
                            documentStore.get(url).setMetadataValue(key, deletedDocument.getMetadataValue(key));
                        }
                    }
                    this.commandStack.pop();
                    };
            this.commandStack.push(new Command(url, undo));
            return true;
        }
    }

    /**
     * undo the last put or delete command
     *
     * @throws IllegalStateException if there are no actions to be undone, i.e. the command stack is empty
     */
    @Override
    public void undo() throws IllegalStateException {
        if(this.commandStack.size() == 0) throw new IllegalStateException("Stack is empty");
        Command commandToUndo = this.commandStack.pop();
        commandToUndo.undo();
    }

    /**
     * undo the last put or delete that was done with the given URI as its key
     *
     * @param url
     * @throws IllegalStateException if there are no actions on the command stack for the given URI
     */
    @Override
    public void undo(URI url) throws IllegalStateException {
        if(this.commandStack.size() == 0) throw new IllegalStateException("Stack is empty");
        Stack<Command> tempStack = new StackImpl<>();
        while (commandStack.size() > 0){
            Command commandToCheck = this.commandStack.peek();
            if(commandToCheck.getUri().equals(url)){
                this.commandStack.pop().undo();
                break;
            } else {
                tempStack.push(this.commandStack.pop());
            }
        }
        while (tempStack.size() > 0){
            this.commandStack.push(tempStack.pop());
        }
    }
}
