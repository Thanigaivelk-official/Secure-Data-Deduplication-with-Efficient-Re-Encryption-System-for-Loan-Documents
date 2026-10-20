package com.example.loandocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SecureLoanDeduplicationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureLoanDeduplicationApplication.class, args);
    }
}
