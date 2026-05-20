package com.example.springboot;

import com.example.springboot.debugger.MessageInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import com.example.springboot.debugger.MessageInterceptor;


@Service
public class MessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);
    private final MessageRepository repository;
    private final MessageInterceptor interceptor;

    public MessageConsumer(MessageRepository repository, MessageInterceptor interceptor) {
        this.repository = repository;
        this.interceptor = interceptor;
    }

    @JmsListener(destination = "messages.queue")
    public void ontvang(MessageEvent event, jakarta.jms.Message rawMessage) {
        interceptor.interceptReceive(rawMessage, "messages.queue");
        log.info("Ontvangen uit queue: type={}, id={}, text={}",
                event.getType(), event.getId(), event.getText());

        if ("CREATE".equals(event.getType())) {
            Message message = new Message(event.getText());
            repository.save(message);
            log.info("Aangemaakt in database: {}", event.getText());

        } else if ("UPDATE".equals(event.getType())) {
            Message msg = repository.findById(event.getId())
                    .orElseThrow(() -> new MessageNotFoundException(event.getId()));
            msg.setText(event.getText());
            repository.save(msg);
            log.info("Bijgewerkt in database: id={}, text={}", event.getId(), event.getText());
        }
    }
}