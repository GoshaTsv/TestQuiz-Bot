package org.example.classes.appLinking;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class Question {
    private String question;
    private LinkedHashMap<String, Boolean> answers;
    private Image image;
    private String type;

    public Question(HashMap<String, Boolean> answers, String question) {
        this.answers = new LinkedHashMap<>(answers);
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public LinkedHashMap<String, Boolean> getAnswers() {
        return answers;
    }

    public void setAnswers(LinkedHashMap<String, Boolean> answers) {
        this.answers = answers;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "Question{" +
                "question='" + question + '\'' +
                ", answers=" + answers +
                ", image=" + image +
                ", type='" + type + '\'' +
                '}';
    }
}
