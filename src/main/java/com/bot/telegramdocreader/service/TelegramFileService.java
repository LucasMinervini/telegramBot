package com.bot.telegramdocreader.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.bot.telegramdocreader.dto.TransferDTO;

@Service
public class TelegramFileService {

    private List<TransferDTO> transferencias;
    private static final String EXCEL_FOLDER = "excelsConcatenados/";
    private final GoogleDriveService googleDriveService;

    public TelegramFileService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
        this.transferencias = new ArrayList<>();
        // Crear el directorio si no existe
        new File(EXCEL_FOLDER).mkdirs();
    }



    public String createExcelFile(TransferDTO transferencia) {
        if (transferencia == null) {
            return "Error: No hay transferencia disponible";
        }
        try {
            // Agregar la transferencia a la lista
            this.transferencias.add(transferencia);
            // Generar el archivo Excel en memoria
            ExportExcel.exportTransferToExcel(transferencia);
            // Guardar el archivo Excel
            return ExportExcel.saveExcelFile();
        } catch (IllegalArgumentException e) {
            return "Error de validación: " + e.getMessage();
        } catch (IOException e) {
            return "Error al crear o guardar el archivo Excel: " + e.getMessage();
        } catch (Exception e) {
            return "Error inesperado al procesar el archivo: " + e.getMessage();
        }
    }

    public String createConcatenatedExcelFile() {
        if (transferencias.isEmpty()) {
            return "Error: No hay transferencias para concatenar";
        }

        // Obtener la lista de archivos Excel en la carpeta
        File folder = new File("excelFolder");
        File[] excelFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Transferencias Concatenadas");

            // Crear estilo para encabezados en negrita
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Crear encabezados
            Row headerRow = sheet.createRow(0);
            Cell headerCell0 = headerRow.createCell(0);
            headerCell0.setCellValue("Fecha");
            headerCell0.setCellStyle(headerStyle);

            Cell headerCell1 = headerRow.createCell(1);
            headerCell1.setCellValue("Tipo de Operación");
            headerCell1.setCellStyle(headerStyle);

            Cell headerCell2 = headerRow.createCell(2);
            headerCell2.setCellValue("CUIT");
            headerCell2.setCellStyle(headerStyle);

            Cell headerCell3 = headerRow.createCell(3);
            headerCell3.setCellValue("Monto Bruto");
            headerCell3.setCellStyle(headerStyle);

            Cell headerCell4 = headerRow.createCell(4);
            headerCell4.setCellValue("Banco Receptor");
            headerCell4.setCellStyle(headerStyle);

            // Agregar datos de todas las transferencias
            int rowNum = 1;
            for (TransferDTO transferencia : transferencias) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(transferencia.getDate());
                row.createCell(1).setCellValue(transferencia.getTypeOFTransfer());
                row.createCell(2).setCellValue(transferencia.getCuit());
                row.createCell(3).setCellValue(transferencia.getAmount());
                row.createCell(4).setCellValue(transferencia.getBank());
            }

            // Ajustar el ancho de las columnas
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Guardar el archivo
            String timeStap = String.valueOf(System.currentTimeMillis());
            String fileName = EXCEL_FOLDER + "transferencias_concatenadas"+ timeStap + ".xlsx";
            java.io.FileOutputStream fileOut = new java.io.FileOutputStream(fileName);
            workbook.write(fileOut);
            fileOut.close();

            // Eliminar los archivos Excel individuales
            if (excelFiles != null) {
                for (File excelFile : excelFiles) {
                    if (excelFile.delete()) {
                    } else {
                        System.out.println("No se pudo eliminar el archivo: " + excelFile.getName());
                    }
                }
            }

            return fileName;

        } catch (Exception e) {
            return "Error al crear el archivo Excel concatenado: " + e.getMessage();
        }
    }

    public String uploadToDrive(String filePath) {
        return googleDriveService.uploadFile(filePath, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
}
