package edu.yu.cs.com3800.stage1;

import edu.yu.cs.com3800.SimpleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class Stage1Test {
    SimpleServer simpleServer;
    Client client;

    @BeforeEach
    void setUp() throws IOException {
        this.simpleServer = new SimpleServerImpl(9000);
        this.simpleServer.start();
        this.client = new ClientImpl("localhost", 9000);

    }

    @AfterEach
    void tearDown() {
        this.simpleServer.stop();
        this.client = null;
        System.out.println();
    }

    @Test
    void testSimpleServerWorksIndpOfClient() throws IOException, InterruptedException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
            "public class TestClass1 {\n" +
                    "public TestClass1(){}\n" +
                    "    public String run() {\n" +
                    "        return(\"Hello World!\");\n" +
                    "    }\n" +
                    "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9000/compileandrun"))
                .header("Content-Type", "text/x-java-source")
                .POST(HttpRequest.BodyPublishers.ofString(javaSourceCode))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String expectedResponse = "200\tHello World!";
        String actualResponse = response.statusCode()+ "\t" + response.body();
        System.out.println("Expected response: \n" + expectedResponse);
        System.out.println("Actual response: \n"+ actualResponse);
        assertEquals("Hello World!", response.body());
        assertEquals(200, response.statusCode());
        assertEquals(expectedResponse, actualResponse);
        //simpleServer.stop();
    }

    @Test
    void testSimpleServerWrongMethodIOC() throws IOException, InterruptedException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        /*String javaSourceCode =
                "public class TestClass {\n" +
                        "public TestClass(){}\n" +
                        "    public String run() {\n" +
                        "        return(\"Hello World!\");\n" +
                        "    }\n" +
                        "}";*/

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9000/compileandrun"))
                .header("Content-Type", "text/x-java-source")
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String expectedResponse = "Method not allowed " + request.method() + ". Only POST allowed.";
        String actualResponse = response.body();
        int expectedCode = 405;
        int actualCode = response.statusCode();
        System.out.println("Expected response: \n" + expectedResponse);
        System.out.println("Actual response: \n" + actualResponse);
        System.out.println("ExpectedCode: \n" + expectedCode);
        System.out.println("Actual Code: \n" + actualCode);
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedCode, actualCode);
        //simpleServer.stop();
    }

    @Test
    void testSimpleServerWrongHeaderIOC() throws IOException, InterruptedException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
                "public class TestClass2 {\n" +
                        "public TestClass2(){}\n" +
                        "    public String run() {\n" +
                        "        return(\"Hello World!\");\n" +
                        "    }\n" +
                        "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9000/compileandrun"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(javaSourceCode))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String expectedResponse = "Content-Type must be \"text/x-java-source\"";
        String actualResponse = response.body();
        int expectedCode = 400;
        int actualCode = response.statusCode();
        System.out.println("Expected response: \n" + expectedResponse);
        System.out.println("Actual response: \n" + actualResponse);
        System.out.println("Expected response: \n" + expectedCode);
        System.out.println("Actual response: \n" + actualCode);
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedCode, actualCode);
        //simpleServer.stop();
    }

    @Test
    void testSimpleServerWrongHeaderAndMethodIOC() throws IOException, InterruptedException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
                "public class TestClass3 {\n" +
                        "public TestClass3(){}\n" +
                        "    public String run() {\n" +
                        "        return(\"Hello World!\");\n" +
                        "    }\n" +
                        "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9000/compileandrun"))
                .header("Content-Type", "text/plain")
                .GET()
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String expectedResponse = "Method not allowed " + request.method() + ". Only POST allowed.";
        String actualResponse = response.body();
        int expectedCode = 405;
        int actualCode = response.statusCode();
        System.out.println("Expected response: \n" + expectedResponse);
        System.out.println("Actual response: \n" + actualResponse);
        System.out.println("ExpectedCode: \n" + expectedCode);
        System.out.println("Actual Code: \n" + actualCode);
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedCode, actualCode);
        //simpleServer.stop();
    }

    @Test
    void testSSNoHeader() throws IOException, InterruptedException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
                "public class TestClass4 {\n" +
                        "public TestClass4 (){}\n" +
                        "    public String run() {\n" +
                        "        return(\"Hello World!\");\n" +
                        "    }\n" +
                        "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9000/compileandrun"))
                .POST(HttpRequest.BodyPublishers.ofString(javaSourceCode))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String expectedResponse = "Content-Type must be \"text/x-java-source\"";
        String actualResponse = response.body();
        int expectedCode = 400;
        int actualCode = response.statusCode();
        System.out.println("Expected response: \n" + expectedResponse);
        System.out.println("Actual response: \n" + actualResponse);
        System.out.println("Expected response: \n" + expectedCode);
        System.out.println("Actual response: \n" + actualCode);
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedCode, actualCode);
        //simpleServer.stop();
    }

    @Test
    void testSSWithClient() throws IOException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        //Client client = new ClientImpl("localhost", 9000);
        String javaSourceCode =
                "public class TestClass5 {\n" +
                        "public TestClass5(){}\n" +
                        "    public String run() {\n" +
                        "        return(\"Hello World!\");\n" +
                        "    }\n" +
                        "}";
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String expectedResponse = "Hello World!";
        String actualResponse = response.getBody();
        System.out.println("Expected response: \n" + expectedResponse);
        System.out.println("Actual response: \n" + actualResponse);
        int expectedCode = 200;
        int actualCode = response.getCode();
        System.out.println("Expected response: \n" + expectedCode);
        System.out.println("Actual response: \n" + actualCode);
        assertEquals(expectedResponse, actualResponse);
        assertEquals(expectedCode, actualCode);
        //simpleServer.stop();
    }

    @Test
    void testSSWithClientBadCode() throws IOException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
                "package edu.yu.cs.com3800;\n" +
                "public class TestClass6 {\n" +
                "    public static void main(String[] args) {\n" +
                "        TestClass6 testClass = new TestClass6();\n" +
                "        String result = testClass.run();\n" +
                "    }\n" +
                "    public TestClass6() {\n" +
                "\n" +
                "    }\n" +
                "\n" +
                "    public String run() {\n" +
                "        int[] array = new int[4];\n" +
                "        for (int i = 0; i <= 5; i++) {\n" +
                "            array[i] = 1;\n" +
                "        }\n" +
                "        return \"Success\";\n" +
                "    }\n" +
                "}\n";

        //Client client = new ClientImpl("localhost", 9000);
        this.client.sendCompileAndRunRequest(javaSourceCode);
        /*String expectedResponse = "Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException: Index 4 out of bounds for length 4\n" +
                "\tat edu.yu.cs.com3800.TestClass.run(TestClass.java:15)\n" +
                "\tat edu.yu.cs.com3800.TestClass.main(TestClass.java:6)";*/
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        assertTrue(body.contains("Index 4 out of bounds for length 4"));
        assertTrue(body.contains("java.lang.ArrayIndexOutOfBoundsException"));
        assertTrue(body.contains("at edu.yu.cs.com3800.TestClass6.run("));
        //System.out.println(response.getCode());
        assertEquals(400, response.getCode());
        //System.out.println(body);
        //simpleServer.stop();
    }

    @Test
    void testCompilationError() throws IOException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
            "package edu.yu.cs.com3800;\n" +
                    "\n" +
                    "public class TestClass7 {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        TestClass7 testClass = new TestClass7();\n" +
                    "        String result = testClass7.run();\n" +
                    "    }\n" +
                    "    public TestClass7() {\n" +
                    "\n" +
                    "    }\n" +
                    "\n" +
                    "    public String run() {\n" +
                    //missing ;
                    "        return \"Success\"\n" +
                    "    }\n" +
                    "}\n";

        //Client client = new ClientImpl("localhost", 9000);
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        //System.out.println(body);
        assertTrue(body.contains("Code did not compile"));

        //simpleServer.stop();
    }

    @Test
    void testRuntimeDivideByZeroException() throws IOException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
            "package edu.yu.cs.com3800;\n" +
                    "\n" +
                    "public class TestClass8 {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        TestClass8 testClass8 = new TestClass8();\n" +
                    "        String result = testClass8.run();\n" +
                    "    }\n" +
                    "    public TestClass8() {\n" +
                    "    }\n" +
                    "\n" +
                    "    public String run() {\n" +
                    "        int num = 8/0;\n" +
                    "        return \"Success\";\n" +
                    "    }\n" +
                    "}\n";

        //Client client = new ClientImpl("localhost", 9000);
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        //System.out.println(body);
        assertTrue(body.contains("/ by zero"));
        assertTrue(body.contains("java.lang.ArithmeticException"));
        assertEquals(400, response.getCode());

        //simpleServer.stop();
    }

    @Test
    void testEmptyStringReturnedInRun() throws IOException {
        String javaSourceCode =
            "package edu.yu.cs.com3800;\n" +
                    "\n" +
                    "public class TestClass9 {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        TestClass9 testClass9 = new TestClass9();\n" +
                    "        String result = testClass9.run();\n" +
                    "    }\n" +
                    "    public TestClass9() {\n" +
                    "    }\n" +
                    "\n" +
                    "    public String run() {\n" +
                    "        return \"\";\n" +
                    "    }\n" +
                    "}\n";

        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        assertEquals("", body);
        System.out.println("Expected output: \n ");
        System.out.println("Actual output: \n" + body);
    }

    @Test
    void test2requestsBeforeGettingResponse() throws IOException {
        String javaSourceCode =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass10 {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        TestClass10 testClass10 = new TestClass10();\n" +
                        "        String result = testClass10.run();\n" +
                        "    }\n" +
                        "    public TestClass10() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"\";\n" +
                        "    }\n" +
                        "}\n";

        this.client.sendCompileAndRunRequest(javaSourceCode);
        javaSourceCode =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass10 {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        TestClass10 testClass10 = new TestClass10();\n" +
                        "        String result = testClass10.run();\n" +
                        "    }\n" +
                        "    public TestClass10() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"Hello World!\";\n" +
                        "    }\n" +
                        "}\n";
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        assertEquals("Hello World!", body);
        System.out.println("Expected output: \nHello World!");
        System.out.println("Actual output: \n" + body);
    }

    @Test
    void test2GoodRequests() throws IOException {
        String javaSourceCode1 =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass11 {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        TestClass11 testClass11 = new TestClass11();\n" +
                        "        String result = testClass11.run();\n" +
                        "    }\n" +
                        "    public TestClass11() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"One\";\n" +
                        "    }\n" +
                        "}\n";

        this.client.sendCompileAndRunRequest(javaSourceCode1);
        Client.Response response1 = this.client.getResponse();
        String body1 = response1.getBody();
        assertEquals("One", body1);
        System.out.println("Expected output: \nOne");
        System.out.println("Actual output: \n" + body1);

        String javaSourceCode2 =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass11 {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        TestClass11 testClass11 = new TestClass11();\n" +
                        "        String result = testClass11.run();\n" +
                        "    }\n" +
                        "    public TestClass11() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"Two\";\n" +
                        "    }\n" +
                        "}\n";

        this.client.sendCompileAndRunRequest(javaSourceCode2);
        Client.Response response2 = this.client.getResponse();
        String body2 = response2.getBody();
        assertEquals("Two", body2);
        System.out.println("Expected output: \nTwo");
        System.out.println("Actual output: \n" + body2);
    }

    @Test
    void testGoodRequestBadRequestGoodRequest() throws IOException {
        String goodJavaSourceCode1 =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass12 {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        TestClass12 testClass12 = new TestClass12();\n" +
                        "        String result = testClass12.run();\n" +
                        "    }\n" +
                        "    public TestClass12() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"One\";\n" +
                        "    }\n" +
                        "}\n";

        this.client.sendCompileAndRunRequest(goodJavaSourceCode1);
        Client.Response response1 = this.client.getResponse();
        String body1 = response1.getBody();
        assertEquals("One", body1);
        System.out.println("Expected output: \nOne");
        System.out.println("Actual output: \n" + body1);

        String badJavaSRC =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass12 {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        TestClass12 testClass12 = new TestClass12();\n" +
                        "        String result = testClass12.run();\n" +
                        "    }\n" +
                        "    public TestClass12() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"One\";\n" +
                        "    }\n" +
                        "}\n";

        this.client.sendCompileAndRunRequest(badJavaSRC);


        String goodJavaSourceCode2 =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass13 {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        TestClass13 testClass13 = new TestClass13();\n" +
                        "        String result = testClass13.run();\n" +
                        "    }\n" +
                        "    public TestClass13() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"Two\";\n" +
                        "    }\n" +
                        "}\n";

        //this.client.sendCompileAndRunRequest(goodJavaSourceCode2);
        Client newClient = new ClientImpl("localhost", 9000);
        newClient.sendCompileAndRunRequest(goodJavaSourceCode2);
        Client.Response response2 = newClient.getResponse();
        String body2 = response2.getBody();
        System.out.println(body2);
        assertEquals("Two", body2);
        System.out.println("Expected output: \nTwo");
        System.out.println("Actual output: \n" + body2);
    }

    @Test
    void SendRequestAfterStop() throws IOException {
        this.simpleServer.stop();
        String javaSourceCode =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass14 {\n" +
                        "    public TestClass14() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"Success\";\n" +
                        "    }\n" +
                        "}\n";

        this.client.sendCompileAndRunRequest(javaSourceCode);
        //Client.Response response = );
        assertThrows(IOException.class, () -> this.client.getResponse());
    }

    @Test
    void testCallGetResponseBeforeSendCompile() throws IOException {
        assertThrows(IOException.class, () -> this.client.getResponse());

    }

    @Test
    void testCallGetResponseBeforeSendCompileThenSendAndCompile() throws IOException {
        String javaSourceCode =
                "package edu.yu.cs.com3800;\n" +
                        "\n" +
                        "public class TestClass15 {\n" +
                        "    public TestClass15() {\n" +
                        "    }\n" +
                        "\n" +
                        "    public String run() {\n" +
                        "        return \"Success\";\n" +
                        "    }\n" +
                        "}\n";

        //assertThrows(IOException.class, () -> this.client.getResponse());
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        assertEquals("Success", body);
        System.out.println("Expected output: \nSuccess");
        System.out.println("Actual output: \n" + body);
    }

}
