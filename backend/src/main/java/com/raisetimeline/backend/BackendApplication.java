package com.raisetimeline.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @ConfigurationPropertiesScan：@ConfigurationProperties の record（AuthProperties など）を Bean として読み込む
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
