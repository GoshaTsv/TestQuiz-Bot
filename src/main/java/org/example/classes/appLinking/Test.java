package org.example.classes.appLinking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.example.bot.TelegramBot;
import org.example.classes.User;
import org.example.database.DBManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

public class Test {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @SerializedName("quizName")
    private String testName;
    private ArrayList<Question> questions;

    public ArrayList<Question> getQuestions() { return questions; }
    public void setQuestions(ArrayList<Question> questions) { this.questions = questions; }

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

    private static final Pattern ALLOWED_CHAR = Pattern.compile(
            "[a-zA-Z" +
                    "\u0410-\u044F\u0401\u0451" +                    // Ru
                    "\u0406\u0456\u0407\u0457\u0404\u0454\u0490\u0491" + // Uk
                    "\u040E\u045E" +                                 // Be
                    "0-9" +
                    " \n" +
                    ".,!?;:\\-\u2013\u2014'\"\u00AB\u00BB()\\[\\]/%+=@#\u2116\\*\\^~_&|<>" +
                    "]"
    );

    private static boolean hasOnlyAllowedChars(String s) {
        for (int i = 0; i < s.length(); i++)
            if (!ALLOWED_CHAR.matcher(String.valueOf(s.charAt(i))).matches())
                return false;
        return true;
    }

