package edu.yu.cs.com3800.stage1;

import edu.yu.cs.com3800.JavaRunner;
import edu.yu.cs.com3800.SimpleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class Stage1Test {
    SimpleServer simpleServer;
    Client client;

    public Stage1Test() {
    }

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
                """
                        public class TestClass5 {
                        public TestClass5(){}
                            public String run() {
                                return("Hello World!");
                            }
                        }""";
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
                """
                        package edu.yu.cs.com3800;
                        public class TestClass6 {
                            public static void main(String[] args) {
                                TestClass6 testClass = new TestClass6();
                                String result = testClass.run();
                            }
                            public TestClass6() {
                        
                            }
                        
                            public String run() {
                                int[] array = new int[4];
                                for (int i = 0; i <= 5; i++) {
                                    array[i] = 1;
                                }
                                return "Success";
                            }
                        }
                        """;

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
        assertEquals(400, response.getCode());
        assertTrue(body.contains("Code did not compile"));

        //simpleServer.stop();
    }

    @Test
    void testRuntimeDivideByZeroException() throws IOException {
        //SimpleServer simpleServer = new SimpleServerImpl(9000);
        //simpleServer.start();
        String javaSourceCode =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass8 {
                            public static void main(String[] args) {
                                TestClass8 testClass8 = new TestClass8();
                                String result = testClass8.run();
                            }
                            public TestClass8() {
                            }
                        
                            public String run() {
                                int num = 8/0;
                                return "Success";
                            }
                        }
                        """;

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
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass9 {
                            public static void main(String[] args) {
                                TestClass9 testClass9 = new TestClass9();
                                String result = testClass9.run();
                            }
                            public TestClass9() {
                            }
                        
                            public String run() {
                                return "";
                            }
                        }
                        """;

        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        System.out.println("Expected output: \n ");
        System.out.println("Actual output: \n" + body);
        assertEquals("", body);
        assertEquals(200, response.getCode());
    }

    @Test
    void test2requestsBeforeGettingResponse() throws IOException {
        String javaSourceCode =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass10 {
                            public static void main(String[] args) {
                                TestClass10 testClass10 = new TestClass10();
                                String result = testClass10.run();
                            }
                            public TestClass10() {
                            }
                        
                            public String run() {
                                return "";
                            }
                        }
                        """;

        this.client.sendCompileAndRunRequest(javaSourceCode);
        javaSourceCode =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass10 {
                            public static void main(String[] args) {
                                TestClass10 testClass10 = new TestClass10();
                                String result = testClass10.run();
                            }
                            public TestClass10() {
                            }
                        
                            public String run() {
                                return "Hello World!";
                            }
                        }
                        """;
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        System.out.println("Expected response: \nHello World!");
        System.out.println("Actual response: \n" + body);
        System.out.println("Expected code: 200");
        System.out.println("Actual code: " + response.getCode());
        assertEquals("Hello World!", body);
        assertEquals(200, response.getCode());
    }

    @Test
    void test2GoodRequests() throws IOException {
        String javaSourceCode1 =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass11 {
                            public static void main(String[] args) {
                                TestClass11 testClass11 = new TestClass11();
                                String result = testClass11.run();
                            }
                            public TestClass11() {
                            }
                        
                            public String run() {
                                return "One";
                            }
                        }
                        """;

        this.client.sendCompileAndRunRequest(javaSourceCode1);
        Client.Response response1 = this.client.getResponse();
        String body1 = response1.getBody();
        System.out.println("Expected output: \nOne");
        System.out.println("Actual output: \n" + body1);
        assertEquals("One", body1);

        String javaSourceCode2 =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass11 {
                            public static void main(String[] args) {
                                TestClass11 testClass11 = new TestClass11();
                                String result = testClass11.run();
                            }
                            public TestClass11() {
                            }
                        
                            public String run() {
                                return "Two";
                            }
                        }
                        """;

        this.client.sendCompileAndRunRequest(javaSourceCode2);
        Client.Response response2 = this.client.getResponse();
        String body2 = response2.getBody();
        System.out.println("Expected output: \nTwo");
        System.out.println("Actual output: \n" + body2);
        assertEquals("Two", body2);
    }

    @Test
    void testGoodRequestBadRequestGoodRequest() throws IOException {
        String goodJavaSourceCode1 =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass12 {
                            public static void main(String[] args) {
                                TestClass12 testClass12 = new TestClass12();
                                String result = testClass12.run();
                            }
                            public TestClass12() {
                            }
                        
                            public String run() {
                                return "One";
                            }
                        }
                        """;

        this.client.sendCompileAndRunRequest(goodJavaSourceCode1);
        Client.Response response1 = this.client.getResponse();
        String body1 = response1.getBody();
        assertEquals("One", body1);
        System.out.println("Expected output: \nOne");
        System.out.println("Actual output: \n" + body1);

        String badJavaSRC =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass12 {
                            public static void main(String[] args) {
                                TestClass12 testClass12 = new TestClass12();
                                String result = testClass12.run();
                            }
                            public TestClass12() {
                            }
                        
                            public String run() {
                                return "One";
                            }
                        }
                        """;

        this.client.sendCompileAndRunRequest(badJavaSRC);


        String goodJavaSourceCode2 =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass13 {
                            public static void main(String[] args) {
                                TestClass13 testClass13 = new TestClass13();
                                String result = testClass13.run();
                            }
                            public TestClass13() {
                            }
                        
                            public String run() {
                                return "Two";
                            }
                        }
                        """;

        //this.client.sendCompileAndRunRequest(goodJavaSourceCode2);
        Client newClient = new ClientImpl("localhost", 9000);
        newClient.sendCompileAndRunRequest(goodJavaSourceCode2);
        Client.Response response2 = newClient.getResponse();
        String body2 = response2.getBody();
        System.out.println(body2);
        System.out.println("Expected output: \nTwo");
        System.out.println("Actual output: \n" + body2);
        assertEquals("Two", body2);
    }


    // I redid this test @line 773 without realizing I tested for this already lol. Found a bug. Did better testing there
    @Test
    void SendRequestAfterStop() throws IOException {
        this.simpleServer.stop();
        String javaSourceCode =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass14 {
                            public TestClass14() {
                            }
                        
                            public String run() {
                                return "Success";
                            }
                        }
                        """;

        this.client.sendCompileAndRunRequest(javaSourceCode);
        //Client.Response response = );
        assertThrows(IOException.class, () -> this.client.getResponse());
    }

    @Test
    void testCallGetResponseBeforeSendCompile() throws IOException {
        IOException exception = assertThrows(IOException.class, () -> this.client.getResponse());
        String expectedMessage = "sendCompileAndRunRequest() must be called before getResponse()";
        System.out.println("Expected exception message: " + expectedMessage);
        System.out.println("Actual exception message: " + exception.getMessage());
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testCallGetResponseBeforeSendCompileThenSendAndCompile() throws IOException {
        String javaSourceCode =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TestClass15 {
                            public TestClass15() {
                            }
                        
                            public String run() {
                                return "Success";
                            }
                        }
                        """;

        //assertThrows(IOException.class, () -> this.client.getResponse());
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        String body = response.getBody();
        assertEquals("Success", body);
        System.out.println("Expected output: \nSuccess");
        System.out.println("Actual output: \n" + body);
    }

    @Test
    void testJunkCode() throws IOException {
        String src = "saifaifafoa";
        JavaRunner javaRunner = new JavaRunner();
        String runnerResponse = null;
        try {
            runnerResponse = javaRunner.compileAndRun(new ByteArrayInputStream(src.getBytes()));
        } catch (Exception e) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(outputStream));
            String expectedResponse = "No class name found in code";
            this.client.sendCompileAndRunRequest(src);
            String response = this.client.getResponse().getBody();
            //System.out.println(expectedResponse);
            System.out.println("Expected message in Exception: " + expectedResponse);
            System.out.println("Actual response: " + response);
            assertTrue(response.contains(expectedResponse));
            assertTrue(response.contains("IllegalArgumentException"));
        }

       
    }


    @Test
    void testNegPortInSS() throws IOException {
        IOException exception = assertThrows(IOException.class, () -> new SimpleServerImpl(-1));
        assertEquals("Port is out of range: -1", exception.getMessage());
    }

    @Test
    void testNullHostName() throws IOException {
        //Client client1 = ;
        String javaSourceCode =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TC1 {
                            public TC1() {
                            }
                        
                            public String run() {
                                return "Success";
                            }
                        }
                        """;
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new ClientImpl(null, 9000));
        assertEquals("HostName can't be null", exception.getMessage());

    }

    @Test
    void testBadPortName() throws IOException {
        //Client client1 = ;
        String javaSourceCode =
                """
                        package edu.yu.cs.com3800;
                        
                        public class TC1 {
                            public TC1() {
                            }
                        
                            public String run() {
                                return "Success";
                            }
                        }
                        """;
        Exception exception = assertThrows(IllegalArgumentException.class, () -> new ClientImpl("localhost", -1));
        assertEquals("Port is out of range: " + -1, exception.getMessage());

    }

    @Test
    void testNullSRCForSendReq() throws IOException {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> this.client.sendCompileAndRunRequest(null));
        assertEquals("Source can't be null", exception.getMessage());
    }

    //server can't be restarted
    /*@Test
    void testStartStopStartSendReq() throws IOException, InterruptedException {
        this.simpleServer.stop();
        wait(500);
        this.simpleServer.start();
        String javaSourceCode =
            """
                package edu.yu.cs.com3800;
                
                public class TC2 {
                    public TC2() {
                    }
                
                    public String run() {
                        return "Success";
                    }
                }
                """;
        this.client.sendCompileAndRunRequest(javaSourceCode);
        Client.Response response = this.client.getResponse();
        assertEquals("Success", response.getBody());
    }*/

    @Test
    void testSSTwoDifferentPorts() throws IOException{
        String source1 =
            """
                package edu.yu.cs.com3800;
                
                   public class TC3 {
                       public TC3() {
                       }

                       public String run() {
                           return "Success1";
                       }
                   }
                """;
        SimpleServer simpleServer2 = new SimpleServerImpl(8000);
        simpleServer2.start();
        Client client2 = new ClientImpl("localhost", 8000);
        this.client.sendCompileAndRunRequest(source1);
        Client.Response response1 = this.client.getResponse();
        assertEquals("Success1", response1.getBody());
        String source2 =
            """
                package edu.yu.cs.com3800;
                
                   public class TC3 {
                       public TC3() {
                       }

                       public String run() {
                           return "Success2";
                       }
                   }
                """;
        client2.sendCompileAndRunRequest(source2);
        Client.Response response2 = client2.getResponse();
        assertEquals("Success2", response2.getBody());
        simpleServer2.stop();
    }

    @Test
    void testTryNewSSOnUnAvailablePort() throws IOException {
        IOException exception = assertThrows(IOException.class, () -> new SimpleServerImpl(9000));
        System.out.println(exception.getMessage());
    }

    @Test
    void multipleClientsOnSamePort() throws IOException {
        String source1 =
                """
                    package edu.yu.cs.com3800;
                    
                       public class TC4 {
                           public TC4() {
                           }
    
                           public String run() {
                               return "Success1";
                           }
                       }
                    """;

        Client client2 = new ClientImpl("localhost", 9000);
        this.client.sendCompileAndRunRequest(source1);
        client2.sendCompileAndRunRequest(source1);
        assertEquals(client2.getResponse().getBody(), client.getResponse().getBody());
    }

    @Test
    void testSendRequestAndCheckResponseAfterServerShutdown() throws IOException {
        this.simpleServer.stop();
        String source1 =
            """
                package edu.yu.cs.com3800;
                
                   public class TC5 {
                       public TC5() {
                       }

                       public String run() {
                           return "Success1";
                       }
                   }
                """;

        this.client.sendCompileAndRunRequest(source1);
        IOException ioException = assertThrows(IOException.class, () -> this.client.getResponse());
        System.out.println("Expected message: Server is unavailable");
        System.out.println("Actual message: " + ioException.getMessage());
        assertEquals("Server is unavailable", ioException.getMessage());
    }
}
