package org.example.spring;

public class Message {
    private String content;
    private String prev_content;
    private String userId;
    private String request;

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
                ", userId='" + userId + '\'' +
                ", request='" + request + '\'' +
                '}';
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
