package com.example.springboot;

import com.example.springboot.debugger.MessageInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageProducer {

    private static final Logger log = LoggerFactory.getLogger(MessageProducer.class);
    private static final String QUEUE = "messages.queue";

    private final JmsTemplate jmsTemplate;
    private final MessageInterceptor interceptor;

    public MessageProducer(JmsTemplate jmsTemplate, MessageInterceptor interceptor) {
        this.jmsTemplate = jmsTemplate;
        this.interceptor = interceptor;
    }

    public void sendCreate(String text) {
        MessageEvent event = new MessageEvent("CREATE", null, text);
        log.info("Sturen CREATE naar queue: {}", text);
        jmsTemplate.convertAndSend(QUEUE, event, message -> {
            interceptor.intercept(message, QUEUE);
            return message;
        });
    }

    public void sendUpdate(Long id, String text) {
        MessageEvent event = new MessageEvent("UPDATE", id, text);
        log.info("Sturen UPDATE naar queue: id={}, text={}", id, text);
        jmsTemplate.convertAndSend(QUEUE, event, message -> {
            interceptor.intercept(message, QUEUE);
            return message;
        });
    }
}