package org.example.bot;

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
        Locale locale = Locale.forLanguageTag(lang);
        return messageSource.getMessage(rawString, args, locale);
    }
}
