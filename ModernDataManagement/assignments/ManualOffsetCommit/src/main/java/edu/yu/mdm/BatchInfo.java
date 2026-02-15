package edu.yu.mdm;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.ArrayList;
import java.util.Map;

public record BatchInfo(ArrayList<SalesRecord> records, Map<TopicPartition, OffsetAndMetadata> offsetsToCommit) {
}
