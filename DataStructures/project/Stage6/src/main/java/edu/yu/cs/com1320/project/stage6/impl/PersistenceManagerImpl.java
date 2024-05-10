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

public class PersistenceManagerImpl<Key, Value> implements PersistenceManager<Key, Value> {
    private File directory;
    private final Gson gson;

    public PersistenceManagerImpl(File baseDir) {
        if (baseDir == null) {
            directory = getBaseDir();
        }
        if (directory != null && !directory.isDirectory()) {
            throw new IllegalArgumentException("The specified base directory is invalid");
        }
        this.directory = baseDir;
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
            Type hashMapType = new TypeToken<HashMap<String, Integer>>(){}.getType();
            Map<String, Integer> wordCountMap = jsonObject.has("wordCountMap") && !jsonObject.get("wordCountMap").isJsonNull() ? context.deserialize(jsonObject.get("wordCountMap"), hashMapType) : null;
            DocumentImpl newDoc;
            if (text != null) {
                newDoc = new DocumentImpl(uri, text,  wordCountMap);
            } else {
                newDoc = new DocumentImpl(uri, byteArray);
            }
            if (jsonObject.has("metadata") && !jsonObject.get("metadata").isJsonNull()) {
                Type metadataMapType = new TypeToken<HashMap<String, String>>(){}.getType();
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

    @Override
    public void serialize(Key key, Value val) throws IOException {
        String url = key.toString();
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

    @Override
    public Value deserialize(Key key) throws IOException {
        String url = key.toString();
        url = url.replace("http://", "");
        String fileToGet = this.directory.getAbsolutePath() + File.separator + url + ".json";
        Reader reader = new FileReader(fileToGet);
        Value value = gson.fromJson(reader, (Class<Value>) Document.class);
        reader.close();
        if(!this.delete(key)){
            throw new IOException("deletion failed");
        }
        return value;
    }

    @Override
    public boolean delete(Key key) throws IOException {
        String url = key.toString();
        url = url.replace("http://", "");
        String fileToGet = this.directory.getAbsolutePath() + File.separator + url + ".json";
        File file = new File(fileToGet);
        if(!file.exists()){
            throw new RuntimeException("file doesn't exist");
        }
        if (file.isDirectory()) {
            return false;
        }
        return file.delete();
    }
}
