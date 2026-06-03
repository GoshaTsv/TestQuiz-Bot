package org.example.spring;

import org.example.classes.appLinking.Test;

public class RequestToBot {
    private Test test;
    private long chatId;
    private String request;

    public RequestToBot(Test test, long chatId, String request) {
        this.test = test;
        this.chatId = chatId;
        this.request = request;
    }

    public Test getTest() {
        return test;
    }

    public void setTest(Test test) {
        this.test = test;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }
}
