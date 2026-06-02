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
                userCurrent.setQuizState(1);
                userCurrent.setCurrentQuiz(quiz);
                try {
                    Thread.sleep(500);
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
                            callbacks.add("ans_" + j);

                        System.out.println("Callbacks: " + callbacks);

                        bot.sendMessage("1Вопрос #" + count + ": " + question.question, x, variants, callbacks);
                        count++;
                        userCurrent.setCorrectAnswer(String.valueOf(rightVar));
                        userCurrent.setPrevType("var");
                        return;
                    } else {
                        bot.sendMessage("1Вопрос #" + count + ": " + question.question, x);
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
}
