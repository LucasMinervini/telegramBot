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
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.File;
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
        
            // Crear botones inline
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();

            InlineKeyboardButton downloadButton = new InlineKeyboardButton();
            downloadButton.setText("Descargar Excel");
            downloadButton.setCallbackData("download_excel");

            InlineKeyboardButton saveButton = new InlineKeyboardButton();
            saveButton.setText("Guardar Excel");
            saveButton.setCallbackData("save_excel");

            rowInline.add(downloadButton);
            rowInline.add(saveButton);
            rowsInline.add(rowInline);
            markupInline.setKeyboard(rowsInline);

            // Enviar mensaje con los botones
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

        try {
            TransferDTO lastTransfer = documentProcessingService.getLastTransfer();
            if (lastTransfer == null) {
                execute(new SendMessage(chatId, "No hay transferencias para procesar."));
                return;
            }

            if ("download_excel".equals(callbackData)) {
                String excelFilePath = telegramFileService.createExcelFile(lastTransfer);
                if (!excelFilePath.startsWith("Error")) {
                    // Crear un objeto File con la ruta del Excel
                    File excelFile = new File(excelFilePath);
                    if (excelFile.exists()) {
                        // Enviar el archivo Excel como documento usando InputFile
                        SendDocument sendDocument = new SendDocument();
                        sendDocument.setChatId(chatId);
                        sendDocument.setDocument(new InputFile(excelFile));
                        sendDocument.setCaption("Archivo Excel generado");
                        execute(sendDocument);
                    } else {
                        execute(new SendMessage(chatId, "Error: No se pudo encontrar el archivo Excel generado."));
                    }
                } 
            } else if ("save_excel".equals(callbackData)) {
                String excelResult = telegramFileService.createExcelFile(lastTransfer);
                if (!excelResult.startsWith("Error")) {
                    execute(new SendMessage(chatId, "El archivo Excel se ha guardado exitosamente en la carpeta."));
                } else {
                    execute(new SendMessage(chatId, excelResult));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                execute(new SendMessage(chatId, "Error al procesar la solicitud: " + e.getMessage()));
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    }
}
