package org.example.spring;

import org.example.bot.TelegramBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@org.springframework.web.bind.annotation.RestController
public class RestController {
    @Autowired
    private TelegramBot telegramBot;
    @GetMapping("/health")
    public String index(){
        return "Hello, sufferings!";
    }
    @Configuration
    public class CorsConfig implements WebMvcConfigurer {
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
}
