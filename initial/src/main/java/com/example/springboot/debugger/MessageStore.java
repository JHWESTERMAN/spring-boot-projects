package com.example.springboot.debugger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class MessageStore {

    private static final Logger log = LoggerFactory.getLogger(MessageStore.class);
    private static final int MAX_SIZE = 5000;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<CapturedMessage> messages = new ArrayList<>();

    // ----------------------------
    // Inner class — captured message
    // ----------------------------
    public static class CapturedMessage {
        public final String id;
        public final String queue;
        public final String body;
        public final String correlationId;
        public final Map<String, String> headers;
        public final Instant timestamp;
        public final String direction; // SEND of RECEIVE

        public CapturedMessage(String id, String queue, String body,
                               String correlationId, Map<String, String> headers,
                               Instant timestamp, String direction) {
            this.id = id;
            this.queue = queue;
            this.body = body;
            this.correlationId = correlationId;
            this.headers = headers;
            this.timestamp = timestamp;
            this.direction = direction;
        }
    }

    // ----------------------------
    // Opslaan
    // ----------------------------
    public void store(CapturedMessage message) {
        lock.writeLock().lock();
        try {
            if (messages.size() >= MAX_SIZE) {
                messages.remove(0); // evict oudste
                log.debug("Ring buffer vol — oudste bericht verwijderd");
            }
            messages.add(message);
            log.debug("Bericht opgeslagen: queue={}, id={}", message.queue, message.id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ----------------------------
    // Ophalen — alle berichten
    // ----------------------------
    public List<CapturedMessage> getAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(messages);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ----------------------------
    // Ophalen — filter op queue
    // ----------------------------
    public List<CapturedMessage> getByQueue(String queue) {
        lock.readLock().lock();
        try {
            return messages.stream()
                    .filter(m -> queue.equals(m.queue))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ----------------------------
    // Ophalen — filter op correlationId
    // ----------------------------
    public List<CapturedMessage> getByCorrelationId(String correlationId) {
        lock.readLock().lock();
        try {
            return messages.stream()
                    .filter(m -> correlationId.equals(m.correlationId))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ----------------------------
    // Ophalen — één bericht op id
    // ----------------------------
    public CapturedMessage getById(String id) {
        lock.readLock().lock();
        try {
            return messages.stream()
                    .filter(m -> id.equals(m.id))
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    // Ophalen — filter op tijdsbereik
    public List<CapturedMessage> getByTimeRange(Instant from, Instant to) {
        lock.readLock().lock();
        try {
            return messages.stream()
                    .filter(m -> m.timestamp.isAfter(from) && m.timestamp.isBefore(to))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ----------------------------
    // Wissen
    // ----------------------------
    public void clear() {
        lock.writeLock().lock();
        try {
            messages.clear();
            log.info("MessageStore geleegd");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ----------------------------
    // Statistieken
    // ----------------------------
    public int size() {
        lock.readLock().lock();
        try {
            return messages.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}