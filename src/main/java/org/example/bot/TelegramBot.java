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
import org.example.spring.ImportClassRequest;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.example.classes.User.getCorrectAnswersForQuestion;
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final String WEB_APP_URL = System.getenv("WEBAPP_URL");
    private List<User> users = Collections.synchronizedList(new ArrayList<>());
    private final Translator translator;
    private final RateLimiterManager rateLimiter = new RateLimiterManager();
    private final int MILLIS_IN_SECONDS = 1000;
    public final String DEFAULT_LANG = "en";

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
            alertMessage(translator.getTranslatedText("only.messages.or.files", user != null ? user.getLang() : DEFAULT_LANG), chatId, 10000, user);
            return;
        }

        if (user == null) {
            User testUser = new User(chatId, "default", DEFAULT_LANG, 0, 0, 0, -1, null);
            System.out.println("User is null for " + chatId);
            if (message.hasText() && message.getText().startsWith("/start") && !(message.getText().startsWith("/startquiz")))
                startRegistration(update, chatId, null);
            else
                alertMessage(translator.getTranslatedText("write.start.to.register", DEFAULT_LANG), chatId, 20000, testUser);
            return;
        }
        messageId = message.getMessageId();
        if (messageId != null) {
            if (user.getWarnings() > 1) {
                if (user.getUntilTime() > System.currentTimeMillis())
                    return;
                else {
                    user.setWarnings(0);
                    user.setUntilTime(0);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                }
            }
            if (!rateLimiter.tryConsume(chatId)) {
                alertMessage(translator.getTranslatedText("message.spam", user.getLang()), chatId, 5000, user);

                user.setWarnings(user.getWarnings() + 1);
                if (user.getWarnings() > 1)
                    user.setUntilTime(System.currentTimeMillis() + 30 * 60 * MILLIS_IN_SECONDS);
                if (!saveUser(user))
                    alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                return;
            }
            if (user.getAutoDeleting().equalsIgnoreCase("autoDeleteUser") || user.getAutoDeleting().equalsIgnoreCase("autoDeleteOn")) {
                new Thread(() -> {
                    try {
                        Thread.sleep((long) user.getAutoDeleteLength() * MILLIS_IN_SECONDS);
                    } catch (InterruptedException e) {
                        System.out.println("An exception in auto delete user's messages: " + e.getMessage());
                    }
                    deleteMessage(messageId, chatId);
                }).start();
            }
        }
        if (user.getQuizState() == -1) {
            if (message.hasText()) {
                String msg = "";
                if (message.hasText()) msg = message.getText();
                if (update.hasCallbackQuery()) msg = update.getCallbackQuery().getData();

                if (msg.trim().isEmpty()) {
                    alertMessage(translator.getTranslatedText("cannot.send.empty", user.getLang()), chatId, 10000, user);
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
                long MAX_FILE_SIZE = 15 * 1024 * 1024; // 15 MB
                if (update.getMessage().getDocument().getFileSize() > MAX_FILE_SIZE) {
                    alertMessage(translator.getTranslatedText("max.file.size", user.getLang()), chatId, 10000, user);
                    return;
                }

                if (!(update.getMessage().getDocument().getFileName().endsWith(".json"))) {
                    alertMessage(translator.getTranslatedText("send.json.file", user.getLang()), chatId, 10000, user);
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
                    alertMessage(translator.getTranslatedText("error.adding.test", user.getLang()), chatId, 10000, user);
                    return;
                }

                String response = createTest(chatId, user, fileName);
                if (response != null)
                    alertMessage(response, chatId, 10000, user);
            }
        }
        if (user.getQuizState() > -1) {
            if (user.getQuizState() < 1) return;
            String userName;
            if (update.hasMessage()) {
                userName = update.getMessage().getFrom().getUserName();
            } else {
                userName = update.getCallbackQuery().getFrom().getUserName();
            }

            if (user.getCurrentQuiz() == null) {
                alertMessage(translator.getTranslatedText("error.try.again", user.getLang()), chatId, 10000, user);
                return;
            }
            Quiz quiz = user.getCurrentQuiz();
            Test test = quiz.getTest();
            Question currentQuestion = test.getQuestions().get(user.getQuizState() - 1);

            if (!(update.hasMessage() || update.hasCallbackQuery())) {
                alertMessage(translator.getTranslatedText("send.answer", user.getLang()), chatId, 10000, user);
                return;
            }
            if (user.getPrevType().equalsIgnoreCase("ans")) {
                if (update.hasMessage() && update.getMessage().hasText()) {
                    String userAnswer = update.getMessage().getText();
                    String correctAnswer = getCorrectAnswersForQuestion(currentQuestion).getFirst();
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
                    alertMessage(translator.getTranslatedText("answer.with.word", user.getLang()), chatId, 10000, user);
                    return;
                }
            }
            if (user.getPrevType().equalsIgnoreCase("var")) {
                if (update.hasCallbackQuery()) {
                    String callbackData = update.getCallbackQuery().getData();
                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(update.getCallbackQuery().getId());

                    int selectedAnswer = Integer.parseInt(callbackData.replace("ans_", ""));
                    List<String> keys = new ArrayList<>(quiz.getTest().getQuestions().get(user.getQuizState() - 1).getAnswers().keySet());
                    ArrayList<String> correctAnswers = getCorrectAnswersForQuestion(currentQuestion);
                    LinkedHashMap<String, Boolean> newUserAnswers = user.getUserAnswers();

                    String userAnswer = keys.get(selectedAnswer);
                    boolean isCorrect = correctAnswers.contains(keys.get(selectedAnswer));

                    newUserAnswers.put(user.getQuizState() + "\uD80C\uDE78" + userAnswer, isCorrect);
                    if (isCorrect)
                        user.setCorrectAnswers(user.getCorrectAnswers() + 1);

                    user.setUserAnswers(newUserAnswers);

                    System.out.println("Var");
                    System.out.println("Selected answer: " + selectedAnswer);
                    System.out.println("Correct answers: " + correctAnswers);
                    System.out.println("added new user answer. size: " + user.getUserAnswers().size());
                    System.out.println("new user answers: " + newUserAnswers);
                    try {
                        execute(answer);
                    } catch (TelegramApiException e) {
                        System.out.println("Exception while processing variant answer: " + e.getMessage());
                    }
                } else {
                    alertMessage(translator.getTranslatedText("click.button", user.getLang()), chatId, 10000, user);
                    return;
                }
            }
            else if(user.getPrevType().equalsIgnoreCase("srv")){
                if (update.hasCallbackQuery()) {
                    String callbackData = update.getCallbackQuery().getData();
                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(update.getCallbackQuery().getId());

                    String selectedAnswer = callbackData.replace("srv_", "");
                    List<String> keys = new ArrayList<>(quiz.getTest().getQuestions().get(user.getQuizState() - 1).getAnswers().keySet());
                    String correctAnswer = String.valueOf(keys.indexOf(getCorrectAnswersForQuestion(currentQuestion)));
                    LinkedHashMap<String, Boolean> newUserAnswers = user.getUserAnswers();

                    String userAnswer = keys.get(Integer.parseInt(selectedAnswer));

                    newUserAnswers.put(user.getQuizState() + "\uD80C\uDE78" + userAnswer, true);

                    user.setUserAnswers(newUserAnswers);
                    ArrayList<String> newUserSurveyAns = new ArrayList<>();
                    newUserSurveyAns.add(userAnswer);
                    user.setSurveyAnswers(newUserSurveyAns);

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
                    alertMessage(translator.getTranslatedText("click.button", user.getLang()), chatId, 10000, user);
                    return;
                }
            }

            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
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
                        alertMessage(translator.getTranslatedText("failed.update.user.short", user.getLang()), chatId, 10000, user);
                }
                if (user.getCurrentQuizPhotoId() != null){
                    deleteMessage(user.getCurrentQuizPhotoId(), chatId);
                    user.setCurrentQuizPhotoId(null);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("failed.update.user.short", user.getLang()), chatId, 10000, user);
                }
                Thread thread = new Thread(() -> {
                    System.out.println("thread launched!");
                    User teacher = users.stream().filter(x -> x.getChatId() == quiz.getTeacherId()).findFirst().orElse(null);

                    if (teacher == null) {
                        alertMessage(translator.getTranslatedText("user.not.found", DEFAULT_LANG), quiz.getTeacherId(), 10000, null);
                        return;
                    }

                    sendMessage(translator.getTranslatedText("quiz.congrats", user.getLang(), user.getCorrectAnswers(), quiz.getTest().getQuestions().size() - user.getSurveyAnswers().size()), chatId);
                    sendMessage(
                            translator.getTranslatedText("quiz.finished", teacher.getLang(), userName, quiz.getTest().getTestName(), user.getCorrectAnswers(), quiz.getTest().getQuestions().size() - user.getSurveyAnswers().size()),
                            quiz.getTeacherId()
                    );

                    List<String> userAnswers = new ArrayList<>(user.getUserAnswers().keySet());

                    System.out.println("User answers: " + userAnswers);
                    int surveyCount = 0;
                    for (int i = 0; i < user.getUserAnswers().size() && i < quiz.getTest().getQuestions().size(); i++) {
                        String question = test.getQuestions().get(i).getQuestion();
                        String userAnswer = userAnswers.get(i).split("\uD80C\uDE78")[1];
                        ArrayList<String> correctAnswers = getCorrectAnswersForQuestion(quiz.getTest().getQuestions().get(i));

                        System.out.println("Question: " + test.getQuestions().get(i));
                        System.out.println("User answer: " + userAnswer);
                        System.out.println("Correct answers: " + correctAnswers);

                        String formattedAnswers = correctAnswers.stream()
                                .map(answer -> "\"" + answer + "\"")
                                .collect(Collectors.joining(", "));

                        if (!test.getQuestions().get(i).getType().equalsIgnoreCase("srv"))
                            sendMessage(translator.getTranslatedText("quiz.question.result", teacher.getLang(), i + 1, question, userName, userAnswer, formattedAnswers), quiz.getTeacherId());
                        else{
                            surveyCount++;
                            sendMessage(translator.getTranslatedText("quiz.survey.result", teacher.getLang(), surveyCount, question, userName, userAnswer), quiz.getTeacherId());
                        }

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {}
                    }
                    user.setQuizState(-1);
                    user.setCurrentQuiz(null);
                    user.setCorrectAnswers(0);
                    user.setUserAnswers(new LinkedHashMap<>());
                    user.setSurveyAnswers(new ArrayList<>());
                    user.setCurrentQuizPhotoId(null);

                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
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
                    } catch (InterruptedException ignored) {}

                    ArrayList<String> callbacks = new ArrayList<>();

                    for (int i = 0; i < variants.size(); i++)
                        callbacks.add("ans_" + i);
                    System.out.println(user.getCurrentQuizPhotoId());
                    Integer quizMessageId = sendMessagePhoto(translator.getTranslatedText("question.number", user.getLang(), user.getQuizState(), question.getQuestion()), chatId, question.getImage(), variants, callbacks, user.getCurrentQuizMessageId());
                    user.setPrevType("var");

                    if (!saveUser(user)) {
                        alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == null) {
                        alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == -1)
                        return;

                    user.setCurrentQuizMessageId(quizMessageId);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                }).start();
            } else if (question.getType().equalsIgnoreCase("ans")){
                new Thread(() -> {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        sendMessage(translator.getTranslatedText("failed.send.question", user.getLang()), chatId);
                    }
                    System.out.println(user.getCurrentQuizPhotoId());
                    Integer quizMessageId = sendMessagePhoto(translator.getTranslatedText("question.number", user.getLang(), user.getQuizState(), question.getQuestion()), chatId, question.getImage(), user.getCurrentQuizMessageId());
                    user.setPrevType("ans");

                    if (!saveUser(user)) {
                        alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == null) {
                        alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (quizMessageId == -1)
                        return;

                    user.setCurrentQuizMessageId(quizMessageId);
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                }).start();
            } else if(question.getType().equalsIgnoreCase("srv")){
                ArrayList<String> variants = new ArrayList<>(question.getAnswers().keySet());
                ArrayList<String> callbacks = new ArrayList<>();
                for (int j = 0; j < variants.size(); j++)
                    callbacks.add("srv_" + j);

                Integer quizMessageId = sendMessagePhoto(
                        getTranslator().getTranslatedText("survey.number", user.getLang(), user.getQuizState(), question.getQuestion()),
                        chatId, question.getImage(), variants, callbacks, user.getCurrentQuizMessageId()
                );
                user.setPrevType("srv");

                if (!saveUser(user)) {
                    alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                    return;
                }

                if (quizMessageId == null) {
                    alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                    return;
                }

                if (quizMessageId == -1)
                    return;

                user.setCurrentQuizMessageId(quizMessageId);
                if (!saveUser(user))
                    alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
            }
        }
    }

    private void startRegistration(Update update, long chatId, User user) {
        String username = update.getMessage().getFrom().getUserName();

        if (user == null) {
            String userLangCode = update.getMessage().getFrom().getLanguageCode();
            ArrayList<String> languages = new ArrayList<>();
            languages.add("ru");
            languages.add("be");
            languages.add("en");

            if (!languages.contains(userLangCode))
                userLangCode = DEFAULT_LANG;

            User testUser = new User(chatId, "default", userLangCode, 0, 0, 0, -1, null);

            if (username == null) {
                alertMessage(translator.getTranslatedText("set.username", userLangCode), chatId, 20000, testUser);
                return;
            }

            int doesUserExit = DBManager.doesUserExist(username);
            if (doesUserExit == 2) {
                alertMessage(translator.getTranslatedText("error.checking.user", userLangCode), chatId, 10000, testUser);
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

            ArrayList<String> studentsNames = DBManager.getUsernamesByIds(chosenClass.getStudents());
            if (studentsNames == null) {
                alertMessage(translator.getTranslatedText("failed.get.class.students", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<String> callbacks = new ArrayList<>();
            for (int i = 0; i < chosenClass.getStudents().size(); i++)
                callbacks.add("real_delete_student_" + i);

            studentsNames.add("back");
            callbacks.add("back_delete_student_" + data.replaceFirst("delete_student_", ""));

            sendMessage(translator.getTranslatedText("select.student.to.remove", user.getLang()), chatId, studentsNames, callbacks, user.getCurrentMyClassesMessageId());
        }
        else if (data.startsWith("real_delete_student")) {
            String deleteStudentId = data.replaceFirst("real_delete_student_", "");
            StudentClass chosenClass = user.getCurrentChangingClass();

            ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
            newSetOfStudents.remove(newSetOfStudents.get(Integer.parseInt(deleteStudentId)));

            DBManager.deleteClass(chatId, chosenClass.getName());
            DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);

            user.setCurrentChangingClass(null);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);

            alertMessage(translator.getTranslatedText("user.removed", user.getLang()), chatId, 10000, user);
            sendClasses(chatId, user);
        }
        else if (data.startsWith("back_delete_student")) {
            String classId = data.replaceFirst("back_delete_student_", "");
            viewClass(classId, chatId, user);
        }
        else if (data.startsWith("add_student")) {
            sendMessage(translator.getTranslatedText("enter.student.name", user.getLang()), chatId);
            user.setState("adding_student");
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("failed.update.user.short", user.getLang()), chatId, 10000, user);
        }
        else if (data.startsWith("view_class_back"))
            sendClasses(chatId, user);
        else if (data.startsWith("delete_class")) {
            StudentClass currentClass = user.getCurrentChangingClass();
            if (currentClass == null) {
                alertMessage(translator.getTranslatedText("failed.get.current.class", user.getLang()), chatId, 10000, user);
                sendClasses(chatId, user);
                return;
            }

            if (!DBManager.deleteClass(chatId, currentClass.getName())) {
                alertMessage(translator.getTranslatedText("failed.delete.class", user.getLang()), chatId, 10000, user);
                return;
            }
            user.setClassCount(user.getClassCount() - 1);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user.ellipsis", user.getLang()), chatId, 10000, user);
                return;
            }

            if (user.getCurrentStartQuizClassMessageId() != null)
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);

            sendClasses(chatId, user);
        }
        else if (data.startsWith("delete_test")) {
            Test currentTest = user.getCurrentChangingTest();
            if (currentTest == null) {
                alertMessage(translator.getTranslatedText("failed.get.current.test", user.getLang()), chatId, 10000, user);
                sendTests(chatId, user);
                return;
            }

            if (DBManager.deleteTest(chatId, DBManager.getTestContent(chatId, currentTest.getTestName()))) {
                alertMessage(translator.getTranslatedText("failed.delete.test", user.getLang()), chatId, 10000, user);
                return;
            }
            user.setTestsCount(user.getTestsCount() - 1);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user.ellipsis", user.getLang()), chatId, 10000, user);
                return;
            }

            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            sendTests(chatId, user);
        }
        else if (data.startsWith("view_test_back")){
            sendTests(chatId, user);
        } else if (data.startsWith("download_test")) {
            Test currentTest = user.getCurrentChangingTest();
            if (currentTest == null) {
                alertMessage(translator.getTranslatedText("failed.get.current.test", user.getLang()), chatId, 10000, user);
                sendClasses(chatId, user);
                return;
            }

            sendTextAsDocument(gson.toJson(currentTest), currentTest.getTestName() + ".json", chatId);
        }
        else if (data.startsWith("view_classes")) {
            System.out.println("Viewing classes");
            String classId = data.replaceFirst("view_classes_", "");
            viewClass(classId, chatId, user);
        }
        else if (data.startsWith("view_tests")) {
            System.out.println("Viewing tests");
            String testId = data.replaceFirst("view_tests_", "");

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage(translator.getTranslatedText("failed.get.user.tests", user.getLang()), chatId, 10000, user);
                return;
            }

            Test chosenTest = tests.get(Integer.parseInt(testId));
            user.setCurrentChangingTest(chosenTest);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
            ArrayList<String> options = new ArrayList<>();
            options.add("edit.test");
            options.add("download.test");
            options.add("back");
            options.add("delete.test.btn");

            ArrayList<String> callbacks = new ArrayList<>();
            callbacks.add("change_test");
            callbacks.add("download_test");
            callbacks.add("view_test_back");
            callbacks.add("delete_test");

            sendMessage(translator.getTranslatedText("test.name.label", user.getLang(), chosenTest.getTestName()), chatId, options, callbacks, user.getCurrentMyTestsMessageId());
        }
        else if (data.startsWith("start_quiz_class")) {
            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            if (user.getCurrentStartQuizClassMessageId() != null)
                deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);

            String classId = data.replaceFirst("start_quiz_class_", "");
            ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
            if (classes == null) {
                alertMessage(translator.getTranslatedText("failed.get.user.classes", user.getLang()), chatId, 10000, user);
                return;
            }

            StudentClass chosenClass = classes.get(Integer.parseInt(classId));

            user.setCurrentStartQuizClass(chosenClass);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<Test> tests = DBManager.getTests(chatId);

            if (tests == null) {
                alertMessage(translator.getTranslatedText("failed.get.user.tests", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<String> testsStrings = new ArrayList<>();
            for (Test test: tests)
                testsStrings.add(test.getTestName());

            ArrayList<String> callbacks = new ArrayList<>();
            for (int i = 0; i < tests.size(); i++)
                callbacks.add("start_quiz_test_" + i);

            Integer messageId = sendMessage(translator.getTranslatedText("select.test.for.quiz", user.getLang()), chatId, testsStrings, callbacks, user.getCurrentStartQuizTestMessageId());
            if (messageId == null) {
                alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setCurrentStartQuizTestMessageId(messageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
        }
        else if (data.startsWith("start_quiz_test")) {
            if (user.getCurrentStartQuizTestMessageId() != null)
                deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);

            String testId = data.replaceFirst("start_quiz_test_", "");
            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage(translator.getTranslatedText("failed.get.user.tests", user.getLang()), chatId, 10000, user);
                return;
            }

            Test chosenTest = tests.get(Integer.parseInt(testId));

            Integer messageId = sendMessage(translator.getTranslatedText("creating.quiz", user.getLang()), chatId);

            Thread quizThread = new Thread(() -> {
                synchronized (this) {
                    alertMessage(translator.getTranslatedText("quiz.created", user.getLang()), chatId, 30000, user);
                    deleteMessage(messageId, chatId);
                    Quiz quiz = new Quiz(chatId, user.getCurrentStartQuizClass(), chosenTest);
                    quiz.startQuiz(this, (ArrayList<User>) users, chatId);
                }
            });
            quizThread.start();
        }
        else if(data.startsWith("start_quiz_user")){
            ArrayList<String> params = new ArrayList<>(List.of(data.replaceFirst("start_quiz_user_", "").split("\uD80C\uDE78")));
            String teacherUsername = params.getFirst();
            String testName = params.getLast();

            ArrayList<Long> rawTeacherId = DBManager.getIdsByUsernames(new ArrayList<>(List.of(teacherUsername)));
            if (rawTeacherId == null) {
                alertMessage(translator.getTranslatedText("teacher.not.found", user.getLang()), chatId, 15000, user);
                return;
            }

            long teacherId = rawTeacherId.getFirst();

            ArrayList<Test> tests = DBManager.getTests(teacherId);

            if (tests == null) {
                alertMessage(translator.getTranslatedText("failed.get.user.tests", user.getLang()), chatId, 10000, user);
                return;
            }
            
            Test test = null;
            for (Test t : tests)
                if (t.getTestName().equals(testName))
                    test = t;
            
            if (test == null) {
                alertMessage(translator.getTranslatedText("failed.get.current.test", user.getLang()), chatId, 10000, user);
                return;
            }

            new Quiz(teacherId, null, test).startQuizzing(this, user, user.getCurrentQuiz().getTeacherId(), (ArrayList<User>) users, chatId);
        }
        else if(data.startsWith("changeAutoDelete")){
            switch(data){
                case "changeAutoDelete" -> {
                    ArrayList<String> options = new ArrayList<>();
                    options.add("enable.everywhere");
                    options.add("enable.user.messages");
                    options.add("disable");
                    ArrayList<String> callbacks = new ArrayList<>();
                    callbacks.add("autoDeleteOn");
                    callbacks.add("autoDeleteUser");
                    callbacks.add("autoDeleteOff");
                    sendMessage(translator.getTranslatedText("select.auto.delete.method", user.getLang()), chatId, options, callbacks, user.getAutoDeleteSetMessageId());
                }
                case "changeAutoDeleteDelay" ->{
                    System.out.println("changeAutoDeleteDelay");
                    Integer messageId = sendMessage(translator.getTranslatedText("enter.seconds", user.getLang()), chatId);
                    if (messageId == null) {
                        alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                        return;
                    }

                    if (messageId == -1)
                        return;

                    user.setCurrentAutoDeleteSetSecondsMessageId(messageId);
                    user.setState("changingAutoDelay");
                    if (!saveUser(user))
                        alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                }
            }
        }
        else if(data.startsWith("autoDelete")){
            switch(data){
                case "autoDeleteOn" -> user.setAutoDeleting("autoDeleteOn");
                case "autoDeleteUser" -> user.setAutoDeleting("autoDeleteUser");
                case "autoDeleteOff" -> user.setAutoDeleting("autoDeleteOff");
                default -> {
                    alertMessage(translator.getTranslatedText("press.button", user.getLang()), chatId, 5000, user);
                    return;
                }
            }
            alertMessage(translator.getTranslatedText("auto.delete.changed", user.getLang()), chatId, 15000, user);
            sendAutoDeleteSettings(chatId, user);
        }
        else if(data.startsWith("lang")) {
            String lang = data.replaceFirst("lang_", "");
            if (lang.equals(user.getLang())) {
                alertMessage(translator.getTranslatedText("language.already.chosen", user.getLang()), chatId, 10000, user);
                return;
            }

            user.setLang(lang);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                return;
            }
            if (!DBManager.updateUserLang(chatId, user.getLang())) {
                alertMessage(translator.getTranslatedText("failed.language", user.getLang()), chatId, 10000, user);
                return;
            }

            alertMessage(translator.getTranslatedText("language.changed", user.getLang()), chatId, 15000, user);
        }
    }

    private void processCommands(Update update, String msg, long chatId, User user) {
        if (msg.startsWith("/start") && !(msg.startsWith("/startquiz")))
            startRegistration(update, chatId, user);
        else if (msg.startsWith("/newclass")) {
            if (user.getClassCount() >= 5) {
                alertMessage(translator.getTranslatedText("class.limit", user.getLang()), chatId, 15000, user);
                return;
            }
            user.setState("class_name");
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                return;
            }

            Integer messageId = sendMessage(translator.getTranslatedText("enter.class.name", user.getLang()), chatId);

            if (messageId == null) {
                alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setLastMessageId(messageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
        } else if (msg.startsWith("/myclasses")) {
            if (user.getCurrentMyClassesMessageId() != null)
                deleteMessage(user.getCurrentMyClassesMessageId(), chatId);
            user.setCurrentMyClassesMessageId(null);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                return;
            }

            sendClasses(chatId, user);
        } else if (msg.startsWith("/newtest")) {
            if (user.getTestsCount() >= 10) {
                alertMessage(translator.getTranslatedText("test.limit", user.getLang()), chatId, 10000, user);
                return;
            }
            user.setState("create_test");
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user.short", user.getLang()), chatId, 10000, user);
                return;
            }

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(translator.getTranslatedText("send.json.or.miniapp", user.getLang()));

            ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
            keyboard.setResizeKeyboard(true);
            keyboard.setOneTimeKeyboard(false);

            KeyboardButton webAppButton = new KeyboardButton();
            webAppButton.setText(translator.getTranslatedText("create.test", user.getLang()));

            WebAppInfo webAppInfo = new WebAppInfo();
            webAppInfo.setUrl(WEB_APP_URL + "?chat_id=" + chatId + "&method=newTest");
            webAppButton.setWebApp(webAppInfo);

            KeyboardRow row = new KeyboardRow();
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
                alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                return;
            }

            if (messageId == -1)
                return;

            user.setLastMessageId(messageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
        }
        else if (msg.startsWith("/mytests")) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            user.setCurrentMyTestsMessageId(null);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
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
                alertMessage(translator.getTranslatedText("failed.get.your.classes", user.getLang()), chatId, 10000, user);
                return;
            }

            if (classes.isEmpty()) {
                alertMessage(translator.getTranslatedText("no.classes.yet", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<Test> tests = DBManager.getTests(chatId);
            if (tests == null) {
                alertMessage(translator.getTranslatedText("failed.get.your.tests", user.getLang()), chatId, 10000, user);
                return;
            }

            if (tests.isEmpty()) {
                alertMessage(translator.getTranslatedText("no.tests.yet", user.getLang()), chatId, 10000, user);
                return;
            }

            ArrayList<String> classesStrings = new ArrayList<>();
            for (StudentClass studentClass: classes)
                classesStrings.add(studentClass.getName());

            ArrayList<String> callbacks = new ArrayList<>();

            for (int i = 0; i < classes.size(); i++)
                callbacks.add("start_quiz_class_" + i);

            Integer classMessageId = sendMessage(translator.getTranslatedText("select.class.for.quiz", user.getLang()), chatId, classesStrings, callbacks, user.getCurrentStartQuizClassMessageId());

            if (classMessageId == null) {
                alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
                return;
            }

            if (classMessageId == -1)
                return;

            user.setCurrentStartQuizClassMessageId(classMessageId);
            if (!saveUser(user))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
        }
        else if (msg.startsWith("/setautodelete")) {
            if (user.getAutoDeleteSetMessageId() != null)
                deleteMessage(user.getAutoDeleteSetMessageId(), chatId);
            user.setAutoDeleteSetMessageId(null);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                return;
            }

            sendAutoDeleteSettings(chatId, user);
        }
        else if (msg.startsWith("/setlanguage")) {
            if (user.getCurrentSetLangMessageId() != null)
                deleteMessage(user.getCurrentSetLangMessageId(), chatId);
            user.setCurrentSetLangMessageId(null);
            if (!saveUser(user)) {
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
                return;
            }

            sendLangSet(chatId, user);
        }
        else if (msg.startsWith("/mute")) { // admin commands
            if (user.getPermissionLevel() < 2)
                return;
            String username = msg.split(" ")[1];
            long seconds = Long.parseLong(msg.split(" ")[2]);

            ArrayList<Long> mutedIds = DBManager.getIdsByUsernames(new ArrayList<>(Collections.singleton(username)));

            if (mutedIds == null) {
                alertMessage(translator.getTranslatedText("user.not.found.short", user.getLang()), chatId, 10000, user);
                return;
            }

            long muteChatId = mutedIds.getFirst();
            User muteUser = users.stream().filter(x -> x.getChatId() == muteChatId).findFirst().orElse(null);

            if (muteUser == null) {
                alertMessage(translator.getTranslatedText("user.not.found.short", user.getLang()), chatId, 10000, user);
                return;
            }

            muteUser.setWarnings(2);
            muteUser.setUntilTime(System.currentTimeMillis() + seconds * MILLIS_IN_SECONDS);
            if (!saveUser(muteUser))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
            sendMessage(translator.getTranslatedText("user.muted", user.getLang()), chatId);
        }
        else if (msg.startsWith("/unmute")) {
            if (user.getPermissionLevel() < 2)
                return;
            String username = msg.split(" ")[1];

            ArrayList<Long> mutedIds = DBManager.getIdsByUsernames(new ArrayList<>(Collections.singleton(username)));

            if (mutedIds == null) {
                alertMessage(translator.getTranslatedText("user.not.found.short", user.getLang()), chatId, 10000, user);
                return;
            }

            long unmuteChatId = mutedIds.getFirst();
            User unmuteUser = users.stream().filter(x -> x.getChatId() == unmuteChatId).findFirst().orElse(null);

            if (unmuteUser == null) {
                alertMessage(translator.getTranslatedText("user.not.found.short", user.getLang()), chatId, 10000, user);
                return;
            }

            unmuteUser.setWarnings(0);
            unmuteUser.setUntilTime(0);
            if (!saveUser(unmuteUser))
                alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
            sendMessage(translator.getTranslatedText("user.unmuted", user.getLang()), chatId);
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
            alertMessage(translator.getTranslatedText("failed.get.user.classes", user.getLang()), chatId, 10000, user);
            return;
        }

        StudentClass chosenClass = classes.get(Integer.parseInt(classId));
        ArrayList<String> userUsernames = DBManager.getUsernamesByIds(chosenClass.getStudents());
        if (userUsernames == null) {
            alertMessage(translator.getTranslatedText("failed.get.class.users", user.getLang()), chatId, 10000, user);
            return;
        }

        StringBuilder classString = new StringBuilder();
        classString.append(translator.getTranslatedText("class.name.label", user.getLang(), chosenClass.getName()));
        for (String username: userUsernames)
            classString.append("- @").append(username).append("\n");

        System.out.println(userUsernames);
        System.out.println(classString);
        ArrayList<String> options = new ArrayList<>();
        options.add("remove.student");
        options.add("add.student");
        options.add("back");
        options.add("delete.class");

        ArrayList<String> callbacks = new ArrayList<>();
        callbacks.add("delete_student_" + classId);
        callbacks.add("add_student");
        callbacks.add("view_class_back");
        callbacks.add("delete_class");

        user.setCurrentChangingClass(chosenClass);
        if (!saveUser(user)) {
            alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
            return;
        }

        sendMessage(classString.toString(), chatId, options, callbacks, user.getCurrentMyClassesMessageId());
    }

    public void sendAutoDeleteSettings(long chatId, User user) {
        ArrayList<String> options = new ArrayList<>();
        options.add("configure.behavior");
        options.add("configure.time");
        ArrayList<String> callbacks = new ArrayList<>();
        callbacks.add("changeAutoDelete");
        callbacks.add("changeAutoDeleteDelay");

        Integer messageId = sendMessage(translator.getTranslatedText("select.auto.delete.method", user.getLang()), chatId, options, callbacks, user.getAutoDeleteSetMessageId());
        if (messageId == null) {
            alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setAutoDeleteSetMessageId(messageId);
        if (!saveUser(user))
            alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
    }

    public void sendLangSet(long chatId, User user) {
        ArrayList<String> langs = new ArrayList<>();
        langs.add("Русский");
        langs.add("Беларуская");
        langs.add("English");
        ArrayList<String> callbacks = new ArrayList<>();
        callbacks.add("lang_ru");
        callbacks.add("lang_be");
        callbacks.add("lang_en");

        Integer messageId = sendMessage(translator.getTranslatedText("language.choose", user.getLang()), chatId, langs, callbacks, user.getCurrentSetLangMessageId());
        if (messageId == null) {
            alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentSetLangMessageId(messageId);
        if (!saveUser(user))
            alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
    }

    public void sendTests(long chatId, User user) {
        ArrayList<Test> tests = DBManager.getTests(chatId);
        if (tests == null) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            alertMessage(translator.getTranslatedText("failed.get.user.tests.ellipsis", user.getLang()), chatId, 10000, user);
            return;
        }
        if (tests.isEmpty()) {
            if (user.getCurrentMyTestsMessageId() != null)
                deleteMessage(user.getCurrentMyTestsMessageId(), chatId);
            alertMessage(translator.getTranslatedText("no.tests", user.getLang()), chatId, 10000, user);
            return;
        }

        ArrayList<String> testsStrings = new ArrayList<>();
        for (Test test : tests)
            testsStrings.add(translator.getTranslatedText("test.list.item", user.getLang(), test.getTestName(), test.getQuestions().size()));

        ArrayList<String> callbacks = new ArrayList<>();

        for (int i = 0; i < tests.size(); i++)
            callbacks.add("view_tests_" + i);

        user.setCurrentChangingTest(null);
        if (!saveUser(user)) {
            alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
            return;
        }

        Integer messageId = sendMessage(translator.getTranslatedText("your.tests", user.getLang(), tests.size()), chatId, testsStrings, callbacks, user.getCurrentMyTestsMessageId());

        if (messageId == null) {
            alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentMyTestsMessageId(messageId);
        if (!saveUser(user))
            alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
    }

    public void sendClasses(long chatId, User user){
        ArrayList<StudentClass> classes = DBManager.getClasses(chatId);
        if (classes == null) {
            alertMessage(translator.getTranslatedText("failed.get.user.classes.ellipsis", user.getLang()), chatId, 10000, user);
            return;
        }
        if (classes.isEmpty()) {
            alertMessage(translator.getTranslatedText("no.classes", user.getLang()), chatId, 10000, user);
            return;
        }

        ArrayList<String> classesStrings = new ArrayList<>();

        for (StudentClass studentClass : classes)
            classesStrings.add(translator.getTranslatedText("class.list.item", user.getLang(), studentClass.getName(), studentClass.getStudents().size()));

        ArrayList<String> callbacks = new ArrayList<>();

        for (int i = 0; i < classes.size(); i++)
            callbacks.add("view_classes_" + i);

        user.setCurrentStartQuizClass(null);
        if (!saveUser(user)) {
            alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
            return;
        }

        Integer messageId = sendMessage(translator.getTranslatedText("your.classes", user.getLang(), classes.size()), chatId, classesStrings, callbacks, user.getCurrentMyClassesMessageId());
        if (messageId == null) {
            alertMessage(translator.getTranslatedText("failed.get.message.id", user.getLang()), chatId, 10000, user);
            return;
        }

        if (messageId == -1)
            return;

        user.setCurrentMyClassesMessageId(messageId);
        if (!saveUser(user))
            alertMessage(translator.getTranslatedText("failed.update.user", user.getLang()), chatId, 10000, user);
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

    public Translator getTranslator() {
        return translator;
    }
}