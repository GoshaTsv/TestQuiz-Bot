package org.example.spring;

import org.example.bot.TelegramBot;
import org.example.classes.User;
import org.example.classes.appLinking.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

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
    public void addClassFromWeb(@RequestBody org.example.spring.Message req) throws IOException {
        telegramBot.handleQuizFromServer(req);
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

    @GetMapping("api/geterror")
    public ResponseEntity<String> getErrorMessage(@RequestParam("content") String content) {
        return ResponseEntity.ok(Test.checkForTest(content));
    }
}
