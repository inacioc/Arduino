package com.example.orders;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.TopicNameStrategy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.orders.infrastructure.out.messaging.avro.OrderCreatedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Launches the application locally with Testcontainers replacing external services.
 * Run via: mvn spring-boot:test-run  or right-click → Run in IDE.
 *
 * Useful for developer testing without a local Docker Compose stack.
 */
public class TestOrdersApplication {

    public static void main(String[] args) {
        SpringApplication.from(OrdersApplication::main)
                .with(LocalDevContainersConfig.class)
                .run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalDevContainersConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("orders_dev")
                    .withUsername("dev")
                    .withPassword("dev");
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafkaContainer() {
            return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));
        }

        @Bean
        KeycloakContainer keycloakContainer() {
            return new KeycloakContainer("quay.io/keycloak/keycloak:25.0.2")
                    .withRealmImportFile("keycloak/test-realm.json");
        }

        @Bean
        @Primary
        KafkaTemplate<String, OrderCreatedEvent> testKafkaTemplate(KafkaContainer kafka) {
            MockSchemaRegistryClient mockRegistry = new MockSchemaRegistryClient();
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://local");
            props.put(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY, TopicNameStrategy.class);

            var factory = new DefaultKafkaProducerFactory<String, OrderCreatedEvent>(
                    props,
                    new StringSerializer(),
                    new KafkaAvroSerializer(mockRegistry)
            );
            return new KafkaTemplate<>(factory);
        }
    }
}
