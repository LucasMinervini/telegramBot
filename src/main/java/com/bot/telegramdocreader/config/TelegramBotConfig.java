package com.bot.telegramdocreader.config;

import com.bot.telegramdocreader.bot.TelegramDocBot;
import com.bot.telegramdocreader.service.DocumentProcessingService;
import com.bot.telegramdocreader.service.TelegramFileService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Bean
    public TelegramDocBot telegramDocBot() {
        return new TelegramDocBot(botToken, botUsername);
    }

    @Bean
    public DocumentProcessingService documentProcessingService(TelegramDocBot telegramDocBot, TelegramFileService telegramFileService) {
        return new DocumentProcessingService(telegramDocBot, telegramFileService);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramDocBot telegramDocBot) throws TelegramApiException {
        if (botToken == null || botUsername == null) {
            throw new IllegalArgumentException("El token y el nombre de usuario del bot no pueden estar vacíos.");
        }

        // Creamos la instancia de TelegramBotsApi y registramos el bot
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(telegramDocBot);  
        return telegramBotsApi;  
    }
}

