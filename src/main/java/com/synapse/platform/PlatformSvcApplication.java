package com.synapse.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class PlatformSvcApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlatformSvcApplication.class, args);
	}

}
