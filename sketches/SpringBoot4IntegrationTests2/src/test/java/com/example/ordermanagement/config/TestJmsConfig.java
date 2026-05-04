package com.example.ordermanagement.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Replaces IBM MQ with an in-process Artemis broker for integration tests.
 * No Docker, no external process — pure in-JVM JMS.
 *
 * Activated only in the "test" Spring profile. The production JmsConfig
 * is annotated @Profile("!test") so there is no conflict.
 */
@TestConfiguration
@Profile("test")
public class TestJmsConfig {

    @Bean(destroyMethod = "stop")
    public ActiveMQServer embeddedArtemisServer() throws Exception {
        var config = new ConfigurationImpl();
        config.setSecurityEnabled(false);
        config.setPersistenceEnabled(false);
        config.addAcceptorConfiguration("in-vm", "vm://0");

        ActiveMQServer server = ActiveMQServers.newActiveMQServer(config);
        server.start();
        return server;
    }

    /** Primary ConnectionFactory bean — overrides any IBM MQ factory. */
    @Bean
    @Primary
    public ConnectionFactory testConnectionFactory(ActiveMQServer embeddedArtemisServer) {
        return new ActiveMQConnectionFactory("vm://0");
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        var converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory testConnectionFactory,
            MessageConverter jacksonJmsMessageConverter) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(testConnectionFactory);
        factory.setMessageConverter(jacksonJmsMessageConverter);
        factory.setSessionTransacted(true);
        return factory;
    }
}
