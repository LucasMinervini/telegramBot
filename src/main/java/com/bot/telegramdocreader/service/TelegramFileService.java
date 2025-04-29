package com.bot.telegramdocreader.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import com.bot.telegramdocreader.dto.TransferDTO;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TelegramFileService {

    public TelegramFileService() {
    }

    public SendDocument createExcelSendDocument(String chatId, TransferDTO transferencia) {
        if (transferencia == null) {
            return null; // No hay transferencia disponible
        }
        try {
            // Generar nombre de archivo con timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "Transferencia_" + timestamp + ".xlsx";
            
            // Exportar a Excel y obtener el archivo
            
            File excelFile = new File(fileName);
            
            if (excelFile.exists()) {
                SendDocument sendDocument = new SendDocument();
                sendDocument.setChatId(chatId);
                sendDocument.setDocument(new InputFile(excelFile));
                sendDocument.setCaption("Datos extraidos a excel:");
                return sendDocument;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}