package org.example;

import org.example.bot.TelegramBot;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
@SpringBootApplication
public class Main {
    public static void main(String[] args) throws TelegramApiException, InterruptedException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        TelegramBot bot = new TelegramBot();
        bot.loadUsers();
        botsApi.registerBot(bot);
        Thread.currentThread().join();
        SpringApplication.run(Main.class, args);
    }
}