package com.dreamworks.bqom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableScheduling
public class BqomApplication {

	public static void main(String[] args) {
		SpringApplication.run(BqomApplication.class, args);
	}

}
