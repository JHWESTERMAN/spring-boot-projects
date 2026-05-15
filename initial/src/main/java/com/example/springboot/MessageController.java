package com.example.springboot.controller;

import com.example.springboot.model.Message;
import com.example.springboot.repository.MessageRepository;
import com.example.springboot.exception.MessageNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

  private final MessageRepository repository;
  private static final Logger log = LoggerFactory.getLogger(MessageController.class);

  public MessageController(MessageRepository repository) {
    this.repository = repository;
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

  // CREATE
  @PostMapping
  public Message create(@RequestBody Message message) {
    log.info("Creating message: {}", message.getText());
    return repository.save(message);
  }

  // UPDATE
  @PutMapping("/{id}")
  public Message update(@PathVariable Long id, @RequestBody Message updated) {

    Message msg = repository.findById(id)
        .orElseThrow(() -> new MessageNotFoundException(id));

    msg.setText(updated.getText());
    return repository.save(msg);
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
