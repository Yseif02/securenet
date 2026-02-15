package edu.yu.mdm;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

public class KafkaTopicManager {
    private final String bootstrapServers;

    public KafkaTopicManager(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public void resetTopic(String topicName) {
        try (AdminClient admin = createAdminClient()) {
            try {
                admin.deleteTopics(Collections.singleton(topicName)).all().get();
                System.out.println("Deleted existing topic: " + topicName);

                Thread.sleep(2000);
            } catch (Exception e) {
                System.out.println("Topic doesn't exist yet: " + topicName);
            }

            NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);

            admin.createTopics(Collections.singleton(newTopic)).all().get();
            System.out.println("Created topic: " + topicName);

            Thread.sleep(2000);
        } catch (Exception e) {
            System.err.println("Error resetting topic: " + e.getMessage());
            throw new RuntimeException("Failed to reset topic", e);
        }
    }


    private AdminClient createAdminClient() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return AdminClient.create(properties);
    }
}
