package org.example.classes;
import org.example.bot.TelegramBot;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;
import org.example.database.DBManager;

import java.util.ArrayList;
import java.util.List;

public class Quiz {
    private long teacherId;
    private StudentClass studentClass;
    private Test test;

    public Quiz(long teacherId, StudentClass studentClass, Test test) {
        this.teacherId = teacherId;
        this.studentClass = studentClass;
        this.test = test;
    }

    public long getTeacherId() { return teacherId; }
    public StudentClass getStudentClass() { return studentClass; }
    public Test getTest() { return test; }
    public void setTeacherId(long teacherId) { this.teacherId = teacherId; }
    public void setStudentClass(StudentClass studentClass) { this.studentClass = studentClass; }
    public void setTest(Test test) { this.test = test; }

    public void startQuiz(TelegramBot bot, ArrayList<User> users, long teacherId) {
        Quiz quiz = this;
        User teacher = users.stream().filter(u -> u.getChatId() == teacherId).findFirst().orElse(null);
        String teacherLang = (teacher != null) ? teacher.getLang() : "ru";

        quiz.getStudentClass().getStudents().forEach(x -> {
            User userCurrent = users.stream().filter(user1 -> user1.getChatId() == x).findFirst().orElse(null);
            if (userCurrent == null) {
                System.out.println("User not found in thread when starting test");
                bot.alertMessage(bot.getTranslator().getTranslatedText("something.went.wrong", teacherLang), teacherId,10000, teacher);
                return;
            }

            ArrayList<String> rawTeacherUsername = DBManager.getUsernamesByIds(new ArrayList<>(List.of(teacherId)));

            if (rawTeacherUsername == null || rawTeacherUsername.isEmpty()) {
                bot.alertMessage(bot.getTranslator().getTranslatedText("failed.get.your.username", teacherLang), teacherId, 10000, teacher);
                return;
            }

            ArrayList<String> callbacks = new ArrayList<>();
            callbacks.add("start_quiz_user_" + rawTeacherUsername.getFirst() + "\uD80C\uDE78" + quiz.getTest().getTestName());
            ArrayList<String> buttons = new ArrayList<>();
            buttons.add(bot.getTranslator().getTranslatedText("test.send.confirm", userCurrent.getLang()));
            Integer messageId = bot.sendMessage(bot.getTranslator().getTranslatedText("test.send.message", userCurrent.getLang(), quiz.getTest().getTestName()), x, buttons, callbacks, null );
            userCurrent.setCurrentQuizMessageId(messageId);
            userCurrent.setCurrentQuiz(quiz);

            if (!bot.saveUser(userCurrent))
                bot.alertMessage(bot.getTranslator().getTranslatedText("failed.update.user", userCurrent.getLang()), x, 10000, userCurrent);
        });
    }
    public void startQuizzing(TelegramBot bot, User userCurrent, long chatId, ArrayList<User> users, long studentId){
        Quiz quiz = this;
        User teacher = users.stream().filter(u -> u.getChatId() == quiz.getTeacherId()).findFirst().orElse(null);
        String teacherLang = (teacher != null) ? teacher.getLang() : "ru";
        new Thread(() -> {
            while (userCurrent.getQuizState() != -1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    bot.sendMessage(bot.getTranslator().getTranslatedText("something.went.wrong", teacherLang), chatId);
                }
            }

            if (userCurrent.getCurrentQuizMessageId() != null)
                bot.deleteMessage(userCurrent.getCurrentQuizMessageId(), chatId);

            userCurrent.setQuizState(1);
            userCurrent.setCurrentQuiz(quiz);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Question question = quiz.getTest().getQuestions().getFirst();
            String questionType = question.getType();
            Integer messageId;
            String userLang = userCurrent.getLang();

            if (questionType.equalsIgnoreCase("var")) {
                ArrayList<String> variants = new ArrayList<>(question.getAnswers().keySet());
                ArrayList<String> callbacks = new ArrayList<>();
                for (int j = 0; j < variants.size(); j++)
                    callbacks.add("ans_" + j);

                messageId = bot.sendMessagePhoto(
                        bot.getTranslator().getTranslatedText("question.number", userLang, 1, question.getQuestion()),
                        studentId, question.getImage(), variants, callbacks, userCurrent.getCurrentQuizMessageId()
                );
                userCurrent.setPrevType("var");

            } else if (questionType.equalsIgnoreCase("ans")) {
                messageId = bot.sendMessagePhoto(
                        bot.getTranslator().getTranslatedText("question.number", userLang, 1, question.getQuestion()),
                        studentId, question.getImage(), userCurrent.getCurrentQuizMessageId()
                );
                userCurrent.setPrevType("ans");
            } else {
                ArrayList<String> variants = new ArrayList<>(question.getAnswers().keySet());
                ArrayList<String> callbacks = new ArrayList<>();
                for (int j = 0; j < variants.size(); j++)
                    callbacks.add("srv_" + j);

                messageId = bot.sendMessagePhoto(
                        bot.getTranslator().getTranslatedText("survey.number", userLang, 1, question.getQuestion()),
                        studentId, question.getImage(), variants, callbacks, userCurrent.getCurrentQuizMessageId()
                );
                userCurrent.setPrevType("srv");
            }

            if (!bot.saveUser(userCurrent)) {
                bot.alertMessage(
                        bot.getTranslator().getTranslatedText("failed.update.user", teacherLang),
                        chatId, 10000, userCurrent
                );
                return;
            }

            if (messageId == null) {
                bot.alertMessage(bot.getTranslator().getTranslatedText("failed.get.message.id", teacherLang), chatId, 10000, userCurrent);
                return;
            }

            if (messageId == -1)
                return;

            userCurrent.setCurrentQuizMessageId(messageId);
            if (!bot.saveUser(userCurrent))
                bot.alertMessage(
                        bot.getTranslator().getTranslatedText("failed.update.user", teacherLang),
                        chatId, 10000, userCurrent
                );

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}