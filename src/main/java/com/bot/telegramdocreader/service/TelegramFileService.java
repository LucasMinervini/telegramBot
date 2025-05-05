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

    public TelegramFileService() {
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

    public String createConcatenatedExcelFile() {
        if (transferencias.isEmpty()) {
            return "Error: No hay transferencias para concatenar";
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Transferencias Concatenadas");

            // Crear encabezados
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Nombre");
            headerRow.createCell(1).setCellValue("CUIT");
            headerRow.createCell(2).setCellValue("Monto");
            headerRow.createCell(3).setCellValue("Banco");

            // Agregar datos de todas las transferencias
            int rowNum = 1;
            for (TransferDTO transferencia : transferencias) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(transferencia.getName());
                row.createCell(1).setCellValue(transferencia.getCuit());
                row.createCell(2).setCellValue(transferencia.getAmount());
                row.createCell(3).setCellValue(transferencia.getBank());
            }

            // Ajustar el ancho de las columnas
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            // Guardar el archivo
            String fileName = EXCEL_FOLDER + "transferencias_concatenadas.xlsx";
            java.io.FileOutputStream fileOut = new java.io.FileOutputStream(fileName);
            workbook.write(fileOut);
            fileOut.close();

            return fileName;

        } catch (Exception e) {
            return "Error al crear el archivo Excel concatenado: " + e.getMessage();
        }
    }
    }
