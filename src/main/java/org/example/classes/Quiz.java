package org.example.classes;
import org.example.bot.TelegramBot;
import org.example.classes.appLinking.Question;
import org.example.classes.appLinking.Test;

import java.util.ArrayList;

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

                Question question = quiz.getTest().questions.getFirst();
                Integer messageId;
                if (question.answers.size() > 1) {
                    ArrayList<String> variants = new ArrayList<>(question.answers.keySet());

                    ArrayList<String> callbacks = new ArrayList<>();

                    for (int j = 0; j < variants.size(); j++)
                        callbacks.add("ans_" + j);

                    System.out.println("Callbacks: " + callbacks);

                    messageId = bot.sendMessage("Вопрос #1: " + question.question, x, variants, callbacks, null);
                    userCurrent.setPrevType("var");

                } else {
                    messageId = bot.sendMessage("Вопрос #1: " + question.question, x);
                    userCurrent.setPrevType("ans");
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
