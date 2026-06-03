package org.example.classes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.bot.TelegramBot;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;
import org.example.database.DBManager;

import java.util.*;

public class User {
    private String state;
    private long chatId;
    private String currentClassName;
    private int classCount;
    private int testsCount;
    private int quizState;
    private Quiz currentQuiz;
    private String correctAnswer;
    private String prevType;
    private int correctAnswers;
    private LinkedHashMap<String, Boolean> userAnswers;
    private StudentClass currentChangingClass;
    private Test currentChangingTest;
    private Integer currentMyClassesMessageId;
    private Integer currentMyTestsMessageId;

    public StudentClass getCurrentChangingClass() {
        return currentChangingClass;
    }

    public void setCurrentChangingClass(StudentClass currentChangingClass) {
        this.currentChangingClass = currentChangingClass;
    }

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

    public void setCurrentMyClassesMessageId(Integer currentMyClassesMessageId) {
        this.currentMyClassesMessageId = currentMyClassesMessageId;
    }

    public void setCurrentMyTestsMessageId(Integer currentMyTestsMessageId) {
        this.currentMyTestsMessageId = currentMyTestsMessageId;
    }

    public void setCurrentChangingTest(Test currentChangingTest) {
        this.currentChangingTest = currentChangingTest;
    }

    public User(long chatId, String state, int classCount, int testsCount, int quizState, Quiz currentQuiz) {
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

    public Integer getCurrentMyClassesMessageId() {
        return currentMyClassesMessageId;
    }

    public Integer getCurrentMyTestsMessageId() {
        return currentMyTestsMessageId;
    }

    public Test getCurrentChangingTest() {
        return currentChangingTest;
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

    public void processMessageStates(TelegramBot bot, String msg, long chatId, ArrayList<User> users) {
        User user = this;
        switch (user.getState()) {
            case "class_name" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Создание класса отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId);
                    return;
                }

                if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                    bot.sendMessage("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", chatId);
                    return;
                }

                int doesClassExit = DBManager.doesClassExit(chatId, msg);
                if (doesClassExit == 2) {
                    bot.sendMessage("Произошла ошибка во время проверки имени класса, попробуйте ещё...", chatId);
                    return;
                }
                if (doesClassExit == 1) {
                    bot.sendMessage("У вас уже существует класс с таким именем.", chatId);
                    return;
                }

                user.setState("class_students");
                user.setCurrentClassName(msg);
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя.", chatId);
                    return;
                }

