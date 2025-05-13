package com.bot.telegramdocreader.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import com.bot.telegramdocreader.dto.TransferDTO;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExportExcel {
    private static Workbook workbook;
    private static String fileName;

    public static String saveExcelFile() throws IOException {
        if (workbook == null || fileName == null) {
            return "Error: No hay archivo Excel para guardar";
        }

        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
            workbook.close();
            return "Archivo Excel guardado exitosamente en: " + fileName;
        } catch (IOException e) {
            throw new IOException("Error al guardar archivo Excel: " + e.getMessage(), e);
        }
    }

    public static String exportTransferToExcel(TransferDTO transferencia) throws IOException {
        if (transferencia == null) {
            throw new IllegalArgumentException("La transferencia no puede ser nula");
        }

        workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Emisor");

        // Crear estilo para encabezados
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Crear estilo para datos
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        dataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Encabezados
        Row headerRow = sheet.createRow(0);
        String[] headers;
        if ("PREX".equals(transferencia.getBank())) {
            headers = new String[]{"Fecha", "Tipo de Operación", "Cuit", "Monto Bruto", "CBU/CVU Destino", "Cuenta Destino"};
        } else {
            headers = new String[]{"Fecha", "Tipo de Operación", "Cuit", "Monto Bruto", "Banco Receptor"};
        }
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        Row dataRow = sheet.createRow(1);
        Cell dateCell = dataRow.createCell(0);
        dateCell.setCellValue(transferencia.getDate());
        dateCell.setCellStyle(dataStyle);

        Cell typeCell = dataRow.createCell(1);
        typeCell.setCellValue(transferencia.getTypeOFTransfer());
        typeCell.setCellStyle(dataStyle);

        Cell cuitCell = dataRow.createCell(2);
        cuitCell.setCellValue(transferencia.getCuit());
        cuitCell.setCellStyle(dataStyle);

        Cell amountCell = dataRow.createCell(3);
        amountCell.setCellValue(transferencia.getAmount());
        amountCell.setCellStyle(dataStyle);

        Cell bankCell = dataRow.createCell(4);
        bankCell.setCellValue(transferencia.getBank());
        bankCell.setCellStyle(dataStyle);
        
        if ("PREX".equals(transferencia.getBank())) {
            Cell cbuCell = dataRow.createCell(5);
            cbuCell.setCellValue(transferencia.getCbuDestino());
            cbuCell.setCellStyle(dataStyle);

            Cell accountCell = dataRow.createCell(6);
            accountCell.setCellValue(transferencia.getCuentaDestino());
            accountCell.setCellStyle(dataStyle);
        }
        
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

        return fileName;
    }

}