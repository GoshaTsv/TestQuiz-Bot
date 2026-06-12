package org.example.classes.appLinking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

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

    private static final java.util.regex.Pattern INVISIBLE_CHARS =
            java.util.regex.Pattern.compile(
                    "[\\p{Cc}\\p{Cf}\\p{Cs}\\u00A0\\u00AD\\u034F\\u061C\\u115F\\u1160" +
                            "\\u17B4\\u17B5\\u180B-\\u180E\\u2000-\\u200F\\u202A-\\u202E" +
                            "\\u2060-\\u206F\\u3000\\uFEFF\\uFFA0\\uFFF0-\\uFFF8]"
            );

    private static boolean hasOnlyVisibleChars(String s) {
        return !INVISIBLE_CHARS.matcher(s).find();
    }

    private static boolean isValidText(String s, int maxLen) {
        if (s == null) return true;
        String stripped = s.strip();
        if (stripped.isEmpty()) return true;
        if (stripped.length() > maxLen) return true;
        return !hasOnlyVisibleChars(stripped);
    }

    public static String checkForTest(String json) {
        if (json == null || json.isBlank()) {
            return "Ваш тест пустой!";
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                return "Некорректный формат теста!";
            }

            JsonNode quizNameNode = root.get("quizName");
            if (quizNameNode == null || !quizNameNode.isTextual()) {
                return "Неправильный заголовок теста!";
            }
            String quizName = quizNameNode.asText();
            if (isValidText(quizName, 200)) {
                return "Заголовок теста пустой, слишком длинный или содержит недопустимые символы!";
            }

            JsonNode questionsNode = root.get("questions");
            if (questionsNode == null || !questionsNode.isArray() || questionsNode.isEmpty() || questionsNode.size() > 30) {
                return "Неправильная структура вопросов!";
            }

            for (JsonNode questionNode : questionsNode) {
                if (!questionNode.isObject()) {
                    return "Неправильная структура вопросов!";
                }

                JsonNode questionTypeNode = questionNode.get("type");
                if (questionTypeNode == null || !questionTypeNode.isTextual()) {
                    return "Неправильно заданный вид вопроса!";
                }
                String questionType = questionTypeNode.asText();
                if (!(questionType.equals("var") || questionType.equals("ans") || questionType.equals("srv"))) {
                    return "Неправильно заданный вид вопроса!";
                }

                JsonNode questionTextNode = questionNode.get("question");
                if (questionTextNode == null || !questionTextNode.isTextual())
                    return "Неправильная структура вопросов!";
                if (isValidText(questionTextNode.asText(), 512))
                    return "Текст вопроса пустой, слишком длинный или содержит недопустимые символы!";

                JsonNode answersNode = questionNode.get("answers");
                if (answersNode == null || !answersNode.isObject() || answersNode.isEmpty())
                    return "Неправильная структура ответов!";

                int minAnswers = (questionType.equals("ans")) ? 1 : 2;
                int maxAnswers = 8;
                if (answersNode.size() < minAnswers || answersNode.size() > maxAnswers)
                    return "Неправильное количество вариантов ответа!";

                boolean hasCorrect = false;
                Set<String> seenAnswers = new java.util.HashSet<>();

                var fields = answersNode.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    String answerKey = field.getKey();
                    JsonNode answerValue = field.getValue();

                    if (isValidText(answerKey, 256))
                        return "Вариант ответа пустой, слишком длинный или содержит недопустимые символы!";

                    String normalizedKey = answerKey.strip().toLowerCase();
                    if (!seenAnswers.add(normalizedKey))
                        return "В одном из вопросов есть повторяющиеся варианты ответа!";

                    if (!answerValue.isBoolean())
                        return "Неправильная структура ответов!";

                    if (answerValue.asBoolean())
                        hasCorrect = true;
                }

                if (!questionType.equals("srv") && !hasCorrect)
                    return "В вопросе с вариантами ответа не отмечен ни один правильный вариант!";
            }
        } catch (Exception e) {
            return "Некорректный формат теста!";
        }

        return "";
    }
}