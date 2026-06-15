package org.example;

import org.example.bot.TelegramBot;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
@SpringBootApplication(scanBasePackages = "org.example")
@EnableScheduling
public class Main {
    public static void main(String[] args) throws TelegramApiException, InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);

        TelegramBot bot = context.getBean(TelegramBot.class);
        bot.loadUsers();
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
    }
}