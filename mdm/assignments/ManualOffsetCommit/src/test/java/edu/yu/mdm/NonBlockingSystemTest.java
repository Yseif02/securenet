package edu.yu.mdm;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;

public class NonBlockingSystemTest {
    public static void main(String[] args) {
        String TOPIC = "sales-records";

        System.out.println("==== Non-Blocking System Test ====");
        System.out.println("==== Cleaning up Kafka topic ====");
        KafkaTopicManager topicManager = new KafkaTopicManager("localhost:9092");
        topicManager.resetTopic(TOPIC);

        System.out.println("Phase 1: Running for 15 seconds");
        System.out.println();

        LinkedBlockingQueue<BatchInfo> phase1BatchQueue = new LinkedBlockingQueue<>();

        RecordProducer producer = new RecordProducer("localhost:9092", TOPIC);
        RecordConsumer phase1Consumer = new RecordConsumer("localhost:9092", phase1BatchQueue, TOPIC);
        RecordProcessor phase1Processor = new RecordProcessor(phase1BatchQueue, phase1Consumer);

        Thread producerThread = new Thread(producer, "producerThread");
        Thread phase1ConsumerThread = new Thread(phase1Consumer, "phase1ConsumerThread");
        Thread phase1ProcessorThread = new Thread(phase1Processor, "phase1ProcessorThread");

        producerThread.start();
        phase1ConsumerThread.start();
        phase1ProcessorThread.start();

        // === Phase 1 ===
        try {
            Thread.sleep(15000);

            System.out.println("\n==== Phase 1 complete ====\n Stopping Consumer and Processor");

            phase1Consumer.shutdown();
            phase1Processor.shutdown();

            phase1ConsumerThread.join(5000);
            phase1ProcessorThread.join(5000);

            System.out.println("Consumer and Processor stopped");
        } catch (InterruptedException e) {
            System.err.println("Test interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        // === Wait 30 Seconds ===
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting between phase 1 and 2", e);
        }

        //=== Phase 2 ===
        System.out.println("==== Starting Phase 2 ====");
        LinkedBlockingQueue<BatchInfo> phase2batchQueue = new LinkedBlockingQueue<>();

        RecordConsumer phase2Consumer = new RecordConsumer("localhost:9092", phase2batchQueue, TOPIC);
        RecordProcessor phase2Processor = new RecordProcessor(phase2batchQueue, phase2Consumer);

        Thread phase2ConsumerThread = new Thread(phase2Consumer, "phase1ConsumerThread");
        Thread phase2ProcessorThread = new Thread(phase2Processor, "phase1ProcessorThread");

        phase2ConsumerThread.start();
        phase2ProcessorThread.start();

        try {
            Thread.sleep(30000);

            System.out.println("==== Phase 2 complete ====");

            // === Shutdown ===
            System.out.println("\n==== Final Shutdown ====");

            producer.shutdown();
            phase2Consumer.shutdown();
            phase2Processor.shutdown();

            phase2ProcessorThread.interrupt();

            producerThread.join(5000);
            phase2ConsumerThread.join(10000);
            phase2ProcessorThread.join(10000);

            System.out.println("All threads stopped");
        } catch (InterruptedException e) {
            System.err.println("Test interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        System.out.println("\n==== Test Complete ====");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        AssignmentLogger.getInstance().close();
    }
}
