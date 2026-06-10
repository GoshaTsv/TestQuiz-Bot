package org.example.classes;

import org.example.bot.TelegramBot;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;
import org.example.database.DBManager;
import org.example.spring.WebRequest;

import java.util.*;

public class User {
    // user data
    private String state;
    private long chatId;
    private String lang;

    // auto deleting
    private String autoDeleting;
    private int autoDeleteLength;
    private Integer autoDeleteSetMessageId;
    private Integer currentAutoDeleteSetSecondsMessageId;

    public int getAutoDeleteLength() {
        return autoDeleteLength;
    }

    public void setAutoDeleteLength(int autoDeleteLength) {
        this.autoDeleteLength = autoDeleteLength;
    }

    // statistic (max 5 classes, 10 tests)
    private int classCount;
    private int testsCount;

    // quiz vars
    private int quizState;
    private Quiz currentQuiz;
    private String prevType;
    private int correctAnswers;
    private LinkedHashMap<String, Boolean> userAnswers;
    private Integer currentQuizMessageId;
    private Integer currentQuizPhotoId;

    // change classes/tests
    private StudentClass currentChangingClass;
    private Test currentChangingTest;

    // callbacks' currents
    private Integer currentMyClassesMessageId;
    private Integer currentMyTestsMessageId;
    private Integer currentStartQuizClassMessageId;
    private Integer currentStartQuizTestMessageId;

    // interim vars for new class (new test) / start quiz
    private StudentClass currentStartQuizClass;
    private String currentNewClassName;
    private Integer lastMessageId;
    //WebRequest for importing classes into the web app
    private WebRequest lastWebReq;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public Integer getCurrentQuizPhotoId() {
        return currentQuizPhotoId;
    }

    public void setCurrentQuizPhotoId(Integer currentQuizPhotoId) {
        this.currentQuizPhotoId = currentQuizPhotoId;
    }

    public Integer getCurrentQuizMessageId() {
        return currentQuizMessageId;
    }

    public void setCurrentQuizMessageId(Integer currentQuizMessageId) {
        this.currentQuizMessageId = currentQuizMessageId;
    }

    public WebRequest getLastWebReqFromUser(User user) {
        return user.getLastWebReq();
    }

    public WebRequest getLastWebReq() {
        return lastWebReq;
    }

    public void setLastWebReq(WebRequest lastWebReq) {
        this.lastWebReq = lastWebReq;
    }



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

    public synchronized int getQuizState() {
        return quizState;
    }

