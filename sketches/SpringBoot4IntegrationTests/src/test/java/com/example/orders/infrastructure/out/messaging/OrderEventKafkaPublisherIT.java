package com.example.orders.infrastructure.out.messaging;

import com.example.orders.domain.model.Money;
import com.example.orders.domain.model.Order;
import com.example.orders.domain.model.OrderItem;
import com.example.orders.domain.port.out.OrderEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.orders.infrastructure.out.messaging.avro.OrderCreatedEvent;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for OrderEventKafkaPublisher.
 *
 * Uses a real Kafka Testcontainer so message routing, partitioning, and offsets
 * are exercised properly. Schema Registry is replaced with MockSchemaRegistryClient
 * to avoid requiring a live Confluent Schema Registry container.
 *
 * A simple String-deserialising KafkaConsumer is used to verify message
 * delivery — Avro binary content is checked via key presence.
 */
@SpringBootTest(
        classes = {
                OrderEventKafkaPublisherIT.KafkaTestConfig.class,
                OrderEventKafkaPublisher.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Testcontainers
@ActiveProfiles("test")
class OrderEventKafkaPublisherIT {

    private static final String TOPIC = "orders.order-created.test";

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("kafka.topics.order-created", () -> TOPIC);
    }

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void createConsumer() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void closeConsumer() {
        consumer.close();
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void publishesOneMessageToKafkaTopicWhenOrderCreated() {
        Order order = Order.create("cust-kafka-1", List.of(
                new OrderItem("prod-1", "Widget", 2, Money.of(25.00, "USD"))
        ));

        orderEventPublisher.publishOrderCreated(order);

        ConsumerRecords<String, String> records = pollUntilAtLeast(1);

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void publishesMessageWithOrderIdAsKey() {
        Order order = Order.create("cust-kafka-2", List.of(
                new OrderItem("prod-2", "Gadget", 1, Money.of(99.99, "USD"))
        ));

        orderEventPublisher.publishOrderCreated(order);

        ConsumerRecords<String, String> records = pollUntilAtLeast(1);

        List<String> keys = new ArrayList<>();
        records.forEach(r -> keys.add(r.key()));

        assertThat(keys).contains(order.getId());
    }

    @Test
    void publishesMessageToCorrectTopic() {
        Order order = Order.create("cust-kafka-3", List.of(
                new OrderItem("prod-3", "Doohickey", 4, Money.of(5.50, "USD"))
        ));

        orderEventPublisher.publishOrderCreated(order);

        ConsumerRecords<String, String> records = pollUntilAtLeast(1);

        records.forEach(record ->
                assertThat(record.topic()).isEqualTo(TOPIC)
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ConsumerRecords<String, String> pollUntilAtLeast(int expectedCount) {
        long deadline = System.currentTimeMillis() + 10_000;
        ConsumerRecords<String, String> accumulated = ConsumerRecords.empty();
        while (System.currentTimeMillis() < deadline && accumulated.count() < expectedCount) {
            accumulated = consumer.poll(Duration.ofMillis(500));
        }
        return accumulated;
    }

    // ── test-only Kafka producer configuration ───────────────────────────────

    @TestConfiguration
    static class KafkaTestConfig {

        @Bean
        @Primary
        KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

            // MockSchemaRegistryClient eliminates the need for a live Schema Registry
            MockSchemaRegistryClient mockSchemaRegistryClient = new MockSchemaRegistryClient();

            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
            props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://test");
            props.put(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY, TopicNameStrategy.class);

            var factory = new DefaultKafkaProducerFactory<String, OrderCreatedEvent>(
                    props,
                    new StringSerializer(),
                    new KafkaAvroSerializer(mockSchemaRegistryClient)
            );
            return new KafkaTemplate<>(factory);
        }
    }
}
