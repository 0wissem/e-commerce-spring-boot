package org.example.springboot0.shared.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class H2ConsoleConfig {

    @Bean
    public ServletRegistrationBean<JakartaWebServlet> h2Console() {
        ServletRegistrationBean<JakartaWebServlet> bean =
                new ServletRegistrationBean<>(new JakartaWebServlet(), "/h2-console/*");
        bean.setLoadOnStartup(1);
        return bean;
    }
}