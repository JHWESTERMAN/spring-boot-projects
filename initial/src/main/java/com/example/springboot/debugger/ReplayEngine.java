package com.example.springboot.debugger;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ReplayEngine {

    private static final Logger log = LoggerFactory.getLogger(ReplayEngine.class);

    @Value("${spring.activemq.broker-url}")
    private String brokerUrl;

    @Value("${spring.activemq.user:admin}")
    private String brokerUser;

    @Value("${spring.activemq.password:admin}")
    private String brokerPassword;

    private final MessageStore store;

    public ReplayEngine(MessageStore store) {
        this.store = store;
    }

    // ----------------------------
    // Replay één bericht op id
    // ----------------------------
    public void replayById(String id) throws JMSException {
        MessageStore.CapturedMessage captured = store.getById(id);
        if (captured == null) {
            throw new IllegalArgumentException("Bericht niet gevonden: " + id);
        }
        replay(captured);
    }

    // ----------------------------
    // Replay hele correlationId keten
    // ----------------------------
    public void replayByCorrelationId(String correlationId) throws JMSException {
        List<MessageStore.CapturedMessage> messages = store.getByCorrelationId(correlationId);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Geen berichten gevonden voor correlationId: " + correlationId);
        }
        log.info("Replaying {} berichten voor correlationId: {}", messages.size(), correlationId);
        for (MessageStore.CapturedMessage message : messages) {
            replay(message);
        }
    }

    // Replay alle berichten in een tijdsbereik
    public void replayByTimeRange(Instant from, Instant to) throws JMSException {
        List<MessageStore.CapturedMessage> messages = store.getByTimeRange(from, to);

        if (messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Geen berichten gevonden tussen " + from + " en " + to);
        }

        log.info("Replaying {} berichten tussen {} en {}",
                messages.size(), from, to);

        for (MessageStore.CapturedMessage message : messages) {
            replay(message);
            log.info("Gereplayd: id={}, queue={}, timestamp={}",
                    message.id, message.queue, message.timestamp);
        }

        log.info("Replay voltooid — {} berichten verstuurd", messages.size());
    }

    // ----------------------------
    // Daadwerkelijke replay
    // ----------------------------
    private void replay(MessageStore.CapturedMessage captured) throws JMSException {
        log.info("Replaying bericht: id={}, queue={}", captured.id, captured.queue);

        ConnectionFactory factory = new ActiveMQConnectionFactory(
                brokerUser, brokerPassword, brokerUrl
        );

        try (Connection connection = factory.createConnection()) {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(captured.queue);
            MessageProducer producer = session.createProducer(queue);

            TextMessage message = session.createTextMessage(captured.body);
            message.setStringProperty("X-MQDebugger-Replay", "true");
            message.setStringProperty("X-MQDebugger-OriginalId", captured.id);

            producer.send(message);
            log.info("Bericht gereplayd naar queue: {}", captured.queue);
        }
        // Sla ook het gereplayde bericht op in de store
        Map<String, String> replayHeaders = new HashMap<>(captured.headers);
        replayHeaders.put("X-MQDebugger-Replay", "true");
        replayHeaders.put("X-MQDebugger-OriginalId", captured.id);

        store.store(new MessageStore.CapturedMessage(
                UUID.randomUUID().toString(),
                captured.queue,
                captured.body,
                captured.correlationId,
                replayHeaders,
                Instant.now(),
                "SEND"
        ));
    }
}