    public synchronized void setQuizState(int quizState) {
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

    public Integer getCurrentAutoDeleteSetSecondsMessageId() {
        return currentAutoDeleteSetSecondsMessageId;
    }

    public void setCurrentAutoDeleteSetSecondsMessageId(Integer currentAutoDeleteSetSecondsMessageId) {
        this.currentAutoDeleteSetSecondsMessageId = currentAutoDeleteSetSecondsMessageId;
    }

    public void setCurrentMyTestsMessageId(Integer currentMyTestsMessageId) {
        this.currentMyTestsMessageId = currentMyTestsMessageId;
    }

    public void setCurrentChangingTest(Test currentChangingTest) {
        this.currentChangingTest = currentChangingTest;
    }

    public void setCurrentStartQuizClassMessageId(Integer currentStartQuizClassMessageId) {
        this.currentStartQuizClassMessageId = currentStartQuizClassMessageId;
    }

    public void setCurrentStartQuizTestMessageId(Integer currentStartQuizTestMessageId) {
        this.currentStartQuizTestMessageId = currentStartQuizTestMessageId;
    }

    public void setCurrentNewClassName(String currentNewClassName) {
        this.currentNewClassName = currentNewClassName;
    }

    public void setLastMessageId(Integer lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public String getAutoDeleting() {
        return autoDeleting;
    }

    public User(long chatId, String state, String lang, int classCount, int testsCount, int quizState, Quiz currentQuiz) {
        this.chatId = chatId;
        this.state = state;
        this.classCount = classCount;
        this.testsCount = testsCount;
        this.quizState = quizState;
        this.currentQuiz = currentQuiz;
        this.lang = lang;
        prevType = "";
        correctAnswers = 0;
        userAnswers = new LinkedHashMap<>();
        this.autoDeleting = "autoDeleteUser";
        lastWebReq = new WebRequest();
        autoDeleteLength = 60;
    }

    public String getState() {
        return state;
    }

    public long getChatId() {
        return chatId;
    }

    public StudentClass getCurrentStartQuizClass() {
        return currentStartQuizClass;
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

    public void setCurrentStartQuizClass(StudentClass currentStartQuizClass) {
        this.currentStartQuizClass = currentStartQuizClass;
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

    public Integer getCurrentStartQuizClassMessageId() {
        return currentStartQuizClassMessageId;
    }

    public Integer getCurrentStartQuizTestMessageId() {
        return currentStartQuizTestMessageId;
    }

    public String getCurrentNewClassName() {
        return currentNewClassName;
    }

    public Integer getLastMessageId() {
        return lastMessageId;
    }

    public void setAutoDeleting(String autoDeleting) {
        this.autoDeleting = autoDeleting;
    }

    public Integer getAutoDeleteSetMessageId() {
        return autoDeleteSetMessageId;
    }

    public void setAutoDeleteSetMessageId(Integer autoDeleteSetMessageId) {
        this.autoDeleteSetMessageId = autoDeleteSetMessageId;
    }

    @Override
    public String toString() {
        return "User{" +
                "state='" + state + '\'' +
                ", chatId=" + chatId +
                ", currentClassName='" + currentStartQuizClass + '\'' +
                ", classCount=" + classCount +
                ", testsCount=" + testsCount +
                ", quizState=" + quizState +
                ", currentQuiz=" + currentQuiz +
                ", prevType='" + prevType + '\'' +
                ", correctAnswers=" + correctAnswers +
                ", userAnswers=" + userAnswers +
                '}';
    }

    public static String getCorrectAnswerForQuestion(Question question) {
        for (Map.Entry<String, Boolean> entry : question.getAnswers().entrySet()) {
            if (entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void processMessageStates(TelegramBot bot, String msg, long chatId, ArrayList<User> users) {
        User user = this;
        final String lang = user.getLang();

        switch (user.getState()) {
            case "changingAutoDelay" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage(bot.getTranslator().getTranslatedText("Изменение отменено.", lang), chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Вы не можете отправлять команды во время изменения задержки (/exit для отмены).", lang), chatId, 20000, user);
                    return;
                }
                int length = Integer.parseInt(msg);
                if (length > 3600){
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Пожалуйста, введите меньшее время.", lang), chatId, 15000, user);
                    return;
                }
                if (length < 5){
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Пожалуйста, введите большее время.", lang), chatId, 15000, user);
                    return;
                }

                user.setAutoDeleteLength(length);
                user.setState("default");
                if (!bot.saveUser(user)){
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", lang), chatId, 10000, user);
                    return;
                }

                if (user.getCurrentAutoDeleteSetSecondsMessageId() != null)
                    bot.deleteMessage(user.getCurrentAutoDeleteSetSecondsMessageId(), chatId);

                bot.alertMessage(bot.getTranslator().getTranslatedText("Время задержки было успешно изменено!", lang), chatId, 10000, user);
            }
            case "class_name" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage(bot.getTranslator().getTranslatedText("Создание класса отменено.", lang), chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", lang), chatId, 20000, user);
                    return;
                }

                if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", lang), chatId, 15000, user);
                    return;
                }

                int doesClassExit = DBManager.doesClassExit(chatId, msg);
                if (doesClassExit == 2) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Произошла ошибка во время проверки имени класса, попробуйте ещё...", lang), chatId, 10000, user);
                    return;
                }
                if (doesClassExit == 1) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("У вас уже существует класс с таким именем.", lang), chatId, 10000, user);
                    return;
                }

                user.setState("class_students");
                user.setCurrentNewClassName(msg);
                if (!bot.saveUser(user)) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", lang), chatId, 10000, user);
                    return;
                }

                if (user.getLastMessageId() != null)
                    bot.deleteMessage(user.getLastMessageId(), chatId);

                Integer messageId = bot.sendMessage(
                        bot.getTranslator().getTranslatedText("Перечислите через пробел username'ы учеников без @ (например: ivan victor test).", lang),
                        chatId
                );

                if (messageId == null) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", lang), chatId, 10000, user);
                    return;
                }
                if (messageId == -1)
                    return;

                user.setLastMessageId(messageId);
                if (!bot.saveUser(user))
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", lang), chatId, 10000, user);
            }
            case "class_students" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage(bot.getTranslator().getTranslatedText("Создания класса отменено.", lang), chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", lang), chatId, 20000, user);
                    return;
                }
                ArrayList<String> usernames = new ArrayList<>(Arrays.asList(msg.split(" ")));
                if (usernames.size() < 2) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Класс должен состоять минимум из 2-х учеников.", lang), chatId, 15000, user);
                    return;
                }

