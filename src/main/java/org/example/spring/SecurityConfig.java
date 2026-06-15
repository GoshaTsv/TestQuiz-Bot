package org.example.spring;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {
    @Bean
    public FilterRegistrationBean<HmacAuthFilter> hmacFilter(HmacAuthFilter filter) {
        FilterRegistrationBean<HmacAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}