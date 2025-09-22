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
    private final String hostName;
    private final int hostPort;
    private final HttpClient client;
    CompletableFuture<HttpResponse<String>> clientResponse;
    private final URI uri;
    //private final Stack<CompletableFuture<HttpResponse<String>>> responses;

    public ClientImpl(String hostName, int hostPort) throws MalformedURLException {
        super();
        this.hostName = hostName;
        this.hostPort = hostPort;
        this.client = HttpClient.newHttpClient();
        clientResponse = null;
        //this.mostRecentResponse = null;
        try {
            this.uri = new URL("http", hostName, hostPort, "/compileandrun").toURI();
        } catch (URISyntaxException e) {
            throw new MalformedURLException();
        }
    }

    @Override
    public void sendCompileAndRunRequest(String src) throws IOException {
        //String url = String.format("http://%s:%d/compileandrun", this.hostName, this.hostPort);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(this.uri)
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(src))
                .build();

        this.clientResponse = this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        //this.responses.add(clientResponse);


    }

    @Override
    public Response getResponse() throws IOException {
        if (clientResponse == null) {
            throw new IOException("sendCompileAndRunRequest() must be called before getResponse()");
        }
        HttpResponse<String> response;
        try {
            response = this.clientResponse.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
        return new Response(response.statusCode(), response.body());
    }
}
