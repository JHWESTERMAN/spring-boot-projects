package com.example.springboot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageRepository repository;
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);
    private final MessageProducer producer;

    public MessageController(MessageRepository repository, MessageProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    // GET all (newest first)
    @GetMapping
    public List<Message> all() {

        return repository.findAllByOrderByIdDesc();
    }

    // GET by id
    @GetMapping("/{id}")
    public Message one(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new MessageNotFoundException(id));
    }

    // SEARCH
    @GetMapping("/search")
    public List<Message> search(@RequestParam String q) {

        return repository.findByTextContainingIgnoreCase(q);
    }

    // CREATE — via queue
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String create(@RequestBody Message message) {
        log.info("Ontvangen create request: {}", message.getText());
        producer.sendCreate(message.getText());
        return "Bericht in verwerking";
    }

    // UPDATE — via queue
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public String update(@PathVariable Long id, @RequestBody Message updated) {
        if (!repository.existsById(id)) {
            throw new MessageNotFoundException(id);
        }
        producer.sendUpdate(id, updated.getText());
        return "Update in verwerking";
    }

    // DELETE
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new MessageNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
