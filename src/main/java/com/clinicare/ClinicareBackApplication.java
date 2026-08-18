package com.clinicare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClinicareBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClinicareBackApplication.class, args);
	}

}