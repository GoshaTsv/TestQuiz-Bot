package org.example.classes.appLinking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public class Test {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @SerializedName("quizName") //added serialized name to match the generated json's field
    private String testName;
    private ArrayList<Question> questions;

    public ArrayList<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(ArrayList<Question> questions) {
        this.questions = questions;
    }

    public String toString() {
        return "Quiz{" +
                "testName='" + testName + '\'' +
                ", questions=" + Arrays.deepToString(questions.toArray()) +
                '}';
    }

    public Test(String testName, ArrayList<Question> questions) {
        this.testName = testName;
        this.questions = questions;
    }

    public void setTestName(String testName) { this.testName = testName; }
    public String getTestName() { return testName; }

    public static String checkForTest(String json) {
        if (json == null || json.isBlank()) {
            return "Ваш тест пустой!";
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                return "Некорректный формат теста!";
            }

            JsonNode quizName = root.get("quizName");
            if (quizName == null || !quizName.isTextual()) {
                return "Неправильный заголовок теста!";
            }

            JsonNode questions = root.get("questions");
            if (questions == null || !questions.isArray() || questions.isEmpty() || questions.size() > 30) {
                return "Неправильная структура вопросов!";
            }

            for (JsonNode questionNode : questions) {
                if (!questionNode.isObject()) {
                    return "Неправильная структура вопросов!";
                }
                JsonNode questionType = questionNode.get("type");
                JsonNode questionText = questionNode.get("question");
                if (questionText == null || !questionText.isTextual() || questionText.asText().length() > 3000) {
                    return "Неправильная структура вопросов!";
                }
                if (!(Objects.equals(questionType.asText(), "var") || Objects.equals(questionType.asText(), "ans") || Objects.equals(questionType.asText(), "srv"))) {
                    return "Неправильно заданный вид вопроса!";
                }
                JsonNode answers = questionNode.get("answers");
                if (answers == null || !answers.isObject() || answers.isEmpty() || answers.size() > 8) {
                    return "Неправильная структура ответов!";
                }

                var fields = answers.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    String answerKey = field.getKey();
                    JsonNode answerValue = field.getValue();

                    if (answerKey == null || answerKey.length() > 3000)
                        return "Неправильная структура вариантов ответа в одном из вопросов! 2";

                    if (!answerValue.isBoolean())
                        return "Неправильная структура ответов!";
                }
            }
        } catch (Exception e) {
            return "Некорректный формат теста!";
        }

        System.out.println("Starting checking questions 2");

        Test test = gson.fromJson(json, Test.class);

        ArrayList<Question> questions = test.questions;

        for (Question question : questions) {
            HashMap<String, Boolean> map = question.getAnswers();
            if ((!map.containsValue(Boolean.TRUE) && map.size()==1)) {
                return "Неправильная структура вопросов!";
            }
        }
        return "";
    }
}
