package com.example.springboot;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

  List<Message> findByTextContainingIgnoreCase(String text);

  List<Message> findAllByOrderByIdDesc();
}
