package edu.yu.cs.com1320.project.stage5.impl;

import edu.yu.cs.com1320.project.Stack;
import edu.yu.cs.com1320.project.impl.HashTableImpl;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage5.Document;
import edu.yu.cs.com1320.project.stage5.DocumentStore;
import edu.yu.cs.com1320.project.undo.CommandSet;
import edu.yu.cs.com1320.project.undo.GenericCommand;
import edu.yu.cs.com1320.project.undo.Undoable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.lang.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DocumentStoreImpl implements DocumentStore {
    private final StackImpl<Undoable> commandStack;
    protected HashTableImpl<URI, Document> documentStore;
    private TrieImpl<Document> documentWordsTrie;
    private MinHeapImpl<Document> storage;
    private long totalMemoryInBytes;
    private long maxMemoryInBytes;
    private int maxDocs;
    private int docTracker;



    public DocumentStoreImpl(){
        this.commandStack = new StackImpl<>();
        this.documentStore = new HashTableImpl<>();
        this.documentWordsTrie = new TrieImpl<>();
        this.totalMemoryInBytes = 0;
        this.maxMemoryInBytes = -1;
        this.maxDocs = -1;
        this.storage = new MinHeapImpl<>();
        //REMOVE THIS LINE
        this.docTracker = 0;
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
        this.commandStack.push(new GenericCommand<>(uri, undo));
        //REMEMBER TO CHANGE THIS LINE
        doc.setLastUseTime(System.nanoTime());
        this.storage.reHeapify(doc);
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
        //REMEMBER TO CHANGE THIS LINE
        get(uri).setLastUseTime(System.nanoTime());
        this.storage.reHeapify(get(uri));
        return get(uri).getMetadataValue(key);
    }

    /**
     * @param input  the document being put
     * @param url    unique identifier for the document
     * @param format indicates which type of document format is being passed
     * @return if there is no previous doc at the given URI, return 0. If there is a previous doc, return the hashCode of the previous doc. If InputStream is null, this is a delete, and thus return either the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException              if there is an issue reading input
     * @throws IllegalArgumentException if url or format are null, OR IF THE MEMORY FOOTPRINT OF THE DOCUMENT IS > MAX DOCUMENT BYTES
     */
    @Override
    public int put(InputStream input, URI url, DocumentFormat format) throws IOException {
        if (format == null || url == null || url.toString().isEmpty()) throw new IllegalArgumentException();
        boolean docExists;
        int previousHashCode = 0;
        Document previousDocument;
        if(get(url) != null){
            docExists = true;
            previousHashCode = get(url).hashCode();
            previousDocument = this.documentStore.get(url);
        } else {
            previousDocument = null;
            docExists = false;
        }
        if (input == null) return handleNullInput(url, docExists, previousHashCode);
        byte[] contents = input.readAllBytes();
        Document createdDocument = createAndAddDocument(url, format, contents, docExists, previousDocument);
        Consumer<URI> undoConsumer = HashTableImpl -> {
            removeDocumentFromStore(createdDocument, true, false, true);
            if(docExists){
                //If put replaced a document, restore that document
                Document document = restoreDocumentToStore(url, previousDocument);
                document.setLastUseTime(System.nanoTime());
                this.storage.insert(document);
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            }
        };
        this.commandStack.push(new GenericCommand<>(url, undoConsumer));
        return (docExists) ? previousHashCode : 0;
    }

    private Document createAndAddDocument(URI url, DocumentFormat format, byte[] contents, boolean docExists, Document previousDoc) {
        DocumentImpl document;
        if (format == DocumentFormat.BINARY) {
            document = new DocumentImpl(url, contents);
            if (maxMemoryInBytes != -1 && getDocumentBytesLength(document) > this.maxMemoryInBytes)
                throw new IllegalArgumentException("No space in store");
            this.documentStore.put(url, document);
            this.totalMemoryInBytes += getDocumentBytesLength(document);
        } else {
            document = new DocumentImpl(url, new String(contents));
            if (maxMemoryInBytes != -1 && getDocumentBytesLength(document) > this.maxMemoryInBytes)
                throw new IllegalArgumentException("No space in store");
            if(docExists) {
                removeDocumentFromStore(previousDoc, true, false, true);
            }
            this.documentStore.put(url, document);
            this.totalMemoryInBytes += getDocumentBytesLength(document);
            addDocumentWordsToTrie(document);
        }
        document.setLastUseTime(System.nanoTime());
        this.storage.insert(document);
        while ((this.maxMemoryInBytes != -1 && this.totalMemoryInBytes > this.maxMemoryInBytes) || (this.maxDocs != -1 && this.documentStore.keySet().size() > this.maxDocs)){
            Document deletedDocument = this.removeDocumentFromStore(this.storage.peek(), true, true, true);
        }
        return document;
    }

    private void addDocumentWordsToTrie(Document document){
        if (document.getDocumentBinaryData() != null) return;
        for (String word : document.getWords()){
            String newWord = word.replaceAll("'", "");
            documentWordsTrie.put(newWord, document);
        }
    }


    private Document restoreDocumentToStore(URI url, Document previousDocument) {
        //rejecting a doc that can't fit in the store and throwing an exception
        if (this.maxMemoryInBytes != -1 && getDocumentBytesLength(previousDocument) > this.maxMemoryInBytes)
            throw new IllegalArgumentException();
        //make space for document
        while ((this.maxMemoryInBytes != -1 && this.totalMemoryInBytes + getDocumentBytesLength(previousDocument) > this.maxMemoryInBytes)){
            Document deletedDocument = this.removeDocumentFromStore(this.storage.peek(), true, true, true);
        }
        this.addDocumentWordsToTrie(previousDocument);
        this.documentStore.put(url, previousDocument);
        //making sure docStore is not above docLimit
        while (this.maxDocs != -1 && this.documentStore.keySet().size() > this.maxDocs){
            Document deletedDocument = this.removeDocumentFromStore(this.storage.peek(), true, true, true);
        }
        return previousDocument;
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
        if(this.documentStore.get(url) != null) {
            this.documentStore.get(url).setLastUseTime(System.nanoTime());
            this.storage.reHeapify(this.documentStore.get(url));
        }
        return this.documentStore.get(url);
    }

    /**
     * @param url the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI url) {
        Document deletedDocument = this.documentStore.get(url);
        if(deletedDocument == null) {
            return false;
        }
        removeDocumentFromStore(deletedDocument, true, false, true);
        Consumer<URI> undoDelete = HashTableImpl -> {
            Document document = restoreDocumentToStore(url, deletedDocument);
            document.setLastUseTime(System.nanoTime());
            this.storage.insert(document);
            this.totalMemoryInBytes += getDocumentBytesLength(document);
        };
        this.commandStack.push(new GenericCommand<>(url, undoDelete));
        return true;
    }

    /**
     * undo the last put or delete command
     *
     * @throws IllegalStateException if there are no actions to be undone, i.e. the command stack is empty
     */
    @Override
    public void undo() throws IllegalStateException {
        if(commandStack.size() == 0) throw new IllegalStateException("Command Stack is empty");
        Undoable command = this.commandStack.pop();
        if(command instanceof CommandSet<?> commandSet){
            long currentTime = System.nanoTime();
            Set<? extends GenericCommand<?>> undone = ((CommandSet<?>) command).undoAll();
            for (GenericCommand<?> genericCommand : undone){
                Document document = this.documentStore.get((URI) genericCommand.getTarget());
                if(document != null) {
                    document.setLastUseTime(currentTime);
                    this.storage.reHeapify(document);
                }
            }
        } else{
            command.undo();
        }
    }

    /**
     * undo the last put or delete that was done with the given URI as its key
     *
     * @param url
     * @throws IllegalStateException if there are no actions on the command stack for the given URI
     */
    @Override
    public void undo(URI url) throws IllegalStateException {
        if(commandStack.size() == 0) throw new IllegalStateException("Command Stack is empty");
        StackImpl<Undoable> tempStack = new StackImpl<>();
        while (this.commandStack.size() > 0) {
            Undoable commandToCheck = this.commandStack.peek();
            if (commandToCheck instanceof CommandSet commandSet) {
                if (checkCommandSet(url, (CommandSet) commandToCheck, System.nanoTime())) return;
                tempStack.push(this.commandStack.pop());
                continue;
            }
            //assert commandToCheck instanceof GenericCommand<?>;
            if (((GenericCommand<?>) commandToCheck).getTarget().equals(url)) {
                commandToCheck.undo();
                this.commandStack.pop();
                while (tempStack.size() > 0) this.commandStack.push(tempStack.pop());
                return;
            }
            tempStack.push(this.commandStack.pop());
        }
    }

    private boolean checkCommandSet(URI url, CommandSet<URI> commandSet, long timeOfUndo) {
        if(commandSet.undo(url)){
            Document undoneDoc = this.documentStore.get(url);
            undoneDoc.setLastUseTime(timeOfUndo);
            this.storage.reHeapify(undoneDoc);
            return true;
        }
        return false;
    }

    /**
     * Retrieve all documents whose text contains the given keyword.
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> search(String keyword) {
        if(keyword == null)
            throw new IllegalArgumentException();
        if(keyword.isEmpty())
            return new ArrayList<>();
        String newKeyword = keyword.replaceAll("[^a-zA-Z0-9\\s]", "");
        Comparator<Document> comparator = (o1, o2) -> {
            int documentOneWordCount = o1.wordCount(newKeyword);
            int documentTwoWordCount = o2.wordCount(newKeyword);
            return Integer.compare(documentTwoWordCount, documentOneWordCount);
        };
        List<Document> documents = this.documentWordsTrie.getSorted(newKeyword, comparator);
        long currentTime = System.nanoTime();
        for(Document document : documents){
            document.setLastUseTime(System.nanoTime());
            this.storage.reHeapify(document);
        }
        return documents;
    }

    /**
     * Retrieve all documents containing a word that starts with the given prefix
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByPrefix(String keywordPrefix) {
        if (keywordPrefix == null)
            throw new IllegalArgumentException();
        if (keywordPrefix.isEmpty())
            return new ArrayList<>();
        String newKey = keywordPrefix.replaceAll("[^a-zA-Z1-9\\s]", "");
        if (newKey.isEmpty()) return new ArrayList<>();
        Comparator<Document> comparator = new Comparator<Document>() {
            @Override
            public int compare(Document o1, Document o2) {
                return Integer.compare(getPrefixCount(o2), getPrefixCount(o1));
            }
            private int getPrefixCount(Document document) {
                if(document.getDocumentBinaryData() != null) return 0;
                int counter = 0;
                Set<String> words = document.getWords();
                for (String word : words) {
                    if (word.startsWith(newKey)) counter += document.wordCount(word);
                }
                return counter;
            }
        };
        List<Document> documents = this.documentWordsTrie.getAllWithPrefixSorted(newKey, comparator);
        long currentTime = System.nanoTime();
        for(Document document : documents){
            document.setLastUseTime(System.nanoTime());
            this.storage.reHeapify(document);
        }
        return documents;
    }

    /**
     * Completely remove any trace of any document which contains the given keyword
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAll(String keyword) {
        if(keyword == null)
            throw new IllegalArgumentException();
        if(keyword.isEmpty())
            return new HashSet<>();
        String newKey = keyword.replaceAll("[^a-zA-Z1-9\\s]", "");
        CommandSet<URI> commandSet = new CommandSet<>();
        Set<Document> documents = this.documentWordsTrie.deleteAll(newKey);
        Set<URI> urisToReturn = documents.stream()
                .map(Document::getKey)
                .collect(Collectors.toSet());
        for (Document document : documents){
            Consumer<URI> undoConsumer = HashTableImpl -> {
                restoreDocumentToStore(document.getKey(), document);
                this.storage.insert(document);
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undoConsumer));
            removeDocumentFromStore(document, true, false, true);
        }
        this.commandStack.push(commandSet);
        return urisToReturn;
    }

    /**
     * Completely remove any trace of any document which contains a word that has the given prefix
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithPrefix(String keywordPrefix) {
        if(keywordPrefix == null)
            throw new IllegalArgumentException();
        String newKey = keywordPrefix.replaceAll("[^a-zA-Z1-9\\s]", "");
        if(newKey.isEmpty())
            return new HashSet<>();
        CommandSet<URI> commandSet = new CommandSet<>();
        Set<Document> documents = this.documentWordsTrie.deleteAllWithPrefix(newKey);
        Set<URI> urisToReturn = documents.stream()
                .map(Document::getKey)
                .collect(Collectors.toSet());
        for (Document document : documents){
            removeDocumentFromStore(document, false, true, true);
            Consumer<URI> undo = HashTableImpl -> {
                restoreDocumentToStore(document.getKey(), document);
                this.storage.insert(document);
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undo));
        }
        this.commandStack.push(commandSet);
        return urisToReturn;
    }

    /**
     * @param keysValues metadata key-value pairs to search for
     * @return a List of all documents whose metadata contains ALL OF the given values for the given keys. If no documents contain all the given key-value pairs, return an empty list.
     */
    @Override
    public List<Document> searchByMetadata(Map<String, String> keysValues) {
        if (keysValues == null || keysValues.isEmpty())
            return new ArrayList<>();
        List<Document> documentList = (List<Document>) this.documentStore.values();
        Set<String> keys = keysValues.keySet();
        List<Document> documentsListToReturn = new ArrayList<>();
        for (Document document : documentList){
            if(document.getMetadata().keySet().isEmpty()) {
                continue;
            }
            if(compareKeys(document.getMetadata().keySet(), keys, document.getKey(), keysValues)) {
                documentsListToReturn.add(document);
            }
        }
        for(Document document : documentsListToReturn){
            document.setLastUseTime(System.nanoTime());
            this.storage.reHeapify(document);
        }
        return documentsListToReturn;
    }

    private boolean compareKeys(Set<String> metaDataKeys, Set<String> keysToSearch, URI uri, Map<String, String> keysValues) {
        boolean containsAll = true;
        for (String key : keysToSearch){
            if(metaDataKeys.contains(key)){
                if(!this.get(uri).getMetadataValue(key).equals(keysValues.get(key))){
                    containsAll = false;
                }
            } else {
                return false;
            }
        }
        return containsAll;
    }


    /**
     * Retrieve all documents whose text contains the given keyword AND which has the given key-value pairs in its metadata
     * Documents are returned in sorted, descending order, sorted by the number of times the keyword appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @param keysValues
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        /*if(keyword == null)
            throw new IllegalArgumentException();
        if(keyword.isEmpty())
            return new ArrayList<>();
        String newKey = keyword.replaceAll("[^a-zA-Z1-9\\s]", "");*/
        List<Document> documents = search(keyword).stream()
                .filter(searchByMetadata(keysValues)::contains)
                .toList();
        long currentTime = System.nanoTime();
        for(Document document : documents){
            document.setLastUseTime(System.nanoTime());
            this.storage.reHeapify(document);
        }
        return documents;
    }

    /**
     * Retrieve all documents that contain text which starts with the given prefix AND which has the given key-value pairs in its metadata
     * Documents are returned in sorted, descending order, sorted by the number of times the prefix appears in the document.
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @param keysValues
     * @return a List of the matches. If there are no matches, return an empty list.
     */
    @Override
    public List<Document> searchByPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        /*if(keywordPrefix == null)
            throw new IllegalArgumentException();
        if(keywordPrefix.isEmpty())
            return new ArrayList<>();
        String newKey = keywordPrefix.replaceAll("[^a-zA-Z1-9\\s]", "");*/
        List<Document> documents = searchByPrefix(keywordPrefix).stream()
                .filter(searchByMetadata(keysValues)::contains)
                .toList();
        long currentTime = System.nanoTime();
        for(Document document : documents){
            document.setLastUseTime(System.nanoTime());
            this.storage.reHeapify(document);
        }
        return documents;
    }

    /**
     * Completely remove any trace of any document which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     *
     * @param keysValues
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithMetadata(Map<String, String> keysValues) {
        CommandSet<URI> commandSet = new CommandSet<>();
        List<Document> documentsToDelete = searchByMetadata(keysValues);
        Set<URI> urisToReturn = new HashSet<>();
        long currentTime = System.nanoTime();
        for (Document document : documentsToDelete){
            urisToReturn.add(document.getKey());
            Consumer<URI> undoConsumer = HashTableImpl -> {
                restoreDocumentToStore(document.getKey(), document);
                this.storage.insert(document);
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undoConsumer));
            removeDocumentFromStore(document, true, false, true);
        }
        this.commandStack.push(commandSet);
        return urisToReturn;
    }

    /**
     * Completely remove any trace of any document which contains the given keyword AND which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     *
     * @param keyword
     * @param keysValues
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        if(keyword == null) throw new IllegalArgumentException();
        if(keyword.isEmpty()) return new HashSet<>();
        String newKey = keyword.replaceAll("[^a-zA-Z1-9\\s]", "");
        CommandSet<URI> commandSet = new CommandSet<>();
        List<Document> keywordDocuments = search(newKey);
        List<Document> metadataDocuments = searchByMetadata(keysValues);
        List<Document> documentsToDelete = new ArrayList<>();
        for (Document document : keywordDocuments){
            if (metadataDocuments.contains(document)) documentsToDelete.add(document);
        }
        Set<URI> urisToReturn = new HashSet<>();
        for (Document document : documentsToDelete){
            urisToReturn.add(document.getKey());
            Consumer<URI> undoConsumer = HashTableImpl -> {
                restoreDocumentToStore(document.getKey(), document);
                this.storage.insert(document);
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undoConsumer));
            removeDocumentFromStore(document, true, false, true);
        }
        this.commandStack.push(commandSet);
        return urisToReturn;
    }

    /**
     * Completely remove any trace of any document which contains a word that has the given prefix AND which has the given key-value pairs in its metadata
     * Search is CASE SENSITIVE.
     *
     * @param keywordPrefix
     * @param keysValues
     * @return a Set of URIs of the documents that were deleted.
     */
    @Override
    public Set<URI> deleteAllWithPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        if(keywordPrefix == null)
            throw new IllegalArgumentException();
        if(keywordPrefix.isEmpty())
            return new HashSet<>();
        String newKey = keywordPrefix.replaceAll("[^a-zA-Z1-9\\s]", "");
        CommandSet<URI> commandSet = new CommandSet<>();
        List<Document> prefixDocuments = searchByPrefix(newKey);
        List<Document> metadataDocuments = searchByMetadata(keysValues);
        List<Document> documentsToDelete = new ArrayList<>();
        for (Document document : prefixDocuments){
            if (metadataDocuments.contains(document)) documentsToDelete.add(document);
        }
        Set<URI> urisToReturn = new HashSet<>();
        for (Document document : documentsToDelete){
            urisToReturn.add(document.getKey());
            Consumer<URI> undoConsumer = HashTableImpl -> {
                restoreDocumentToStore(document.getKey(), document);
                this.storage.insert(document);
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undoConsumer));
            removeDocumentFromStore(document, true, false, true);
        }
        this.commandStack.push(commandSet);
        return urisToReturn;
    }


    /**
     * set maximum number of documents that may be stored
     *
     * @param limit
     * @throws IllegalArgumentException if limit < 1
     */
    @Override
    public void setMaxDocumentCount(int limit) {
        if(limit < 1) throw new IllegalArgumentException();
        this.maxDocs = limit;
        while (documentStore.keySet().size() > this.maxDocs){
            Document deletedDocument = this.removeDocumentFromStore(this.storage.peek(), true, true, true);
        }
    }

    private Document removeDocumentFromStore(Document documentToDelete, boolean removeFromTrie, boolean removeFromComStack, boolean removeFromStorage){
        //remove document from Trie
        if(removeFromTrie) removeDocumentFromTrie(documentToDelete);
        //remove trace of document from command stack
        if(removeFromComStack) removeDocumentFromCommandStack(documentToDelete);
        //remove document from storage
        if(removeFromStorage) removeDocumentFromStorage(documentToDelete);
        //remove doc from document store
        this.documentStore.put(documentToDelete.getKey(), null);
        return documentToDelete;
    }

    private void removeDocumentFromStorage(Document documentToDelete) {
        HashSet<Document> tempSet = new HashSet<>();
        boolean found = false;
        while (!found){
            Document documentToCheck = this.storage.peek();
            if(documentToCheck == null) {
                break;
            }
            if(documentToCheck.equals(documentToDelete)){
                this.storage.remove();
                this.totalMemoryInBytes -= getDocumentBytesLength(documentToDelete);
                found = true;
            } else {
                tempSet.add(this.storage.remove());
            }
        }
        for (Document document : tempSet){
            this.storage.insert(document);
        }
    }

    private void removeDocumentFromTrie(Document documentToDelete) {
        if (documentToDelete.getWords() == null) return;
        for (String word : documentToDelete.getWords()) {
            documentWordsTrie.delete(word, documentToDelete);
        }
    }

    private void removeDocumentFromCommandStack(Document documentToDelete) {
        //create a temp stack
        Stack<Undoable> tempStack = new StackImpl<>();
        //loop through command stack until there are no more commands on the stack
        while (commandStack.size() > 0){
            if (commandStack.peek() instanceof CommandSet<?> tempCommandSet){
                //if the command is a command set loop through each command in the set
                for (GenericCommand<?> command : tempCommandSet){
                    //if the URI of the command is equal to the URI of the doc,
                    //remove the command from the set and set it to null
                    if (command.getTarget().equals(documentToDelete.getKey())){
                        tempCommandSet.remove(command);
                        command = null;
                    }
                }
                //push the set onto the temp stack
                tempStack.push(commandStack.pop());
            } else if (commandStack.peek() instanceof GenericCommand<?> command){
                if (command.getTarget().equals(documentToDelete.getKey())) {
                    commandStack.pop();
                    command = null;
                } else {
                    tempStack.push(commandStack.pop());
                }
            }
        }
        while (tempStack.size() > 0) commandStack.push(tempStack.pop());
    }


    /**
     * set maximum number of bytes of memory that may be used by all the documents in memory combined
     *
     * @param limit
     * @throws IllegalArgumentException if limit < 1
     */
    @Override
    public void setMaxDocumentBytes(int limit) {
        if (limit < 1) throw new IllegalArgumentException();
        this.maxMemoryInBytes = limit;
        while (this.totalMemoryInBytes > this.maxMemoryInBytes){
            Document deletedDocument = this.removeDocumentFromStore(this.storage.peek(), true, true, true);

        }

    }

    private long getDocumentBytesLength(Document document){
        if(document.getDocumentBinaryData() != null){
           return document.getDocumentBinaryData().length;
        } else {
           return document.getDocumentTxt().getBytes().length;
        }
    }
}
