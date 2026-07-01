package org.example.spring;

import org.example.bot.RateLimiterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {
    @Autowired
    private RateLimiterManager rateLimiterManager;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(
                new RateLimitFilter(rateLimiterManager)
        );
        reg.addUrlPatterns("/api/*");
        reg.setOrder(0);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<TelegramInitDataFilter> telegramFilter() {
        FilterRegistrationBean<TelegramInitDataFilter> reg = new FilterRegistrationBean<>(
                new TelegramInitDataFilter(botToken)
        );
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}