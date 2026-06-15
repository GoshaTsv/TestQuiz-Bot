package org.example.spring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class Message {
    @NotBlank
    @Size(max = 15 * 1024 * 1024)
    private String content;

    @Pattern(regexp = "^(|exportJSON|changeTest)$")
    private String request;

    private String prev_content;

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPrev_content() {
        return prev_content;
    }

    public void setPrev_content(String prev_content) {
        this.prev_content = prev_content;
    }

    @Override
    public String toString() {
        return "Message{" +
                "content='" + content + '\'' +
                ", prev_content='" + prev_content + '\'' +
                ", request='" + request + '\'' +
                '}';
    }
}
