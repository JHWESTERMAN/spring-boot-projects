package com.example.springboot;

import jakarta.persistence.*;

@Entity
@Table(name = "messages")
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @SuppressWarnings("unused")
  private Long id;

  private String text;

  public Message() {}

  public Message(String text) {
    this.text = text;
  }
  @SuppressWarnings("unused")
  public Long getId() {
    return id;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }
}
