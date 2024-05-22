package edu.yu.cs.com1320.project.stage6.impl;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public class DocumentPersistenceManager<Key, Value> implements PersistenceManager<URI, Document> {
    private File directory;
    private final Gson gson;

    public DocumentPersistenceManager(File baseDir) {
        if (baseDir == null) {
            this.directory = getBaseDir();
        }else{
            this.directory = baseDir;
        }
        if (directory != null && !directory.isDirectory()) {
            throw new IllegalArgumentException("The specified base directory is invalid");
        }
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Document.class, (JsonSerializer<Document>) (document, typeOfT, context) -> {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("uri", document.getKey().toString());
                    jsonObject.addProperty("text", document.getDocumentTxt());
                    jsonObject.addProperty("binaryData", document.getDocumentBinaryData() != null ? Base64.getEncoder().encodeToString(document.getDocumentBinaryData()) : null);
                    JsonObject wordCountMapJson = new JsonObject();
                    if (document.getWordMap() != null) {
                        for (Map.Entry<String, Integer> entry : document.getWordMap().entrySet()) {
                            wordCountMapJson.addProperty(entry.getKey(), entry.getValue());
                        }
                    }
                    jsonObject.add("wordCountMap", wordCountMapJson);
                    JsonObject metadataJson = new JsonObject();
                    if (document.getMetadata() != null) {
                        Map<String, String> metadata = document.getMetadata();
                        for (Map.Entry<String, String> entry : metadata.entrySet()) {
                            metadataJson.addProperty(entry.getKey(), entry.getValue());
                        }
                    }
                    jsonObject.add("metadata", metadataJson);
                    return jsonObject;
                })
                .registerTypeAdapter(Document.class, (JsonDeserializer<Document>) (document, typeOfT, context) -> {
                    JsonObject jsonObject = document.getAsJsonObject();
                    URI uri = URI.create(jsonObject.get("uri").getAsString());
                    String text = jsonObject.has("text") && !jsonObject.get("text").isJsonNull() ? jsonObject.get("text").getAsString() : null;
                    byte[] byteArray = jsonObject.has("binaryData") && !jsonObject.get("binaryData").isJsonNull() ? Base64.getDecoder().decode(jsonObject.get("binaryData").getAsString()) : null;
                    Type hashMapType = new TypeToken<HashMap<String, Integer>>() {}.getType();
                    Map<String, Integer> wordCountMap = jsonObject.has("wordCountMap") && !jsonObject.get("wordCountMap").isJsonNull() ? context.deserialize(jsonObject.get("wordCountMap"), hashMapType) : null;
                    DocumentImpl newDoc;
                    if (text != null) {
                        newDoc = new DocumentImpl(uri, text, wordCountMap);
                    } else {
                        newDoc = new DocumentImpl(uri, byteArray);
                    }
                    if (jsonObject.has("metadata") && !jsonObject.get("metadata").isJsonNull()) {
                        Type metadataMapType = new TypeToken<HashMap<String, String>>() {}.getType();
                        HashMap<String, String> metadata = context.deserialize(jsonObject.get("metadata"), metadataMapType);
                        //only time this method is called
                        newDoc.setMetadata(metadata);
                    }
                    return newDoc;
                })
                .setPrettyPrinting()
                .create();
    }

    private File getBaseDir() {
        String baseDir = System.getProperty("user.dir");
        return new File(baseDir);
    }
    /**
     * @param uri
     * @param val
     * @throws IOException
     */
    @Override
    public void serialize(URI uri, Document val) throws IOException {
        if(uri == null || val == null)
            throw new IllegalArgumentException("uri or document to serialize is null");
        String url = uri.toString();
        if (url.startsWith("http://")) {
            url = url.substring("http://".length()); // Remove "http://"
        }
        url = url.replace("/", File.separator);
        String directoryToWriteTo = this.directory.getAbsolutePath() + File.separator + url + ".json";
        File file = new File(directoryToWriteTo);
        file.getParentFile().mkdirs();
        file.createNewFile();
        try (FileWriter fileWriter = new FileWriter(file)) {
            gson.toJson(val, Document.class, fileWriter);
        }
    }

    /**
     * @param uri
     * @return
     * @throws IOException
     */
    @Override
    public Document deserialize(URI uri) throws IOException {
        if(uri == null)
            throw new IllegalArgumentException("uri to deserialize is null");
        String url = uri.toString();
        url = url.replace("http://", "");
        String fileToGet = this.directory.getAbsolutePath() + File.separator + url + ".json";
        Reader reader = new FileReader(fileToGet);
        Document document = gson.fromJson(reader, Document.class);
        reader.close();
        if(!this.delete(uri)){
            throw new IOException("deletion failed");
        }
        return document;
    }

    /**
     * delete the file stored on disk that corresponds to the given key
     *
     * @param uri
     * @return true or false to indicate if deletion occured or not
     * @throws IOException
     */
    @Override
    public boolean delete(URI uri) throws IOException {
        if(uri == null)
            throw new IllegalArgumentException("uri to delete is null");
        String url = uri.toString();
        url = url.replace("http://", "");
        String fileToGet = this.directory.getAbsolutePath() + File.separator + url + ".json";
        File file = new File(fileToGet);
        if(!file.exists()){
            throw new RuntimeException("file doesn't exist");
        }
        if (file.isDirectory()) {
            return false;
        }
        boolean fileDeleted = file.delete();
        if (fileDeleted) {
            deleteEmptyParentDirectories(file.getParentFile());
        }
        return fileDeleted;
    }

    private void deleteEmptyParentDirectories(File currentDirectory) {
        while (!currentDirectory.equals(this.directory) && currentDirectory.isDirectory() && currentDirectory.list().length == 0 && currentDirectory.delete()) {
            currentDirectory = currentDirectory.getParentFile();
        }
    }
}
