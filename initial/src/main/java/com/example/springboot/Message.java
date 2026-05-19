package com.example.springboot;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "messages")
public class Message {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @SuppressWarnings("unused")
  private Long id;

  @NotNull
  @Size(min = 1, max = 255)
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
