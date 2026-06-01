package org.example.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.example.classes.Quiz;
import org.example.classes.StudentClass;
import org.example.classes.User;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;
import org.example.database.DBManager;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.example.classes.User.getCorrectAnswerForQuestion;

public class TelegramBot extends TelegramLongPollingBot {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String WEB_APP_URL = System.getenv("WEBAPP_URL");
    private ArrayList<User> users = new ArrayList<>();

    @Override
    public String getBotUsername() {
        return "TestQuizBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    //moved all the user.setTestCount and user.setClassCount a bit lower so that it doesnt count when you get an error
    @Override
    public void onUpdateReceived(Update update) {
        long chatId;
        Message message = new Message();
        if (update.hasMessage()) {
            message = update.getMessage();
            chatId = update.getMessage().getChatId();
        } else {
            chatId = update.getCallbackQuery().getFrom().getId();
        }

        User user = users.stream().filter(x -> x.getChatId() == chatId).findFirst().orElse(null);

        if (message.getWebAppData() != null) {
            System.out.println("Получены web app data");
            String data = message.getWebAppData().getData();
            System.out.println("Данные: " + data);
            processWebAppData(message, user);
            return;
        }

        if (!message.hasText() && !message.hasDocument() && !update.hasCallbackQuery()) {
            sendMessage("Вы можете отправлять только сообщения или файлы.", chatId); //fixed a sensical error (is that a real word?)
            return;
        }

        if (user == null) {
            System.out.println("User is null for " + chatId);
            System.out.print("Users: ");
            if (message.hasText() && message.getText().startsWith("/start") && !(message.getText().startsWith("/startquiz"))) //added a check so that it doesnt get activated by /startquiz
                startRegistration(update, chatId);
            else
                sendMessage("Напишите /start для регистрации.", chatId);
            return;
        }
        if (user.getQuizState() == -1) {
            if (message.hasText()) {
                String msg = "";
                if (message.hasText()) msg = message.getText();
                if (update.hasCallbackQuery()) msg = update.getCallbackQuery().getData();

                if (msg.trim().isEmpty()) {
                    sendMessage("Вы не можете отправлять пустые сообщения.", chatId);
                    return;
                }

                String finalMsg = msg;
                switch (user.getState()) {
                    case "class_name" -> {
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/exit")) {
                                sendMessage("Создание класса отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId);
                            return;
                        }

                        if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                            sendMessage("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", chatId);
                            return;
                        }

                        int doesClassExit = DBManager.doesClassExit(chatId, msg);
                        if (doesClassExit == 2) {
                            sendMessage("Произошла ошибка во время проверки имени класса, попробуйте ещё...", chatId);
                            return;
                        }
                        if (doesClassExit == 1) {
                            sendMessage("У вас уже существует класс с таким именем.", chatId);
                            return;
                        }

                        user.setState("class_students");
                        user.setCurrentClassName(msg);
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя.", chatId);
                            return;
                        }

                        sendMessage("Перечислите через пробел username'ы учеников без @ (например: ivan victor test).", chatId);
                        return;
                    }
                    case "class_students" -> {
                        if (msg.startsWith("/")) { // exit command
                            if (msg.startsWith("/exit")) {
                                sendMessage("Создания класса отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId);
                            return;
                        }
                        ArrayList<String> usernames = new ArrayList<>(Arrays.asList(msg.split(" ")));
                        if (usernames.size() < 2) {
                            sendMessage("Класс должен состоять минимум из 2-х учеников.", chatId);
                            return;
                        }

                        user.setState("default");

                        sendMessage("Создание класса...", chatId);

                        ArrayList<Long> students = DBManager.getIdsByUsernames(usernames);

                        if (students == null || students.size() != usernames.size()) {
                            sendMessage("Не удалось получить учеников, возможно они не зарегистрированы в боте.", chatId);
                            return;
                        }

                        if (!DBManager.createClass(user.getCurrentClassName(), chatId, students)) {
                            sendMessage("Не удалось создать класс, попробуйте снова...", chatId);
                            return;
                        }
                        user.setClassCount(user.getClassCount() + 1);
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя.", chatId);
                            return;
                        }

                        sendMessage("Класс успешно создан!", chatId);
                        return;
                    }
                    case "delete_class" -> { // new state
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/exit")) {
                                sendMessage("Удаление класса отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время удаления класса (/exit для отмены удаления класса).", chatId);
                            return;
                        }

                        if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                            sendMessage("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", chatId);
                            return;
                        }

                        int doesClassExit = DBManager.doesClassExit(chatId, msg);

                        if (doesClassExit == 2) {
                            sendMessage("Произошла ошибка во время проверки имени класса, попробуйте ещё...", chatId);
                            return;
                        }
                        if (doesClassExit == 0) {
                            sendMessage("У вас нету класса с таким именем.", chatId);
                            return;
                        }

                        user.setState("default");
                        if (!DBManager.deleteClass(chatId, msg)) {
                            sendMessage("Не удалось удалить класс, попробуйте ещё...", chatId);
                            return;
                        }
                        user.setClassCount(user.getClassCount() - 1);
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                            return;
                        }


                        sendMessage("Класс успешно удалён!", chatId);
                    }
                    //sent most of the logic of case "create_test" outside the if-clause as for it to be activated the message needs to have text
                    case "create_test" -> {
                     /* added an if-clause to check for any command and the /exit command specifically
                    if the user sent just a command, the bot tells them they can exit using /exit
                    * the bot sends a message telling the person that they have cancelled the import of the test
                    * then it sets the state to default, checks for it being done and goes back to the start
                    also added an if-clause for checking the state
                    * */
                        if (!user.getState().equalsIgnoreCase("create_test")) {
                            System.out.println("чё");
                            return;
                        }
                        if (update.getMessage().hasText()) {
                            if (update.getMessage().getText().startsWith("/")) {
                                if (update.getMessage().getText().startsWith("/exit")) {
                                    sendMessage("Вы отменили загрузку теста.", chatId);
                                    user.setState("default");
                                    if (!saveUser(user)) {
                                        sendMessage("Не удалось обновить состояние пользователя.", chatId);
                                    }
                                    return;
                                }
                                sendMessage("Вы можете отменить загрузку файла с помощью команды /exit.", chatId);
                                return;
                            }
                        }
                        if (!(update.getMessage().hasDocument())) {
                            sendMessage("Пожалуйста, отправьте файл.", chatId);
                            return;
                        }
                        sendMessage("test", chatId);
                    }
                    case "delete_test" -> { // new state
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/exit")) {
                                sendMessage("Удаление теста отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время удаления теста (/exit для отмены удаления теста).", chatId);
                            return;
                        }

                        if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                            sendMessage("Введите корректное имя теста (минимум 2 символа без знаков препинания и пробелов).", chatId);
                            return;
                        }

