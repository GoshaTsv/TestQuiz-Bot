package org.example.bot;

import com.google.gson.*;
import org.example.classes.Quiz;
import org.example.classes.StudentClass;
import org.example.classes.User;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;
import org.example.database.DBManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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
import java.util.stream.IntStream;

import static org.example.classes.User.getCorrectAnswerForQuestion;
@Component
public class TelegramBot extends TelegramLongPollingBot {
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
            sendMessage("Вы можете отправлять только сообщения или файлы.", chatId);
            return;
        }

        if (user == null) {
            System.out.println("User is null for " + chatId);
            if (message.hasText() && message.getText().startsWith("/start") && !(message.getText().startsWith("/startquiz")))
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

                user.processMessageStates(this, msg, chatId, users);
                processCommands(update, msg, chatId, user);
            }
            if (update.hasCallbackQuery()) {
                String data = update.getCallbackQuery().getData();
                processCallbackData(data, user, update, chatId);
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

                    String selectedAnswer = callbackData.replace("ans_", "");
                    List<String> keys = new ArrayList<>(quiz.getTest().questions.get(user.getQuizState() - 1).answers.keySet());
                    String correctAnswer = String.valueOf(keys.indexOf(User.getCorrectAnswerForQuestion(currentQuestion))); // removed -1
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

                    sendMessage("Вопрос #" + user.getQuizState() + ": " + question.question, chatId, variants, callbacks, null);
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

                    sendMessage("Вопрос #" + user.getQuizState() + ": " + question.question, chatId);
                    userCurrent.setCorrectAnswer(question.answers.keySet().toArray(new String[0])[0]);
                    userCurrent.setPrevType("ans");

                    if (!saveUser(userCurrent))
                        sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                }).start();
            }
        }
    }

    private void startRegistration(Update update, long chatId) {
        String username = update.getMessage().getFrom().getUserName();

        if (username == null) {
            sendMessage("Задайте username своему аккаунту чтобы продолжить.", chatId);
            return;
        }

        int doesUserExit = DBManager.doesUserExist(username);
        if (doesUserExit == 2) {
            sendMessage("Произошла ошибка во время проверки пользователя на существование, попробуйте ещё...", chatId);
            return;
        }

        if (doesUserExit == 0)
            if (!DBManager.registerAccount(username, chatId)) {
                sendMessage("Произошла ошибка во время регистрации, попробуйте ещё... (/start)", chatId);
                return;
            }

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
        users.add(new User(chatId, "default", 0, 0, -1, null));
    }

    public boolean saveUser(User user) {
        int index = IntStream.range(0, users.size()).filter(i -> users.get(i).getChatId() == user.getChatId()).findFirst().orElse(-1);
        if (index == -1)
            return false;

        users.set(index, user);
        return true;
    }

    public void deleteMessage(Integer deleteMessageId, long chatId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(String.valueOf(chatId));
        deleteMessage.setMessageId(deleteMessageId);

        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            System.out.println("An exception while deleting message: " + e.getMessage());
        }
    }

    public Integer sendMessage(String msg, long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(msg);

        Integer messageId = null;
        try {
            messageId = execute(sendMessage).getMessageId();
        } catch (TelegramApiException e) {
            System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
        }
        return messageId;
    }

    public Integer sendMessage(String msg, long chatId, ArrayList<String> buttons, ArrayList<String> callbacks, Integer editMessageId) {
        System.out.println("Buttons: " + buttons);
        System.out.println("Callbacks: " + callbacks);

        InlineKeyboardMarkup keyboard = getKeyboardMarkup(buttons, callbacks);
        if (editMessageId == null) {
            System.out.println("Edit message id = null");
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setReplyMarkup(keyboard);
            sendMessage.setText(msg);

            Integer messageId = null;
            try {
                messageId = execute(sendMessage).getMessageId();
            } catch (TelegramApiException e) {
                System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
            }
            return messageId;
        } else {
            System.out.println("Edit message id: " + editMessageId);
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId(editMessageId);
            editMessage.setText(msg);
            editMessage.setReplyMarkup(keyboard);

            try {
                execute(editMessage);
            } catch (TelegramApiException e) {
                System.out.println("An exception while editing msg: \" " + msg + "\" to " + chatId);
            }

            return -1;
        }
    }

    private InlineKeyboardMarkup getKeyboardMarkup(ArrayList<String> buttons, ArrayList<String> callbacks) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        AtomicInteger count = new AtomicInteger();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        buttons.forEach(x -> {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(x);
            button.setCallbackData(callbacks.get(count.get()));
            count.getAndIncrement();
            row.add(button);
            rows.add(row);
        });
        keyboard.setKeyboard(rows);

        return keyboard;
    }

    private void createTest(long chatId, User user, String fileName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                json.append(line);

            String response = Test.checkForTest(json.toString());
            System.out.println("Checked test: " + response);
            if (!response.isBlank()) {
                sendMessage(response, chatId);
                return;
            }

            Test test = gson.fromJson(json.toString(), Test.class);
            test.setTestName(test.getTestName().toLowerCase());
            json = new StringBuilder(gson.toJson(test));

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                sendMessage("Не удалось получить тесты, попробуйте ещё раз...", chatId);
                return;
            }

            user.setState("default");
            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                return;
            }

            if (tests.stream().filter(t -> t.getTestName().equalsIgnoreCase(test.getTestName())).findFirst().orElse(null) != null) {
                sendMessage("У вас уже есть такой тест, отправьте другой.", chatId);
                return;
            }

            if (!DBManager.createTest(json.toString(), chatId)) {
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

    public boolean isNameValid(String name) {
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

    private void processCallbackData(String data, User user, Update update, long chatId) {
        try {
            execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        if (data.startsWith("delete_test")) {
            String name = data.replaceAll("delete_test_", "");
            if (!DBManager.deleteTest(chatId, DBManager.getTestContent(chatId, name))) {
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
        if (data.startsWith("delete_student")) {
            System.out.println("Changing class");

            StudentClass chosenClass = user.getCurrentChangingClass();
            if (chosenClass.getStudents().size() <= 2){
                sendMessage("Вы не можете больше удалять учеников, минимум - 2 ученика.", chatId);
                return;
            }
            sendMessage("Пожалуйста, введите имя ученика, которого хотите убрать из класса (без @).", chatId);
            user.setState("deleting_student");
            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя.", chatId);
            }
        }
        else if (data.startsWith("add_student")) {
            sendMessage("Пожалуйста, введите имя ученика, которого хотите добавить в класс (без @).", chatId);
            user.setState("adding_student");
            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя.", chatId);
            }
        }
        else if (data.startsWith("view_class_back"))
            sendClasses(chatId, user);
        else if (data.startsWith("view_classes")) {
            System.out.println("Viewing classes");
            String classId = data.replaceAll("view_classes_", "");
            ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
            assert classes != null;
            StudentClass chosenClass = classes.get(Integer.parseInt(classId));
            try {
                execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            StringBuilder classString = new StringBuilder();
            classString.append(String.format("Название класса: \"%s\"\nУченики: \n", chosenClass.getName()));
            ArrayList<String> userUsernames = DBManager.getUsernamesByIds(chosenClass.getStudents());
            assert userUsernames != null;
            for (String username: userUsernames)
                classString.append("- @").append(username).append("\n");

            System.out.println(userUsernames);
            System.out.println(classString);
            ArrayList<String> options = new ArrayList<>();
            options.add("Удалить ученика");
            options.add("Добавить ученика");
            options.add("Назад");
            options.add("Удалить класс");

            ArrayList<String> callbacks = new ArrayList<>();
            callbacks.add("delete_student");
            callbacks.add("add_student");
            callbacks.add("view_class_back");
            callbacks.add("delete_class");

            sendMessage(classString.toString(), chatId, options, callbacks, user.getCurrentMyClassesMessageId());
            user.setCurrentChangingClass(chosenClass);
            if (!saveUser(user))
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
        }
    }

    private void processCommands(Update update, String msg, long chatId, User user) {
        if (msg.startsWith("/start") && !(msg.startsWith("/startquiz")))
            startRegistration(update, chatId);
        else if (msg.startsWith("/newclass")) {
            if (user.getClassCount() >= 5) {
                sendMessage("Вы больше не можете создавать классы! Лимит - 5 классов", chatId);
                return;
            }
            user.setState("class_name");
            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                return;
            }

            sendMessage("Введите название класса.", chatId);
        } else if (msg.startsWith("/myclasses")) {
            if (user.getCurrentMyClassesMessageId() != null)
                deleteMessage(user.getCurrentMyClassesMessageId(), chatId);
            user.setCurrentMyClassesMessageId(null);
            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                return;
            }

            sendClasses(chatId, user);
        } else if (msg.startsWith("/deleteclass")) {
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
            if (user.getTestsCount() >= 10) {
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
            webAppInfo.setUrl(WEB_APP_URL + "?chat_id=" + chatId);
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

            if (!saveUser(user)) {
                sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId);
                return;
            }

            ArrayList<String> testButtons = new ArrayList<>();

            for (Test test: tests)
                testButtons.add(test.getTestName());

            ArrayList<String> callbacks = new ArrayList<>();

            for (String name: testButtons)
                callbacks.add("delete_test_" + name);

            sendMessage("Выберете тест для удаления.", chatId, testButtons, callbacks, null);
        } else if (msg.startsWith("/startquiz")) {
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

    public void loadUsers() {
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
            callbacks.add("view_classes_" + i);

        Integer messageId = sendMessage((String.format("Ваши классы (%d): \n", classes.size())), chatId, classesStrings, callbacks, user.getCurrentMyClassesMessageId());
        if (messageId == null) {
            sendMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentMyClassesMessageId(messageId);
        if (!saveUser(user))
            sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
    }
    @GetMapping("/health")
    public String index(){
        return "Hello, sufferings!";
    }
    @Configuration
    public class CorsConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*");
        }
    }

   public void handleQuizFromServer(org.example.spring.Message req) throws IOException {

        System.out.println("Got a message: " + req.toString());
        String request = req.getRequest();
        long chatId = Long.parseLong(req.getUserId());
        String jsonData = req.getContent();
        User user = users.stream()
                .filter(x -> x.getChatId() == chatId)
                .findFirst()
                .orElse(null);
        if (user==null){
            sendMessage("Не получилось найти пользователя...", chatId);
            return;
        }
        String fileName = "newTest_" + chatId + "_" + chatId + ".json";
        File writtenFile = new File(fileName);
        try (FileWriter fileWriter = new FileWriter(writtenFile)) {
            fileWriter.write(jsonData);

        System.out.println("JSON сохранен в файл: " + fileName);
        if (request.isEmpty()){
            createTest(chatId, user, writtenFile.getName());
        } else if (request.equals("exportJSON")) {
            InputFile inputFile = new InputFile();
            inputFile.setMedia(writtenFile);
            SendDocument sendDocument = new SendDocument(String.valueOf(chatId), inputFile);
            try {
               execute(sendDocument);
            } catch (TelegramApiException e) {
                System.err.println("An exception while sending document: \" " + inputFile.getMediaName() + "\" to " + chatId);
            }
         }
        }
        finally{
            if (writtenFile.exists()) {
                boolean deleted = writtenFile.delete();
                if (!deleted) {
                    System.err.println("Не удалось удалить файл: " + fileName);
                    writtenFile.deleteOnExit();
                }
            }
        }

    }
}