package com.bot.telegramdocreader.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.bot.telegramdocreader.dto.SenderDTO;
import com.bot.telegramdocreader.dto.TransferDTO;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExportExcel {

    public static String exportTransferToExcel(TransferDTO transferencia) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Emisor");

            // Encabezados
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Nombre", "CUIT", "Número de cuenta", "Banco"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(transferencia.getName());
            dataRow.createCell(1).setCellValue(transferencia.getCuit());
            dataRow.createCell(2).setCellValue(transferencia.getAccountNumber());
            dataRow.createCell(3).setCellValue(transferencia.getBank());
            

            // Ajustar ancho de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Generar nombre de archivo con timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            String fileName = "Transferencia_" + timestamp + ".xlsx";
            
            // Guardar archivo
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
            }

            return "Archivo Excel creado exitosamente: " + fileName;
        } catch (IOException e) {
            return "Error al crear archivo Excel: " + e.getMessage();
        }
    }

    
    public static String exportSenderToExcel(SenderDTO sender) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Remitente");

            // Encabezados
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Nombre", "CUIT", "Número de cuenta", "Banco"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(sender.getName());
            dataRow.createCell(1).setCellValue(sender.getCuit());
            dataRow.createCell(2).setCellValue(sender.getAccountNumber());
            dataRow.createCell(3).setCellValue(sender.getBank());

            // Ajustar ancho de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Generar nombre de archivo con timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "Remitente_" + timestamp + ".xlsx";
            
            // Guardar archivo
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
            }
            
            return "Archivo Excel creado exitosamente: " + fileName;
        } catch (IOException e) {
            return "Error al crear archivo Excel: " + e.getMessage();
        }
    }
}