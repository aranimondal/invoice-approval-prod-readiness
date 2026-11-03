package com.invoiceapproval.api.config;

import com.invoiceapproval.api.auth.RequireRoleInterceptor;
import com.invoiceapproval.api.auth.SessionAuthFilter;
import com.invoiceapproval.api.auth.SessionStore;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Bean
    public FilterRegistrationBean<SessionAuthFilter> sessionAuthFilter(SessionStore sessions) {
        FilterRegistrationBean<SessionAuthFilter> registration = new FilterRegistrationBean<>(new SessionAuthFilter(sessions));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequireRoleInterceptor()).addPathPatterns("/api/**");
    }
}
