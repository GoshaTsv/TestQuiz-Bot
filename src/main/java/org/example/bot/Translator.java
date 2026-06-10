package org.example.bot;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class Translator {
    private final MessageSource messageSource;

    public Translator(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getTranslatedText(String rawString, String lang, Object... args) {
        if (rawString == null || rawString.isEmpty())
            return "";

        if (lang == null || "ru".equalsIgnoreCase(lang))
            return args.length > 0 ? String.format(rawString, args) : rawString;

        if ("by".equalsIgnoreCase(lang))
            lang = "be";

        System.out.println("Lang:" + lang);

        Locale locale = Locale.forLanguageTag(lang);

        try {
            return messageSource.getMessage(rawString, args, locale);
        } catch (NoSuchMessageException e) {
            return args.length > 0 ? String.format(rawString, args) : rawString;
        }
    }
}
