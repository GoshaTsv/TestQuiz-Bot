package org.example.classes.appLinking;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class Question {
    public String question;
    public LinkedHashMap<String, Boolean> answers;

    public Question(HashMap<String, Boolean> answers, String question) {
        this.answers = (LinkedHashMap<String, Boolean>) answers;
        this.question = question;
    }

    @Override
    public String toString() {
        return "Question{" +
                "question='" + question + '\'' +
                ", answers=" + answers +
                '}';
    }
}
