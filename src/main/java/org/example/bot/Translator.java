package org.example.bot;

import jakarta.annotation.PostConstruct;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class Translator {
    private final MessageSource messageSource;

    public Translator(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getTranslatedText(String rawString, String lang, Object... args) {
        if (rawString == null || rawString.isBlank()) {
            return "";
        }

        System.out.println("Raw string: " + rawString);
        System.out.println("Lang: " + lang);

        Locale locale = Locale.forLanguageTag(lang);
        return messageSource.getMessage(rawString, args, locale);
    }

    @PostConstruct
    public void init() {
        System.out.println("=== Translator initialized ===");
        System.out.println("MessageSource class: " + messageSource.getClass().getName());

        String[] langs = {"ru", "be", "en"};
        for (String lang : langs) {
            Locale locale = Locale.forLanguageTag(lang);
            try {
                String msg = messageSource.getMessage("start.message", null, locale);
                System.out.println("+ " + lang + ": " + msg);
            } catch (Exception e) {
                System.out.println("- " + lang + ": " + e.getMessage());
            }
        }
    }
}
