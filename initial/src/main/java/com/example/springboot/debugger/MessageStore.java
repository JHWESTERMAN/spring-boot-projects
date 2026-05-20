package com.example.springboot.debugger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

@Component
public class MessageStore {

    private static final Logger log = LoggerFactory.getLogger(MessageStore.class);
    private static final int MAX_SIZE = 5000;

    private final ArrayBlockingQueue<CapturedMessage> messages =
            new ArrayBlockingQueue<>(MAX_SIZE);

    // ----------------------------
    // Inner class — captured message
    // ----------------------------
    public static class CapturedMessage {
        public final String id;
        public final String queue;
        public final String body;
        public final String correlationId;
        public final Map<String, String> headers;
        public final String timestamp;
        public final String direction;

        public CapturedMessage(String id, String queue, String body,
                               String correlationId, Map<String, String> headers,
                               Instant timestamp, String direction) {
            this.id = id;
            this.queue = queue;
            this.body = body;
            this.correlationId = correlationId;
            this.headers = headers;
            this.timestamp = timestamp.toString();
            this.direction = direction;
        }
    }

    // ----------------------------
    // Opslaan
    // ----------------------------
    public void store(CapturedMessage message) {
        if (!messages.offer(message)) {
            messages.poll();           // verwijder oudste
            messages.offer(message);   // voeg nieuwe toe
            log.debug("Ring buffer vol — oudste bericht verwijderd");
        }
        log.debug("Bericht opgeslagen: queue={}, id={}", message.queue, message.id);
    }

    // ----------------------------
    // Ophalen — alle berichten
    // ----------------------------
    public List<CapturedMessage> getAll() {
        return new ArrayList<>(messages);
    }

    // ----------------------------
    // Ophalen — filter op queue
    // ----------------------------
    public List<CapturedMessage> getByQueue(String queue) {
        return messages.stream()
                .filter(m -> queue.equals(m.queue))
                .toList();
    }

    // ----------------------------
    // Ophalen — filter op correlationId
    // ----------------------------
    public List<CapturedMessage> getByCorrelationId(String correlationId) {
        return messages.stream()
                .filter(m -> correlationId.equals(m.correlationId))
                .toList();
    }

    // ----------------------------
    // Ophalen — filter op tijdsbereik
    // ----------------------------
    public List<CapturedMessage> getByTimeRange(Instant from, Instant to) {
        return messages.stream()
                .filter(m -> {
                    Instant ts = Instant.parse(m.timestamp);
                    return ts.isAfter(from) && ts.isBefore(to);
                })
                .toList();
    }

    // ----------------------------
    // Ophalen — één bericht op id
    // ----------------------------
    public CapturedMessage getById(String id) {
        return messages.stream()
                .filter(m -> id.equals(m.id))
                .findFirst()
                .orElse(null);
    }

    // ----------------------------
    // Wissen
    // ----------------------------
    public void clear() {
        messages.clear();
        log.info("MessageStore geleegd");
    }

    // ----------------------------
    // Statistieken
    // ----------------------------
    public int size() {
        return messages.size();
    }
}