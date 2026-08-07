package com.cmcu.itstudy;

import com.cmcu.itstudy.config.PayOsProperties;
import com.cmcu.itstudy.config.SupabaseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({PayOsProperties.class, SupabaseProperties.class})
@EnableScheduling
public class ItstudyApplication {

	public static void main(String[] args) {
		SpringApplication.run(ItstudyApplication.class, args);
	}

}
