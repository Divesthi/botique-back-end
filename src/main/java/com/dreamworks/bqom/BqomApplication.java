package com.dreamworks.bqom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BqomApplication {

	public static void main(String[] args) {
		SpringApplication.run(BqomApplication.class, args);
	}

}
