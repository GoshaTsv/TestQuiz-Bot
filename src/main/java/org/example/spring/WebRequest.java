package org.example.spring;

import org.example.classes.appLinking.Test;

public class WebRequest {
    private Test test;
    private ButtonDTO button;

    public WebRequest() {
    }

    public WebRequest(Test test, ButtonDTO button) {
        this.test = test;
        this.button = button;
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
}
