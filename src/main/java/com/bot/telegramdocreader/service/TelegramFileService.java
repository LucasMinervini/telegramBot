package com.bot.telegramdocreader.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.bot.telegramdocreader.dto.TransferDTO;

@Service
public class TelegramFileService {

    private Map<Long, List<TransferDTO>> transferenciasByChatId;
    private static final String EXCEL_FOLDER = "excelsConcatenados/";
    private final GoogleDriveService googleDriveService;

    public TelegramFileService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
        this.transferenciasByChatId = new HashMap<>();
        // Crear el directorio si no existe
        new File(EXCEL_FOLDER).mkdirs();
    }



    public String createExcelFile(TransferDTO transferencia) {
        return createExcelFile(transferencia, null);
    }
    
    public String createExcelFile(TransferDTO transferencia, Long chatId) {
        if (transferencia == null) {
            return "Error: No hay transferencia disponible";
        }
        try {
            boolean esDuplicada = false;
            
            if (chatId != null) {
                // Si se proporciona chatId, verificar duplicados solo en ese chat
                List<TransferDTO> transferenciasChat = transferenciasByChatId.getOrDefault(chatId, new ArrayList<>());
                esDuplicada = transferenciasChat.stream().anyMatch(t ->
                    Objects.equals(t.getDate(), transferencia.getDate()) &&
                    Objects.equals(t.getTypeOFTransfer(), transferencia.getTypeOFTransfer()) &&
                    Objects.equals(t.getCuit(), transferencia.getCuit()) &&
                    (Objects.equals(t.getAmount(), transferencia.getAmount()) || Objects.equals(t.getAmount(), "$" + transferencia.getAmount())) &&
                    Objects.equals(t.getBank(), transferencia.getBank())
                );
            } else {
                // Si no se proporciona chatId, verificar en todas las transferencias
                for (List<TransferDTO> transferenciasChat : transferenciasByChatId.values()) {
                    if (transferenciasChat.stream().anyMatch(t ->
                    Objects.equals(t.getDate(), transferencia.getDate()) &&
                    Objects.equals(t.getTypeOFTransfer(), transferencia.getTypeOFTransfer()) &&
                    Objects.equals(t.getCuit(), transferencia.getCuit()) &&
                    (Objects.equals(t.getAmount(), transferencia.getAmount()) || Objects.equals(t.getAmount(), "$" + transferencia.getAmount())) &&
                    Objects.equals(t.getBank(), transferencia.getBank())
                )) {
                    esDuplicada = true;
                    break;
                }
                }
            }
            
            if (esDuplicada) {
                // No agregar la transferencia duplicada a la lista
                String excelFilePath = ExportExcel.exportTransferToExcel(transferencia);
                ExportExcel.saveExcelFile();
                return "Advertencia: La transferencia ya ha sido procesada, pero el archivo Excel se guardó nuevamente en: " + excelFilePath;
            }
            
            // Agregar la transferencia a la lista solo si no es duplicada
            if (chatId != null) {
                // Inicializar la lista si no existe para este chatId
                transferenciasByChatId.computeIfAbsent(chatId, k -> new ArrayList<>());
                // Agregar la transferencia a la lista del chatId
                transferenciasByChatId.get(chatId).add(transferencia);
            }
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

    /**
     * Verifica si hay transferencias disponibles para un chat específico
     * @param chatId ID del chat a verificar
     * @return true si hay transferencias para el chat, false en caso contrario
     */
    public boolean hasTransferenciasForChat(Long chatId) {
        if (chatId == null) {
            return false;
        }
        return transferenciasByChatId.containsKey(chatId) && !transferenciasByChatId.get(chatId).isEmpty();
    }
    
    /**
     * Crea un archivo Excel concatenado con todas las transferencias acumuladas
     * @return Ruta del archivo Excel creado o mensaje de error
     */
    public String createConcatenatedExcelFile() {
        return createConcatenatedExcelFile(null);
    }
    
    /**
     * Crea un archivo Excel concatenado con las transferencias de un chat específico o todas si chatId es null
     * @param chatId ID del chat para el cual crear el Excel, o null para incluir todas las transferencias
     * @return Ruta del archivo Excel creado o mensaje de error
     */
    public String createConcatenatedExcelFile(Long chatId) {
        // Si se especifica un chatId, verificamos que haya transferencias para ese chat
        if (chatId != null && !hasTransferenciasForChat(chatId)) {
            return "Error: No hay transferencias para concatenar en este chat.";
        }
        
        // Si no se especifica chatId, verificamos que haya transferencias en general
        if (chatId == null && transferenciasByChatId.isEmpty()) {
            return "Error: No hay transferencias para concatenar";
        }

        File folder = new File(EXCEL_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File[] excelFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));

        if (EXCEL_FOLDER.length() == 10) {
            // Eliminar el archivo más antiguo si hay más de 10 archivos
            if (excelFiles.length > 10) {
                Arrays.sort(excelFiles, Comparator.comparing(File::lastModified));
                File oldestFile = excelFiles[0];
                if (oldestFile.delete()) {
                    System.out.println("Archivo más antiguo eliminado: " + oldestFile.getName());
                } else {
                    System.out.println("No se pudo eliminar el archivo más antiguo: " + oldestFile.getName());
                }
            }
        }

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

            // Eliminar duplicados antes de agregar al Excel
            List<TransferDTO> transferenciasUnicas = new ArrayList<>();
            // Recopilar las transferencias según el chatId
            List<TransferDTO> todasLasTransferencias = new ArrayList<>();
            if (chatId != null) {
                // Si se especifica un chatId, solo incluimos las transferencias de ese chat
                if (transferenciasByChatId.containsKey(chatId)) {
                    todasLasTransferencias.addAll(transferenciasByChatId.get(chatId));
                }
            } else {
                // Si no se especifica chatId, incluimos todas las transferencias
                for (List<TransferDTO> transferenciasChat : transferenciasByChatId.values()) {
                    todasLasTransferencias.addAll(transferenciasChat);
                }
            }
            
            // Filtrar duplicados
            for (TransferDTO transferencia : todasLasTransferencias) {
                boolean yaExiste = false;
                for (TransferDTO unica : transferenciasUnicas) {
                    if (Objects.equals(unica.getDate(), transferencia.getDate()) &&
                        Objects.equals(unica.getTypeOFTransfer(), transferencia.getTypeOFTransfer()) &&
                        Objects.equals(unica.getCuit(), transferencia.getCuit()) &&
                        (Objects.equals(unica.getAmount(), transferencia.getAmount()) || 
                         Objects.equals(unica.getAmount(), "$" + transferencia.getAmount()) ||
                         Objects.equals("$" + unica.getAmount(), transferencia.getAmount())) &&
                        Objects.equals(unica.getBank(), transferencia.getBank())) {
                        yaExiste = true;
                        break;
                    }
                }
                if (!yaExiste) {
                    transferenciasUnicas.add(transferencia);
                }
            }

            // Agregar datos de las transferencias únicas
            int rowNum = 1;
            for (TransferDTO transferencia : transferenciasUnicas) {
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
                    // Si no hay CUIT pero hay titular, usar el nombre del titular
                    if ((transferencia.getCuit() == null || transferencia.getCuit().isEmpty())) {
                        if (transferencia.getTitular() != null && !transferencia.getTitular().isEmpty()) {
                            cuitCell.setCellValue(transferencia.getTitular());
                        } else if (transferencia.getTitularCuentaDestino() != null && !transferencia.getTitularCuentaDestino().isEmpty()) {
                            cuitCell.setCellValue(transferencia.getTitularCuentaDestino());
                        } else {
                            cuitCell.setCellValue(transferencia.getCuit());
                        }
                    } else {
                        cuitCell.setCellValue(transferencia.getCuit());
                    }
                    bankCell.setCellValue(transferencia.getBank());
                }
                cuitCell.setCellStyle(dataStyle);

                Cell amountCell = row.createCell(3);
                amountCell.setCellValue("$" + transferencia.getAmount());
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
            File excelFolder = new File(EXCEL_FOLDER);
            if (!excelFolder.exists()) {
                boolean created = excelFolder.mkdirs();
                if (!created) {
                    System.out.println("No se pudo crear el directorio: " + EXCEL_FOLDER);
                    return "Error: No se pudo crear el directorio para guardar el Excel";
                }
            }
            String fileName = EXCEL_FOLDER + "transferencias_concatenadas" + timeStap + ".xlsx";
            File excelFile = new File(fileName);
            java.io.FileOutputStream fileOut = new java.io.FileOutputStream(excelFile);
            workbook.write(fileOut);
            fileOut.close();
            System.out.println("Excel concatenado creado exitosamente en: " + excelFile.getAbsolutePath());

            // Eliminar los archivos Excel anteriores después de crear el nuevo
            if (excelFiles != null) {
                for (File file : excelFiles) {
                    if (!file.delete()) {
                        System.out.println("No se pudo eliminar el archivo anterior: " + file.getName());
                    } else {
                        System.out.println("Archivo anterior eliminado: " + file.getName());
                    }
                }
            }

            // Limpiamos todas las transferencias después de crear el Excel concatenado
            if (chatId != null) {
                // Si se especificó un chatId, solo limpiamos las transferencias de ese chat
                clearTransferencias(chatId);
                System.out.println("Transferencias del chat " + chatId + " eliminadas después de crear el Excel concatenado");
            } else {
                // Si no se especificó chatId, limpiamos todas las transferencias
                clearTransferencias();
                System.out.println("Todas las transferencias eliminadas después de crear el Excel concatenado");
            }
            System.out.println("Excel concatenado creado y memoria limpiada: " + fileName);
            
            return fileName;

        } catch (Exception e) {
            System.out.println("Error al crear el archivo Excel concatenado: " + e.getMessage());
            e.printStackTrace();
            return "Error al crear el archivo Excel concatenado: " + e.getMessage();
        }
    }

    public String uploadToDrive(String filePath) {
        return googleDriveService.uploadFile(filePath, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
    
    /**
     * Limpia la lista de transferencias acumuladas
     * @return Mensaje indicando que se han limpiado las transferencias
     */
    public String clearTransferencias() {
        int cantidad = getTransferenciasCount();
        this.transferenciasByChatId.clear();
        return "Se han eliminado " + cantidad + " transferencias de la memoria.";
    }
    
    /**
     * Limpia la lista de transferencias acumuladas para un chat específico
     * @param chatId ID del chat para el cual limpiar las transferencias
     * @return Mensaje indicando que se han limpiado las transferencias
     */
    public String clearTransferencias(Long chatId) {
        if (chatId == null) {
            return clearTransferencias();
        }
        
        int cantidad = 0;
        if (this.transferenciasByChatId.containsKey(chatId)) {
            cantidad = this.transferenciasByChatId.get(chatId).size();
            this.transferenciasByChatId.remove(chatId);
        }
        
        return "Se han eliminado " + cantidad + " transferencias del chat " + chatId + ".";
    }
    
    /**
     * Obtiene el número de transferencias acumuladas actualmente
     * @return Número de transferencias
     */
    public int getTransferenciasCount() {
        return getTransferenciasCount(null);
    }
    
    /**
     * Obtiene el número de transferencias acumuladas para un chat específico o todas si chatId es null
     * @param chatId ID del chat para el cual contar las transferencias, o null para contar todas
     * @return Número de transferencias
     */
    public int getTransferenciasCount(Long chatId) {
        if (chatId != null) {
            // Si se especifica un chatId, solo contamos las transferencias de ese chat
            if (transferenciasByChatId.containsKey(chatId)) {
                return transferenciasByChatId.get(chatId).size();
            }
            return 0;
        } else {
            // Si no se especifica chatId, contamos todas las transferencias
            int totalTransferencias = 0;
            for (List<TransferDTO> transferenciasChat : this.transferenciasByChatId.values()) {
                totalTransferencias += transferenciasChat.size();
            }
            return totalTransferencias;
        }
    }
    
    /**
     * Obtiene la lista de transferencias para un chat específico
     * @param chatId ID del chat para el cual obtener las transferencias
     * @return Lista de transferencias del chat especificado
     */
    public List<TransferDTO> getTransferenciasByChatId(Long chatId) {
        if (chatId == null) {
            return new ArrayList<>();
        }
        return this.transferenciasByChatId.getOrDefault(chatId, new ArrayList<>());
    }
    
    /**
     * Obtiene todas las transferencias acumuladas de todos los chats
     * @return Lista con todas las transferencias
     */
    public List<TransferDTO> getAllTransferencias() {
        // Si no hay transferencias, devolver lista vacía
        if (transferenciasByChatId.isEmpty()) {
            return new ArrayList<>();
        }
        // Devolver todas las transferencias de todos los chatIds
        List<TransferDTO> allTransfers = new ArrayList<>();
        for (List<TransferDTO> transfers : transferenciasByChatId.values()) {
            allTransfers.addAll(transfers);
        }
        return allTransfers;
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
