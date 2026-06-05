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
    private String correctAnswer;
    private String prevType;
    private int correctAnswers;
    private LinkedHashMap<String, Boolean> userAnswers;

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
            case "changingAutoDelay" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage("Изменение отменено.", chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage("Вы не можете отправлять команды во время изменения проекта (/exit для отмены создания класса).", chatId, 20000, user);
                    return;
                }
                int length = Integer.parseInt(msg);
                if (length > 3600){
                    bot.alertMessage("Пожалуйста, введите меньшее время.", chatId, 15000, user);
                    return;
                }

                if (length < 5){
                    bot.alertMessage("Пожалуйста, введите большее время.", chatId, 15000, user);
                    return;
                }

                user.setAutoDeleteLength(length);
                user.setState("default");
                if (!bot.saveUser(user)){
                    bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                    return;
                }

                if (user.getCurrentAutoDeleteSetSecondsMessageId() != null)
                    bot.deleteMessage(user.getCurrentAutoDeleteSetSecondsMessageId(), chatId);

                bot.alertMessage("Время задержки было успешно изменено!", chatId, 10000, user);
            }
            case "class_name" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage("Создание класса отменено.", chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId, 20000, user);
                    return;
                }

                if (msg.replaceAll("\\p{Punct}", "").length() < 2) {
                    bot.alertMessage("Введите корректное имя класса (минимум 2 символа без знаков препинания и пробелов).", chatId, 15000, user);
                    return;
                }

                int doesClassExit = DBManager.doesClassExit(chatId, msg);
                if (doesClassExit == 2) {
                    bot.alertMessage("Произошла ошибка во время проверки имени класса, попробуйте ещё...", chatId, 10000, user);
                    return;
                }
                if (doesClassExit == 1) {
                    bot.alertMessage("У вас уже существует класс с таким именем.", chatId, 10000, user);
                    return;
                }

                user.setState("class_students");
                user.setCurrentNewClassName(msg);
                if (!bot.saveUser(user)) {
                    bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                    return;
                }

                if (user.getLastMessageId() != null)
                    bot.deleteMessage(user.getLastMessageId(), chatId);

                Integer messageId = bot.sendMessage("Перечислите через пробел username'ы учеников без @ (например: ivan victor test).", chatId);

                if (messageId == null) {
                    bot.alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, user);
                    return;
                }

                if (messageId == -1)
                    return;

                user.setLastMessageId(messageId);
                if (!bot.saveUser(user))
                    bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
            }
            case "class_students" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage("Создания класса отменено.", chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage("Вы не можете отправлять команды во время создания класса (/exit для отмены создания класса).", chatId, 20000, user);
                    return;
                }
                ArrayList<String> usernames = new ArrayList<>(Arrays.asList(msg.split(" ")));
                if (usernames.size() < 2) {
                    bot.alertMessage("Класс должен состоять минимум из 2-х учеников.", chatId, 15000, user);
                    return;
                }

                user.setState("default");

                bot.sendMessage("Создание класса...", chatId);

                ArrayList<Long> students = DBManager.getIdsByUsernames(usernames);

                if (students == null || students.size() != usernames.size()) {
                    bot.alertMessage("Не удалось получить учеников, возможно они не зарегистрированы в боте.", chatId, 20000, user);
                    return;
                }

                if (!DBManager.createClass(user.getCurrentNewClassName(), chatId, students)) {
                    bot.alertMessage("Не удалось создать класс, попробуйте снова...", chatId, 10000, user);
                    return;
                }
                user.setClassCount(user.getClassCount() + 1);
                user.setCurrentNewClassName(null);
                if (!bot.saveUser(user)) {
                    bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
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

                bot.sendMessage("Класс успешно создан!", chatId);
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
                        bot.alertMessage("Вы отменили загрузку теста.", chatId, 10000, user);
                        user.setState("default");
                        if (!bot.saveUser(user))
                            bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                        return;
                    }
                    bot.sendMessage("Вы можете отменить загрузку файла с помощью команды /exit.", chatId);
                    return;
                }

                bot.sendMessage("Пожалуйста, отправьте файл.", chatId);
            }
            case "adding_student" -> {
                if (msg.startsWith("/")) {
                    if (msg.startsWith("/exit")) {
                        bot.alertMessage("Изменение класса отменено.", chatId, 10000, user);
                        user.setState("default");
                        bot.saveUser(user);
                        return;
                    }
                    bot.alertMessage("Вы не можете отправлять команды во время изменения класса (/exit для отмены изменения класса).", chatId, 20000, user);
                    return;
                }
                StudentClass chosenClass = user.getCurrentChangingClass();
                if (chosenClass == null) {
                    bot.alertMessage("Не удалось получить текущий класс, попробуйте ещё раз...", chatId, 10000, user);
                    bot.sendClasses(chatId, user);
                    return;
                }

                ArrayList<String> username = new ArrayList<>();
                username.add(msg);
                ArrayList<Long> userId = DBManager.getIdsByUsernames(username);
                if (userId == null){
                    bot.alertMessage("Этот пользователь не зарегистрировал в боте.", chatId, 15000, user);
                    return;
                }
                if (chosenClass.getStudents().contains(userId.getFirst())){
                    bot.alertMessage("Этого пользователя уже есть в вашем классе!", chatId, 10000, user);
                    return;
                }
                ArrayList<Long> newSetOfStudents = chosenClass.getStudents();
                newSetOfStudents.add(userId.getFirst());
                DBManager.deleteClass(chatId, chosenClass.getName());
                DBManager.createClass(chosenClass.getName(), chatId, newSetOfStudents);
                user.setCurrentChangingClass(null);
                user.setState("default");
                if (!bot.saveUser(user)) {
                    bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, user);
                }
                bot.alertMessage("Пользователь успешно добавлен!", chatId, 10000, user);
                bot.sendClasses(chatId, user);
            }
        }
    }
}
