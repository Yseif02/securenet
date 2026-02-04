package edu.yu.mdm;


import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class RecordProcessor implements Runnable{
    private final LinkedBlockingQueue<BatchInfo> batchQueue;
    private final RecordConsumer consumer;
    private volatile boolean running;
    private final AssignmentLogger logger;

    public RecordProcessor(LinkedBlockingQueue<BatchInfo> batchQueue,
                                     RecordConsumer consumer) {
        this.batchQueue = batchQueue;
        this.consumer = consumer;
        this.running = true;
        this.logger = AssignmentLogger.getInstance();
    }

    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        log(("Starting processRecords"));
        try {
            while (this.running && ! Thread.currentThread().isInterrupted()) {
                BatchInfo batchInfo = batchQueue.poll(100, TimeUnit.MILLISECONDS);
                if (batchInfo == null) continue;
                for (SalesRecord record : batchInfo.records()) {
                    if (Thread.currentThread().isInterrupted()) {
                        log("processRecords Interrupted during batch processing");
                        throw new InterruptedException("Interrupted during processing");
                    }
                    processRecord(record);
                }
                this.consumer.commitBatch(batchInfo.offsetsToCommit());
            }
        } catch (InterruptedException e) {
            log("processRecords Processor interrupted");
        } finally {
            log("processRecords stopped");
        }
    }

    private void processRecord(SalesRecord record) throws InterruptedException {
        log(String.format(
                "processRecords Began processing datum (<SALE#-%d>, timestamp=%d) for %s with product %s for a total sale of %.1f",
                record.getRecordId(),
                record.getTimestamp(),
                record.getCustomerName(),
                record.getProductName(),
                record.getPrice()
        ));
        Thread.sleep(1000);
        log(String.format("processRecords Finished processing of sales datum <SALE#-%d>", record.getRecordId()));
    }

    public void shutdown() {
        log("Stopping processRecords");
        this.running = false;
    }

    private void log(String message) {
        this.logger.log(message);
    }

}
