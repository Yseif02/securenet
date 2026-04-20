package com.securenet.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Shared JSON serialization utility for all SecureNet inter-service
 * HTTP/JSON communication.
 *
 * <p>Uses Gson with custom type adapters for {@link Instant} (serialized
 * as ISO-8601 strings) and {@link Duration} (serialized as ISO-8601
 * duration strings like "PT30S").
 *
 * <p>Thread-safe: the internal {@link Gson} instance is immutable and
 * safe for concurrent use.
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(Duration.class, new DurationAdapter())
            .create();

    private JsonUtil() {}

    /**
     * Serializes any object to its JSON representation.
     *
     * @param object the object to serialize
     * @return JSON string
     */
    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Deserializes a JSON string into an object of the given type.
     *
     * @param json  the JSON string
     * @param clazz the target type
     * @param <T>   target type
     * @return the deserialized object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /**
     * Returns the shared {@link Gson} instance for advanced use cases
     * such as deserializing parameterized types.
     *
     * @return the configured Gson instance
     */
    public static Gson gson() {
        return GSON;
    }

    // =====================================================================
    // Type adapters
    // =====================================================================

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }

    private static final class DurationAdapter extends TypeAdapter<Duration> {
        @Override
        public void write(JsonWriter out, Duration value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Duration read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Duration.parse(in.nextString());
        }
    }
}
