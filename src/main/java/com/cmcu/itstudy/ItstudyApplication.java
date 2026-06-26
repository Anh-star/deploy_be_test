package com.cmcu.itstudy;

import com.cmcu.itstudy.config.PayOsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PayOsProperties.class)
public class ItstudyApplication {

	public static void main(String[] args) {
		SpringApplication.run(ItstudyApplication.class, args);
	}

}
