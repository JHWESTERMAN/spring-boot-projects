package com.example.springboot.debugger;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageStoreTest {

    private final MessageStore store = new MessageStore();

    @Test
    void testOpslaan() {
        store.store(new MessageStore.CapturedMessage(
                "1", "messages.queue", "Hallo wereld",
                "corr-1", Map.of(), Instant.now(), "SEND"
        ));
        assertEquals(1, store.size());
    }

    @Test
    void testFilterOpQueue() {
        store.store(new MessageStore.CapturedMessage(
                "1", "messages.queue", "Bericht 1",
                "corr-1", Map.of(), Instant.now(), "SEND"
        ));
        store.store(new MessageStore.CapturedMessage(
                "2", "andere.queue", "Bericht 2",
                "corr-2", Map.of(), Instant.now(), "SEND"
        ));
        assertEquals(1, store.getByQueue("messages.queue").size());
    }

    @Test
    void testRingBuffer() {
        for (int i = 0; i < 5001; i++) {
            store.store(new MessageStore.CapturedMessage(
                    String.valueOf(i), "messages.queue", "Bericht " + i,
                    "corr-" + i, Map.of(), Instant.now(), "SEND"
            ));
        }
        assertEquals(5000, store.size());
    }
}