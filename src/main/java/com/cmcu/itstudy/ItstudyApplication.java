package com.cmcu.itstudy;

import com.cmcu.itstudy.config.PayOsProperties;
import com.cmcu.itstudy.config.SupabaseProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({PayOsProperties.class, SupabaseProperties.class})
@EnableScheduling
public class ItstudyApplication {

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(ItstudyApplication.class, args);
	}

}

