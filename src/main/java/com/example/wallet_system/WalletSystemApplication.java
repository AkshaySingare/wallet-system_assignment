package com.example.wallet_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WalletSystemApplication {

	public static void main(String[] args) {
        System.out.println("Started...");
		SpringApplication.run(WalletSystemApplication.class, args);
	}

}
