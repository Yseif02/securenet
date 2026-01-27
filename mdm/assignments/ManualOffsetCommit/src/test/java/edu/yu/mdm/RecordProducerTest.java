package edu.yu.mdm;

import static org.junit.jupiter.api.Assertions.*;

class RecordProducerTest {
    public static void main(String[] args) throws InterruptedException{
        System.out.println("==== Testing Producer ====");
        System.out.println("Running for 15 seconds");
        System.out.println();

        RecordProducer producer = new RecordProducer("localhost:9092", "sales-records");

        Thread producerThread = new Thread(producer, "ProducerThread");
        producerThread.start();

        try {
            Thread.sleep(15000);

            System.out.println("\n==== Shutting down producer ====");

            producer.shutdown();
            //producerThread.interrupt();
            producerThread.join(5000);

            if (producerThread.isAlive()) {
                System.out.println("WARNING: Producer thread still running after 5 seconds");
            } else {
                System.out.println("Producer stopped cleanly");
            }

        } catch (InterruptedException e) {
            System.err.println("Test interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            AssignmentLogger.getInstance().close();
        }

        System.out.println("==== Test Complete ====");
    }
}