    private static Character findDisallowedChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!ALLOWED_CHAR.matcher(String.valueOf(c)).matches())
                return c;
        }
        return null;
    }

    private static boolean isValidText(String s, int maxLen) {
        if (s == null) return true;
        String stripped = s.strip();
        if (stripped.isEmpty()) return true;
        if (stripped.length() > maxLen) return true;
        return !hasOnlyAllowedChars(stripped);
    }

    public static String checkForTest(TelegramBot bot, String json, User user, long chatId) {
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
            if (isValidText(quizNameNode.asText(), 200)) {
                Character bad = findDisallowedChar(quizNameNode.asText().strip());
                return bad != null
                        ? "Недопустимый символ в заголовке теста: '" + bad + "' (U+" + String.format("%04X", (int) bad) + ")"
                        : "Заголовок теста пустой или слишком длинный!";
            }

            JsonNode questionsNode = root.get("questions");
            if (questionsNode == null || !questionsNode.isArray()
                    || questionsNode.isEmpty() || questionsNode.size() > 30) {
                return "Неправильная структура вопросов!";
            }

            int qIndex = 0;
            for (JsonNode questionNode : questionsNode) {
                qIndex++;
                if (!questionNode.isObject())
                    return "Неправильная структура вопроса #" + qIndex + "!";
                JsonNode kindNode = questionNode.get("kind");
                if (kindNode == null || !kindNode.isTextual()){
                    return "Не указан вид вопроса #" + qIndex + "!";
                }
                String qKind = kindNode.asText();
                if (!qKind.equals("que") && !qKind.equals("srv")){
                    return "Неправильный тип вопроса #" + qIndex + "!";
                }
                JsonNode typeNode = questionNode.get("type");
                if (typeNode == null || !typeNode.isTextual())
                    return "Не указан тип вопроса #" + qIndex + "!";

                String qType = typeNode.asText();
                if (!qType.equals("var") && !qType.equals("ans")) {
                    return "Неправильный тип вопроса #" + qIndex + "!";
                }

                JsonNode textNode = questionNode.get("question");
                if (textNode == null || !textNode.isTextual()) {
                    return "Неправильная структура вопроса #" + qIndex + "!";
                }
                String qText = textNode.asText();
                if (isValidText(qText, 256)) {
                    Character bad = findDisallowedChar(qText.strip());
                    return bad != null
                            ? "Недопустимый символ в вопросе #" + qIndex + ": '" + bad + "' (U+" + String.format("%04X", (int) bad) + ")"
                            : "Текст вопроса #" + qIndex + " пустой или слишком длинный!";
                }

                JsonNode answersNode = questionNode.get("answers");
                if (answersNode == null || !answersNode.isObject() || (answersNode.isEmpty() && !qKind.equals("srv") && !qType.equals("ans")))
                    return "Неправильная структура ответов в вопросе #" + qIndex + "!";

                int minAnswers = qType.equals("ans") ? 1 : 2;
                if ((answersNode.size() < minAnswers || answersNode.size() > 8) && !qType.equalsIgnoreCase("ans") && !qKind.equalsIgnoreCase("srv"))
                    return "Неправильное количество вариантов ответа в вопросе #" + qIndex + "!";

                boolean hasCorrect = false;
                Set<String> seenAnswers = new java.util.HashSet<>();
                var fields = answersNode.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    String key = field.getKey();
                    JsonNode val = field.getValue();

                    if (isValidText(key, 3000)) {
                        Character bad = findDisallowedChar(key.strip());
                        return bad != null
                                ? "Недопустимый символ в варианте ответа (вопрос #" + qIndex + "): '" + bad + "' (U+" + String.format("%04X", (int) bad) + ")"
                                : "Вариант ответа в вопросе #" + qIndex + " пустой или слишком длинный!";
                    }
                    if (!seenAnswers.add(key.strip().toLowerCase()))
                        return "Повторяющийся вариант ответа в вопросе #" + qIndex + "!";

                    if (!val.isBoolean())
                        return "Неправильная структура ответов в вопросе #" + qIndex + "!";

                    if (val.asBoolean()) hasCorrect = true;
                }
            }
        } catch (Exception e) {
            return "Некорректный формат теста!";
        }

        Test test = gson.fromJson(json, Test.class);
        test.setTestName(test.getTestName().toLowerCase());
        json = gson.toJson(test);

        ArrayList<Test> tests = DBManager.getTests(chatId);
        if (tests == null)
            return bot.getTranslator().getTranslatedText("failed.get.tests", user.getLang());

        if (tests.stream().filter(t -> t.getTestName().equalsIgnoreCase(test.getTestName())).findFirst().orElse(null) != null)
            return bot.getTranslator().getTranslatedText("test.already.exists", user.getLang());

        user.setState("default");
        if (!bot.saveUser(user))
            return bot.getTranslator().getTranslatedText("failed.update.user.ellipsis", user.getLang());

        if (!DBManager.createTest(json, chatId))
            return bot.getTranslator().getTranslatedText("test.add.failed", user.getLang());

        user.setTestsCount(user.getTestsCount() + 1);
        if (!bot.saveUser(user))
            return bot.getTranslator().getTranslatedText("failed.update.user.ellipsis", user.getLang());

        if (user.getLastMessageId() != null) {
            bot.deleteMessage(user.getLastMessageId(), chatId);
            user.setLastMessageId(null);
            if (!bot.saveUser(user))
                return bot.getTranslator().getTranslatedText("failed.update.user.ellipsis", user.getLang());
        }

        if (user.getCurrentStartQuizClassMessageId() != null) {
            bot.deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);
            user.setCurrentStartQuizClass(null);
            if (!bot.saveUser(user))
                return bot.getTranslator().getTranslatedText("failed.update.user.ellipsis", user.getLang());
        }

        if (user.getCurrentMyClassesMessageId() != null) {
            bot.deleteMessage(user.getCurrentMyClassesMessageId(), chatId);
            user.setCurrentMyClassesMessageId(null);
            if (!bot.saveUser(user))
                return bot.getTranslator().getTranslatedText("failed.update.user.ellipsis", user.getLang());
        }

        if (user.getCurrentStartQuizTestMessageId() != null) {
            bot.deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);
            user.setCurrentStartQuizTestMessageId(null);
            if (!bot.saveUser(user))
                return bot.getTranslator().getTranslatedText("failed.update.user.ellipsis", user.getLang());
        }

        if (user.getCurrentMyTestsMessageId() != null) {
            bot.deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            user.setCurrentMyTestsMessageId(null);
            if (!bot.saveUser(user))
                return bot.getTranslator().getTranslatedText("failed.update.user.ellipsis", user.getLang());
        }

        return null;
    }
}