package org.example.classes.appLinking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Test {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @SerializedName("quizName") //added serialized name to match the generated json's field
    public String testName;
    public ArrayList<Question> questions;

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

                JsonNode questionText = questionNode.get("question");
                if (questionText == null || !questionText.isTextual() || questionText.size() > 3000) {
                    return "Неправильная структура вопросов!";
                }

                JsonNode answers = questionNode.get("answers");
                if (answers == null || !answers.isObject() || answers.isEmpty() || answers.size() > 8) {
                    return "Неправильная структура ответов!";
                }

                var fields = answers.fields();
                for(JsonNode answer : answers){
                    if (answer.get(0) == null || answer.get(0).size() > 3000){
                        return "Неправильная структура вариантов ответа в одном из вопросов!";
                    }
                }
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (!field.getValue().isBoolean()) {
                        return "Неправильная структура ответов!";
                    }
                }
            }
        } catch (Exception e) {
            return "Некорректный формат теста!";
        }

        System.out.println("Starting checking questions 2");

        Test test = gson.fromJson(json, Test.class);

        ArrayList<Question> questions = test.questions;

        for (Question question : questions) {
            HashMap<String, Boolean> map = question.answers;
            if ((!map.containsValue(Boolean.TRUE) && map.size()==1)) {
                return "Неправильная структура вопросов!";
            }
        }
        return "";
    }
}
