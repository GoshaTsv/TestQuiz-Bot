package org.example.bot;

import com.google.gson.*;
import org.example.classes.Quiz;
import org.example.classes.StudentClass;
import org.example.classes.User;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;
import org.example.database.DBManager;
import org.example.spring.ButtonDTO;
import org.example.spring.WebRequest;
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

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.example.classes.User.getCorrectAnswerForQuestion;
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String WEB_APP_URL = System.getenv("WEBAPP_URL");
    private WebRequest lastWebReq = new WebRequest();
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
        Integer messageId;
        if (update.hasMessage()) {
            message = update.getMessage();
            chatId = update.getMessage().getChatId();
        } else {
            chatId = update.getCallbackQuery().getFrom().getId();
        }


        User user = users.stream().filter(x -> x.getChatId() == chatId).findFirst().orElse(null);


        if (!message.hasText() && !message.hasDocument() && !update.hasCallbackQuery()) {
            alertMessage("Вы можете отправлять только сообщения или файлы.", chatId, 10000, user);
            return;
        }

        if (user == null) {
            System.out.println("User is null for " + chatId);
            if (message.hasText() && message.getText().startsWith("/start") && !(message.getText().startsWith("/startquiz")))
                startRegistration(update, chatId);
            else
                alertMessage("Напишите /start для регистрации.", chatId, 20000, user);
            return;
        }
        messageId = message.getMessageId();
        if (messageId != null) {
            if (user.isAutoDeleting())
                new Thread(() -> {
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        System.out.println("An exception in auto delete user's messages: " + e.getMessage());
                    }
                    deleteMessage(messageId, chatId);
                }).start();
        }
        if (user.getQuizState() == -1) {
            if (message.hasText()) {
                String msg = "";
                if (message.hasText()) msg = message.getText();
                if (update.hasCallbackQuery()) msg = update.getCallbackQuery().getData();

                if (msg.trim().isEmpty()) {
                    alertMessage("Вы не можете отправлять пустые сообщения.", chatId, 10000, user);
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
                    alertMessage("Пожалуйста, отправьте файл с типом .json.", chatId, 10000, user);
                    return;
                }
                String fileId = update.getMessage().getDocument().getFileId();
                GetFile getFile = new GetFile();
                getFile.setFileId(fileId);

                String fileName;
                try {
                    String filePath = execute(getFile).getFilePath();
                    fileName = "newTest" + chatId + ".json";
                    downloadFile(filePath, new File(fileName));
                } catch (TelegramApiException e) {
                    alertMessage("Произошла ошибка во время добавления теста. Попробуйте ещё раз...", chatId, 10000, user);
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
                alertMessage("Произошла ошибка, попробуйте ещё раз...", chatId, 10000, user);
            }
            Quiz quiz = userCurrent.getCurrentQuiz();
            Test test = quiz.getTest();
            Question currentQuestion = test.questions.get(user.getQuizState() - 1);

            if (!(update.hasMessage() || update.hasCallbackQuery())) {
                alertMessage("Пожалуйста, отправьте ответ на вопрос.", chatId, 10000, user);
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
                    alertMessage("Пожалуйста, ответьте на вопрос словом/словами.", chatId, 10000, user);
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
                    String correctAnswer = String.valueOf(keys.indexOf(User.getCorrectAnswerForQuestion(currentQuestion)));
                    LinkedHashMap<String, Boolean> newUserAnswers = user.getUserAnswers();

                    String userAnswer = keys.get(Integer.parseInt(selectedAnswer));
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
                    alertMessage("Пожалуйста, нажмите на 1 из кнопок.", chatId, 10000, user);
                    return;
                }
            }

            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            if (!saveUser(userCurrent)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
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
                        alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
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
                        alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
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
                        alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                }).start();
            }
        }
    }

    private void startRegistration(Update update, long chatId) {
        String username = update.getMessage().getFrom().getUserName();
        User testUser = new User(chatId, "default", 0, 0, -1, null);

        if (username == null) {
            alertMessage("Задайте username своему аккаунту чтобы продолжить.", chatId, 20000, testUser);
            return;
        }

        int doesUserExit = DBManager.doesUserExist(username);
        if (doesUserExit == 2) {
            alertMessage("Произошла ошибка во время проверки пользователя на существование, попробуйте ещё раз...", chatId, 10000, testUser);
            return;
        }

        if (doesUserExit == 0)
            if (!DBManager.registerAccount(username, chatId)) {
                alertMessage("Произошла ошибка во время регистрации, попробуйте ещё раз... (/start)", chatId, 10000, testUser);
                return;
            }

        sendMessage("""
                Здравствуйте, это бот для тестов, вот все команды бота:
                 - /newclass - создать новый класс (максимум 5 классов)
                 - /myclasses - просмотреть свои классы
                 - /newtest - создать новый тест (максимум 10 тестов)
                 - /mytests - просмотреть свои тесты
                 - /startquiz - провести тестирование
                 - /toggledelete - вкл./выкл. автоудаление сообщений (вкл. по умолчанию)
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

    public void sendTextAsDocument(String content, String fileName, long chatId) {
        try {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            InputFile inputFile = new InputFile(new java.io.ByteArrayInputStream(bytes), fileName);

            sendDocument.setDocument(inputFile);

            execute(sendDocument);
        } catch (TelegramApiException e) {
            System.out.println("An exception while sending document: " + e.getMessage());
        }
    }

    public void alertMessage(String msg, long chatId, long length, User user) {
        Integer messageId = sendMessage(msg, chatId);
        System.out.println(user.getChatId()==chatId);
        System.out.println(chatId);
        if(user.isAutoDeleting()){
            new Thread(() -> {
                System.out.println("Alert thread started");
                try {
                    Thread.sleep(length);
                } catch (InterruptedException e) {
                    System.out.println("An exception in alert thread: " + e.getMessage());
                }
                deleteMessage(messageId, chatId);
                System.out.println("Deleting alert message");
            }).start();
        }
        System.out.println("User: " + user.getChatId() + " isn't auto deleting.");
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
        InlineKeyboardMarkup keyboard = getKeyboardMarkup(buttons, callbacks, chatId);
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

    private InlineKeyboardMarkup getKeyboardMarkup(ArrayList<String> buttons, ArrayList<String> callbacks, long chatId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        AtomicInteger count = new AtomicInteger();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        buttons.forEach(x -> {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(x);
            if (callbacks.get(count.get()).equalsIgnoreCase("change_test")){
                System.out.println("found change_test");
                WebAppInfo webAppInfo = new WebAppInfo();
                webAppInfo.setUrl(WEB_APP_URL + "?chat_id=" + chatId);
                button.setWebApp(webAppInfo);
                User user = users.stream().filter(z -> z.getChatId()==chatId).findFirst().get();
                lastWebReq = new WebRequest(user.getCurrentChangingTest(), new ButtonDTO("change_test", chatId));
                System.out.println(lastWebReq);
                System.out.println(button.getWebApp().toString());
            }
            else{
                button.setCallbackData(callbacks.get(count.get()));
            }
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
                alertMessage("Не удалось получить тесты, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            if (tests.stream().filter(t -> t.getTestName().equalsIgnoreCase(test.getTestName())).findFirst().orElse(null) != null) {
                alertMessage("У вас уже есть такой тест, отправьте другой.", chatId, 10000, user);
                return;
            }

            user.setState("default");
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId, 10000, user);
                return;
            }

            if (!DBManager.createTest(json.toString(), chatId)) {
                alertMessage("Тест не получилось добавить. Попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            user.setTestsCount(user.getTestsCount() + 1);
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId, 10000, user);
                return;
            }

            if (user.getLastMessageId() != null)
                deleteMessage(user.getLastMessageId(), chatId);

            if (user.getCurrentStartQuizClassMessageId() != null)
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);

            if (user.getCurrentMyClassesMessageId() != null)
                deleteMessage(user.getCurrentMyClassesMessageId(), chatId);

            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);

            alertMessage("Тест успешно добавлен!", chatId, 15000, user);
        } catch (IOException e) {
            alertMessage("Произошла ошибка во время добавления теста. Попробуйте ещё раз...", chatId, 10000, user);
        }
    }

    private void processCallbackData(String data, User user, Update update, long chatId) {
        try {
            execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        if (data.startsWith("delete_student")) {
            System.out.println("Changing class");

            StudentClass chosenClass = user.getCurrentChangingClass();
            if (chosenClass.getStudents().size() <= 2){
                alertMessage("Вы не можете больше удалять учеников, минимум - 2 ученика.", chatId, 10000, user);
                return;
            }
            sendMessage("Пожалуйста, введите имя ученика, которого хотите убрать из класса (без @).", chatId);
            user.setState("deleting_student");
            if (!saveUser(user))
                alertMessage("Не удалось обновить состояние пользователя.", chatId, 10000, user);
        }
        else if (data.startsWith("add_student")) {
            sendMessage("Пожалуйста, введите имя ученика, которого хотите добавить в класс (без @).", chatId);
            user.setState("adding_student");
            if (!saveUser(user))
                alertMessage("Не удалось обновить состояние пользователя.", chatId, 10000, user);
        }
        else if (data.startsWith("view_class_back"))
            sendClasses(chatId, user);
        else if (data.startsWith("delete_class")) {
            user.setState("default");

            if (!DBManager.deleteClass(chatId, user.getCurrentChangingClass().getName())) {
                alertMessage("Не удалось удалить класс, попробуйте ещё...", chatId, 10000, user);
                return;
            }
            user.setClassCount(user.getClassCount() - 1);
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId, 10000, user);
                return;
            }

            sendClasses(chatId, user);
        }
        else if (data.startsWith("delete_test")) {
            if (!DBManager.deleteTest(chatId, DBManager.getTestContent(chatId, user.getCurrentChangingTest().getTestName()))) {
                alertMessage("Не удалось удалить тест, попробуйте ещё...", chatId, 10000, user);
                return;
            }
            user.setTestsCount(user.getTestsCount() - 1);
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё...", chatId, 10000, user);
                return;
            }

            sendTests(chatId, user);
        }
        else if (data.startsWith("view_test_back")){
            sendTests(chatId, user);
        } else if (data.startsWith("download_test"))
            sendTextAsDocument(gson.toJson(user.getCurrentChangingTest()), user.getCurrentChangingTest().getTestName() + ".json", chatId);
        else if (data.startsWith("view_classes")) {
            System.out.println("Viewing classes");
            String classId = data.replaceAll("view_classes_", "");
            ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
            if (classes == null) {
                alertMessage("Не удалось получить классы пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            StudentClass chosenClass = classes.get(Integer.parseInt(classId));
            StringBuilder classString = new StringBuilder();
            classString.append(String.format("Название класса: \"%s\"\nУченики: \n", chosenClass.getName()));
            ArrayList<String> userUsernames = DBManager.getUsernamesByIds(chosenClass.getStudents());
            if (userUsernames == null) {
                alertMessage("Не удалось получить пользователей класса, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

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
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
        }
        else if (data.startsWith("view_tests")) {
            System.out.println("Viewing tests");
            String testId = data.replaceAll("view_tests_", "");

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage("Не удалось получить тесты пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            Test chosenTest = tests.get(Integer.parseInt(testId));

            ArrayList<String> options = new ArrayList<>();
            options.add("Изменить тест");
            options.add("Скачать тест");
            options.add("Назад");
            options.add("Удалить тест");

            ArrayList<String> callbacks = new ArrayList<>();
            callbacks.add("change_test");
            callbacks.add("download_test");
            callbacks.add("view_test_back");
            callbacks.add("delete_test");

            sendMessage(String.format("Название теста: \"%s\"", chosenTest.getTestName()), chatId, options, callbacks, user.getCurrentMyTestsMessageId());

            user.setCurrentChangingTest(chosenTest);
            if (!saveUser(user))
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
        }
        else if (data.startsWith("start_quiz_class")) {
            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            if (user.getCurrentStartQuizClassMessageId() != null)
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);

            String classId = data.replaceAll("start_quiz_class_", "");
            ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
            if (classes == null) {
                alertMessage("Не удалось получить классы пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            StudentClass chosenClass = classes.get(Integer.parseInt(classId));

            user.setCurrentStartQuizClass(chosenClass);
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            ArrayList<Test> tests = DBManager.getTests(chatId);

            if (tests == null) {
                alertMessage("Не удалось получить тесты пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            ArrayList<String> testsStrings = new ArrayList<>();
            for (Test test: tests)
                testsStrings.add(test.getTestName());

            ArrayList<String> callbacks = new ArrayList<>();
            for (int i = 0; i < tests.size(); i++)
                callbacks.add("start_quiz_test_" + i);

            Integer messageId = sendMessage("Выберете тест для начала квиза.", chatId, testsStrings, callbacks, user.getCurrentMyClassesMessageId());
            if (messageId == null) {
                alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setCurrentStartQuizTestMessageId(messageId);
            if (!saveUser(user))
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
        }
        else if (data.startsWith("start_quiz_test")) {
            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            String testId = data.replaceAll("start_quiz_test_", "");
            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage("Не удалось получить тесты пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            Test chosenTest = tests.get(Integer.parseInt(testId));

            Integer messageId = sendMessage("Создание квиза...", chatId);

            Thread quizThread = new Thread(() -> {
                synchronized (this) {
                    alertMessage("Квиз успешно создан!", chatId, 30000, user);
                    deleteMessage(messageId, chatId);
                    Quiz quiz = new Quiz(chatId, user.getCurrentStartQuizClass(), chosenTest);
                    quiz.startQuiz(this, users, chatId);
                }
            });
            quizThread.start();
        }
    }

    private void processCommands(Update update, String msg, long chatId, User user) {
        if (msg.startsWith("/start") && !(msg.startsWith("/startquiz")))
            startRegistration(update, chatId);
        else if (msg.startsWith("/newclass")) {
            if (user.getClassCount() >= 5) {
                alertMessage("Вы больше не можете создавать классы! Лимит - 5 классов", chatId, 15000, user);
                return;
            }
            user.setState("class_name");
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            Integer messageId = sendMessage("Введите название нового класса.", chatId);

            if (messageId == null) {
                alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setLastMessageId(messageId);
            if (!saveUser(user))
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
        } else if (msg.startsWith("/myclasses")) {
            if (user.getCurrentMyClassesMessageId() != null)
                deleteMessage(user.getCurrentMyClassesMessageId(), chatId);
            user.setCurrentMyClassesMessageId(null);
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            sendClasses(chatId, user);
        } else if (msg.startsWith("/newtest")) {
            if (user.getTestsCount() >= 10) {
                alertMessage("Вы больше не можете создавать тесты! Лимит - 10 тестов", chatId, 10000, user);
                return;
            }
            user.setState("create_test");
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя.", chatId, 10000, user);
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

            Integer messageId = null;

            try {
                messageId = execute(sendMessage).getMessageId();
            } catch (TelegramApiException e) {
                System.err.println("Ошибка отправки /newtest сообщения: " + e.getMessage());
            }

            if (messageId == null) {
                alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setLastMessageId(messageId);
            if (!saveUser(user))
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
        }

        else if (msg.startsWith("/mytests")) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            user.setCurrentMyTestsMessageId(null);
            if (!saveUser(user)) {
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                return;
            }

            sendTests(chatId, user);
        }
        else if (msg.startsWith("/startquiz")) {
            if (user.getCurrentStartQuizClassMessageId() != null)
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);

            ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
            if (classes == null) {
                alertMessage("Не удалось получить ваши классы, попробуйте ещё...", chatId, 10000, user);
                return;
            }

            if (classes.isEmpty()) {
                alertMessage("У вас ещё нет классов.", chatId, 10000, user);
                return;
            }

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage("Не удалось получить ваши тесты, попробуйте ещё...", chatId, 10000, user);
                return;
            }

            if (tests.isEmpty()) {
                alertMessage("У вас ещё нет тестов.", chatId, 10000, user);
                return;
            }

            ArrayList<String> classesStrings = new ArrayList<>();
            for (StudentClass studentClass: classes)
                classesStrings.add(studentClass.getName());

            ArrayList<String> callbacks = new ArrayList<>();

            for (int i = 0; i < classes.size(); i++)
                callbacks.add("start_quiz_class_" + i);

            Integer classMessageId = sendMessage("Выберете класс для запуска квиза.", chatId, classesStrings, callbacks, null);

            if (classMessageId == null) {
                alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, user);
                return;
            }

            if (classMessageId == -1)
                return;

            user.setCurrentStartQuizClassMessageId(classMessageId);
            if (!saveUser(user))
                alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
        }
        else if (msg.startsWith("/toggledelete")) {
            if (user.isAutoDeleting()) {
                user.setAutoDeleting(false);
                if (!saveUser(user)) {
                    sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                    return;
                }
                alertMessage("Теперь сообщения не будут автоматически исчезать.", chatId, 10000, user);
            }
            else {
                user.setAutoDeleting(true);
                if (!saveUser(user)) {
                    sendMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId);
                    return;
                }
                alertMessage("Теперь сообщения будут автоматически исчезать.", chatId, 10000, user);
            }
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

    public void sendTests(long chatId, User user) {
        ArrayList<Test> tests = DBManager.getTests(chatId);
        if (tests == null) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            alertMessage("Не удалось получить тесты пользователя...", chatId, 10000, user);
            return;
        }
        if (tests.isEmpty()) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            alertMessage("У вас нет тестов!", chatId, 10000, user);
            return;
        }

        ArrayList<String> testsStrings = new ArrayList<>();
        //append(String.format("Ваши тесты (%d): \n", tests.size()));
        for (Test test : tests)
            testsStrings.add(String.format(" - %s (%d вопросов).\n", test.testName, test.questions.size()));

        ArrayList<String> callbacks = new ArrayList<>();

        for (int i = 0; i < tests.size(); i++)
            callbacks.add("view_tests_" + i);

        Integer messageId = sendMessage(String.format("Ваши тесты (%d): \n", tests.size()), chatId, testsStrings, callbacks, user.getCurrentMyTestsMessageId());

        if (messageId == null) {
            alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentMyTestsMessageId(messageId);
        if (!saveUser(user))
            alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
    }

    public void sendClasses(long chatId, User user){
        ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
        if (classes == null) {
            alertMessage("Не удалось получить классы пользователя...", chatId, 10000, user);
            return;
        }
        if (classes.isEmpty()) {
            alertMessage("У вас нет классов!", chatId, 10000, user);
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
            alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentMyClassesMessageId(messageId);
        if (!saveUser(user))
            alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
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
        System.out.println("jsonData: " + jsonData);
        User user = users.stream()
                .filter(x -> x.getChatId() == chatId)
                .findFirst()
                .orElse(null);
        if (user == null){
            alertMessage("Не получилось найти пользователя...", chatId, 10000, user);
            return;
        }
        String fileName = "newTest_" + chatId + "_" + chatId + ".json";
        File writtenFile = new File(fileName);
        String cleanJson = "";
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
            cleanJson = new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject);
        } catch (Exception e) {
            cleanJson = jsonData;
        }

       System.out.println("Clean JSON to write: " + cleanJson);
        try (FileWriter fileWriter = new FileWriter(writtenFile)) {
            fileWriter.write(cleanJson);
            fileWriter.flush();
            fileWriter.close();
            BufferedReader br = new BufferedReader(new FileReader(writtenFile));
            String line;
            System.out.println("reading file:");
            while ((line = br.readLine()) != null){
                System.out.println(line);
            }
            br.close();
            System.out.println("finished reading the file.");
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
    public WebRequest getLastWebReq() {
        return lastWebReq;
    }
}