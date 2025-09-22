package edu.yu.cs.com3800.stage1;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.JavaRunner;
import edu.yu.cs.com3800.SimpleServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class SimpleServerImpl implements SimpleServer {

    private final int port;
    //private final ServerSocket serverSocket;
    private final HttpServer server;
    private boolean on;

    public SimpleServerImpl(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(this.port), 0);
        this.on = false;
        server.createContext("/compileandrun", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String requestType = exchange.getRequestMethod();
                // check if request type is post
                if (!requestType.equalsIgnoreCase("POST")) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    String response = "Method not allowed " + exchange.getRequestMethod() + ". Only POST allowed.";
                    sendBadResponse(exchange, 405, response);
                    return;
                }

                //check content type
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.equals("text/x-java-source")) {
                    //System.out.println(contentType);
                    String response = "Content-Type must be \"text/x-java-source\"";
                    sendBadResponse(exchange, 400, response);
                    return;
                }
                JavaRunner runner = new JavaRunner();
                String runnerResponse;
                try {
                    runnerResponse = runner.compileAndRun(exchange.getRequestBody());
                    sendGoodResponse(exchange, runnerResponse);
                } catch (Exception e) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    e.printStackTrace(new PrintStream(outputStream));
                    String response = e.getMessage() + "\n" + outputStream.toString();
                    sendBadResponse(exchange, 400, response);
                }

                //String requestAsString = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            }

            private void sendGoodResponse(HttpExchange exchange, String response) throws IOException {
                byte[] message = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, message.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(message);
                }
            }

            private void sendBadResponse(HttpExchange exchange, int status, String response) throws IOException {
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                if (response == null) {
                    exchange.sendResponseHeaders(status, -1);
                    return;
                }
                byte[] message = response.getBytes(StandardCharsets.UTF_8);
                //exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(status, message.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(message);
                }
            }
        });
    }

    /**
     * start the server
     */
    @Override
    public void start() {
        if (!this.on) {
            this.server.start();
            this.on = true;
        }

    }



    /**
     * stop the server
     */
    @Override
    public void stop() {
        if (this.on) {
            this.server.stop(0);
            this.on = false;
        }
    }

    public static void main(String[] args) {
        int port = 9000;
        if(args.length >0)
        {
            port = Integer.parseInt(args[0]);
        }
        SimpleServer myserver = null;
        try
        {
            myserver = new SimpleServerImpl(port);
            myserver.start();
        }
        catch(Exception e)
        {
            System.err.println(e.getMessage());
            myserver.stop();
        }
    }
}
