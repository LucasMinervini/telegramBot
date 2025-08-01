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
import java.util.Objects;


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

            if (message.hasText()) {
                String messageText = message.getText();
                if (Objects.equals(messageText, "/start")) {
                    ClientsDTO client = mapClient.computeIfAbsent(chatId, id ->
                        ClientsDTO.builder()
                            .chatId(id)
                            .name(message.getFrom().getFirstName())
                            .build()
                    );
                    sendTextMessage(chatId, "¡Hola " + client.getName() + "! 👋 Ya estás listo para enviar comprobantes. Mandame una imagen o PDF para procesar.");
                } else if (Objects.equals(messageText, "/limpiar")) {
                    // Limpiar la lista de transferencias solo para este chat
                    Long currentChatId = chatId;
                    String resultado = telegramFileService.clearTransferencias(currentChatId);
                    sendTextMessage(chatId, resultado);
                } else if (Objects.equals(messageText, "/status")) {
                    // Mostrar el número de transferencias acumuladas para este chat
                    Long currentChatId = chatId;
                    int countForChat = telegramFileService.getTransferenciasCount(currentChatId);
                    int totalCount = telegramFileService.getTransferenciasCount();
                    
                    String mensaje = "En este chat hay " + countForChat + " transferencias.";
                    if (totalCount > countForChat) {
                        mensaje += "\nEn total hay " + totalCount + " transferencias en todos los chats.";
                    }
                    
                    sendTextMessage(chatId, mensaje);
                }
            } else if (message.hasDocument()) {
                handleDocumentMessage(update);
            } else if (message.hasPhoto()) {
                handlePhotoMessage(message);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handlePhotoMessage(Message message) {
        Long chatId = message.getChatId();
        String botToken = getBotToken();
        try {
            
            List<org.telegram.telegrambots.meta.api.objects.PhotoSize> photos = message.getPhoto();
            org.telegram.telegrambots.meta.api.objects.PhotoSize largestPhoto = photos.get(photos.size() - 1);
            String fileId = largestPhoto.getFileId();
            

            org.telegram.telegrambots.meta.api.objects.Document fakeDoc = new org.telegram.telegrambots.meta.api.objects.Document();
            fakeDoc.setFileId(fileId);
            fakeDoc.setFileName("foto_telegram.jpg");
            fakeDoc.setMimeType("image/jpeg");

            
            // Procesar como documento
            String result = documentProcessingService.processDocument(fakeDoc, botToken, chatId);
            
            // Crear botones inline (igual que en handleDocumentMessage)
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();

            InlineKeyboardButton downloadButton = new InlineKeyboardButton();
            downloadButton.setText("Bajar Excel concatenados");
            downloadButton.setCallbackData("download_concat_excel");

            InlineKeyboardButton saveButton = new InlineKeyboardButton();
            saveButton.setText("Guardar Excel");
            saveButton.setCallbackData("save_excel");

           /*  InlineKeyboardButton clearButton = new InlineKeyboardButton();
            clearButton.setText("Limpiar transferencias");
            clearButton.setCallbackData("clear_transfers");
            */
            rowInline.add(downloadButton);
            rowInline.add(saveButton);
            
            List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
           // rowInline2.add(clearButton);
            rowsInline.add(rowInline);
            rowsInline.add(rowInline2);
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
                execute(new SendMessage(chatId.toString(), "Error al procesar la imagen enviada como foto."));
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
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

            //BOTON DE DRIVE 

           /*  InlineKeyboardButton driveButton = new InlineKeyboardButton();
            driveButton.setText("Guardar en Drive");
            driveButton.setCallbackData("save_to_drive");
            */

            InlineKeyboardButton clearButton = new InlineKeyboardButton();
            clearButton.setText("Limpiar transferencias");
            clearButton.setCallbackData("clear_transfers");
            
            rowInline.add(downloadButton);
            rowInline.add(saveButton);
            rowsInline.add(rowInline);
            
            List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
            rowInline2.add(clearButton);
            rowsInline.add(rowInline2);
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

            if (Objects.equals("download_concat_excel", callbackData)) {
                try {
                    // Verificar si hay transferencias disponibles para este chat específico
                    Long currentChatId = Long.parseLong(chatId);
                    boolean hasTransferenciasForChat = telegramFileService.hasTransferenciasForChat(currentChatId);
                    
                    if (!hasTransferenciasForChat) {
                        execute(new SendMessage(chatId, "Error: No hay transferencias para concatenar. Por favor, procese al menos un documento primero."));
                        System.out.println("Error: No hay transferencias para este chat");
                        return;
                    }
                    
                    String excelFilePath = telegramFileService.createConcatenatedExcelFile(currentChatId);
                    if (excelFilePath.startsWith("Error")) {
                        // Mostrar el mensaje de error al usuario
                        execute(new SendMessage(chatId, excelFilePath));
                        System.out.println("Error al crear Excel concatenado: " + excelFilePath);
                    } else {
                        // Crear un objeto File con la ruta del Excel concatenado
                        File excelFile = new File(excelFilePath);
                        if (excelFile.exists()) {
                            // Enviar el archivo Excel concatenado como documento usando InputFile
                            SendDocument sendDocument = new SendDocument();
                            sendDocument.setChatId(chatId);
                            sendDocument.setDocument(new InputFile(excelFile));
                            sendDocument.setCaption("Archivo Excel concatenado generado");
                            execute(sendDocument);
                            
                            // Eliminar el archivo Excel después de enviarlo
                            if (excelFile.delete()) {
                                System.out.println("Archivo Excel concatenado eliminado después de la descarga: " + excelFilePath);
                            } else {
                                System.out.println("No se pudo eliminar el archivo Excel concatenado: " + excelFilePath);
                            }
                            
                            // Ya no limpiamos las transferencias después de enviar el Excel concatenado
                            // para permitir que el usuario pueda descargar múltiples veces
                            // String clearResult = telegramFileService.clearTransferencias(currentChatId);
                            // System.out.println(clearResult);
                        } else {
                            execute(new SendMessage(chatId, "Error: No se pudo encontrar el archivo Excel concatenado."));
                        }
                    }
                } catch (Exception e) {
                    execute(new SendMessage(chatId, "Error al generar Excel concatenado: " + e.getMessage()));
                    System.out.println("Excepción al generar Excel concatenado: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (Objects.equals("save_excel", callbackData)) {
                String excelResult = telegramFileService.createExcelFile(lastTransfer);
                if (!excelResult.startsWith("Error")) {
                    execute(new SendMessage(chatId, "El archivo Excel se ha guardado exitosamente en la carpeta."));
                } else {
                    execute(new SendMessage(chatId, excelResult));
                }
            } else if (Objects.equals("save_to_drive", callbackData)) {
                String excelFilePath = telegramFileService.createConcatenatedExcelFile();
                if (!excelFilePath.startsWith("Error")) {
                    String driveResult = telegramFileService.uploadToDrive(excelFilePath);
                    execute(new SendMessage(chatId, driveResult));
                } else {
                    execute(new SendMessage(chatId, excelFilePath));
                }
            } else if (Objects.equals("clear_transfers", callbackData)) {
                // Limpiar la lista de transferencias solo para este chat
                Long currentChatId = Long.parseLong(chatId);
                String resultado = telegramFileService.clearTransferencias(currentChatId);
                execute(new SendMessage(chatId, resultado));
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
