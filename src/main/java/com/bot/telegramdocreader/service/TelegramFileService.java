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
            // Comparar duplicados considerando todos los campos relevantes
            boolean esDuplicada = transferencias.stream().anyMatch(t ->
                t.getDate().equals(transferencia.getDate()) &&
                t.getTypeOFTransfer().equals(transferencia.getTypeOFTransfer()) &&
                t.getCuit().equals(transferencia.getCuit()) &&
                t.getAmount().equals(transferencia.getAmount()) &&
                t.getBank().equals(transferencia.getBank())
            );
            if (esDuplicada) {
                // Permitir guardar el Excel aunque sea duplicada, pero informar al usuario
                String excelFilePath = ExportExcel.exportTransferToExcel(transferencia);
                ExportExcel.saveExcelFile();
                return "Advertencia: La transferencia ya ha sido procesada, pero el archivo Excel se guardó nuevamente en: " + excelFilePath;
            }
            // Agregar la transferencia a la lista
            this.transferencias.add(transferencia);
            // Generar y guardar un archivo Excel único para cada transferencia
            String excelFilePath = ExportExcel.exportTransferToExcel(transferencia);
            ExportExcel.saveExcelFile();
            return "Archivo Excel guardado exitosamente en: " + excelFilePath;
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

        File folder = new File("excelFolder");
        File[] excelFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Transferencias Concatenadas");

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

            // Agregar datos de todas las transferencias
            int rowNum = 1;
            for (TransferDTO transferencia : transferencias) {
                Row row = sheet.createRow(rowNum++);
                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(transferencia.getDate());
                dateCell.setCellStyle(dataStyle);

               /*  Cell photoCell = row.createCell(1);
                photoCell.setCellValue(transferencia.getPhotoName() != null ? transferencia.getPhotoName() : "");
                photoCell.setCellStyle(dataStyle);
                */

                Cell typeCell = row.createCell(1);
                typeCell.setCellValue(transferencia.getTypeOFTransfer());
                typeCell.setCellStyle(dataStyle);

                Cell cuitCell = row.createCell(2);
                Cell bankCell = row.createCell(4);
                if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("PREX")) {
                    cuitCell.setCellValue(transferencia.getCbuDestiny() != null ? transferencia.getCbuDestiny() : "");
                    bankCell.setCellValue(transferencia.getAccountDestiny() != null ? transferencia.getAccountDestiny() : "");
                } else {
                    cuitCell.setCellValue(transferencia.getCuit());
                    bankCell.setCellValue(transferencia.getBank());
                }
                cuitCell.setCellStyle(dataStyle);

                Cell amountCell = row.createCell(3);
                amountCell.setCellValue(transferencia.getAmount());
                amountCell.setCellStyle(dataStyle);

                bankCell.setCellStyle(dataStyle);

               /* Cell statusCell = row.createCell(6);
                statusCell.setCellValue(transferencia.getStatusOp() != null ? transferencia.getStatusOp() : "");
                statusCell.setCellStyle(dataStyle);
                */ 
            }

            // Ajustar el ancho de las columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            String timeStap = String.valueOf(System.currentTimeMillis());
            String fileName = EXCEL_FOLDER + "transferencias_concatenadas" + timeStap + ".xlsx";
            java.io.FileOutputStream fileOut = new java.io.FileOutputStream(fileName);
            workbook.write(fileOut);
            fileOut.close();

            if (excelFiles != null) {
                for (File excelFile : excelFiles) {
                    if (!excelFile.delete()) {
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

    public java.io.File downloadFileByFileId(String fileId, String botToken) throws IOException {
        try {
            String url = "https://api.telegram.org/file/bot" + botToken + "/" + getFilePathFromTelegram(fileId, botToken);
            java.net.URL downloadUrl = new java.net.URL(url);
            java.io.InputStream in = downloadUrl.openStream();
            java.io.File tempFile = java.io.File.createTempFile("telegram_photo_", ".jpg");
            java.nio.file.Files.copy(in, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            in.close();
            return tempFile;
        } catch (Exception e) {
            throw new IOException("Error descargando archivo de Telegram: " + e.getMessage(), e);
        }
    }

    private String getFilePathFromTelegram(String fileId, String botToken) throws IOException {
        String url = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
        java.net.URL apiUrl = new java.net.URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("No se pudo obtener el file_path de Telegram. Código: " + responseCode);
        }
        java.io.InputStream is = conn.getInputStream();
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        String response = s.hasNext() ? s.next() : "";
        s.close();
        is.close();
        int idx = response.indexOf("\"file_path\":");
        if (idx == -1) throw new IOException("file_path no encontrado en la respuesta de Telegram");
        int start = response.indexOf('"', idx + 12) + 1;
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }
}
