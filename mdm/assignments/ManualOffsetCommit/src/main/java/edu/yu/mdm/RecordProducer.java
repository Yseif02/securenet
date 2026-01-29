package edu.yu.mdm;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;


import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;


public class RecordProducer implements Runnable{
    private static final int BATCH_SIZE = 25;
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final AtomicLong idGen;
    private final Random random;
    private volatile boolean running;
    private final long startTime = System.currentTimeMillis();
    private final AssignmentLogger logger;


    public RecordProducer(String bootstrapServers, String topic) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.RETRIES_CONFIG, 3);

        this.topic = topic;
        this.producer = new KafkaProducer<>(properties);
        this.idGen = new AtomicLong();
        this.random = new Random();
        this.running = true;
        this.logger = AssignmentLogger.getInstance();
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        log("Starting runProducer");
        try {
            while (this.running) {
                for (int i = 0; i < BATCH_SIZE; i++) {
                    SalesRecord record = generateSalesRecord();
                    sendRecord(record);
                }

                Thread.sleep(3000);
            }
        } catch (InterruptedException e) {
            log("runProducer: Producer interrupted");
            Thread.currentThread().interrupt();
        } finally {
            log("runProducer: Stopping runProducer");
            if (this.producer != null) {
                producer.flush();
                producer.close();
            }
        }
    }

    private SalesRecord generateSalesRecord() {
        String product = RandomDataGenerator.randomProductName();
        double unitPrice = RandomDataGenerator.getProductPrice(product);
        int quantity = random.nextInt(101);
        double totalPrice = unitPrice * quantity;
        String name = RandomDataGenerator.randomName();
        long timeSinceEpoch = System.currentTimeMillis() - this.startTime;
        long id = this.idGen.getAndIncrement();
        return new SalesRecord(product, quantity, totalPrice, System.currentTimeMillis(), id, name);
    }

    private void sendRecord(SalesRecord record) {
        String key = "SALE#-" + record.getRecordId();
        String value = record.toKafkaValue();

        ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(topic, key, value);

        this.producer.send(kafkaRecord, ((recordMetadata, exception) -> {
            if (exception != null) {
                log("runProducer: ERROR sending " + key + ": " + exception.getMessage());
            } else {
                log(String.format("runProducer: Metadata sent back by kafka for Sales datum <%s>: %d", key, recordMetadata.offset()));
            }
        }));
    }

    public void shutdown() {
        if (!this.running) {
            log("runProducer: Producer not started, can't shutdown");
            return;
        }
        log("runProducer: Stopping producer");
        this.running = false;
    }

    private void log(String message) {
        logger.log(message);
    }

    public String getTopicName() {
        return topic;
    }


}