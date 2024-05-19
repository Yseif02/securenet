package edu.yu.cs.com1320.project.stage6.impl;

import edu.yu.cs.com1320.project.Stack;
import edu.yu.cs.com1320.project.impl.BTreeImpl;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import edu.yu.cs.com1320.project.undo.CommandSet;
import edu.yu.cs.com1320.project.undo.GenericCommand;
import edu.yu.cs.com1320.project.undo.Undoable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

public class DocumentStoreImpl implements DocumentStore {
    private final StackImpl<Undoable> commandStack;
    private BTreeImpl<URI, Document> documentStore;
    private TrieImpl<URI> documentWordsTrie;
    private MinHeapImpl<DocumentGrabberURI> storage;
    private HashMap<URI, DocumentGrabberURI> documentGrabber;
    private Set<URI> docStoreURIs;
    private Map<URI, HashMap<String,String>> URItoMetadataMap;
    private long totalMemoryInBytes;
    private long maxMemoryInBytes;
    private int maxDocs;
    private int documentStoreMemorySize;
    private int fullDocStoreSize;

    public DocumentStoreImpl() {
        this.commandStack = new StackImpl<>();
        this.documentStore = new BTreeImpl<>();
        this.documentStore.setPersistenceManager(new DocumentPersistenceManager<URI, Document>(null));
        this.documentWordsTrie = new TrieImpl<>();
        this.storage = new MinHeapImpl<>();
        this.docStoreURIs = new HashSet<>();
        this.documentGrabber = new HashMap<>();
        this.URItoMetadataMap = new HashMap<>();
        this.documentStoreMemorySize = 0;
        this.fullDocStoreSize = 0;
        this.totalMemoryInBytes = 0;
        this.maxMemoryInBytes = -1;
        this.maxDocs = -1;
    }

    public DocumentStoreImpl(File baseDir){
        this.commandStack = new StackImpl<>();
        this.documentStore = new BTreeImpl<>();
        this.documentStore.setPersistenceManager(new DocumentPersistenceManager<URI, Document>(baseDir));
        this.documentWordsTrie = new TrieImpl<>();
        this.storage = new MinHeapImpl<>();
        this.docStoreURIs = new HashSet<>();
        this.documentGrabber = new HashMap<>();
        this.URItoMetadataMap = new HashMap<>();
        this.documentStoreMemorySize = 0;
        this.fullDocStoreSize = 0;
        this.totalMemoryInBytes = 0;
        this.maxMemoryInBytes = -1;
        this.maxDocs = -1;
    }

