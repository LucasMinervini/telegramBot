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

        // Estilo para encabezados (azul oscuro, blanco, centrado, borde)
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        // Estilo para datos (amarillo claro, borde, alineado)
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        dataStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        dataStyle.setAlignment(HorizontalAlignment.CENTER);
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);

        // Encabezados personalizados
        String[] headers = new String[]{"Fecha", "Tipo Operación", "Cuit", "Monto Bruto", "Banco receptor"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Fila de datos (ejemplo, puedes adaptar los campos según TransferDTO)
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