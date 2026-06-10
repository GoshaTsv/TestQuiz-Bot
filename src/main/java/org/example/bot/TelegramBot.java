package org.example.bot;
// 8032286461 superwarden0
import com.google.gson.*;
import org.example.classes.Quiz;
import org.example.classes.StudentClass;
import org.example.classes.User;
import org.example.classes.appLinking.Image;
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
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.example.classes.User.getCorrectAnswerForQuestion;
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String WEB_APP_URL = System.getenv("WEBAPP_URL");
    private List<User> users = Collections.synchronizedList(new ArrayList<>());
    private Translator translator;
    private final int MILLIS_IN_SECONDS = 1000;

    public List<User> getUsers() {
        return users;
    }

    @Override
    public String getBotUsername() {
        return "TestQuizBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    public TelegramBot(Translator translator) {
        this.translator = translator;
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
            alertMessage(translator.getTranslatedText("Вы можете отправлять только сообщения или файлы.", user != null ? user.getLang() : "ru"), chatId, 10000, user);
            return;
        }

        if (user == null) {
            User testUser = new User(chatId, "default", "ru", 0, 0, -1, null);
            System.out.println("User is null for " + chatId);
            if (message.hasText() && message.getText().startsWith("/start") && !(message.getText().startsWith("/startquiz")))
                startRegistration(update, chatId);
            else
                alertMessage(translator.getTranslatedText("Напишите /start для регистрации.", "ru"), chatId, 20000, testUser);
            return;
        }
        messageId = message.getMessageId();
        if (messageId != null) {
            if (user.getAutoDeleting().equalsIgnoreCase("autoDeleteUser") || user.getAutoDeleting().equalsIgnoreCase("autoDeleteOn"))
                new Thread(() -> {
                    try {
                        Thread.sleep((long) user.getAutoDeleteLength() * MILLIS_IN_SECONDS);
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
                    alertMessage(translator.getTranslatedText("Вы не можете отправлять пустые сообщения.", user.getLang()), chatId, 10000, user);
                    return;
                }

                user.processMessageStates(this, msg, chatId, (ArrayList<User>) users);
                processCommands(update, msg, chatId, user);
            }
            if (update.hasCallbackQuery()) {
                String data = update.getCallbackQuery().getData();
                processCallbackData(data, user, update, chatId);
            }
            if (message.hasDocument()) {
                if (!(update.getMessage().getDocument().getFileName().endsWith(".json"))) {
                    alertMessage(translator.getTranslatedText("Пожалуйста, отправьте файл с типом .json.", user.getLang()), chatId, 10000, user);
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
                    alertMessage(translator.getTranslatedText("Произошла ошибка во время добавления теста. Попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
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

            if (user.getCurrentQuiz() == null) {
                alertMessage(translator.getTranslatedText("Произошла ошибка, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }
            Quiz quiz = user.getCurrentQuiz();
            Test test = quiz.getTest();
            Question currentQuestion = test.getQuestions().get(user.getQuizState() - 1);

            if (!(update.hasMessage() || update.hasCallbackQuery())) {
                alertMessage(translator.getTranslatedText("Пожалуйста, отправьте ответ на вопрос.", user.getLang()), chatId, 10000, user);
                return;
            }
            if (user.getPrevType().equalsIgnoreCase("ans")) {
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
                    alertMessage(translator.getTranslatedText("Пожалуйста, ответьте на вопрос словом/словами.", user.getLang()), chatId, 10000, user);
                    return;
                }
            }
            if (user.getPrevType().equalsIgnoreCase("var")) {
                if (update.hasCallbackQuery()) {
                    String callbackData = update.getCallbackQuery().getData();
                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(update.getCallbackQuery().getId());

                    String selectedAnswer = callbackData.replace("ans_", "");
                    List<String> keys = new ArrayList<>(quiz.getTest().getQuestions().get(user.getQuizState() - 1).getAnswers().keySet());
                    String correctAnswer = String.valueOf(keys.indexOf(getCorrectAnswerForQuestion(currentQuestion)));
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
                    alertMessage(translator.getTranslatedText("Пожалуйста, нажмите на 1 из кнопок.", user.getLang()), chatId, 10000, user);
                    return;
                }
            }

            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            user.setQuizState(user.getQuizState() + 1);

            if (messageId != null)
                deleteMessage(messageId, chatId);

            if (user.getQuizState() > quiz.getTest().getQuestions().size()) {
                System.out.println("got to start of the thread!");
                if (user.getCurrentQuizMessageId() != null) {
                    deleteMessage(user.getCurrentQuizMessageId(), chatId);
                    user.setCurrentQuizMessageId(null);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя.", user.getLang()), chatId, 10000, user);
                }
                if (user.getCurrentQuizPhotoId() != null){
                    deleteMessage(user.getCurrentQuizPhotoId(), chatId);
                    user.setCurrentQuizPhotoId(null);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя.", user.getLang()), chatId, 10000, user);
                }
                Thread thread = new Thread(() -> {
                    System.out.println("thread launched!");
                    sendMessage(translator.getTranslatedText("Поздравляем! Вы правильно ответили на %d из %d вопросов! Надеемся, вам понравилось!", user.getLang(), user.getCorrectAnswers(), quiz.getTest().getQuestions().size()), chatId);
                    sendMessage(
                            translator.getTranslatedText(
                                    "Здравствуйте. @%s закончил ваш тест \"%s\" и правильно ответил на %d из %d вопросов. \nОтветы ученика:",
                                    user.getLang(),
                                    userName,
                                    quiz.getTest().getTestName(),
                                    user.getCorrectAnswers(),
                                    quiz.getTest().getQuestions().size()
                            ),
                            quiz.getTeacherId()
                    );

                    List<String> userAnswers = new ArrayList<>(user.getUserAnswers().keySet());

                    System.out.println("User answers: " + userAnswers);

                    for (int i = 0; i < user.getUserAnswers().size() && i < quiz.getTest().getQuestions().size(); i++) {
                        String question = test.getQuestions().get(i).getQuestion();
                        String userAnswer = userAnswers.get(i).split("\uD80C\uDE78")[1];
                        String correctAnswer = getCorrectAnswerForQuestion(quiz.getTest().getQuestions().get(i));

                        System.out.println("Question: " + question);
                        System.out.println("User answer: " + userAnswer);
                        System.out.println("Correct answer: " + correctAnswer);
                        sendMessage(translator.getTranslatedText("Вопрос #%d: \"%s\"\nОтвет вашего ученика (@%s): \"%s\"\nПравильный ответ: \"%s\"", user.getLang(), i + 1, question, userName, userAnswer, correctAnswer), quiz.getTeacherId());

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    user.setQuizState(-1);
                    user.setCurrentQuiz(null);
                    user.setCorrectAnswers(0);
                    user.setUserAnswers(new LinkedHashMap<>());
                    user.setCurrentQuizPhotoId(null);

                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                });
                thread.start();
                return;
            }

            Question question = quiz.getTest().getQuestions().get(user.getQuizState() - 1);

            if (question.getType().equalsIgnoreCase("var")) {
                new Thread(() -> {
                    ArrayList<String> variants = new ArrayList<>(question.getAnswers().keySet());

                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    ArrayList<String> callbacks = new ArrayList<>();

                    for (int i = 0; i < variants.size(); i++)
                        callbacks.add("ans_" + i);
                    System.out.println(user.getCurrentQuizPhotoId());
                    Integer quizMessageId = sendMessagePhoto(translator.getTranslatedText("Вопрос #%d: %s", user.getLang(), user.getQuizState(), question.getQuestion()), chatId, question.getImage(), variants, callbacks, user.getCurrentQuizMessageId());
                    user.setPrevType("var");


                    if (!saveUser(user)) {
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == null) {
                        alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == -1)
                        return;

                    user.setCurrentQuizMessageId(quizMessageId);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                }).start();
            } else {
                new Thread(() -> {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        sendMessage(translator.getTranslatedText("Не удалось отправить вопрос.", user.getLang()), chatId);
                    }
                    System.out.println(user.getCurrentQuizPhotoId());
                    Integer quizMessageId = sendMessagePhoto(translator.getTranslatedText("Вопрос #%d: %s", user.getLang(), user.getQuizState(), question.getQuestion()), chatId, question.getImage(), user.getCurrentQuizMessageId());
                    user.setPrevType("ans");

                    if (!saveUser(user)) {
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == null) {
                        alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == -1)
                        return;

                    user.setCurrentQuizMessageId(quizMessageId);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                }).start();
            }
        }
    }

    private void startRegistration(Update update, long chatId) {
        String username = update.getMessage().getFrom().getUserName();
        User testUser = new User(chatId, "default", "ru", 0, 0, -1, null);

        if (username == null) {
            alertMessage(translator.getTranslatedText("Задайте username своему аккаунту чтобы продолжить.", "ru"), chatId, 20000, testUser);
            return;
        }

        int doesUserExit = DBManager.doesUserExist(username);
        if (doesUserExit == 2) {
            alertMessage(translator.getTranslatedText("Произошла ошибка во время проверки пользователя на существование, попробуйте ещё раз...", "ru"), chatId, 10000, testUser);
            return;
        }

        if (doesUserExit == 0)
            if (!DBManager.registerAccount(username, chatId)) {
                alertMessage(translator.getTranslatedText("Произошла ошибка во время регистрации, попробуйте ещё раз... (/start)", "ru"), chatId, 10000, testUser);
                return;
            }

        sendMessage("""
                Здравствуйте, это бот для тестов, вот все команды бота:
                 - /newclass - создать новый класс (максимум 5 классов)
                 - /myclasses - просмотреть свои классы
                 - /newtest - создать новый тест (максимум 10 тестов)
                 - /mytests - просмотреть свои тесты
                 - /startquiz - провести тестирование
                 - /setautodelete - настроить автоудаление сообщений
                """, chatId);
        users.add(new User(chatId, "default", "ru", 0, 0, -1, null));
    }

    public boolean saveUser(User user) {
        int index = IntStream.range(0, users.size()).filter(i -> users.get(i).getChatId() == user.getChatId()).findFirst().orElse(-1);
        if (index == -1)
            return false;

        users.set(index, user);
        return true;
    }

    public void deleteMessage(Integer deleteMessageId, long chatId) {
        ForwardMessage forward = new ForwardMessage();
        forward.setChatId(String.valueOf(chatId));
        forward.setFromChatId(String.valueOf(chatId));
        forward.setMessageId(deleteMessageId);
        try {
            DeleteMessage deleteForwarded = new DeleteMessage();
            deleteForwarded.setChatId(String.valueOf(chatId));
            deleteForwarded.setMessageId(forward.getMessageId());
            execute(deleteForwarded);
        } catch (TelegramApiException e) {
            System.out.println("An exception while deleting message: " + e.getMessage());
            return;
        }
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
            InputFile inputFile = new InputFile(new ByteArrayInputStream(bytes), fileName);

            sendDocument.setDocument(inputFile);
            execute(sendDocument);
        } catch (TelegramApiException e) {
            System.out.println("An exception while sending document: " + e.getMessage());
        }
    }

    public void alertMessage(String msg, long chatId, long length, User user) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(msg);
        Integer messageId;
        try {
            messageId = execute(sendMessage).getMessageId();
        }
        catch (TelegramApiException e){
            System.out.println("An exception in alert: " + e.getMessage());
            return;
        }
        System.out.println(user.getChatId() == chatId);
        System.out.println(chatId);
        if(user.getAutoDeleting().equalsIgnoreCase("autoDeleteOn")){
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
        AtomicReference<Integer> messageId = new AtomicReference<>();
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(msg);
        try {
            messageId.set(execute(sendMessage).getMessageId());
        } catch (TelegramApiException e) {
            System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
        }
        return messageId.get();
    }

    public Integer sendMessage(String msg, long chatId, Integer editMessageId) {
        if (editMessageId == null) {
            Integer messageId = null;
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(msg);
            try {
                messageId = execute(sendMessage).getMessageId();
            } catch (TelegramApiException e) {
                System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
            }
            return messageId;
        }
        else {
            System.out.println("Edit message id: " + editMessageId);
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId(editMessageId);
            editMessage.setText(msg);

            try {
                execute(editMessage);
            } catch (TelegramApiException e) {
                System.out.println("An exception while editing msg: \" " + msg + "\" to " + chatId);
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(String.valueOf(chatId));
                sendMessage.setText(msg);

                Integer messageId = null;
                try {
                    messageId = execute(sendMessage).getMessageId();
                } catch (TelegramApiException e1) {
                    System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
                }
                return messageId;
            }

            return -1;
        }
    }

    public Integer sendMessage(String msg, long chatId, ArrayList<String> buttons, ArrayList<String> callbacks, Integer editMessageId) {
        System.out.println("Buttons: " + buttons);
        System.out.println("Callbacks: " + callbacks);
        InlineKeyboardMarkup keyboard = getKeyboardMarkup(buttons, callbacks, chatId);
        User user = users.stream().filter(x -> x.getChatId() == chatId).findFirst().orElse(null);
        if (user == null){
            alertMessage(translator.getTranslatedText("Не получилось найти пользователя...", "ru"), chatId, 10000, new User(chatId, "default", "ru", 0, 0, -1, null));
            return null;
        }
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
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(String.valueOf(chatId));
                sendMessage.setReplyMarkup(keyboard);
                sendMessage.setText(msg);

                Integer messageId = null;
                try {
                    messageId = execute(sendMessage).getMessageId();
                } catch (TelegramApiException e1) {
                    System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
                }
                return messageId;
            }

            return -1;
        }
    }

    public Integer sendMessagePhoto(String msg, long chatId, Image image, Integer editPhotoMessageId) {
        if (image == null) {
            if (editPhotoMessageId != null && editPhotoMessageId != -1)
                deleteMessage(editPhotoMessageId, chatId);

            Integer messageId = null;
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(msg);
            try {
                messageId = execute(sendMessage).getMessageId();
            } catch (TelegramApiException e) {
                System.out.println("An exception while sending msg: \" " + msg + "\" to " + chatId);
            }
            return messageId;
        }

        if (editPhotoMessageId != null && editPhotoMessageId != -1)
            deleteMessage(editPhotoMessageId, chatId);

        InputFile file = getInputFileFromImage(image, chatId);

        System.out.println("Message id = null");
        SendPhoto sendPhotoRequest = SendPhoto.builder()
                .chatId(chatId)
                .caption(msg)
                .photo(file)
                .build();

        try {
            return execute(sendPhotoRequest).getMessageId();
        } catch (TelegramApiException e) {
            sendMessage(translator.getTranslatedText("Произошла ошибка, попробуйте ещё раз...", "ru"), chatId);
            return -1;
        }
    }

    public Integer sendMessagePhoto(String msg, long chatId, Image image, ArrayList<String> buttons, ArrayList<String> callbacks, Integer editPhotoMessageId) {
        System.out.println("Buttons: " + buttons);
        System.out.println("Callbacks: " + callbacks);

        InlineKeyboardMarkup keyboard = getKeyboardMarkup(buttons, callbacks, chatId);
        if (image == null) {
            if (editPhotoMessageId != null && editPhotoMessageId != -1)
                deleteMessage(editPhotoMessageId, chatId);

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
        }

        if (editPhotoMessageId != null && editPhotoMessageId != -1)
            deleteMessage(editPhotoMessageId, chatId);

        InputFile file = getInputFileFromImage(image, chatId);

        SendPhoto sendPhotoRequest = SendPhoto.builder()
                .chatId(chatId)
                .caption(msg)
                .photo(file)
                .replyMarkup(keyboard)
                .build();
        try {
            return execute(sendPhotoRequest).getMessageId();
        } catch (TelegramApiException e) {
            sendMessage(translator.getTranslatedText("Произошла ошибка, попробуйте ещё раз...", "ru"), chatId);
            return -1;
        }
    }

    public Integer sendMessagePhoto(String msg, long chatId, Image image) {
        return sendMessagePhoto(msg, chatId, image, null);
    }

    public InputFile getInputFileFromImage(Image image, long chatId) {
        String[] base64String = image.getDataURL().split(",");
        StringBuilder pureBase64 = new StringBuilder();
        for(String base64: base64String){
            if (base64.equalsIgnoreCase(base64String[0])){
                continue;
            }
            pureBase64.append(base64);
        }
        String realBase64 = pureBase64.toString();
        byte[] imageBytes = Base64.getDecoder().decode(realBase64);

        return new InputFile(
                new ByteArrayInputStream(imageBytes),
                "image" + chatId + "_" + System.currentTimeMillis() + ".png"
        );
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
                webAppInfo.setUrl(WEB_APP_URL + "?chat_id=" + chatId + "&method=changeTest");
                button.setWebApp(webAppInfo);
                User user = users.stream().filter(z -> z.getChatId()==chatId).findFirst().orElse(null);
                if (user == null){
                    alertMessage(translator.getTranslatedText("Не получилось найти пользователя...", "ru"), chatId, 10000, null);
                    return;
                }

                Test currentTest = user.getCurrentChangingTest();
                if (currentTest == null) {
                    alertMessage(translator.getTranslatedText("Не удалось получить текущий тест, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                    sendClasses(chatId, user);
                    return;
                }

                user.setLastWebReq(new WebRequest(currentTest, gson.toJson(currentTest), new ButtonDTO("change_test", chatId)));
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
                alertMessage(translator.getTranslatedText("Не удалось получить тесты, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            if (tests.stream().filter(t -> t.getTestName().equalsIgnoreCase(test.getTestName())).findFirst().orElse(null) != null) {
                alertMessage(translator.getTranslatedText("У вас уже есть такой тест, отправьте другой.", user.getLang()), chatId, 10000, user);
                return;
            }

            user.setState("default");
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }

            if (!DBManager.createTest(json.toString(), chatId)) {
                alertMessage(translator.getTranslatedText("Тест не получилось добавить. Попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            user.setTestsCount(user.getTestsCount() + 1);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }

            if (user.getLastMessageId() != null) {
                deleteMessage(user.getLastMessageId(), chatId);
                user.setLastMessageId(null);
                if (!saveUser(user)) {
                    alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                    return;
                }
            }

            if (user.getCurrentStartQuizClassMessageId() != null) {
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);
                user.setCurrentStartQuizClass(null);
                if (!saveUser(user)) {
                    alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                    return;
                }
            }

            if (user.getCurrentMyClassesMessageId() != null) {
                deleteMessage(user.getCurrentMyClassesMessageId(), chatId);
                user.setCurrentMyClassesMessageId(null);
                if (!saveUser(user)) {
                    alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                    return;
                }
            }

            if (user.getCurrentStartQuizTestMessageId() != null) {
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);
                user.setCurrentStartQuizTestMessageId(null);
                if (!saveUser(user)) {
                    alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                    return;
                }
            }

            if (user.getCurrentMyTestsMessageId() != null) {
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
                user.setCurrentMyTestsMessageId(null);
                if (!saveUser(user)) {
                    alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                    return;
                }
            }

            alertMessage(translator.getTranslatedText("Тест успешно сохранён!", user.getLang()), chatId, 15000, user);
        } catch (IOException e) {
            alertMessage(translator.getTranslatedText("Произошла ошибка во время добавления теста. Попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
        }
    }

    private void processCallbackData(String data, User user, Update update, long chatId) {
        try {
            execute(new AnswerCallbackQuery(update.getCallbackQuery().getId()));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        if (data.startsWith("delete_student")) {
            System.out.println("Deleting student class");

            StudentClass chosenClass = user.getCurrentChangingClass();
            if (chosenClass == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить текущий класс, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                sendClasses(chatId, user);
                return;
            }

            if (chosenClass.getStudents().size() <= 2){
                alertMessage(translator.getTranslatedText("Вы не можете больше удалять учеников, минимум - 2 ученика.", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<String> studentsNames = DBManager.getUsernamesByIds(chosenClass.getStudents());
            if (studentsNames == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить учеников класса, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<String> callbacks = new ArrayList<>();
            for (int i = 0; i < chosenClass.getStudents().size(); i++)
                callbacks.add("real_delete_student_" + i);

            sendMessage(translator.getTranslatedText("Выберите ученика, которого хотите убрать из класса.", user.getLang()), chatId, studentsNames, callbacks, user.getCurrentMyClassesMessageId());
        }
        else if (data.startsWith("real_delete_student")) {
            String deleteStudentId = data.replaceAll("real_delete_student_", "");
            StudentClass chosenClass = user.getCurrentChangingClass();

            ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
            newSetOfStudents.remove(newSetOfStudents.get(Integer.parseInt(deleteStudentId)));

            DBManager.deleteClass(chatId, chosenClass.getName());
            DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);

            user.setCurrentChangingClass(null);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);

            alertMessage(translator.getTranslatedText("Пользователь успешно удалён!", user.getLang()), chatId, 10000, user);
            sendClasses(chatId, user);
        }
        else if (data.startsWith("add_student")) {
            sendMessage(translator.getTranslatedText("Пожалуйста, введите имя ученика, которого хотите добавить в класс (без @), или введите /exit для отмены.", user.getLang()), chatId);
            user.setState("adding_student");
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя.", user.getLang()), chatId, 10000, user);
        }
        else if (data.startsWith("view_class_back"))
            sendClasses(chatId, user);
        else if (data.startsWith("delete_class")) {
            StudentClass currentClass = user.getCurrentChangingClass();
            if (currentClass == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить текущий класс, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                sendClasses(chatId, user);
                return;
            }

            if (!DBManager.deleteClass(chatId, currentClass.getName())) {
                alertMessage(translator.getTranslatedText("Не удалось удалить класс, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }
            user.setClassCount(user.getClassCount() - 1);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }

            sendClasses(chatId, user);
        }
        else if (data.startsWith("delete_test")) {
            Test currentTest = user.getCurrentChangingTest();
            if (currentTest == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить текущий тест, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                sendTests(chatId, user);
                return;
            }

            if (!DBManager.deleteTest(chatId, DBManager.getTestContent(chatId, currentTest.getTestName()))) {
                alertMessage(translator.getTranslatedText("Не удалось удалить тест, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }
            user.setTestsCount(user.getTestsCount() - 1);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }

            sendTests(chatId, user);
        }
        else if (data.startsWith("view_test_back")){
            sendTests(chatId, user);
        } else if (data.startsWith("download_test")) {
            Test currentTest = user.getCurrentChangingTest();
            if (currentTest == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить текущий тест, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                sendClasses(chatId, user);
                return;
            }

            sendTextAsDocument(gson.toJson(currentTest), currentTest.getTestName() + ".json", chatId);
        }
        else if (data.startsWith("view_classes")) {
            System.out.println("Viewing classes");
            String classId = data.replaceAll("view_classes_", "");
            viewClass(classId, chatId, user);
        }
        else if (data.startsWith("view_tests")) {
            System.out.println("Viewing tests");
            String testId = data.replaceAll("view_tests_", "");

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить тесты пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            Test chosenTest = tests.get(Integer.parseInt(testId));
            user.setCurrentChangingTest(chosenTest);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
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
        }
        else if (data.startsWith("start_quiz_class")) {
            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            if (user.getCurrentStartQuizClassMessageId() != null)
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);

            String classId = data.replaceAll("start_quiz_class_", "");
            ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
            if (classes == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить классы пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            StudentClass chosenClass = classes.get(Integer.parseInt(classId));

            user.setCurrentStartQuizClass(chosenClass);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<Test> tests = DBManager.getTests(chatId);

            if (tests == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить тесты пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<String> testsStrings = new ArrayList<>();
            for (Test test: tests)
                testsStrings.add(test.getTestName());

            ArrayList<String> callbacks = new ArrayList<>();
            for (int i = 0; i < tests.size(); i++)
                callbacks.add("start_quiz_test_" + i);

            Integer messageId = sendMessage(translator.getTranslatedText("Выберите тест для начала квиза.", user.getLang()), chatId, testsStrings, callbacks, user.getCurrentStartQuizTestMessageId());
            if (messageId == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setCurrentStartQuizTestMessageId(messageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
        }
        else if (data.startsWith("start_quiz_test")) {
            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            String testId = data.replaceAll("start_quiz_test_", "");
            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить тесты пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            Test chosenTest = tests.get(Integer.parseInt(testId));

            Integer messageId = sendMessage(translator.getTranslatedText("Создание квиза...", user.getLang()), chatId);

            Thread quizThread = new Thread(() -> {
                synchronized (this) {
                    alertMessage(translator.getTranslatedText("Квиз успешно создан!", user.getLang()), chatId, 30000, user);
                    deleteMessage(messageId, chatId);
                    Quiz quiz = new Quiz(chatId, user.getCurrentStartQuizClass(), chosenTest);
                    quiz.startQuiz(this, (ArrayList<User>) users, chatId);
                }
            });
            quizThread.start();
        }
        else if(data.startsWith("changeAutoDelete")){
            switch(data){
                case "changeAutoDelete" -> {
                    ArrayList<String> options = new ArrayList<>();
                    options.add("Включить везде");
                    options.add("Включить только для сообщений пользователя");
                    options.add("Выключить");
                    ArrayList<String> callbacks = new ArrayList<>();
                    callbacks.add("autoDeleteOn");
                    callbacks.add("autoDeleteUser");
                    callbacks.add("autoDeleteOff");
                    sendMessage(translator.getTranslatedText("Пожалуйста, выберите способ автоудаления сообщений.", user.getLang()), chatId, options, callbacks, user.getAutoDeleteSetMessageId());
                }
                case "changeAutoDeleteDelay" ->{
                    System.out.println("changeAutoDeleteDelay");
                    Integer messageId = sendMessage(translator.getTranslatedText("Пожалуйста, введите количество секунд, перед тем как сообщение будет удаляться, или /exit для отмены..", user.getLang()), chatId);
                    if (messageId == null) {
                        alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (messageId == -1)
                        return;

                    user.setCurrentAutoDeleteSetSecondsMessageId(messageId);
                    user.setState("changingAutoDelay");
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                }
            }
        }
        else if(data.startsWith("autoDelete")){
            switch(data){
                case "autoDeleteOn" -> user.setAutoDeleting("autoDeleteOn");
                case "autoDeleteUser" -> user.setAutoDeleting("autoDeleteUser");
                case "autoDeleteOff" -> user.setAutoDeleting("autoDeleteOff");
                default -> {
                    alertMessage(translator.getTranslatedText("Пожалуйста, нажмите на кнопку.", user.getLang()), chatId, 5000, user);
                    return;
                }
            }
            alertMessage(translator.getTranslatedText("Поведение автоудаления успешно изменено!", user.getLang()), chatId, 15000, user);
            sendAutoDeleteSettings(chatId, user);
        }
    }

    private void processCommands(Update update, String msg, long chatId, User user) {
        if (msg.startsWith("/start") && !(msg.startsWith("/startquiz")))
            startRegistration(update, chatId);
        else if (msg.startsWith("/newclass")) {
            if (user.getClassCount() >= 5) {
                alertMessage(translator.getTranslatedText("Вы больше не можете создавать классы! Лимит - 5 классов", user.getLang()), chatId, 15000, user);
                return;
            }
            user.setState("class_name");
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            Integer messageId = sendMessage(translator.getTranslatedText("Введите название нового класса. (/exit для отмены)", user.getLang()), chatId);

            if (messageId == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setLastMessageId(messageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
        } else if (msg.startsWith("/myclasses")) {
            if (user.getCurrentMyClassesMessageId() != null)
                deleteMessage(user.getCurrentMyClassesMessageId(), chatId);
            user.setCurrentMyClassesMessageId(null);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            sendClasses(chatId, user);
        } else if (msg.startsWith("/newtest")) {
            if (user.getTestsCount() >= 10) {
                alertMessage(translator.getTranslatedText("Вы больше не можете создавать тесты! Лимит - 10 тестов", user.getLang()), chatId, 10000, user);
                return;
            }
            user.setState("create_test");
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя.", user.getLang()), chatId, 10000, user);
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
            webAppInfo.setUrl(WEB_APP_URL + "?chat_id=" + chatId + "&method=newTest");
            webAppButton.setWebApp(webAppInfo);

            KeyboardRow row =
                    new KeyboardRow();
            row.add(webAppButton);

            keyboard.setKeyboard(List.of(row));
            sendMessage.setReplyMarkup(keyboard);

            Integer messageId = null;

            try {
                messageId = execute(sendMessage).getMessageId();
            } catch (TelegramApiException e) {
                System.err.println("Ошибка отправки /newtest сообщения: " + e.getMessage());
            }

            if (messageId == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setLastMessageId(messageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
        }

        else if (msg.startsWith("/mytests")) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            user.setCurrentMyTestsMessageId(null);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            sendTests(chatId, user);
        }
        else if (msg.startsWith("/startquiz")) {
            if (user.getCurrentStartQuizClassMessageId() != null)
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);

            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
            if (classes == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить ваши классы, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }

            if (classes.isEmpty()) {
                alertMessage(translator.getTranslatedText("У вас ещё нет классов.", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить ваши тесты, попробуйте ещё...", user.getLang()), chatId, 10000, user);
                return;
            }

            if (tests.isEmpty()) {
                alertMessage(translator.getTranslatedText("У вас ещё нет тестов.", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<String> classesStrings = new ArrayList<>();
            for (StudentClass studentClass: classes)
                classesStrings.add(studentClass.getName());

            ArrayList<String> callbacks = new ArrayList<>();

            for (int i = 0; i < classes.size(); i++)
                callbacks.add("start_quiz_class_" + i);

            Integer classMessageId = sendMessage(translator.getTranslatedText("Выберите класс для запуска квиза.", user.getLang()), chatId, classesStrings, callbacks, user.getCurrentStartQuizClassMessageId());

            if (classMessageId == null) {
                alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
                return;
            }

            if (classMessageId == -1)
                return;

            user.setCurrentStartQuizClassMessageId(classMessageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
        }
        else if (msg.startsWith("/setautodelete")) {
            if (user.getAutoDeleteSetMessageId() != null)
                deleteMessage(user.getAutoDeleteSetMessageId(), chatId);
            user.setAutoDeleteSetMessageId(null);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
                return;
            }

            sendAutoDeleteSettings(chatId, user);
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

    public void viewClass(String classId, long chatId, User user) {
        ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
        if (classes == null) {
            alertMessage(translator.getTranslatedText("Не удалось получить классы пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
            return;
        }

        StudentClass chosenClass = classes.get(Integer.parseInt(classId));
        StringBuilder classString = new StringBuilder();
        classString.append(String.format("Название класса: \"%s\"\nУченики: \n", chosenClass.getName()));
        ArrayList<String> userUsernames = DBManager.getUsernamesByIds(chosenClass.getStudents());
        if (userUsernames == null) {
            alertMessage(translator.getTranslatedText("Не удалось получить пользователей класса, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
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

        user.setCurrentChangingClass(chosenClass);
        if (!saveUser(user)) {
            alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
            return;
        }

        sendMessage(classString.toString(), chatId, options, callbacks, user.getCurrentMyClassesMessageId());
    }

    public void sendAutoDeleteSettings(long chatId, User user) {
        ArrayList<String> options = new ArrayList<>();
        options.add("Настроить поведение");
        options.add("Настроить время");
        ArrayList<String> callbacks = new ArrayList<>();
        callbacks.add("changeAutoDelete");
        callbacks.add("changeAutoDeleteDelay");
//
        Integer messageId = sendMessage(translator.getTranslatedText("Пожалуйста, выберите способ автоудаления сообщений.", user.getLang()), chatId, options, callbacks, user.getAutoDeleteSetMessageId());
        if (messageId == null) {
            alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setAutoDeleteSetMessageId(messageId);
        if (!saveUser(user))
            alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
    }

    public void sendTests(long chatId, User user) {
        ArrayList<Test> tests = DBManager.getTests(chatId);
        if (tests == null) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            alertMessage(translator.getTranslatedText("Не удалось получить тесты пользователя...", user.getLang()), chatId, 10000, user);
            return;
        }
        if (tests.isEmpty()) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            alertMessage(translator.getTranslatedText("У вас нет тестов!", user.getLang()), chatId, 10000, user);
            return;
        }

        ArrayList<String> testsStrings = new ArrayList<>();
        for (Test test : tests)
            testsStrings.add(String.format(" - %s (%d вопросов).\n", test.getTestName(), test.getQuestions().size()));

        ArrayList<String> callbacks = new ArrayList<>();

        for (int i = 0; i < tests.size(); i++)
            callbacks.add("view_tests_" + i);

        user.setCurrentChangingTest(null);
        if (!saveUser(user)) {
            alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
            return;
        }

        Integer messageId = sendMessage(String.format("Ваши тесты (%d): \n", tests.size()), chatId, testsStrings, callbacks, user.getCurrentMyTestsMessageId());

        if (messageId == null) {
            alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentMyTestsMessageId(messageId);
        if (!saveUser(user))
            alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
    }

    public void sendClasses(long chatId, User user){
        ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
        if (classes == null) {
            alertMessage(translator.getTranslatedText("Не удалось получить классы пользователя...", user.getLang()), chatId, 10000, user);
            return;
        }
        if (classes.isEmpty()) {
            alertMessage(translator.getTranslatedText("У вас нет классов!", user.getLang()), chatId, 10000, user);
            return;
        }

        ArrayList<String> classesStrings = new ArrayList<>();

        for (StudentClass studentClass : classes)
            classesStrings.add(String.format("%s (%d учеников)\n", studentClass.getName(), studentClass.getStudents().size()));

        ArrayList<String> callbacks = new ArrayList<>();

        for (int i = 0; i < classes.size(); i++)
            callbacks.add("view_classes_" + i);

        user.setCurrentStartQuizClass(null);
        if (!saveUser(user)) {
            alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
            return;
        }

        Integer messageId = sendMessage((String.format("Ваши классы (%d): \n", classes.size())), chatId, classesStrings, callbacks, user.getCurrentMyClassesMessageId());
        if (messageId == null) {
            alertMessage(translator.getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", user.getLang()), chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentMyClassesMessageId(messageId);
        if (!saveUser(user))
            alertMessage(translator.getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", user.getLang()), chatId, 10000, user);
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
            alertMessage(translator.getTranslatedText("Не получилось найти пользователя...", "ru"), chatId, 10000, null);
            return;
        }
        String fileName = "newTest_" + chatId + "_" + System.currentTimeMillis() + ".json";
        File writtenFile = new File(fileName);

        System.out.println("Clean JSON to write: " + jsonData);
        try (FileWriter fileWriter = new FileWriter(writtenFile)) {
            fileWriter.write(jsonData);
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
            switch (request) {
                case "" -> createTest(chatId, user, writtenFile.getName());
                case "exportJSON" -> {
                    InputFile inputFile = new InputFile();
                    inputFile.setMedia(writtenFile);
                    SendDocument sendDocument = new SendDocument(String.valueOf(chatId), inputFile);
                    try {
                        execute(sendDocument);
                    } catch (TelegramApiException e) {
                        System.err.println("An exception while sending document: \" " + inputFile.getMediaName() + "\" to " + chatId);
                    }
                }
                case "changeTest" -> {
                    String prevContent = req.getPrev_content();
                    System.out.println("Prev content: " + prevContent);
                    if (!DBManager.deleteTest(chatId, prevContent)) {
                        sendMessage(translator.getTranslatedText("Не удалось изменить тест, попробуйте ещё раз...", user.getLang()), chatId);
                        return;
                    }
                    createTest(chatId, user, writtenFile.getName());
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

    public Translator getTranslator() {
        return translator;
    }
}