    private class DocumentGrabberURI implements Comparable<DocumentGrabberURI>{
        private final URI uri;
        private DocumentGrabberURI(URI uri){
            this.uri = uri;
        }
        private Document getDocument() {
            Document document = documentStore.get(uri);
            return document;
        }
        @Override
        public int compareTo(@NotNull DocumentStoreImpl.DocumentGrabberURI o) {
            return this.getDocument().compareTo(o.getDocument());
        }
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
    public String setMetadata(URI uri, String key, String value) throws IOException {
        //check if doc is in memory for undo
        boolean inMemory = this.docStoreURIs.contains(uri);
        if(uri == null || uri.toString().isEmpty() || get(uri) == null|| key == null || key.isEmpty()){
            throw new IllegalArgumentException();
        }
        String oldKey, oldValue;
        oldKey = getMetadata(uri, key) == null ? null : key;
        Document document = get(uri);
        oldValue = document.getMetadataValue(key);
        document.setMetadataValue(key, value);
        Consumer<URI> undo =
            BTreeImpl -> {
                document.setMetadataValue(key, (oldKey == null) ? null : oldValue);
                if(!inMemory) {
                    try {
                        this.documentStore.moveToDisk(uri);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        this.commandStack.push(new GenericCommand<>(uri, undo));
        document.setLastUseTime(System.nanoTime());
        this.storage.reHeapify(this.documentGrabber.get(uri));
        URItoMetadataMap.put(uri, document.getMetadata());
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
    public String getMetadata(URI uri, String key) throws IOException {
        if(uri == null || key == null || key.isEmpty() || get(uri) == null || uri.toString().isEmpty()) throw new IllegalArgumentException();
        Document document = this.get(uri);
        document.setLastUseTime(System.nanoTime());
        this.storage.reHeapify(this.documentGrabber.get(uri));
        return document.getMetadataValue(key);
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
        boolean documentExists;
        int previousHashCode = 0;
        Document previousDocument;
        boolean docExistsInMemory = this.docStoreURIs.contains(url);
        if (get(url) != null) {
            documentExists = true;
            previousHashCode = get(url).hashCode();
            previousDocument = this.documentStore.get(url);
        } else {
            previousDocument = null;
            documentExists = false;
        }
        if (input == null) return handleNullInput(url, documentExists, previousHashCode);
        byte[] contents = input.readAllBytes();
        Document newDocument = createAndAddDocument(url, format, contents, documentExists, previousDocument);
        Consumer<URI> undoConsumer = uri -> {
        removeDocumentFromStore(newDocument, true, false, true);
        if (documentExists && !docExistsInMemory){
            //this case is where the old document was on disk, need to send it back
            try {
                this.documentStore.put(url, previousDocument);
                this.documentStore.moveToDisk(url);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if(documentExists) {
                //If put replaced a document, restore that document
                Document document = restoreDocumentToStore(url, previousDocument);
                document.setLastUseTime(System.nanoTime());
                this.storage.insert(this.documentGrabber.get(document.getKey()));
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            }else {
                this.docStoreURIs.remove(url);
                this.documentStoreMemorySize--;
                this.fullDocStoreSize--;
            }
        };
        this.commandStack.push(new GenericCommand<>(url, undoConsumer));
        return (documentExists) ? previousHashCode : 0;
    }

    private Document createAndAddDocument(URI url, DocumentFormat format, byte[] contents, boolean documentExists, Document previousDocument) throws IOException {
        DocumentImpl document;
        document = (format == DocumentFormat.BINARY) ? new DocumentImpl(url, contents) : new DocumentImpl(url, new String(contents), null);
        if (maxMemoryInBytes != -1 && getDocumentBytesLength(document) > this.maxMemoryInBytes)
            throw new IllegalArgumentException("No space in store");
        if(format == DocumentFormat.TXT) addDocumentWordsToTrie(document);
        if(documentExists)
            removeDocumentFromStore(previousDocument, true, false, true);
        this.documentStore.put(url, document);
        this.totalMemoryInBytes += getDocumentBytesLength(document);
        this.docStoreURIs.add(url);
        DocumentGrabberURI documentGrabberURI = new DocumentGrabberURI(url);
        this.documentGrabber.put(url, documentGrabberURI);
        this.documentStoreMemorySize++;
        this.fullDocStoreSize++;
        document.setLastUseTime(System.nanoTime());
        this.storage.insert(this.documentGrabber.get(url));
        while ((this.maxMemoryInBytes != -1 && this.totalMemoryInBytes > this.maxMemoryInBytes) || (this.maxDocs != -1 && this.documentStoreMemorySize > this.maxDocs)){
            sendOldestDocumentToDisk();
        }
        return document;
    }

    private void addDocumentWordsToTrie(Document document) {
        if (document.getDocumentBinaryData() != null) return;
        for (String word : document.getWords()){
            String newWord = word.replaceAll("'", "");
            DocumentGrabberURI documentGrabberURI = this.documentGrabber.get(document.getKey());
            documentWordsTrie.put(newWord, document.getKey());
        }
    }

    private long getDocumentBytesLength(Document document) {
        return (document.getDocumentBinaryData() != null) ? document.getDocumentBinaryData().length : document.getDocumentTxt().getBytes().length;
    }

    private int handleNullInput(URI url, boolean documentExists, int previousHashCode) {
        if (documentExists) {
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
    public Document get(URI url) throws IOException {
        Document document = this.documentStore.get(url);
        if(document == null){
            return null;
        }
        //have to see if document is coming from disk
        //can do that by checking URI set
        if(!this.docStoreURIs.contains(url)){
            //document came from disk, have to restore it
            restoreDocumentToStore(url, document);
            this.docStoreURIs.add(url);
            this.documentStoreMemorySize = this.docStoreURIs.size();
        }
        document.setLastUseTime(System.nanoTime());
        this.storage.reHeapify(documentGrabber.get(document.getKey()));
        return document;
    }



    /**
     * @param url the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI url) {
        Document deletedDocument;
        try {
            deletedDocument = get(url);
        } catch (IOException e){
            throw new RuntimeException();
        }
        if (deletedDocument == null) {return false;}
        removeDocumentFromStore(deletedDocument, true, true, true);
        this.docStoreURIs.remove(url);
        this.documentStoreMemorySize--;
        this.fullDocStoreSize--;
        Consumer<URI> undoDelete = BTreeImpl -> {
            Document document = restoreDocumentToStore(url, deletedDocument);
            this.docStoreURIs.add(url);
            this.documentStoreMemorySize++;
            this.fullDocStoreSize++;
            document.setLastUseTime(System.nanoTime());
            this.storage.insert(this.documentGrabber.get(url));
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
        //check if in memory here
        if(commandStack.size() == 0) throw new IllegalStateException("Command Stack is empty");
        Undoable command = this.commandStack.pop();
        if(command instanceof CommandSet<?> commandSet){
            undoCommandSet(commandSet);
        } else{
            GenericCommand<?> genericCommand = (GenericCommand<?>) command;
            boolean inMemory = this.docStoreURIs.contains((URI) ((GenericCommand<?>) command).getTarget());
            command.undo();
        }
    }

    private void undoCommandSet(CommandSet<?> command) {
        long currentTime = System.nanoTime();
        Set<? extends GenericCommand<?>> undone = command.undoAll();

        for (GenericCommand<?> genericCommand : undone){
            if (this.docStoreURIs.contains((URI) genericCommand.getTarget())) {
                Document document = this.documentStore.get((URI) genericCommand.getTarget());
                document.setLastUseTime(currentTime);
                    this.storage.reHeapify(this.documentGrabber.get(document.getKey()));
            }
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
            this.storage.reHeapify(this.documentGrabber.get(url));
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
    public List<Document> search(String keyword) throws IOException {
        if(keyword == null)
            throw new IllegalArgumentException();
        if(keyword.isEmpty())
            return new ArrayList<>();
        String newKeyword = keyword.replaceAll("[^a-zA-Z0-9\\s]", "");
        Comparator<URI> comparator = (o1, o2) -> {
            Document document1 = documentStore.get(o1);
            Document document2 = documentStore.get(o1);
            int documentOneWordCount = document1.wordCount(newKeyword);
            int documentTwoWordCount = document2.wordCount(newKeyword);
            return Integer.compare(documentTwoWordCount, documentOneWordCount);
        };
        List<URI> returnedDocumentURIS = this.documentWordsTrie.getSorted(newKeyword, comparator);
        long currentTime = System.nanoTime();
        List<Document> documents = new ArrayList<>();
        for(URI uri : returnedDocumentURIS){
            Document document = documentStore.get(uri);
            documents.add(document);
            document.setLastUseTime(currentTime);
            this.storage.reHeapify(documentGrabber.get(uri));
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
    public List<Document> searchByPrefix(String keywordPrefix) throws IOException {
        if (keywordPrefix == null)          throw new IllegalArgumentException();
        if (keywordPrefix.isEmpty())        return new ArrayList<>();
        String newKey = keywordPrefix.replaceAll("[^a-zA-Z1-9\\s]", "");
        if (newKey.isEmpty())               return new ArrayList<>();
        Comparator<URI> comparator = new Comparator<>() {
            @Override
            public int compare(URI o1, URI o2) {
                return Integer.compare(getPrefixCount(documentStore.get(o1)), getPrefixCount(documentStore.get(o2)));
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
        List<URI> returnedDocumentURIS = this.documentWordsTrie.getAllWithPrefixSorted(newKey, comparator);
        long currentTime = System.nanoTime();
        List<Document> documents = new ArrayList<>();
        for(URI uri : returnedDocumentURIS){
            Document document = documentStore.get(uri);
            documents.add(document);
            document.setLastUseTime(currentTime);
            this.storage.reHeapify(documentGrabber.get(uri));
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
        Set<URI> urisToReturn = this.documentWordsTrie.deleteAll(newKey);
        for (URI uri : urisToReturn){
            //need to check if in memory
            boolean inMemory = this.docStoreURIs.remove(uri);
            Document document = this.documentStore.get(uri);
            Consumer<URI> undoConsumer = BtreeImpl -> {
                restoreForUndoDelete(uri, inMemory, document);
            };
            commandSet.addCommand(new GenericCommand<>(uri, undoConsumer));
            removeDocumentFromStore(document, true, true, true);
        }
        this.documentStoreMemorySize -= urisToReturn.size();
        this.fullDocStoreSize -= urisToReturn.size();
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
        Set<URI> urisToReturn = this.documentWordsTrie.deleteAllWithPrefix(newKey);
        for (URI uri : urisToReturn){
            boolean inMemory = this.docStoreURIs.remove(uri);
            Document document = this.documentStore.get(uri);
            removeDocumentFromStore(document, false, true, true);
            Consumer<URI> undo = BTreeImpl -> {
                restoreForUndoDelete(uri, inMemory, document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undo));
        }
        this.fullDocStoreSize -= urisToReturn.size();
        this.documentStoreMemorySize -= urisToReturn.size();
        this.commandStack.push(commandSet);
        return urisToReturn;
    }

    private void restoreForUndoDelete(URI uri, boolean inMemory, Document document) {
        if(!inMemory) {
            this.documentStore.put(uri, document);
            try {
                this.documentStore.moveToDisk(uri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            restoreDocumentToStore(uri, document);
            this.docStoreURIs.add(document.getKey());
            this.documentStoreMemorySize++;
            this.fullDocStoreSize++;
            this.storage.insert(this.documentGrabber.get(uri));
            this.totalMemoryInBytes += getDocumentBytesLength(document);
        }
    }

    /**
     * @param keysValues metadata key-value pairs to search for
     * @return a List of all documents whose metadata contains ALL OF the given values for the given keys. If no documents contain all the given key-value pairs, return an empty list.
     */
    @Override
    public List<Document> searchByMetadata(Map<String, String> keysValues) throws IOException {
        if (keysValues == null || keysValues.isEmpty())
            return new ArrayList<>();
        List<Document> documentList = new ArrayList<>();
        Set<Map.Entry<URI, HashMap<String, String>>> entrySet = this.URItoMetadataMap.entrySet();
        for(Map.Entry<URI, HashMap<String, String>> entry : entrySet){
            HashMap<String, String> documentMetadata = entry.getValue();
            if(compareMaps(keysValues, documentMetadata)){
                documentList.add(this.documentGrabber.get(entry.getKey()).getDocument());
            }
        }
        long currentTime = System.nanoTime();
        for(Document document : documentList){
            document.setLastUseTime(currentTime);
            this.storage.reHeapify(this.documentGrabber.get(document.getKey()));
        }
        return documentList;
    }

    private boolean compareMaps(Map<String, String> keysValues, HashMap<String, String> documentMetadata) {
        return (keysValues.equals(documentMetadata));
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
    public List<Document> searchByKeywordAndMetadata(String keyword, Map<String, String> keysValues) throws IOException {
        List<Document> documents = search(keyword).stream()
                .filter(searchByMetadata(keysValues)::contains)
                .toList();
        long currentTime = System.nanoTime();
        for (Document document : documents) {
            document.setLastUseTime(currentTime);
            this.storage.reHeapify(this.documentGrabber.get(document.getKey()));
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
    public List<Document> searchByPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) throws IOException {
        List<Document> documents = searchByPrefix(keywordPrefix).stream()
                .filter(searchByMetadata(keysValues)::contains)
                .toList();
        long currentTime = System.nanoTime();
        for (Document document : documents) {
            document.setLastUseTime(currentTime);
            this.storage.reHeapify(this.documentGrabber.get(document.getKey()));
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
    public Set<URI> deleteAllWithMetadata(Map<String, String> keysValues) throws IOException {
        CommandSet<URI> commandSet = new CommandSet<>();
        Set<URI> urisToReturn = new HashSet<>();
        Set<Map.Entry<URI, HashMap<String, String>>> entrySet = this.URItoMetadataMap.entrySet();
        for(Map.Entry<URI, HashMap<String, String>> entry : entrySet) {
            HashMap<String, String> documentMetadata = entry.getValue();
            if (documentMetadata.equals(keysValues))    {urisToReturn.add(entry.getKey());}
        }
        for (URI uri : urisToReturn){
            boolean inMemory = this.docStoreURIs.remove(uri);
            Document document = this.documentStore.get(uri);
            removeDocumentFromStore(document, true, true, true);
            Consumer<URI> undo = BTreeImpl -> {
                restoreForUndoDelete(uri, inMemory, document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undo));
        }
        this.documentStoreMemorySize -= urisToReturn.size();
        this.fullDocStoreSize -= urisToReturn.size();
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
    public Set<URI> deleteAllWithKeywordAndMetadata(String keyword, Map<String, String> keysValues) throws IOException {
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
            this.docStoreURIs.remove(document.getKey());
            Consumer<URI> undoConsumer = uri -> {
                restoreDocumentToStore(document.getKey(), document);
                this.docStoreURIs.add(document.getKey());
                this.documentStoreMemorySize++;
                this.fullDocStoreSize++;
                this.storage.insert(this.documentGrabber.get(document.getKey()));
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undoConsumer));
            removeDocumentFromStore(document, true, true, true);
        }
        this.documentStoreMemorySize -= urisToReturn.size();
        this.fullDocStoreSize -= urisToReturn.size();
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
    public Set<URI> deleteAllWithPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) throws IOException {
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
            removeDocumentFromStore(document, true, true, true);
            urisToReturn.add(document.getKey());
            this.docStoreURIs.remove(document.getKey());
            Consumer<URI> undoConsumer = HashTableImpl -> {
                restoreDocumentToStore(document.getKey(), document);
                this.docStoreURIs.add(document.getKey());
                this.documentStoreMemorySize++;
                this.fullDocStoreSize++;
                this.storage.insert(this.documentGrabber.get(document.getKey()));
                this.totalMemoryInBytes += getDocumentBytesLength(document);
            };
            commandSet.addCommand(new GenericCommand<>(document.getKey(), undoConsumer));
        }
        //using the size of the uri return set to delete from size
        this.documentStoreMemorySize -= urisToReturn.size();
        this.fullDocStoreSize -= urisToReturn.size();
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
        while (this.documentStoreMemorySize > this.maxDocs){
            sendOldestDocumentToDisk();
        }
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
            sendOldestDocumentToDisk();
        }
    }

    //only for deletes?
    private Document removeDocumentFromStore(Document documentToDelete, boolean removeFromTrie, boolean removeFromComStack, boolean removeFromStorage){
        //remove document from Trie
        if(removeFromTrie) removeDocumentFromTrie(documentToDelete);
        //remove trace of document from command stack
        if(removeFromComStack) removeDocumentFromCommandStack(documentToDelete);
        //remove document from storage
        if(removeFromStorage) removeDocumentFromStorage(documentToDelete);
        //remove doc from document store
        this.documentStore.put(documentToDelete.getKey(), null);
        //remove doc from URI-Document pair hashmap
        this.documentGrabber.remove(documentToDelete.getKey());
        return documentToDelete;
    }

    private void removeDocumentFromStorage(Document documentToDelete) {
        HashSet<Document> tempSet = new HashSet<>();
        boolean found = false;
        while (!found){
            Document documentToCheck = this.storage.peek().getDocument();
            if(documentToCheck == null) {
                break;
            }
            if(documentToCheck.equals(documentToDelete)){
                this.storage.remove();
                this.totalMemoryInBytes -= getDocumentBytesLength(documentToDelete);
                found = true;
            } else {
                tempSet.add(this.storage.remove().getDocument());
            }
        }
        for (Document document : tempSet){
            this.storage.insert(this.documentGrabber.get(document.getKey()));
        }
    }

    private void removeDocumentFromTrie(Document documentToDelete) {
        if (documentToDelete.getWords() == null) return;
        for (String word : documentToDelete.getWords()) {
            documentWordsTrie.delete(word, documentToDelete.getKey());
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

    //this method does not add the uri to the uri set. That action should be done in the caller method
    private Document restoreDocumentToStore(URI url, Document previousDocument) {
        //rejecting a doc that can't fit in the store and throwing an exception
        if (this.maxMemoryInBytes != -1 && getDocumentBytesLength(previousDocument) >= this.maxMemoryInBytes)
            throw new IllegalArgumentException("doc is too big");
        //make space for document
        while ((this.maxMemoryInBytes != -1 && this.totalMemoryInBytes + getDocumentBytesLength(previousDocument) > this.maxMemoryInBytes)){
            sendOldestDocumentToDisk();
        }

        this.addDocumentWordsToTrie(previousDocument);
        this.documentStore.put(previousDocument.getKey(), previousDocument);
        if(!this.documentGrabber.containsKey(previousDocument.getKey()))
            {this.documentGrabber.put(previousDocument.getKey(), new DocumentGrabberURI(previousDocument.getKey()));}
        previousDocument.setLastUseTime(System.nanoTime());
        this.storage.insert(this.documentGrabber.get(previousDocument.getKey()));
        this.documentStoreMemorySize++;
        this.totalMemoryInBytes += getDocumentBytesLength(previousDocument);

        //making sure docStore is not above docLimit
        while (this.maxDocs != -1  && this.documentStoreMemorySize > this.maxDocs){
            //if this is the last document in the store and max docs != 0 don't move it to disk
            sendOldestDocumentToDisk();
        }
        return previousDocument;
    }

    private void sendOldestDocumentToDisk() {
        Document documentToSendToDisk = this.storage.remove().getDocument();
        try {
            this.documentStore.moveToDisk(documentToSendToDisk.getKey());
            this.documentStoreMemorySize--;
            this.totalMemoryInBytes -= getDocumentBytesLength(documentToSendToDisk);
            this.docStoreURIs.remove(documentToSendToDisk.getKey());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
