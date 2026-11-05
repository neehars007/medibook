package com.coolapp.medibook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// Login is handled by our own JWT AuthService, so Spring's default in-memory user is disabled.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class MedibookApplication {

	public static void main(String[] args) {
		SpringApplication.run(MedibookApplication.class, args);
	}

}