                user.setState("default");
                bot.sendMessage(bot.getTranslator().getTranslatedText("Создание класса...", lang), chatId);

                ArrayList<Long> students = DBManager.getIdsByUsernames(usernames);
                if (students == null || students.size() != usernames.size()) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось получить учеников, возможно они не зарегистрированы в боте.", lang), chatId, 20000, user);
                    return;
                }

                if (!DBManager.createClass(user.getCurrentNewClassName(), chatId, students)) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось создать класс, попробуйте снова...", lang), chatId, 10000, user);
                    return;
                }
                user.setClassCount(user.getClassCount() + 1);
                user.setCurrentNewClassName(null);
                if (!bot.saveUser(user)) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", lang), chatId, 10000, user);
                    return;
                }

                if (user.getLastMessageId() != null)
                    bot.deleteMessage(user.getLastMessageId(), chatId);
                if (user.getCurrentStartQuizClassMessageId() != null)
                    bot.deleteMessage(user.getCurrentStartQuizClassMessageId(), chatId);
                if (user.getCurrentMyClassesMessageId() != null)
                    bot.deleteMessage(user.getCurrentMyClassesMessageId(), chatId);
                if (user.getCurrentStartQuizTestMessageId() != null)
                    bot.deleteMessage(user.getCurrentStartQuizTestMessageId(), chatId);
                if (user.getCurrentMyTestsMessageId() != null)
                    bot.deleteMessage(user.getCurrentMyTestsMessageId(), chatId);

                bot.sendMessage(bot.getTranslator().getTranslatedText("Класс успешно создан!", lang), chatId);
            }
            case "create_test" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage(bot.getTranslator().getTranslatedText("Вы отменили загрузку теста.", lang), chatId, 10000, user);
                        user.setState("default");
                        if (!bot.saveUser(user))
                            bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", lang), chatId, 10000, user);
                        return;
                    }
                    bot.sendMessage(bot.getTranslator().getTranslatedText("Вы можете отменить загрузку файла с помощью команды /exit.", lang), chatId);
                    return;
                }
                bot.sendMessage(bot.getTranslator().getTranslatedText("Пожалуйста, отправьте файл.", lang), chatId);
            }
            case "adding_student" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage(bot.getTranslator().getTranslatedText("Изменение класса отменено.", lang), chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Вы не можете отправлять команды во время изменения класса (/exit для отмены изменения класса).", lang), chatId, 20000, user);
                    return;
                }
                StudentClass chosenClass = user.getCurrentChangingClass();
                if (chosenClass == null) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось получить текущий класс, попробуйте ещё раз...", lang), chatId, 10000, user);
                    bot.sendClasses(chatId, user);
                    return;
                }

                ArrayList<String> username = new ArrayList<>();
                username.add(msg);
                ArrayList<Long> userId = DBManager.getIdsByUsernames(username);
                if (userId == null){
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Этот пользователь не зарегистрировал в боте.", lang), chatId, 15000, user);
                    return;
                }
                if (chosenClass.getStudents().contains(userId.getFirst())){
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Этого пользователя уже есть в вашем классе!", lang), chatId, 10000, user);
                    return;
                }
                ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
                newSetOfStudents.add(userId.getFirst());
                DBManager.deleteClass(chatId, chosenClass.getName());
                DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);
                user.setCurrentChangingClass(null);
                user.setState("default");
                if (!bot.saveUser(user)) {
                    bot.alertMessage(bot.getTranslator().getTranslatedText("Не удалось обновить состояние пользователя, попробуйте ещё раз...", lang), chatId, 10000, user);
                }
                bot.alertMessage(bot.getTranslator().getTranslatedText("Пользователь успешно добавлен!", lang), chatId, 10000, user);
                bot.sendClasses(chatId, user);
            }
        }
    }
}
