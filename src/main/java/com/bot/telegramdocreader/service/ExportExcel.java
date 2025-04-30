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

    public static String exportTransferToExcel(TransferDTO transferencia) throws IOException {
        if (transferencia == null) {
            throw new IllegalArgumentException("La transferencia no puede ser nula");
        }

        String fileName = null;
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
            fileName = System.getProperty("user.dir") + "/excelFolder/Transferencia_" + timestamp + ".xlsx";
            
            // Crear directorio si no existe
            java.io.File directory = new java.io.File(System.getProperty("user.dir") + "/excelFolder");
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    throw new IOException("No se pudo crear el directorio para los archivos Excel");
                }
            }
            
            // Guardar archivo
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
                System.out.println("Archivo Excel guardado en: " + fileName);
            }

            return "Archivo Excel creado exitosamente." ;
        } catch (IOException e) {
            throw new IOException("Error al crear archivo Excel: " + e.getMessage(), e);
        }
    }

    
    public static String exportSenderToExcel(SenderDTO sender) throws IOException {
        if (sender == null) {
            throw new IllegalArgumentException("El remitente no puede ser nulo");
        }

        String fileName = null;
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

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            fileName = System.getProperty("user.dir") + "/excelFolder/Transferencia_" + timestamp + ".xlsx";
            
            // Crear directorio si no existe
            java.io.File directory = new java.io.File(System.getProperty("user.dir") + "/excelFolder");
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    throw new IOException("No se pudo crear el directorio para los archivos Excel");
                }
            }
            
            // Guardar archivo
            try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
                workbook.write(fileOut);
                System.out.println("Archivo Excel guardado en: " + fileName);
            }

            return "Archivo Excel creado exitosamente.";
            
        } catch (IOException e) {
            throw new IOException("Error al crear archivo Excel: " + e.getMessage(), e);
        }
    }
}