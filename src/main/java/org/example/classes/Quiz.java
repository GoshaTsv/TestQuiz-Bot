package org.example.classes;
import org.example.bot.TelegramBot;
import org.example.classes.appLinking.Image;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;

public class Quiz {
    private long teacherId;
    private StudentClass studentClass;
    private Test test;

    public Quiz(long teacherId, StudentClass studentClass, Test test) {
        this.teacherId = teacherId;
        this.studentClass = studentClass;
        this.test = test;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public StudentClass getStudentClass() {
        return studentClass;
    }

    public Test getTest() {
        return test;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    public void setStudentClass(StudentClass studentClass) {
        this.studentClass = studentClass;
    }

    public void setTest(Test test) {
        this.test = test;
    }

    public void startQuiz(TelegramBot bot, ArrayList<User> users, long chatId) {
        Quiz quiz = this;
        quiz.getStudentClass().getStudents().forEach(x -> {
            User userCurrent = users.stream().filter(user1 -> user1.getChatId() == x).findFirst().orElse(null);
            if (userCurrent == null) {
                System.out.println("User not found in thread when starting test");
                bot.sendMessage("Что-то пошло не так. Попробуйте ещё раз...", chatId);
                return;
            }

            new Thread(() -> {
                while (userCurrent.getQuizState() != -1) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        bot.sendMessage("Что-то пошло не так. Попробуйте ещё раз...", chatId);
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
                Integer photoId = bot.sendPhoto(question, x);
                if (photoId>0){
                    userCurrent.setCurrentQuizPhotoId(photoId);
                }
                if (questionType.equalsIgnoreCase("var")) {
                    ArrayList<String> variants = new ArrayList<>(question.getAnswers().keySet());

                    ArrayList<String> callbacks = new ArrayList<>();

                    for (int j = 0; j < variants.size(); j++)
                        callbacks.add("ans_" + j);

                    System.out.println("Callbacks: " + callbacks);

                    messageId = bot.sendMessage("Вопрос #1: " + question.getQuestion(), x, variants, callbacks, null);
                    userCurrent.setPrevType("var");

                } else if (questionType.equalsIgnoreCase("ans")){
                    messageId = bot.sendMessage("Вопрос #1: " + question.getQuestion(), x);
                    userCurrent.setPrevType("ans");
                }
                else {
                    //place holder for surveys
                    ArrayList<String> variants = new ArrayList<>(question.getAnswers().keySet());
                    ArrayList<String> callbacks = new ArrayList<>();

                    for (int j = 0; j < variants.size(); j++)
                        callbacks.add("srv_" + j);

                    messageId = bot.sendMessage("Опрос #1 + " + question.getQuestion(), x, variants, callbacks, null);
                }

                if (!bot.saveUser(userCurrent)) {
                    bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, userCurrent);
                    return;
                }

                if (messageId == null) {
                    bot.alertMessage("Не удалось получить ID сообщения, возможно оно не будет обрабатываться.", chatId, 10000, userCurrent);
                    return;
                }

                if (messageId == -1)
                    return;

                userCurrent.setCurrentQuizMessageId(messageId);
                if (!bot.saveUser(userCurrent))
                    bot.alertMessage("Не удалось обновить состояние пользователя, попробуйте ещё раз...", chatId, 10000, userCurrent);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }
}
