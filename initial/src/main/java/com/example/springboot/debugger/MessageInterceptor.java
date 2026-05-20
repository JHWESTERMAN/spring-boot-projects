package com.example.springboot.debugger;

import com.example.springboot.MessageEvent;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class MessageInterceptor implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageInterceptor.class);

    @Autowired
    private MessageStore store;

    // ----------------------------
    // Onderschep binnenkomend bericht
    // ----------------------------
    @Override
    public void onMessage(Message message) {
        try {
            store.store(capture(message, "RECEIVE"));
        } catch (Exception e) {
            log.error("Fout bij onderscheppen bericht", e);
        }
    }

    // ----------------------------
    // Onderschep uitgaand bericht
    // ----------------------------
    public void intercept(Message message, String queueName) {
        try {
            store.store(capture(message, queueName, "SEND"));
        } catch (Exception e) {
            log.error("Fout bij onderscheppen uitgaand bericht", e);
        }
    }

    // ----------------------------
    // Capture zonder queuenaam
    // ----------------------------
    private MessageStore.CapturedMessage capture(Message message, String direction)
            throws JMSException {
        String queue = "unknown";
        if (message.getJMSDestination() != null) {
            queue = message.getJMSDestination().toString();
        }
        return capture(message, queue, direction);
    }

    // ----------------------------
    // Capture met queuenaam
    // ----------------------------
    private MessageStore.CapturedMessage capture(Message message,
                                                 String queue, String direction) throws JMSException {

        String id = UUID.randomUUID().toString();
        String correlationId = message.getJMSCorrelationID();
        String body = extractBody(message);
        Map<String, String> headers = extractHeaders(message);

        log.debug("Onderschept: direction={}, queue={}, correlationId={}",
                direction, queue, correlationId);

        return new MessageStore.CapturedMessage(
                id, queue, body, correlationId, headers, Instant.now(), direction
        );
    }

    // ----------------------------
    // Body extraheren
    // ----------------------------
    private String extractBody(Message message) throws JMSException {
        if (message instanceof TextMessage) {
            return ((TextMessage) message).getText();
        } else if (message instanceof ObjectMessage objectMessage) {
            Object obj = objectMessage.getObject();
            if (obj instanceof MessageEvent event) {
                return "type=" + event.getType() +
                        ", id=" + event.getId() +
                        ", text=" + event.getText();
            }
            return obj != null ? obj.toString() : "null";
        } else if (message instanceof BytesMessage bytesMessage) {
            byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(bytes);
            return new String(bytes);
        }
        return message.toString();
    }

    // ----------------------------
    // Headers extraheren
    // ----------------------------
    private Map<String, String> extractHeaders(Message message) throws JMSException {
        Map<String, String> headers = new HashMap<>();
        headers.put("JMSMessageID", message.getJMSMessageID());
        headers.put("JMSTimestamp", String.valueOf(message.getJMSTimestamp()));
        headers.put("JMSDeliveryMode", String.valueOf(message.getJMSDeliveryMode()));
        headers.put("JMSPriority", String.valueOf(message.getJMSPriority()));

        Enumeration<?> propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements()) {
            String name = (String) propertyNames.nextElement();
            headers.put(name, message.getStringProperty(name));
        }
        return headers;
    }
}