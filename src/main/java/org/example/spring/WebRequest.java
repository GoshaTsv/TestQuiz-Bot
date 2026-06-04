package org.example.spring;

import org.example.classes.appLinking.Test;

public class WebRequest {
    private Test test;
    private ButtonDTO button;
    private String firstContent;

    public WebRequest() {}

    public WebRequest(Test test, String firstContent, ButtonDTO button) {
        this.test = test;
        this.button = button;
        this.firstContent = firstContent;
    }

    public Test getTest() {
        return test;
    }

    public void setTest(Test test) {
        this.test = test;
    }

    public ButtonDTO getButton() {
        return button;
    }

    public void setButton(ButtonDTO button) {
        this.button = button;
    }

    public String getFirstContent() {
        return firstContent;
    }

    public void setFirstContent(String firstContent) {
        this.firstContent = firstContent;
    }
}