                        int doesTestExit = DBManager.doesTestExit(chatId, msg);
                        if (doesTestExit == 2) {
                            sendMessage("Произошла ошибка во время проверки имени теста, попробуйте ещё...", chatId);
                            return;
                        }
                        if (doesTestExit == 0) {
                            sendMessage("У вас нету теста с таким именем.", chatId);
                            return;
                        }

                        user.setState("default");

                        if (!DBManager.deleteTest(chatId, DBManager.getTestContent(chatId, msg))) {
                            sendMessage("Не удалось удалить тест, попробуйте ещё...", chatId);
                            return;
                        }
                        user.setTestsCount(user.getTestsCount() - 1);
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                            return;
                        }

                        sendMessage("Тест успешно удалён!", chatId);
                    }
                    case "quiz_start" -> {       // new state
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/exit")) {
                                sendMessage("Создания квиза отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId);
                            return;
                        }

                        if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                            sendMessage("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", chatId);
                            return;
                        }

                        int doesClassExit = DBManager.doesClassExit(chatId, msg);
                        if (doesClassExit == 2) {
                            sendMessage("Произошла ошибка во время проверки имени класса, попробуйте ещё...", chatId);
                            return;
                        }
                        if (doesClassExit == 0) {
                            sendMessage("У вас нету класса с таким именем.", chatId);
                            return;
                        }

                        user.setState("quiz_test");
                        user.setCurrentClassName(msg);
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя.", chatId);
                            return;
                        }

                        sendMessage("Введите название теста для начала квиза.", chatId);
                        return;
                    }
                    case "quiz_test" -> {       // new state
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/exit")) {
                                sendMessage("Создание квиза отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время удаления теста (/exit для отмены удаления теста).", chatId);
                            return;
                        }

                        if (isNameValid(msg)) {
                            sendMessage("Введите корректное имя теста (минимум 2 символа без знаков препинания и пробелов).", chatId);
                            return;
                        }

                        System.out.println("Checking the existence of the test");
                        int doesTestExit = DBManager.doesTestExit(chatId, msg);
                        System.out.println("Does test exit: " + doesTestExit);
                        if (doesTestExit == 2) {
                            sendMessage("Произошла ошибка во время проверки имени теста, попробуйте ещё...", chatId);
                            return;
                        }
                        if (doesTestExit == 0) {
                            sendMessage("У вас нету теста с таким именем.", chatId);
                            return;
                        }

                        user.setState("default");
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                            return;
                        }

                        sendMessage("Создание квиза...", chatId);

                        Thread quizThread = new Thread(() -> {
                            synchronized (this) {
                                sendMessage("Квиз успешно создан!", chatId);
                                Quiz quiz = new Quiz(chatId, DBManager.getClass(user.getCurrentClassName(), chatId), DBManager.getTest(chatId, finalMsg));
                                quiz.getStudentClass().getStudents().forEach(x -> {
                                    User userCurrent = users.stream().filter(user1 -> user1.getChatId() == x).findFirst().orElse(null);
                                    if (userCurrent == null) {
                                        System.out.println("User not found in thread when starting test");
                                        sendMessage("Что-то пошло не так. Попробуйте ещё раз...", chatId);
                                        return;
                                    }

                                    // waiting while previous quiz end

                                    new Thread(() -> {
                                        while (userCurrent.getQuizState() != -1) {
                                            try {
                                                Thread.sleep(100);
                                            } catch (InterruptedException e) {
                                                sendMessage("Что-то пошло не так. Попробуйте ещё раз...", chatId);
                                            }
                                        }
                                        userCurrent.setQuizState(1);
                                        userCurrent.setCurrentQuiz(quiz);
                                        try {
                                            Thread.sleep(500); // delay before the start
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }

                                        int count = 1;
                                        for (int i = 0; i < quiz.getTest().questions.size(); i++) {
                                            Question question = quiz.getTest().questions.get(i);

                                            if (question.answers.size() > 1) {
                                                ArrayList<String> variants = new ArrayList<>(question.answers.keySet());
                                                int rightVar = 1;
                                                Boolean[] answers = question.answers.values().toArray(new Boolean[0]);

                                                for (int j = 0; j < answers.length; j++) {
                                                    if (answers[j]) {
                                                        rightVar = j;
                                                        break;
                                                    }
                                                }

                                                ArrayList<String> callbacks = new ArrayList<>();

                                                for (int j = 0; j < variants.size(); j++)
                                                    callbacks.add("ans_" + i);

                                                sendMessage("Вопрос #" + count + ": " + question.question, x, variants, callbacks);
                                                count++;
                                                userCurrent.setCorrectAnswer(String.valueOf(rightVar));
                                                userCurrent.setPrevType("var");
                                                return;
                                            } else {
                                                sendMessage("Вопрос #" + count + ": " + question.question, x);
                                                userCurrent.setPrevType("ans");
                                                userCurrent.setCorrectAnswer(question.answers.keySet().toArray(new String[0])[0]);
                                            }

                                            try {
                                                Thread.sleep(1000);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                                break;
                                            }

                                            count++;
                                            return;
                                        }
                                    }).start();
                                });
                            }

                        });
                        quizThread.start();
                    }
                    case "deleting_student" -> {
                        if (!update.getMessage().hasText()){
                            user.setState("default");
                            if (!saveUser(user)) {
                                sendMessage("Не удалось обновить состояние пользователя.", chatId);
                                return;
                            }
                            sendMessage("Вы отменили удаление пользователя.\nЕсли вы хотели его удалить, то в следующий раз пожалуйста напишите его имя (без @).", chatId);
                        }
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/exit")) {
                                sendMessage("Изменение класса отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время изменения класса (/exit для отмены изменения класса).", chatId);
                            return;
                        }
                        StudentClass chosenClass = user.getCurrentChangingClass();
                        ArrayList<String> usernamemaybes = new ArrayList<>();
                        usernamemaybes.add(msg);
                        ArrayList<Long> userId = DBManager.getIdsByUsernames(usernamemaybes);
                        if (userId==null){
                            sendMessage("Этого пользователя нету в базе данных.", chatId);
                            return;
                        }
                        if (!chosenClass.getStudents().contains(userId.getFirst())){
                            sendMessage("Этого пользователя нету в вашем классе!", chatId);
                            return;
                        }
                        ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
                        newSetOfStudents.remove(userId.getFirst());
                        DBManager.deleteClass(chatId, chosenClass.getName());
                        DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);
                        user.setCurrentChangingClass(null);
                        user.setState("default");
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя.", chatId);
                        }
                        sendMessage("Пользователь успешно удалён!", chatId);
                        sendClasses(chatId, user);
                        return;
                    }
                    case "adding_student" ->{
                        if (!update.getMessage().hasText()){
                            user.setState("default");
                            if (!saveUser(user)) {
                                sendMessage("Не удалось обновить состояние пользователя.", chatId);
                                return;
                            }
                            sendMessage("Вы отменили удаление пользователя.\nЕсли вы хотели его удалить, то в следующий раз пожалуйста напишите его имя (без @).", chatId);
                        }
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/exit")) {
                                sendMessage("Изменение класса отменено.", chatId);
                                user.setState("default");
                                saveUser(user);
                                return;
                            }
                            sendMessage("Вы не можете отправлять команды во время изменения класса (/exit для отмены изменения класса).", chatId);
                            return;
                        }
                        StudentClass chosenClass = user.getCurrentChangingClass();
                        ArrayList<String> usernamemaybes = new ArrayList<>();
                        usernamemaybes.add(msg);
                        ArrayList<Long> userId = DBManager.getIdsByUsernames(usernamemaybes);
                        if (userId==null){
                            sendMessage("Этого пользователя нету в базе данных.", chatId);
                            return;
                        }
                        if (chosenClass.getStudents().contains(userId.getFirst())){
                            sendMessage("Этого пользователя уже есть в вашем классе!", chatId);
                            return;
                        }
                        ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
                        newSetOfStudents.add(userId.getFirst());
                        DBManager.deleteClass(chatId, chosenClass.getName());
                        DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);
                        user.setCurrentChangingClass(null);
                        user.setState("default");
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя.", chatId);
                        }
                        sendMessage("Пользователь успешно добавлен!", chatId);
                        sendClasses(chatId, user);
                        return;
                    }
                }


                //added a check for not being startquiz so that it doesn't activate when starting a quiz
                if (msg.startsWith("/start") && !(msg.startsWith("/startquiz")))
                    startRegistration(update, chatId);
                else if (msg.startsWith("/newclass")) {
                    if (user.getClassCount() >= 5) { //added >= so that you can create 5 and not 6 classes
                        sendMessage("Вы больше не можете создавать классы! Лимит - 5 классов", chatId);
                        return;
                    }
                    user.setState("class_name");
                    if (!saveUser(user)) {
                        sendMessage("Не удалось обновить состояние пользователя.", chatId);
                        return;
                    }

                    sendMessage("Введите название класса.", chatId);
                } else if (msg.startsWith("/myclasses")) {
                    sendClasses(chatId, user);
                } else if (msg.startsWith("/deleteclass")) { // new command
                    ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
                    if (classes == null) {
                        sendMessage("Произошла ошибка во время загрузки классов, попробуйте ещё...", chatId);
                        return;
                    }
                    if (classes.isEmpty()) {
                        sendMessage("У вас ещё нет классов.", chatId);
                        return;
                    }

                    user.setState("delete_class");
                    if (!saveUser(user)) {
                        sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                        return;
                    }

                    sendMessage("Введите название класса", chatId);
                } else if (msg.startsWith("/newtest")) {
                    if (user.getTestsCount() >= 10) { //added >= so that you can create 10 and not 11 tests
                        sendMessage("Вы больше не можете создавать тесты! Лимит - 10 тестов", chatId);
                        return;
                    }
                    user.setState("create_test");
                    if (!saveUser(user)) {
                        sendMessage("Не удалось обновить состояние пользователя.", chatId);
                        return;
                    }

                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(String.valueOf(chatId));
                    sendMessage.setText("Пожалуйста, отправьте .json файл с тестом, создайте новый тест в мини-приложении или напишите /exit для отмены.");

                    ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
                    keyboard.setResizeKeyboard(true);
                    keyboard.setOneTimeKeyboard(false);

                    KeyboardButton webAppButton = new KeyboardButton();
                    webAppButton.setText("Создать тест");

                    WebAppInfo webAppInfo = new WebAppInfo();
                    webAppInfo.setUrl(WEB_APP_URL);
                    webAppButton.setWebApp(webAppInfo);

                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow row =
                            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow();
                    row.add(webAppButton);

                    keyboard.setKeyboard(java.util.List.of(row));
                    sendMessage.setReplyMarkup(keyboard);

                    try {
                        execute(sendMessage);
                    } catch (TelegramApiException e) {
                        System.err.println("Ошибка отправки /newtest сообщения: " + e.getMessage());
                    }
                }

                else if (msg.startsWith("/mytests")) {
                    ArrayList<Test> tests = DBManager.getTests(chatId);
                    if (tests == null) {
                        sendMessage("Не удалось получить тесты пользователя...", chatId);
                        return;
                    }
                    if (tests.isEmpty()) {
                        sendMessage("У вас нет тестов!", chatId);
                        return;
                    }

                    StringBuilder msgBuilder = new StringBuilder();
                    msgBuilder.append(String.format("Ваши тесты (%d): \n", tests.size()));
                    for (Test test : tests)
                        msgBuilder.append(String.format(" - %s (%d вопросов).\n", test.testName, test.questions.size()));

                    sendMessage(msgBuilder.toString(), chatId);
                    if (!saveUser(user)) {
                        sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                        return;
                    }
                } else if (msg.startsWith("/deletetest")) { // new command
                    ArrayList<Test> tests = DBManager.getTests(chatId);
                    if (tests == null) {
                        sendMessage("Произошла ошибка во время загрузки тестов, попробуйте ещё...", chatId);
                        return;
                    }
                    if (tests.isEmpty()) {
                        sendMessage("У вас ещё нет тестов.", chatId);
                        return;
                    }

                    user.setState("delete_test");
                    if (!saveUser(user)) {
                        sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                        return;
                    }

                    sendMessage("Введите название теста", chatId);
                } else if (msg.startsWith("/startquiz")) { // new command
                    //started the logic for starting the quiz
                    ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
                    if (classes == null) {
                        sendMessage("Не удалось получить ваши классы, попробуйте ещё...", chatId);
                        return;
                    }

                    if (classes.isEmpty()) {
                        sendMessage("У вас ещё нет классов.", chatId);
                        return;
                    }

                    ArrayList<Test> tests = DBManager.getTests(chatId);
                    if (tests == null) {
                        sendMessage("Не удалось получить ваши тесты, попробуйте ещё...", chatId);
                        return;
                    }

                    if (tests.isEmpty()) {
                        sendMessage("У вас ещё нет тестов.", chatId);
                        return;
                    }

                    user.setState("quiz_start");
                    if (!saveUser(user)) {
                        sendMessage("Не удалось обновить состояние пользователя.", chatId);
                        return;
                    }

                    sendMessage("Введите название класса для запуска квиза.", chatId);
                }
            }
            if (update.hasCallbackQuery()) {
                switch(user.getState()){
                    case "change_classes" -> {
                        System.out.println("changing class");
                        if (!update.hasCallbackQuery()){
                            user.setState("default");
                            if (!saveUser(user)) {
                                sendMessage("Не удалось обновить состояние пользователя.", chatId);
                                return;
                            }
                            sendMessage("Вы отменили изменение класса.\nЕсли вы хотели его изменить, то в следующий раз пожалуйста нажмите на кнопку.", chatId);
                        }
                        String data = update.getCallbackQuery().getData();
                        String result = data.replaceAll("class_", "");
                        try {
                            execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
                        } catch (TelegramApiException e) {
                            throw new RuntimeException(e);
                        }
                        StudentClass chosenClass = user.getCurrentChangingClass();
                        if (result.equalsIgnoreCase("delete")){
                            if (chosenClass.getStudents().size() <= 2){
                                sendMessage("Вы не можете больше удалять учеников, минимум - 2 ученика.", chatId);
                                return;
                            }
                            sendMessage("Пожалуйста, введите имя ученика, которого хотите убрать из класса (без @).", chatId);
                            user.setState("deleting_student");
                            if (!saveUser(user)) {
                                sendMessage("Не удалось обновить состояние пользователя.", chatId);
                                return;
                            }
                            return;
                        }
                        sendMessage("Пожалуйста, введите имя ученика, которого хотите добавить в класс (без @).", chatId);
                        user.setState("adding_student");
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя.", chatId);
                            return;
                        }
                    }
                    case "view_classes" -> {
                        System.out.println("viewing classes");
                        if (!update.hasCallbackQuery()){
                            user.setState("default");
                            if (!saveUser(user)) {
                                sendMessage("Не удалось обновить состояние пользователя.", chatId);
                                return;
                            }
                            sendMessage("Вы отменили просмотр классов.\nЕсли вы хотели их просмотреть, то в следующий раз пожалуйста нажмите на кнопку.", chatId);
                        }
                        String data = update.getCallbackQuery().getData();
                        String result = data.replaceAll("class_", "");
                        ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
                        assert classes != null;
                        StudentClass chosenClass = classes.get(Integer.parseInt(result)); // deleted -1
                        try {
                            execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
                        } catch (TelegramApiException e) {
                            throw new RuntimeException(e);
                        }
                        StringBuilder classString = new StringBuilder();
                        classString.append(String.format("Название класса: \"%s\"\nУченики: \n", chosenClass.getName()));
                        ArrayList<String> userUsernames = DBManager.getUsernamesByIds(chosenClass.getStudents());
                        assert userUsernames != null;
                        for (String username: userUsernames){
                            classString.append("- @").append(username).append("\n");
                        }
                        System.out.println(userUsernames);
                        System.out.println(classString);
                        ArrayList<String> options = new ArrayList<>();
                        options.add("Удалить ученика");
                        options.add("Добавить ученика");

                        ArrayList<String> callbacks = new ArrayList<>();
                        callbacks.add("class_delete");
                        callbacks.add("class_add");

                        sendMessage(classString.toString(), chatId, options, callbacks);
                        user.setState("change_classes");
                        user.setCurrentChangingClass(chosenClass);
                        if (!saveUser(user)) {
                            sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                            return;
                        }
                    }

                }
            }
            if (message.hasDocument()) {
                if (!(update.getMessage().getDocument().getFileName().endsWith(".json"))) {
                    sendMessage("Пожалуйста, отправьте файл с типом .json.", chatId);
                    return;
                }
                String fileId = update.getMessage().getDocument().getFileId();
                GetFile getFile = new GetFile();
                getFile.setFileId(fileId);

                String fileName;
                try {
                    String filePath = execute(getFile).getFilePath();
                    fileName = "newTest" + chatId + ".json";
                    downloadFile(filePath, new File(fileName)); //added chat id so that the bot doesnt accidentally download 2 files of the same name
                } catch (TelegramApiException e) {
                    sendMessage("Произошла ошибка во время добавления теста. Попробуйте ещё раз...", chatId);
                    return;
                }

                createTest(chatId, user, fileName);
            }
        }
        if (user.getQuizState() > -1) {
            String userName;
            if (update.hasMessage()) {
                userName = update.getMessage().getFrom().getUserName();
            } else {
                userName = update.getCallbackQuery().getFrom().getUserName();
            }

            User userCurrent = users.stream().filter(user1 -> user1.getChatId() == chatId).findFirst().orElse(null);

            assert userCurrent != null;
            if (userCurrent.getCurrentQuiz() == null) {
                System.out.println("unable to get quiz when continuing the quiz (user.getQuizState()!=-1)");
                sendMessage("Произошла ошибка, попробуйте ещё раз...", chatId);
            }
            Quiz quiz = userCurrent.getCurrentQuiz();
            Test test = quiz.getTest();
            Question currentQuestion = test.questions.get(user.getQuizState() - 1);

            if (!(update.hasMessage() || update.hasCallbackQuery())) {
                sendMessage("Пожалуйста, отправьте ответ на вопрос.", chatId);
                return;
            }
            if (userCurrent.getPrevType().equalsIgnoreCase("ans")) {
                if (update.hasMessage() && update.getMessage().hasText()) {
                    String userAnswer = update.getMessage().getText();
                    String correctAnswer = getCorrectAnswerForQuestion(currentQuestion);
                    System.out.println("Ans");
                    System.out.println("User answer: " + userAnswer);
                    System.out.println("Correct answer: " + correctAnswer);
                    LinkedHashMap<String, Boolean> newUserAnswers = user.getUserAnswers();

                    boolean isCorrect = userAnswer.equalsIgnoreCase(correctAnswer);
                    newUserAnswers.put(user.getQuizState() + "\uD80C\uDE78" + userAnswer, isCorrect);

                    if (isCorrect)
                        user.setCorrectAnswers(user.getCorrectAnswers() + 1);

                    user.setUserAnswers(newUserAnswers);
                    System.out.println("added new user answer. size: " + user.getUserAnswers().size());
                    System.out.println("new user answers: " + newUserAnswers);
                } else {
                    sendMessage("Пожалуйста, ответьте на вопрос словом/словами.", chatId);
                    return;
                }
            }
            if (userCurrent.getPrevType().equalsIgnoreCase("var")) {
                if (update.hasCallbackQuery()) {
                    String callbackData = update.getCallbackQuery().getData();
                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(update.getCallbackQuery().getId());

                    // call back
                    String selectedAnswer = callbackData.replace("ans_", "");
                    List<String> keys = new ArrayList<>(quiz.getTest().questions.get(user.getQuizState() - 1).answers.keySet());
                    String correctAnswer = String.valueOf(keys.indexOf(User.getCorrectAnswerForQuestion(currentQuestion)) + 1);
                    LinkedHashMap<String, Boolean> newUserAnswers = user.getUserAnswers();

                    String userAnswer = keys.get(Integer.parseInt(selectedAnswer)); // removed -1
                    boolean isCorrect = selectedAnswer.equals(correctAnswer);

                    newUserAnswers.put(user.getQuizState() + "\uD80C\uDE78" + userAnswer, isCorrect);
                    if (isCorrect)
                        user.setCorrectAnswers(user.getCorrectAnswers() + 1);

                    user.setUserAnswers(newUserAnswers);

                    System.out.println("Var");
                    System.out.println("Selected answer: " + selectedAnswer);
                    System.out.println("Correct answer: " + correctAnswer);
                    System.out.println("added new user answer. size: " + user.getUserAnswers().size());
                    System.out.println("new user answers: " + newUserAnswers);
                    try {
                        execute(answer);
                    } catch (TelegramApiException e) {
                        System.out.println("Exception while processing variant answer: " + e.getMessage());
                    }
                } else {
                    sendMessage("Пожалуйста, нажмите на 1 из кнопок.", chatId);
                    return;
                }
            }

            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                return;
            }

            if (!saveUser(userCurrent)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                return;
            }

            // move to the next answer
            userCurrent.setQuizState(user.getQuizState() + 1);
            if (user.getQuizState() > quiz.getTest().questions.size()) {
                System.out.println("got to start of the thread!");
                Thread thread = new Thread(() -> {
                    System.out.println("thread launched!");
                    sendMessage("Поздравляем! Вы правильно ответили на " + userCurrent.getCorrectAnswers() + " из " + quiz.getTest().questions.size() + " вопросов! Надеемся, вам понравилось!", chatId);
                    sendMessage("Здравствуйте. @" + userName + " закончил ваш тест \"" + quiz.getTest().testName + "\" и правильно ответил на " + userCurrent.getCorrectAnswers() + " из " + quiz.getTest().questions.size() + " вопросов. \nОтветы ученика:", quiz.getTeacherId());
                    List<String> userAnswers = new ArrayList<>(user.getUserAnswers().keySet());

                    System.out.println("User answers: " + userAnswers);

                    for (int i = 0; i < user.getUserAnswers().size() && i < quiz.getTest().questions.size(); i++) {
                        String question = test.questions.get(i).question;
                        String userAnswer = userAnswers.get(i).split("\uD80C\uDE78")[1];
                        String correctAnswer = getCorrectAnswerForQuestion(quiz.getTest().questions.get(i));

                        System.out.println("Question: " + question);
                        System.out.println("User answer: " + userAnswer);
                        System.out.println("Correct answer: " + correctAnswer);
                        sendMessage("Вопрос #" + (i + 1) + ": \"" + question + "\" \nОтвет вашего ученика (@"  + userName + "): \"" + userAnswer + "\"\nПравильный ответ: \"" + correctAnswer + "\"", quiz.getTeacherId());

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    userCurrent.setQuizState(-1);
                    userCurrent.setCurrentQuiz(null);
                    userCurrent.setCorrectAnswers(0);
                    userCurrent.setUserAnswers(new LinkedHashMap<>());

                    if (!saveUser(userCurrent))
                        sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                });
                thread.start();
                return;
            }

            Question question = quiz.getTest().questions.get(user.getQuizState() - 1);

            if (question.answers.size() > 1) {
                new Thread(() -> {
                    ArrayList<String> variants = new ArrayList<>(question.answers.keySet());

                    String correctAnswer = null;
                    for (Map.Entry<String, Boolean> entry : question.answers.entrySet()) {
                        if (entry.getValue()) {
                            correctAnswer = entry.getKey();
                            break;
                        }
                    }
                    userCurrent.setCorrectAnswer(correctAnswer);
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    ArrayList<String> callbacks = new ArrayList<>();

                    for (int i = 0; i < variants.size(); i++)
                        callbacks.add("ans_" + i);

                    sendMessage("Вопрос #" + user.getQuizState() + ": " + question.question, chatId, variants, callbacks);
                    userCurrent.setPrevType("var");

                    if (!saveUser(userCurrent))
                        sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                }).start();
            } else {
                new Thread(() -> {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    //sending a message with a question and storing the answer
                    sendMessage("Вопрос #" + user.getQuizState() + ": " + question.question, chatId);
                    userCurrent.setCorrectAnswer(question.answers.keySet().toArray(new String[0])[0]);
                    userCurrent.setPrevType("ans");

                    if (!saveUser(userCurrent))
                        sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                }).start();
            }
        }
    }

    //changed all JsonObject to Test
    private String checkForTest(String json) {
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
                if (questionText == null || !questionText.isTextual()) {
                    return "Неправильная структура вопросов!";
                }

                JsonNode answers = questionNode.get("answers");
                if (answers == null || !answers.isObject() || answers.isEmpty() || answers.size() > 8) {
                    return "Неправильная структура ответов!";
                }

                var fields = answers.fields();
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
//        if (questions.isEmpty()) {
//            return "В вашем тесте нет вопросов!";
//        }

        for (Question question : questions) {
//            String questionName = x.question;
//            if (questionName.isBlank()) {
//                flag.set("У какого-то вопроса нет названия.");
//            }
            HashMap<String, Boolean> map = question.answers;
//            if (map.values().stream().anyMatch(Objects::isNull)) {
//                flag.set("У какого-то вопроса нет вариантов.");
//            }
            if (!map.containsValue(Boolean.TRUE))
                return "Неправильная структура вопросов!";
        }
        return "";
    }

    private void startRegistration(Update update, long chatId) {
        String username = update.getMessage().getFrom().getUserName();

        if (username == null) {
            sendMessage("Задайте username своему аккаунту чтобы продолжить.", chatId);
            return;
        }

        int doesUserExit = DBManager.doesUserExist(username); // Checking the existence of the user
        if (doesUserExit == 2) {
            sendMessage("Произошла ошибка во время проверки пользователя на существование, попробуйте ещё...", chatId);
            return;
        }

        if (doesUserExit == 0)
            if (!DBManager.registerAccount(username, chatId)) {
                sendMessage("Произошла ошибка во время регистрации, попробуйте ещё... (/start)", chatId);
                return;
            }
        // new delete commands
        // 3 tests -> 10 tests
        // 10 classes
        sendMessage("""
                Здравствуйте, это бот для тестов, вот все комманды бота:
                 - /newclass - создать новый класс (максимум 5)
                 - /myclasses - просмотреть свои классы
                 - /deleteclass - удалить класс
                 - /newtest - создать новый тест (максимум 10 тестов)
                 - /mytests - просмотреть свои тесты
                 - /deletetest - удалить тест
                 - /startquiz - провести тестирование
                """, chatId);
        users.add(new User(chatId, "default", 0, 0, -1, null)); // add user
    }

    private boolean saveUser(User user) {
        int index = IntStream.range(0, users.size()).filter(i -> users.get(i).getChatId() == user.getChatId()).findFirst().orElse(-1);
        if (index == -1)
            return false;

        users.set(index, user);
        return true;
    }

    private void sendMessage(String msg, long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(msg);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
        }
    }

    //added another sendMessage method to add buttons to messages
    private void sendMessage(String msg, long chatId, ArrayList<String> buttons, ArrayList<String> callbacks) {
        System.out.println("Buttons: " + buttons);
        System.out.println("Callbacks: " + callbacks);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        AtomicInteger count = new AtomicInteger();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        buttons.forEach(x -> {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(x);
            count.getAndIncrement();
            button.setCallbackData(callbacks.get(count.get()));
            row.add(button);
            rows.add(row);
        });
        keyboard.setKeyboard(rows);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setReplyMarkup(keyboard);
        sendMessage.setText(msg);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
        }
    }

    private void createTest(long chatId, User user, String fileName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            String json = "";
            String line;
            while ((line = br.readLine()) != null) {
                json += line;
            }

            String response = checkForTest(json);
            System.out.println("Checked test: " + response);
            if (!response.isBlank()) {
                sendMessage(response, chatId);
                return;
            }

            Test test = gson.fromJson(json, Test.class);
            test.setTestName(test.getTestName().toLowerCase());
            json = gson.toJson(test);

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                sendMessage("Не удалось получить тесты, попробуйте ещё раз...", chatId);
                return;
            }

            if (tests.stream().filter(t -> t.getTestName().equalsIgnoreCase(test.getTestName())).findFirst().orElse(null) != null) {
                sendMessage("У вас уже есть такой тест, отправьте другой.", chatId);
                return;
            }

            user.setState("default");
            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                return;
            }
            if (!DBManager.createTest(json, chatId)) {
                sendMessage("Тест не получилось добавить. Попробуйте ещё раз...", chatId);
                return;
            }

            user.setTestsCount(user.getTestsCount() + 1);
            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                return;
            }
            sendMessage("Тест успешно добавлен!", chatId);
        } catch (IOException e) {
            sendMessage("Произошла ошибка во время добавления теста. Попробуйте ещё раз...", chatId);
        }
    }

    private boolean isNameValid(String name) {
        return name.replaceAll("\\p{Punct}", "").length() < 2;
    }

    private void processWebAppData(Message message, User user) {
        long chatId = message.getChatId();
        String jsonData = message.getWebAppData().getData();
        Long userId = message.getFrom().getId();
        String userName = message.getFrom().getUserName();

        if (jsonData.trim().isEmpty()) {
            sendMessage("Получены пустые данные. Пожалуйста, попробуйте ещё раз...", chatId);
            return;
        }

        System.out.println("Получен тест от " + userName + " (ID: " + userId + ")");
        System.out.println("JSON данные: " + jsonData);

        if (user == null) {
            sendMessage("Пользователь не найден. Пожалуйста, введите команду /start", chatId);
            return;
        }

        String fileName = null;
        try {
            fileName = "newTest_" + chatId + "_" + chatId + ".json";

            try (FileWriter fileWriter = new FileWriter(fileName)) {
                fileWriter.write(jsonData);
            }

            System.out.println("JSON сохранен в файл: " + fileName);

            createTest(chatId, user, fileName);
        } catch (Exception e) {
            System.err.println("Ошибка обработки данных: " + e.getMessage());
            sendMessage("Произошла ошибка при создании теста. Пожалуйста, попробуйте ещё раз...", chatId);
        } finally {
            try {
                File tempFile = new File(fileName);
                if (tempFile.exists()) {
                    boolean hasDeleted = tempFile.delete();
                    if (hasDeleted)
                        System.out.println("Временный файл удален: " + fileName);
                    else throw new RuntimeException("File hasn't deleted");
                }
            } catch (Exception e) {
                System.err.println("Не удалось удалить временный файл: " + e.getMessage());
            }
        }
    }

    public void loadUsers() { // new method load users
        users = DBManager.getUsers();
        if (users == null) {
            System.out.println("An error while getting users: users is null");
            throw new RuntimeException("Users is null");
        }
        System.out.print("Loaded users: ");
    }
    public void sendClasses(long chatId, User user){
        ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
        if (classes == null) {
            sendMessage("Не удалось получить классы пользователя...", chatId);
            return;
        }
        if (classes.isEmpty()) {
            sendMessage("У вас нет классов!", chatId);
            return;
        }

        ArrayList<String> classesStrings = new ArrayList<>();

        for (StudentClass studentClass : classes)
            classesStrings.add(String.format("%s (%d учеников)\n", studentClass.getName(), studentClass.getStudents().size()));

        ArrayList<String> callbacks = new ArrayList<>();

        for (int i = 0; i < classes.size(); i++)
            callbacks.add("class_" + i);

        System.out.println("Callbacks: " + callbacks);

        sendMessage((String.format("Ваши классы (%d): \n", classes.size())), chatId, classesStrings, callbacks);
        user.setState("view_classes");
        if (!saveUser(user)) {
            sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
            return;
        }
    }
}