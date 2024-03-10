package edu.yu.cs.com1320.project.stage3.impl;

import edu.yu.cs.com1320.project.Stack;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
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

    private Consumer<URI> getUndoConsumerForPut(URI url, boolean docExists, Document previousDocument) {
        return HashTableImpl -> {
            if(docExists){
                this.documentStore.put(url, previousDocument);
            }else{
                this.documentStore.put(url, null);
            }
    };
    }


    private void createAndAddDocument(URI url, DocumentFormat format, byte[] contents) {
        Document document;
        if (format == DocumentFormat.BINARY) {
            document = new DocumentImpl(url, contents);
            this.documentStore.put(url, document);
        } else {
            document = new DocumentImpl(url, new String(contents));
            this.documentStore.put(url, document);
        }
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
        Document deletedDocument = this.documentStore.get(url);
        if(this.documentStore.get(url) == null) {
            return false;
        }else{
            this.documentStore.put(url, null);
            Consumer<URI> undoDelete = HashTableImpl -> this.documentStore.put(url, deletedDocument );
            this.commandStack.push(new Command(url, undoDelete));
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
     * @param url - uri
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
