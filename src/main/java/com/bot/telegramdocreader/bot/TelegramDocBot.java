package com.bot.telegramdocreader.bot;

import com.bot.telegramdocreader.dto.ClientsDTO;
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
import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TelegramDocBot extends TelegramLongPollingBot {

    private String botUsername;
    private String botToken;
    private DocumentProcessingService documentProcessingService;
    private TelegramFileService telegramFileService;
    private java.util.Map<Long, ClientsDTO> mapClient = new java.util.concurrent.ConcurrentHashMap<>();

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

    // Método para enviar mensajes de texto a un chat específico
    private void sendTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
public void onUpdateReceived(Update update) {
    if (update.hasMessage()) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();

        if (message.hasText() && message.getText().equals("/start")) {
            ClientsDTO client = mapClient.computeIfAbsent(chatId, id ->
            ClientsDTO.builder()
            .chatId(id)
            .name(message.getFrom().getFirstName()) 
            .build()
            );

            sendTextMessage(chatId, "¡Hola " + client.getName() + "! 👋 Ya estás listo para enviar comprobantes. Mandame una imagen o PDF para procesar.");
        } else if (message.hasDocument()) {
            handleDocumentMessage(update); 
        }
    } else if (update.hasCallbackQuery()) {
        handleCallbackQuery(update);
    }
}

    private void handleDocumentMessage(Update update) {
        Document doc = update.getMessage().getDocument();
        String botToken = getBotToken();
        Long chatId = update.getMessage().getChatId();

        try {
            // Procesar el documento pasando el chatId correctamente
            String result = documentProcessingService.processDocument(doc, botToken, chatId);

            // Crear botones inline
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();

            InlineKeyboardButton downloadButton = new InlineKeyboardButton();
            downloadButton.setText("Bajar Excel concatenados");
            downloadButton.setCallbackData("download_concat_excel");

            InlineKeyboardButton saveButton = new InlineKeyboardButton();
            saveButton.setText("Guardar Excel");
            saveButton.setCallbackData("save_excel");

            InlineKeyboardButton driveButton = new InlineKeyboardButton();
            driveButton.setText("Guardar en Drive");
            driveButton.setCallbackData("save_to_drive");

            rowInline.add(downloadButton);
            rowInline.add(saveButton);
            rowInline.add(driveButton);
            rowsInline.add(rowInline);
            markupInline.setKeyboard(rowsInline);

            // Enviar mensaje con los botones
            SendMessage response = new SendMessage();
            response.setChatId(chatId.toString());
            response.setText(result);
            response.setReplyMarkup(markupInline);
            execute(response);

        } catch (Exception e) {
            e.printStackTrace();
            try {
                execute(new SendMessage(chatId.toString(), "Error al procesar el documento."));
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

            if ("download_concat_excel".equals(callbackData)) {
                String excelFilePath = telegramFileService.createConcatenatedExcelFile();
                if (!excelFilePath.startsWith("Error")) {
                    // Crear un objeto File con la ruta del Excel concatenado
                    File excelFile = new File(excelFilePath);
                    if (excelFile.exists()) {
                        // Enviar el archivo Excel concatenado como documento usando InputFile
                        SendDocument sendDocument = new SendDocument();
                        sendDocument.setChatId(chatId);
                        sendDocument.setDocument(new InputFile(excelFile));
                        sendDocument.setCaption("Archivo Excel concatenado generado");
                        execute(sendDocument);
                    } else {
                        execute(new SendMessage(chatId, "Error: No se pudo encontrar el archivo Excel concatenado."));
                    }
                } 
            } else if ("save_excel".equals(callbackData)) {
                String excelResult = telegramFileService.createExcelFile(lastTransfer);
                if (!excelResult.startsWith("Error")) {
                    execute(new SendMessage(chatId, "El archivo Excel se ha guardado exitosamente en la carpeta."));
                } else {
                    execute(new SendMessage(chatId, excelResult));
                }
            } else if ("save_to_drive".equals(callbackData)) {
                String excelFilePath = telegramFileService.createConcatenatedExcelFile();
                if (!excelFilePath.startsWith("Error")) {
                    String driveResult = telegramFileService.uploadToDrive(excelFilePath);
                    execute(new SendMessage(chatId, driveResult));
                } else {
                    execute(new SendMessage(chatId, excelFilePath));
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