                bot.sendMessage("Перечислите через пробел username'ы учеников без @ (например: ivan victor test).", chatId);
            }
            case "class_students" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Создания класса отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId);
                    return;
                }
                ArrayList<String> usernames = new ArrayList<>(Arrays.asList(msg.split(" ")));
                if (usernames.size() < 2) {
                    bot.sendMessage("Класс должен состоять минимум из 2-х учеников.", chatId);
                    return;
                }

                user.setState("default");

                bot.sendMessage("Создание класса...", chatId);

                ArrayList<Long> students = DBManager.getIdsByUsernames(usernames);

                if (students == null || students.size() != usernames.size()) {
                    bot.sendMessage("Не удалось получить учеников, возможно они не зарегистрированы в боте.", chatId);
                    return;
                }

                if (!DBManager.createClass(user.getCurrentClassName(), chatId, students)) {
                    bot.sendMessage("Не удалось создать класс, попробуйте снова...", chatId);
                    return;
                }
                user.setClassCount(user.getClassCount() + 1);
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя.", chatId);
                    return;
                }

                bot.sendMessage("Класс успешно создан!", chatId);
            }
            case "delete_class" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Удаление класса отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время удаления класса (/exit для отмены удаления класса).", chatId);
                    return;
                }

                if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                    bot.sendMessage("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", chatId);
                    return;
                }

                int doesClassExit = DBManager.doesClassExit(chatId, msg);

                if (doesClassExit == 2) {
                    bot.sendMessage("Произошла ошибка во время проверки имени класса, попробуйте ещё...", chatId);
                    return;
                }
                if (doesClassExit == 0) {
                    bot.sendMessage("У вас нету класса с таким именем.", chatId);
                    return;
                }

                user.setState("default");
                if (!DBManager.deleteClass(chatId, msg)) {
                    bot.sendMessage("Не удалось удалить класс, попробуйте ещё...", chatId);
                    return;
                }
                user.setClassCount(user.getClassCount() - 1);
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                    return;
                }


                bot.sendMessage("Класс успешно удалён!", chatId);
            }
            //sent most of the logic of case "create_test" outside the if-clause as for it to be activated the message needs to have text
            case "create_test" -> {
                 /* added an if-clause to check for any command and the /exit command specifically
                if the user sent just a command, the bot tells them they can exit using /exit
                * the bot sends a message telling the person that they have cancelled the import of the test
                * then it sets the state to default, checks for it being done and goes back to the start
                also added an if-clause for checking the state
                * */
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Вы отменили загрузку теста.", chatId);
                        user.setState("default");
                        if (!bot.saveUser(user)) {
                            bot.sendMessage("Не удалось обновить состояние пользователя.", chatId);
                        }
                        return;
                    }
                    bot.sendMessage("Вы можете отменить загрузку файла с помощью команды /exit.", chatId);
                    return;
                }

                bot.sendMessage("Пожалуйста, отправьте файл.", chatId);
            }
            case "delete_test" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Удаление теста отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время удаления теста (/exit для отмены удаления теста).", chatId);
                    return;
                }

                if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                    bot.sendMessage("Введите корректное имя теста (минимум 2 символа без знаков препинания и пробелов).", chatId);
                    return;
                }

                int doesTestExit = DBManager.doesTestExit(chatId, msg);
                if (doesTestExit == 2) {
                    bot.sendMessage("Произошла ошибка во время проверки имени теста, попробуйте ещё...", chatId);
                    return;
                }
                if (doesTestExit == 0) {
                    bot.sendMessage("У вас нету теста с таким именем.", chatId);
                    return;
                }

                user.setState("default");

                if (!DBManager.deleteTest(chatId, DBManager.getTestContent(chatId, msg))) {
                    bot.sendMessage("Не удалось удалить тест, попробуйте ещё...", chatId);
                    return;
                }
                user.setTestsCount(user.getTestsCount() - 1);
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                    return;
                }

                bot.sendMessage("Тест успешно удалён!", chatId);
            }
            case "quiz_start" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Создания квиза отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId);
                    return;
                }

                if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                    bot.sendMessage("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", chatId);
                    return;
                }

                int doesClassExit = DBManager.doesClassExit(chatId, msg);
                if (doesClassExit == 2) {
                    bot.sendMessage("Произошла ошибка во время проверки имени класса, попробуйте ещё...", chatId);
                    return;
                }
                if (doesClassExit == 0) {
                    bot.sendMessage("У вас нету класса с таким именем.", chatId);
                    return;
                }

                user.setState("quiz_test");
                user.setCurrentClassName(msg);
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя.", chatId);
                    return;
                }

                bot.sendMessage("Введите название теста для начала квиза.", chatId);
                return;
            }
            case "quiz_test" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Создание квиза отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время удаления теста (/exit для отмены удаления теста).", chatId);
                    return;
                }

                if (bot.isNameValid(msg)) {
                    bot.sendMessage("Введите корректное имя теста (минимум 2 символа без знаков препинания и пробелов).", chatId);
                    return;
                }

                System.out.println("Checking the existence of the test");
                int doesTestExit = DBManager.doesTestExit(chatId, msg);
                System.out.println("Does test exit: " + doesTestExit);
                if (doesTestExit == 2) {
                    bot.sendMessage("Произошла ошибка во время проверки имени теста, попробуйте ещё...", chatId);
                    return;
                }
                if (doesTestExit == 0) {
                    bot.sendMessage("У вас нету теста с таким именем.", chatId);
                    return;
                }

                user.setState("default");
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                    return;
                }

                bot.sendMessage("Создание квиза...", chatId);

                Thread quizThread = new Thread(() -> {
                    synchronized (this) {
                        bot.sendMessage("Квиз успешно создан!", chatId);
                        Quiz quiz = new Quiz(chatId, DBManager.getClass(user.getCurrentClassName(), chatId), DBManager.getTest(chatId, msg));
                        quiz.startQuiz(bot, users, chatId);
                    }
                });
                quizThread.start();
            }
            case "deleting_student" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Изменение класса отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время изменения класса (/exit для отмены изменения класса).", chatId);
                    return;
                }
                StudentClass chosenClass = user.getCurrentChangingClass();
                ArrayList<String> usernamemaybes = new ArrayList<>();
                usernamemaybes.add(msg);
                ArrayList<Long> userId = DBManager.getIdsByUsernames(usernamemaybes);
                if (userId==null){
                    bot.sendMessage("Этого пользователя нету в базе данных.", chatId);
                    return;
                }
                if (!chosenClass.getStudents().contains(userId.getFirst())){
                    bot.sendMessage("Этого пользователя нету в вашем классе!", chatId);
                    return;
                }
                ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
                newSetOfStudents.remove(userId.getFirst());
                DBManager.deleteClass(chatId, chosenClass.getName());
                DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);
                user.setCurrentChangingClass(null);
                user.setState("default");
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя.", chatId);
                }
                bot.sendMessage("Пользователь успешно удалён!", chatId);
                bot.sendClasses(chatId, user);
            }
            case "adding_student" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.sendMessage("Изменение класса отменено.", chatId);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.sendMessage("Вы не можете отправлять команды во время изменения класса (/exit для отмены изменения класса).", chatId);
                    return;
                }
                StudentClass chosenClass = user.getCurrentChangingClass();
                ArrayList<String> usernamemaybes = new ArrayList<>();
                usernamemaybes.add(msg);
                ArrayList<Long> userId = DBManager.getIdsByUsernames(usernamemaybes);
                if (userId == null){
                    bot.sendMessage("Этого пользователя нету в базе данных.", chatId);
                    return;
                }
                if (chosenClass.getStudents().contains(userId.getFirst())){
                    bot.sendMessage("Этого пользователя уже есть в вашем классе!", chatId);
                    return;
                }
                ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
                newSetOfStudents.add(userId.getFirst());
                DBManager.deleteClass(chatId, chosenClass.getName());
                DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);
                user.setCurrentChangingClass(null);
                user.setState("default");
                if (!bot.saveUser(user)) {
                    bot.sendMessage("Не удалось обновить состояние пользователя.", chatId);
                }
                bot.sendMessage("Пользователь успешно добавлен!", chatId);
                bot.sendClasses(chatId, user);
            }
        }
    }
}
