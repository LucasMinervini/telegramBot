package com.bot.telegramdocreader.bot;

import com.bot.telegramdocreader.dto.TransferDTO;
import com.bot.telegramdocreader.service.DocumentProcessingService;
import com.bot.telegramdocreader.service.TelegramFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramDocBot extends TelegramLongPollingBot {

    private String botUsername;
    private String botToken;
    private DocumentProcessingService documentProcessingService;
    private TelegramFileService telegramFileService;

    // Constructor con inyección de dependencias
    public TelegramDocBot(@Value("${telegram.bot.token}") String botToken,
                          @Value("${telegram.bot.username}") String botUsername) {
        this.botToken = botToken;
        this.botUsername = botUsername;
    }

    @Autowired
    public void setDocumentProcessingService(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @Autowired
    public void setTelegramFileService(TelegramFileService telegramFileService) {
        this.telegramFileService = telegramFileService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasDocument()) {
            handleDocumentMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handleDocumentMessage(Update update) {
        Document doc = update.getMessage().getDocument();
        String botToken = getBotToken();
        String chatId = update.getMessage().getChatId().toString();

        try {
            // Procesar el documento
            String result = documentProcessingService.processDocument(doc, botToken);
            
            // Crear botón para descargar Excel
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            
            InlineKeyboardButton downloadButton = new InlineKeyboardButton();
            downloadButton.setText("Descargar Excel");
            downloadButton.setCallbackData("download_excel");
            rowInline.add(downloadButton);
            rowsInline.add(rowInline);
            markupInline.setKeyboard(rowsInline);

            // Enviar mensaje con el botón
            SendMessage response = new SendMessage();
            response.setChatId(chatId);
            response.setText("Texto extraído:\n\n" + result);
            response.setReplyMarkup(markupInline);
            execute(response);

        } catch (Exception e) {
            e.printStackTrace();
            try {
                execute(new SendMessage(chatId, "Error al procesar el documento."));
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void handleCallbackQuery(Update update) {
        String chatId = update.getCallbackQuery().getMessage().getChatId().toString();
        String callbackData = update.getCallbackQuery().getData();

        if ("download_excel".equals(callbackData)) {
            try {
                TransferDTO lastTransfer = documentProcessingService.getLastTransfer();
                SendDocument sendDocument = telegramFileService.createExcelSendDocument(chatId, lastTransfer);
                if (sendDocument != null) {
                    execute(sendDocument);
                } else {
                    execute(new SendMessage(chatId, "Error al generar el archivo Excel."));
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
                try {
                    execute(new SendMessage(chatId, "Error al enviar el archivo Excel."));
                } catch (TelegramApiException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
