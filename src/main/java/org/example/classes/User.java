package org.example.classes;

import org.example.classes.appLinking.Question;

import java.util.LinkedHashMap;
import java.util.Map;

public class User {
    private String state;
    private long chatId;
    private String currentClassName;
    private int classCount; // new val
    private int testsCount;
    private int quizState;
    private Quiz currentQuiz;
    private String correctAnswer;
    private String prevType;
    private int correctAnswers;
    private LinkedHashMap<String, Boolean> userAnswers;

    public LinkedHashMap<String, Boolean> getUserAnswers() {
        return userAnswers;
    }

    public void setUserAnswers(LinkedHashMap<String, Boolean> userAnswers) {
        this.userAnswers = userAnswers;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(int correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public String getPrevType() {
        return prevType;
    }

    public void setPrevType(String prevType) {
        this.prevType = prevType;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public int getQuizState() {
        return quizState;
    }

    public void setQuizState(int quizState) {
        this.quizState = quizState;
    }

    public Quiz getCurrentQuiz() {
        return currentQuiz;
    }

    public void setCurrentQuiz(Quiz currentQuiz) {
        this.currentQuiz = currentQuiz;
    }

    public User(long chatId, String state, int classCount, int testsCount, int quizState, Quiz currentQuiz) { // new constructor
        this.chatId = chatId;
        this.state = state;
        this.classCount = classCount;
        this.testsCount = testsCount;
        this.quizState = quizState;
        this.currentQuiz = currentQuiz;
        prevType = "";
        correctAnswers = 0;
        userAnswers = new LinkedHashMap<>();
    }

    public String getState() {
        return state;
    }

    public long getChatId() {
        return chatId;
    }

    public String getCurrentClassName() {
        return currentClassName;
    }

    public int getClassCount() {
        return classCount;
    }

    public int getTestsCount() {
        return testsCount;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public void setCurrentClassName(String currentClassName) {
        this.currentClassName = currentClassName;
    }

    public void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public void setTestsCount(int testsCount) {
        this.testsCount = testsCount;
    }

    @Override
    public String toString() {
        return "User{" +
                "state='" + state + '\'' +
                ", chatId=" + chatId +
                ", currentClassName='" + currentClassName + '\'' +
                ", classCount=" + classCount +
                ", testsCount=" + testsCount +
                ", quizState=" + quizState +
                ", currentQuiz=" + currentQuiz +
                ", correctAnswer='" + correctAnswer + '\'' +
                ", prevType='" + prevType + '\'' +
                ", correctAnswers=" + correctAnswers +
                ", userAnswers=" + userAnswers +
                '}';
    }

    public static String getCorrectAnswerForQuestion(Question question) {
        for (Map.Entry<String, Boolean> entry : question.answers.entrySet()) {
            if (entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }
}
