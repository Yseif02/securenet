package edu.yu.mdm;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.protocol.types.Field;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class RecordConsumer implements Runnable{
    private static final String GROUP_ID = "no-auto-commit-application";
    private static final int BATCH_SIZE = 25;

    private volatile boolean running;
    private final KafkaConsumer<String, String> consumer;
    private final AssignmentLogger logger;
    private final LinkedBlockingQueue<BatchInfo> batchQueue;
    private final LinkedBlockingQueue<Map<TopicPartition, OffsetAndMetadata>> commitQueue = new LinkedBlockingQueue<>();
    private volatile boolean entryLogged = false;


    public RecordConsumer(String bootstrapServers, LinkedBlockingQueue<BatchInfo> batchQueue, String topic) {
        Properties properties = new Properties();

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, BATCH_SIZE);

        this.consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(
                Collections.singletonList(topic),
                new ConsumerRebalanceListener() {

                    @Override
                    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {

                        Map<TopicPartition, OffsetAndMetadata> committed =
                                consumer.committed(new HashSet<>(partitions));

                        // REQUIRED LOG — must be exactly this format
                        if (!entryLogged) {
                            log(String.format(
                                    "runConsumer At entry, the committed offset is %s",
                                    committed
                            ));
                            entryLogged = true;
                        }

                        for (TopicPartition p : partitions) {
                            OffsetAndMetadata meta = committed.get(p);

                            if (meta != null) {
                                consumer.seek(p, meta.offset());
                            } else {
                                consumer.seekToBeginning(Collections.singleton(p));
                            }
                        }
                    }

                    @Override
                    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}
                }
        );
        this.batchQueue = batchQueue;
        this.running = true;
        this.logger = AssignmentLogger.getInstance();
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        log("Starting runConsumer");

        //logCommitedOffset();

        try {
            while (this.running && !Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = this.consumer.poll(Duration.ofSeconds(5));
                if (!records.isEmpty()) {
                    ArrayList<SalesRecord> salesRecords = new ArrayList<>();
                    for (ConsumerRecord<String, String> record : records) {
                        SalesRecord salesRecord = SalesRecord.fromKafkaValue(record.value());
                        salesRecords.add(salesRecord);
                    }

                    Map<TopicPartition, OffsetAndMetadata> offsetToCommit = buildOffsetMap(records);
                    BatchInfo batchInfo = new BatchInfo(salesRecords, offsetToCommit);
                    this.batchQueue.put(batchInfo);
                    log(String.format("runConsumer Queued batch of %d records for processing", salesRecords.size()));
                }

                Map<TopicPartition, OffsetAndMetadata> offsets = this.commitQueue.poll();
                if (offsets != null && !offsets.isEmpty()) {
                    try {
                        this.consumer.commitSync(offsets);
                        log(String.format("runConsumer Batch completed now committing the offsets %s", offsets));
                    } catch (Exception e) {
                        log("runConsumer Error committing offsets: " + e.getMessage());
                    }
                }

            }
        } catch (InterruptedException e) {
            log("runConsumer Consumer interrupted");
        } finally {
            Map<TopicPartition, OffsetAndMetadata> offsets;
            while ((offsets = this.commitQueue.poll()) != null) {
                try {
                    this.consumer.commitSync(offsets);
                    log(String.format("runConsumer Final commit of offsets %s", offsets));
                } catch (Exception e) {
                    log("runConsumer Error in final commit: " + e.getMessage());
                }
            }
            if (this.consumer != null) {
                try {
                    Thread.interrupted();
                    this.consumer.close();
                    log("runConsumer runConsumer closed correctly");
                } catch (Exception e) {
                    log("runConsumer Error closing consumer: " + e.getMessage());
                }
            }
        }
    }

    private Map<TopicPartition, OffsetAndMetadata> buildOffsetMap(ConsumerRecords<String, String> records) {
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();

        for (TopicPartition partition : records.partitions()) {
            List<ConsumerRecord<String, String>> partitionRecords = records.records(partition);
            if (!partitionRecords.isEmpty()) {
                long lastOffset = partitionRecords.get(partitionRecords.size() - 1).offset();
                offsets.put(partition, new OffsetAndMetadata(lastOffset + 1));
            }
        }
        return offsets;
    }

    private void logCommitedOffset() {
        try {
            Set<TopicPartition> assignment;

            do {
                this.consumer.poll(Duration.ofMillis(100));
                assignment = this.consumer.assignment();
            } while (assignment.isEmpty());

            Map<TopicPartition, OffsetAndMetadata> committed =
                    this.consumer.committed(assignment);

            log(String.format(
                    "runConsumer At entry, the committed offset is %s",
                    committed
            ));
        } catch (Exception e) {
            log("runConsumer Error getting committed offset: " + e.getMessage());
        }
    }

    public void shutdown() {
        log("Stopping runConsumer");
        this.running = false;
    }

    public synchronized void commitBatch(Map<TopicPartition, OffsetAndMetadata> offsets) {
        try {
            this.commitQueue.put(offsets);
        } catch (InterruptedException e) {
            log("runConsumer Error queuing commit: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void log(String message) {
        this.logger.log(message);
    }
}
