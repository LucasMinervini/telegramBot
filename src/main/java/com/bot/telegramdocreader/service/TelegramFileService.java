package com.bot.telegramdocreader.service;

import java.io.IOException;

import org.springframework.stereotype.Service;


import com.bot.telegramdocreader.dto.TransferDTO;




@Service
public class TelegramFileService {

    public TelegramFileService() {
    }

    public String createExcelFile(TransferDTO transferencia) {
        if (transferencia == null) {
            return "Error: No hay transferencia disponible";
        }
        try {
            // Generar el archivo Excel
            return ExportExcel.exportTransferToExcel(transferencia);
        } catch (IllegalArgumentException e) {
            return "Error de validación: " + e.getMessage();
        } catch (IOException e) {
            return "Error al crear o guardar el archivo Excel: " + e.getMessage();
        } catch (Exception e) {
            return "Error inesperado al procesar el archivo: " + e.getMessage();
        }
    }
    }
