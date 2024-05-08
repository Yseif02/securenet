package edu.yu.cs.com1320.project.stage6.impl;

import com.google.gson.*;
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
                //.registerTypeAdapter(Document.class, new DocumentSerializer())
                .registerTypeAdapter(Document.class, new DocumentDeserializer())
                .create();
    }

    private static class DocumentSerializer implements JsonSerializer<Document>{
        @Override
        public JsonElement serialize(Document document, Type type, JsonSerializationContext jsonSerializationContext) {

            return null;
        }
    }

    private static class DocumentDeserializer implements JsonDeserializer<Document>{
        @Override
        public Document deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            URI uri = URI.create(jsonObject.get("uri").getAsString());
            String text = jsonObject.get("text").isJsonNull() ? null : jsonObject.get("text").getAsString();
            byte[] byteArray = jsonObject.get("binaryData").isJsonNull() ? null : Base64.getDecoder().decode(jsonObject.get("binaryData").getAsString());

            JsonElement metaDataElement = jsonObject.get("metaData");
            HashMap<String, String> metaData = metaDataElement.isJsonNull() ? null : context.deserialize(new JsonParser().parse(metaDataElement.getAsString()), HashMap.class);

            JsonElement wordCountMapElement = jsonObject.get("wordCountMap");
            HashMap<String, Integer> wordCountMap = wordCountMapElement.isJsonNull() ? null : context.deserialize(new JsonParser().parse(wordCountMapElement.getAsString()), HashMap.class);

            DocumentImpl newDoc;
            if (text != null){
                newDoc = new DocumentImpl(uri, text, wordCountMap);
            }else{
                newDoc = new DocumentImpl(uri, byteArray);
            }
            newDoc.setMetadata(metaData);
            return newDoc;
        }
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
            fileWriter.write(serializeDocument((Document) val));
        }
    }

    @Override
    public Value deserialize(Key key) throws IOException {
        String url = key.toString();
        url = url.replace("http://", "");
        String fileToGet = this.directory.getAbsolutePath() + File.separator + url + ".json";
        Reader reader = new FileReader(fileToGet);
        this.delete(key);
        return gson.fromJson(reader, (Class<Value>) Document.class);
    }

    @Override
    public boolean delete(Key key) throws IOException {
        String url = key.toString();
        File file = new File(url);
        if (file.isDirectory()) return false;
        return file.delete();
    }

    private String serializeDocument(Document document) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("uri", document.getKey().toString());
        jsonObject.addProperty("text", document.getDocumentTxt());
        jsonObject.addProperty("binaryData", document.getDocumentBinaryData() != null ? Base64.getEncoder().encodeToString(document.getDocumentBinaryData()) : null);
        jsonObject.addProperty("metaData", gson.toJson(document.getWordMap()));
        jsonObject.addProperty("wordCountMap", gson.toJson(document.getWordMap()));
        return jsonObject.toString();
    }
}
