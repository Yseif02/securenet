package edu.yu.cs.com3800.stage1;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ClientImpl implements Client {
    private final HttpClient client;
    CompletableFuture<HttpResponse<String>> clientResponse;
    private final URI uri;
    //private final Stack<CompletableFuture<HttpResponse<String>>> responses;

    public ClientImpl(String hostName, int hostPort) throws MalformedURLException {
        if (hostName == null) throw new IllegalArgumentException("HostName can't be null");
        if (hostPort < 0) throw new IllegalArgumentException("Port is out of range: " + hostPort);
        this.client = HttpClient.newHttpClient();
        this.clientResponse = null;
        //this.mostRecentResponse = null;
        try {
            //this.uri = new URL("http", hostName, hostPort, "/compileandrun").toURI();
            this.uri = new URI("http", null, hostName, hostPort, "/compileandrun", null, null);
        } catch (URISyntaxException e) {
            throw new MalformedURLException();
        }
    }

    @Override
    public void sendCompileAndRunRequest(String src) throws IOException {
        //String url = String.format("http://%s:%d/compileandrun", this.hostName, this.hostPort);
        if (src == null) throw new IllegalArgumentException("Source can't be null");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(this.uri)
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(src))
                .build();

        this.clientResponse = this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete(((stringHttpResponse, throwable) ->{
                    if (throwable != null) {
                       this.clientResponse = null;
                    }
                }));


    }

    @Override
    public Response getResponse() throws IOException {
        if (this.clientResponse == null) {
            throw new IOException("sendCompileAndRunRequest() must be called before getResponse()");
        }
        /*if (!this.clientResponse.isCompletedExceptionally()) {
            throw new IOException("Request failed to send to the server");
        }*/
        HttpResponse<String> response;
        try {
            response = this.clientResponse.get();
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for response", e);
        } catch (ExecutionException e) {
            // See why request/response couldn't complete
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException) {
                throw new IOException("Server is unavailable", cause);
            }
            throw new IOException("Request failed", cause);
        }
    }
}
