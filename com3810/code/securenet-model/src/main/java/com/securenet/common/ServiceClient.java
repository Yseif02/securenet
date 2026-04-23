package com.securenet.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight HTTP client used by every SecureNet service to call
 * other services over the network.
 *
 * <p>Wraps {@link java.net.http.HttpClient} with JSON serialization
 * via {@link JsonUtil} and provides simple GET/POST/PUT/DELETE methods
 * that return a {@link ServiceResponse} carrying status code and body.
 *
 * <p>Thread-safe: the underlying {@link HttpClient} is thread-safe.
 */
public class ServiceClient {

    private final HttpClient httpClient;

    public ServiceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Sends an HTTP GET request and returns the raw response.
     *
     * @param url the full URL to GET
     * @return the response containing status code and body
     * @throws IOException if the request fails at the network level
     */
    public ServiceResponse get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();
        return execute(request);
    }

    /**
     * Sends an HTTP POST request with a JSON body.
     *
     * @param url  the full URL to POST to
     * @param body the object to serialize as JSON; may be {@code null}
     *             for an empty body
     * @return the response containing status code and body
     * @throws IOException if the request fails at the network level
     */
    public ServiceResponse post(String url, Object body) throws IOException {
        return post(url, body, Map.of());
    }

    public ServiceResponse post(String url, Object body, Map<String, String> headers)
            throws IOException {
        String json = (body != null) ? JsonUtil.toJson(body) : "";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10));
        headers.forEach(builder::header);
        return execute(builder.build());
    }

    /**
     * Sends an HTTP PUT request with a JSON body.
     *
     * @param url  the full URL to PUT to
     * @param body the object to serialize as JSON
     * @return the response containing status code and body
     * @throws IOException if the request fails at the network level
     */
    public ServiceResponse put(String url, Object body) throws IOException {
        String json = (body != null) ? JsonUtil.toJson(body) : "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();
        return execute(request);
    }

    /**
     * Sends an HTTP DELETE request.
     *
     * @param url the full URL to DELETE
     * @return the response containing status code and body
     * @throws IOException if the request fails at the network level
     */
    public ServiceResponse delete(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();
        return execute(request);
    }

    private ServiceResponse execute(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return new ServiceResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }

    /**
     * Immutable response carrying the HTTP status code and body text.
     *
     * @param statusCode the HTTP status code
     * @param body       the response body as a string (may be empty)
     */
    public record ServiceResponse(int statusCode, String body) {

        public ServiceResponse {
            Objects.requireNonNull(body, "body");
        }

        /** @return {@code true} if the status code is in the 2xx range */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        /**
         * Deserializes the response body as JSON into the given type.
         *
         * @param clazz the target type
         * @param <T>   target type
         * @return the deserialized object
         */
        public <T> T bodyAs(Class<T> clazz) {
            return JsonUtil.fromJson(body, clazz);
        }
    }
}
