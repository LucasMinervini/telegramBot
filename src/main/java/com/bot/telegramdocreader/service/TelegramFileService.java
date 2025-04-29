package com.bot.telegramdocreader.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import com.bot.telegramdocreader.dto.TransferDTO;

import java.io.File;


@Service
public class TelegramFileService {

    public TelegramFileService() {
    }

    public SendDocument createExcelSendDocument(String chatId, TransferDTO transferencia) {
        if (transferencia == null) {
            return null; // No hay transferencia disponible
        }
        try {
            // Generar el archivo Excel
            String result = ExportExcel.exportTransferToExcel(transferencia);
            
            if (result.startsWith("Error")) {
                return null;
            }
            
            // Obtener el nombre del archivo generado
            String fileName = result.substring(result.lastIndexOf(":") + 2);
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