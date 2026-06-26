package com.cmcu.itstudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ItstudyApplication {

	public static void main(String[] args) {
		String pass = System.getenv("SPRING_DATASOURCE_PASSWORD");
		String url = System.getenv("SPRING_DATASOURCE_URL");
		System.out.println("[DEBUG] SPRING_DATASOURCE_URL: " + url);
		if (pass != null) {
			System.out.println("[DEBUG] SPRING_DATASOURCE_PASSWORD length: " + pass.length());
			if (pass.length() >= 3) {
				System.out.println("[DEBUG] SPRING_DATASOURCE_PASSWORD starts with: " + pass.substring(0, 3));
				System.out.println("[DEBUG] SPRING_DATASOURCE_PASSWORD ends with: " + pass.substring(pass.length() - 3));
			}
		} else {
			System.out.println("[DEBUG] SPRING_DATASOURCE_PASSWORD is null");
		}
		SpringApplication.run(ItstudyApplication.class, args);
	}

}
