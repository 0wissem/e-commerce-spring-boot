package org.example.springboot0;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringBoot0Application {

    public static void main(String[] args) {
        SpringApplication.run(SpringBoot0Application.class, args);
    }

}
