package org.example.spring;

import org.example.bot.TelegramBot;
import org.example.classes.User;
import org.example.database.DBManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;

@org.springframework.web.bind.annotation.RestController
public class RestController {
    @Autowired
    private TelegramBot telegramBot;
    @GetMapping("/health")
    public String index() {
        return "Hello, sufferings!";
    }
    @Configuration
    public static class CorsConfig implements WebMvcConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*");
        }
    }
    @CrossOrigin(origins = "*")
    @PostMapping("/api/messages")
    public ResponseEntity<String> addClassFromWeb(@RequestBody org.example.spring.Message req) throws IOException {
        String response = handleQuizFromServer(req);

        if (response != null)
            return ResponseEntity.badRequest().body(response);

        return ResponseEntity.ok("OK");
    }
    @GetMapping("/api/importquiz")
    public ResponseEntity<ImportClassRequest> importClassToWeb(@RequestParam("chat_id") long chatId){
        User neededUser = telegramBot.getUsers().stream().filter(x -> x.getChatId() == chatId).findFirst().orElse(null);

        if (neededUser == null) {
            System.out.println("Needed user is null");
            return null;
        }

        ImportClassRequest presses = neededUser.getLastWebReqFromUser(neededUser);
        neededUser.setLastWebReq(null);
        return ResponseEntity.ok(presses);
    }

    public String handleQuizFromServer(org.example.spring.Message req) throws IOException {
        System.out.println("Got a message: " + req.toString());
        String request = req.getRequest();
        long chatId = Long.parseLong(req.getUserId());
        String jsonData = req.getContent();

        System.out.println("jsonData: " + jsonData);
        User user = telegramBot.getUsers().stream()
                .filter(x -> x.getChatId() == chatId)
                .findFirst()
                .orElse(null);

        if (user == null)
            return telegramBot.getTranslator().getTranslatedText("user.not.found.short", telegramBot.DEFAULT_LANG);

        String fileName = "newTest_" + chatId + "_" + System.currentTimeMillis() + ".json";
        File writtenFile = new File(fileName);

        System.out.println("Clean JSON to write: " + jsonData);
        try (FileWriter fileWriter = new FileWriter(writtenFile)) {
            fileWriter.write(jsonData);
            fileWriter.flush();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(writtenFile))) {
            String line;
            System.out.println("reading file:");
            while ((line = br.readLine()) != null)
                System.out.println(line);

            br.close();
            System.out.println("finished reading the file.");
            System.out.println("JSON сохранен в файл: " + fileName);
            switch (request) {
                case "" -> {
                    return telegramBot.createTest(chatId, user, writtenFile.getName());
                }
                case "exportJSON" -> {
                    InputFile inputFile = new InputFile();
                    inputFile.setMedia(writtenFile);
                    SendDocument sendDocument = new SendDocument(String.valueOf(chatId), inputFile);
                    try {
                        telegramBot.execute(sendDocument);
                    } catch (TelegramApiException e) {
                        System.err.println("An exception while sending document: \" " + inputFile.getMediaName() + "\" to " + chatId);
                    }
                    return null;
                }
                case "changeTest" -> {
                    String prevContent = req.getPrev_content();
                    System.out.println("Prev content: " + prevContent);
                    if (!DBManager.deleteTest(chatId, prevContent))
                        return telegramBot.getTranslator().getTranslatedText("failed.modify.test", telegramBot.DEFAULT_LANG);

                    return telegramBot.createTest(chatId, user, writtenFile.getName());
                }
            }
        }
        finally {
            if (writtenFile.exists()) {
                boolean deleted = writtenFile.delete();
                if (!deleted) {
                    System.err.println("Не удалось удалить файл: " + fileName);
                    writtenFile.deleteOnExit();
                }
            }
        }
        return null;
    }
}
