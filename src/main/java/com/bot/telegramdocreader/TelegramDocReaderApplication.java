package com.bot.telegramdocreader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class TelegramDocReaderApplication {

    public static void main(String[] args) {
    
        SpringApplication.run(TelegramDocReaderApplication.class, args);

        System.out.println("Bot Corriendo a leer documentos...");
    }
}