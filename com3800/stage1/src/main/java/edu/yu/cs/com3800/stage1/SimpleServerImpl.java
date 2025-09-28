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

    //private final ServerSocket serverSocket;
    private final HttpServer server;
    private int port;
    private boolean on;

    //Stopped is used to see if the server has been stopped in its lifetime
    private boolean stopped = false;

    public SimpleServerImpl(int port) throws IOException {
        if (port < 0) {
            throw new IOException("Port is out of range: " + port);
        }
        this.port = port;
        this.on = false;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        createContext(this.server);
    }

    private void createContext(HttpServer server) {
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
        /*if (!this.on && stopped) {
            try {
                this.server = HttpServer.create(new InetSocketAddress(this.port), 0);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.on = true;
        }*/
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
