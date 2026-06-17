package org.example.spring;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.bot.RateLimiterManager;
import org.example.bot.TelegramBot;
import org.example.bot.Translator;
import org.example.classes.User;
import org.example.database.DBManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@org.springframework.web.bind.annotation.RestController
public class RestController {
    @Autowired
    private TelegramBot telegramBot;
    @Autowired
    private RateLimiterManager rateLimiter;

    @GetMapping("/health")
    public String index() {
        return "Hello, sufferings!";
    }
    @Configuration
    public static class CorsConfig implements WebMvcConfigurer {
        @Value("${app.allowed-origin}")
        private String allowedOrigin;

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            System.out.println("Allowed: " + allowedOrigin);
            registry.addMapping("/api/**")
                    .allowedOrigins(allowedOrigin)
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders("*")
                    .maxAge(3600);
            registry.addMapping("/health").allowedOrigins("*");
            registry.addMapping("/config.js").allowedOrigins("*");
        }
    }

    @Value("${app.hmac-secret}")
    private String hmacSecret;

    @Value("${app.public-url}")
    private String publicUrl;

    @GetMapping("/config.js")
    public ResponseEntity<String> getConfigJs() {
        String js = "window.APP_SECRET = '" + hmacSecret + "';\n" +
                "window.APP_API_URL = '" + publicUrl + "';\n";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript"))
                .header("Cache-Control", "no-store, no-cache")
                .body(js);
    }

    @PostMapping("/api/messages")
    public ResponseEntity<String> addClassFromWeb(@Valid @RequestBody Message req, HttpServletRequest httpRequest) throws IOException {
        long chatId = (Long) httpRequest.getAttribute("verified_chat_id");
        String response = handleQuizFromServer(req, chatId);

        if (response != null)
            return ResponseEntity.badRequest().body(response);

        return ResponseEntity.ok("OK");
    }
    @GetMapping("/api/importquiz")
    public ResponseEntity<ImportClassRequest> importClassToWeb(HttpServletRequest httpRequest) {
        long chatId = (Long) httpRequest.getAttribute("verified_chat_id");
        User neededUser = telegramBot.getUsers().stream().filter(x -> x.getChatId() == chatId).findFirst().orElse(null);

        if (neededUser == null) {
            System.out.println("Needed user is null");
            return null;
        }

        ImportClassRequest presses = neededUser.getLastWebReqFromUser(neededUser);
        neededUser.setLastWebReq(null);
        return ResponseEntity.ok(presses);
    }

    public String handleQuizFromServer(Message req, long chatId) throws IOException {
        System.out.println("Got a message: " + req.toString());
        String request = req.getRequest();
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
    @GetMapping("/api/language")
    public ResponseEntity<Map<String, String>> getWebLang(@RequestParam(defaultValue = "en") String lang){
        System.out.println("received getWebLang query, lang: " + lang);
        Map<String, String> strings = new HashMap<>();
        Translator translator = telegramBot.getTranslator();
        if (translator == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String[] keys = {
                "quiz_name_field_plchdr", "quest_count", "question_field_plchdr", "choice_var", "choice_ans", "choice_srv", "var_field_plchdr",
                "var_checkbox_right", "ans_field_plchdr", "new_var_btn", "new_quest_btn", "old_quest_btn", "dlt_quest_btn", "add_img_btn", "dlt_img_btn",
                "export_btn", "new_test_btn", "old_test_btn", "image_choice_wrongType", "image_choice_tooBig", "imageReader_error", "get_test_no_data",
                "change_current_question_first", "change_current_question_last", "add_variant_too_much", "repeating_variant", "less_than_two_vars",
                "no_right_vars", "too_much_questions", "no_questions_left_to_delete", "empty_quiz_name", "no_questions_to_add", "json_saved", "web_error",
                "please_add_questions", "unfixed_question", "telegram_non_existent", "no_user_id"
        };
        for (String key : keys) {
            String translated = translator.getTranslatedText(key, lang);
            if (translated != null) {
                strings.put(key, translated);
            }
        }
        return ResponseEntity.ok(strings);

    }
}
