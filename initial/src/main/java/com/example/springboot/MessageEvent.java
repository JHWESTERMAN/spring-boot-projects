package com.example.springboot;

import java.io.Serializable;

public class MessageEvent implements Serializable {

    private String type;
    private Long id;
    private String text;

    public MessageEvent() {}

    public MessageEvent(String type, Long id, String text) {
        this.type = type;
        this.id = id;
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setText(String text) {
        this.text = text;
    }
}