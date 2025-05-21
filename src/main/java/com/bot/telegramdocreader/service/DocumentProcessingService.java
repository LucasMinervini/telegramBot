package com.bot.telegramdocreader.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;


import com.bot.telegramdocreader.bot.TelegramDocBot;
import com.bot.telegramdocreader.dto.TransferDTO;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;



import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;

@Service
public class DocumentProcessingService {

    // Declarar el bot como un campo privado
    private TelegramDocBot bot;
    private TransferDTO lastTransfer; // Almacenar la última transferencia procesada
    private List<TransferDTO> transferencias = new ArrayList<>(); // Lista para acumular transferencias
    private TelegramFileService telegramFileService;

    public DocumentProcessingService(TelegramDocBot bot, TelegramFileService telegramFileService) {
        this.bot = bot;
        this.telegramFileService = telegramFileService;
    }

    // Este método se encarga de procesar el documento recibido por el bot
    public String processDocument(Document doc, String botToken) throws Exception {
        String textoExtraido;
        boolean isPdfFormat = isPdf(doc); // Verificamos si es un PDF antes de procesar
        
        try {
            if (isImage(doc)) {
                File file = getFileFromTelegram(doc.getFileId(), botToken);
                URL fileUrl = new URL("https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath());
                InputStream inputStream = fileUrl.openStream();
                BufferedImage image = ImageIO.read(inputStream);
        
                ITesseract instance = new Tesseract();
                instance.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
                instance.setLanguage("spa");
                instance.setPageSegMode(1);
                instance.setOcrEngineMode(1);
        
                textoExtraido = instance.doOCR(image);
                // Nuevo flujo: procesar el texto OCR con processOcrText
                TransferDTO transferencia = mapearTransferencia(textoExtraido, false, doc);
                this.lastTransfer = transferencia;
                if (transferencia != null) {
                    telegramFileService.createExcelFile(transferencia);
                    try {
                        String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                        if (excelResult.startsWith("Error")) {
                            System.out.println("Error al generar el archivo Excel: " + excelResult);
                            return "Error al generar el archivo Excel: " + excelResult;
                        }
                        if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("PREX")) {
                            String formatoPrex = "Fecha: %s \n" +
                                    "Tipo de Operación: %s\n" +
                                    "Monto Bruto: $ %s\n" +
                                    "CBU/CVU Destino: %s\n" +
                                    "Cuenta Destino: %s";
                            return String.format(formatoPrex,
                                    transferencia.getDate() != null ? transferencia.getDate() : "",
                                    transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                                    transferencia.getAmount() != null ? transferencia.getAmount() : "",
                                    transferencia.getCbuDestino() != null ? transferencia.getCbuDestino() : "",
                                    transferencia.getCuentaDestino() != null ? transferencia.getCuentaDestino() : "");
                        } else {
                            String formatoBase = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
                            return String.format(formatoBase,
                                transferencia.getDate(),
                                transferencia.getTypeOFTransfer(),
                                transferencia.getCuit(),
                                transferencia.getAmount(),
                                transferencia.getBank());
                        }
                    } catch (IOException e) {
                        System.out.println("Error al generar el archivo Excel: " + e.getMessage());
                        return "Error al generar el archivo Excel: " + e.getMessage();
                    }
                } else {
                    return textoExtraido;
                }
            } else if (isPdf(doc)) {
                textoExtraido = extractTextFromPdf(doc, botToken);
                if (textoExtraido.startsWith("Error") || textoExtraido.contains("protegido con contraseña")) {
                    return textoExtraido;
                }
            } else {
                return "Formato de archivo no soportado.";
            }
            
            // Mapper Transferencia 
            TransferDTO transferencia = mapearTransferencia(textoExtraido, isPdfFormat, doc);
            this.lastTransfer = transferencia;
            if (transferencia != null) {
                // Agregar la transferencia a TelegramFileService para centralizar la acumulación
                telegramFileService.createExcelFile(transferencia);
                try {
                    String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                    if (excelResult.startsWith("Error")) {
                        System.out.println("Error al generar el archivo Excel: " + excelResult);
                        return "Error al generar el archivo Excel: " + excelResult;
                    }
                    // Retornar los detalles de la transferencia según el tipo de banco
                    if (transferencia.getBank().equalsIgnoreCase("PREX")) {
                        String formatoPrex = "Fecha: %s \n" +
                                "Tipo de Operación: %s\n" +
                                "Monto Bruto: $ %s\n" +
                                "CBU/CVU Destino: %s\n" +
                                "Cuenta Destino: %s";
                        return String.format(formatoPrex,
                                transferencia.getDate() != null ? transferencia.getDate() : "",
                                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                                transferencia.getCbuDestino() != null ? transferencia.getCbuDestino() : "",
                                transferencia.getCuentaDestino() != null ? transferencia.getCuentaDestino() : "");
                    } else {
                        String formatoBase = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
                        return String.format(formatoBase,
                            transferencia.getDate(),
                            transferencia.getTypeOFTransfer(),
                            transferencia.getCuit(),
                            transferencia.getAmount(),
                            transferencia.getBank());
                    }
                } catch (IOException e) {
                    System.out.println("Error al generar el archivo Excel: " + e.getMessage());
                    return "Error al generar el archivo Excel: " + e.getMessage();
                }
            } else {
                return textoExtraido;
            }
        } catch (Exception e) {
            System.out.println("Error en el procesamiento del documento: " + e.getMessage());
            e.printStackTrace();
            return "Error en el procesamiento del documento: " + e.getMessage();
        }
    }
    

    
    // Archivos De Img Soportados
    private boolean isImage(Document doc) {
        String fileName = doc.getFileName().toLowerCase();
        String mimeType = doc.getMimeType().toLowerCase();
        
        // Verificar por extensión de archivo
        boolean isImageByExtension = fileName.endsWith(".jpg") || 
                                   fileName.endsWith(".jpeg") || 
                                   fileName.endsWith(".png") || 
                                   fileName.endsWith(".heic"); 
        
        // Verificar por tipo MIME
        boolean isImageByMimeType = mimeType.startsWith("image/");
        
        return isImageByExtension || isImageByMimeType;
    }
    
    // Método para verificar si el archivo es un PDF
    private boolean isPdf(Document doc) {
        String fileName = doc.getFileName().toLowerCase();
        String mimeType = doc.getMimeType().toLowerCase();
        
        // Verificar por extensión de archivo y tipo MIME
        boolean isPdfByExtension = fileName.endsWith(".pdf");
        boolean isPdfByMimeType = mimeType.equals("application/pdf");
        
        return isPdfByExtension || isPdfByMimeType;
    }

    // Método para extraer texto de un archivo PDF 
    private String extractTextFromPdf(Document doc, String botToken) {
        // Obtener el archivo desde Telegram
        File file = null;
        InputStream inputStream = null;
        PDDocument document = null;
        try {
            file = getFileFromTelegram(doc.getFileId(), botToken);
            URL fileUrl = new URL("https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath());
            
            inputStream = fileUrl.openStream();
            
            // Intentar cargar el documento con una contraseña vacía
            document = PDDocument.load(inputStream, "");
    
            // Configuración para permitir la extracción de texto
            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
            }
    
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            
            if (text.isEmpty()) {
                throw new IOException("No se pudo extraer texto del PDF. El documento podría estar vacío o tener un formato no compatible.");
            }
            
            
            return text;
    
        } catch (InvalidPasswordException e) {
            return "El PDF está protegido con contraseña. Por favor, proporcione la contraseña correcta.";
        } catch (IOException e) {
            return "Error al procesar el PDF: " + e.getMessage();
        } catch (Exception e) {
            return "Error inesperado al procesar el PDF: " + e.getMessage();
        } finally {
            // Cerrar el documento y el InputStream al finalizar
            try {
                if (document != null) {
                    document.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                System.out.println("Error al cerrar los recursos: " + e.getMessage());
            }
        }
    }
    
    

    
    // Método para detectar coincidencias aproximadas de palabras
    private static boolean containsApproxWord(String line, String targetWord, int tolerance) {
        String cleanedLine = line.toLowerCase().replaceAll("[^a-z]", "");
        targetWord = targetWord.toLowerCase();
        int distance = StringUtils.getLevenshteinDistance(cleanedLine, targetWord);
        return distance <= tolerance;
    }

    private File getFileFromTelegram(String fileId, String botToken) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File file = bot.execute(getFile);
        return file;
    }


    public TransferDTO getLastTransfer() {
        return this.lastTransfer;
    }

    private TransferDTO mapearTransferencia(String textoExtraido, boolean isPdfFormat, Document doc) {
        // Detectar si es transferencia de Brubank por texto o por nombre de archivo
        String fileNameLower = doc.getFileName().toLowerCase();
        boolean esBrubank = textoExtraido.toLowerCase().contains("brubank") || fileNameLower.contains("brubank");
        if (esBrubank) {
            TransferDTO transferencia =  TransferDTO.builder().build();
            // Procesar líneas individualmente para extraer cada campo
            String[] lines = textoExtraido.split("\\r?\\n");
            String fecha = "";
            String tipoOperacion = "";
            String cuit = "";
            String monto = "";
            String bancoReceptor = "";
            for (String line : lines) {
                line = line.trim();
                String lower = line.toLowerCase();
                // Fecha: buscar variantes y formatos
                if (lower.startsWith("fecha:")) {
                    fecha = line.replaceFirst("(?i)fecha:", "").trim();
                } else if (fecha.isEmpty() && lower.matches(".*\\d{2}/\\d{2}/\\d{4}.*")) {
                    // Buscar fechas tipo 12/05/2024
                    fecha = line.replaceAll(".*?(\\d{2}/\\d{2}/\\d{4}).*", "$1").trim();
                } else if (fecha.isEmpty() && lower.matches(".*\\d{2}-\\d{2}-\\d{4}.*")) {
                    // Buscar fechas tipo 12-05-2024
                    fecha = line.replaceAll(".*?(\\d{2}-\\d{2}-\\d{4}).*", "$1").trim();
                } else if (fecha.isEmpty() && lower.matches(".*\\d{4}/\\d{2}/\\d{2}.*")) {
                    // Buscar fechas tipo 2024/05/12
                    fecha = line.replaceAll(".*?(\\d{4}/\\d{2}/\\d{2}).*", "$1").trim();
                }
                // Tipo de operación: buscar variantes
                if (lower.startsWith("tipo de operación:") || lower.startsWith("tipo de operacion:")) {
                    tipoOperacion = line.replaceFirst("(?i)tipo de operaci[oó]n:", "").trim();
                } else if (tipoOperacion.isEmpty() && (lower.contains("transferencia") || lower.contains("envío de dinero") || lower.contains("envio de dinero"))) {
                    tipoOperacion = "Transferencia";
                }
                // CUIT/CUIL: buscar variantes y sin etiqueta
                if (lower.startsWith("cuit/cuil:") || lower.startsWith("cuit:") || lower.startsWith("cuil:")) {
                    cuit = line.replaceAll("(?i)cuit/cuil:|cuit:|cuil:", "").replaceAll("[^0-9]", "").trim();
                } else if (cuit.isEmpty() && line.replaceAll("[^0-9]", "").length() == 11) {
                    cuit = line.replaceAll("[^0-9]", "").trim();
                }
                // Monto: buscar variantes y sin etiqueta
                if (lower.startsWith("monto bruto:")) {
                    monto = line.replaceFirst("(?i)monto bruto:", "").replace("$", "").replace(" ", "").trim();
                } else if (monto.isEmpty() && lower.contains("$")) {
                    monto = line.replaceAll("[^0-9.,]", "").trim();
                }
                // Banco receptor: buscar variantes y patrones
                if (lower.startsWith("banco receptor:")) {
                    bancoReceptor = line.replaceFirst("(?i)banco receptor:", "").trim();
                } else if (bancoReceptor.isEmpty() && (lower.contains("capital") || lower.contains("cocos") || lower.contains("banco") || lower.contains("capital sa"))) {
                    bancoReceptor = line.replaceAll("(?i)banco receptor:|banco|receptor|:|\u00a0", "").trim();
                } else if (bancoReceptor.isEmpty() && lower.contains("origen caja de ahorro")) {
                    bancoReceptor = "BRUBANK";
                }
            }
            // Si no se encontró banco receptor explícito, dejar como BRUBANK solo si no hay otro nombre
            if (bancoReceptor.isEmpty()) {
                bancoReceptor = "BRUBANK";
            }
            // Formatear CUIT si es posible
            if (cuit.length() == 11) {
                cuit = cuit.substring(0,2) + "-" + cuit.substring(2,10) + "-" + cuit.substring(10);
            }
            transferencia.setDate(fecha);
            transferencia.setTypeOFTransfer(!tipoOperacion.isEmpty() ? tipoOperacion : "Transferencia");
            transferencia.setCuit(cuit);
            transferencia.setAmount(monto);
            transferencia.setBank(bancoReceptor);
            return transferencia;
        }
        textoExtraido = textoExtraido.replaceAll("[^\\p{Print}\\s]", "").trim();
        String[] lines = textoExtraido.split("\\r?\\n");
    
        String destinatario = "";
        String fecha = "";
        String cuitSender = "";
        String monto = "";
        String bankReceiver = "";
        String tipoOperacion = "";
        String cbuDestino = "";
        String cuentaDestino = "";
        boolean cuitEmisorEncontrado = false;
        
        // Detectar si es PREX analizando las primeras líneas, el texto completo y el nombre del archivo
        boolean isPrex = false;
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String lineaNormalizada = lines[i].replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]", "").toLowerCase();
            if (lineaNormalizada.contains("prex") || containsApproxWord(lineaNormalizada, "prex", 1)) {
                isPrex = true;
                bankReceiver = "PREX";
                break;
            }
        }
        if (!isPrex) {
            String fileName = doc.getFileName().toLowerCase();
            if (fileName.contains("prex")) {
                isPrex = true;
                bankReceiver = "PREX";
            }
        }
        if(textoExtraido.matches("(?i).*\\bprex\\b.*")) {
            isPrex = true;
            bankReceiver = "PREX";
        }
        // Unificación lógica PREX para ambos formatos
        if (isPrex) {
            tipoOperacion = "Transferencia";
            for (String line : lines) {
                String lineaLower = line.toLowerCase().trim();
                String lineaOriginal = line.trim();
                // Extraer monto
                if (lineaLower.contains("enviaste:") || lineaLower.contains("enviaste $") || (lineaLower.contains("$") && monto.isEmpty())) {
                    String montoTemp = lineaOriginal.replaceAll("[^0-9.,]", "").trim();
                    if (!montoTemp.isEmpty()) {
                        monto = montoTemp;
                    }
                }
                // Extraer fecha
                if ((lineaLower.contains("de") && lineaLower.contains("hs") && fecha.isEmpty()) || (lineaLower.matches(".*\\d+.*de.*202\\d.*") && fecha.isEmpty())) {
                    fecha = lineaOriginal;
                }
                // Extraer destinatario
                if (lineaLower.contains("enviaste a:") || lineaLower.contains("destinatario:")) {
                    destinatario = lineaOriginal.replaceAll("(?i)Enviaste a:|Destinatario:", "").trim();
                    if (cuentaDestino.isEmpty()) {
                        cuentaDestino = destinatario;
                    }
                }
                // Extraer SOLO el primer CUIT/CUIL que aparezca después de una referencia clara al emisor
                if (!cuitEmisorEncontrado && (lineaLower.contains("cuit emisor") || lineaLower.contains("cuil emisor") || lineaLower.contains("cuit del emisor") || lineaLower.contains("cuil del emisor") || lineaLower.contains("cuit/cuil emisor") || lineaLower.contains("cuit/cuil del emisor"))) {
                    String posibleCuit = lineaOriginal.replaceAll("[^0-9]", "");
                    if (posibleCuit.length() == 11) {
                        cuitSender = posibleCuit.substring(0,2) + "-" + posibleCuit.substring(2,10) + "-" + posibleCuit.substring(10);
                        cuitEmisorEncontrado = true;
                    }
                } else if (!cuitEmisorEncontrado && (lineaLower.matches(".*cu[il]t.*:.*") || lineaLower.contains("cuit:") || lineaLower.contains("cuil:"))) {
                    // Si no hay referencia explícita al emisor, tomar solo el primer CUIT/CUIL que aparezca en el documento
                    String posibleCuit = lineaOriginal.replaceAll("[^0-9]", "");
                    if (posibleCuit.length() == 11) {
                        cuitSender = posibleCuit.substring(0,2) + "-" + posibleCuit.substring(2,10) + "-" + posibleCuit.substring(10);
                        cuitEmisorEncontrado = true;
                    }
                }
                // Extraer CBU/CVU
                if (lineaLower.matches(".*c[bv]u.*:.*") || lineaLower.contains("destino:")) {
                    cbuDestino = lineaOriginal.replaceAll("(?i)CVU/CBU:|CVU destino:|CBU destino:|Destino:", "").trim();
                }
                // Extraer cuenta destino
                if (lineaLower.contains("cuenta") && lineaLower.contains("destino")) {
                    cuentaDestino = lineaOriginal.replaceAll("(?i)Cuenta destino:?|Cuenta:", "").trim();
                }
            }
            if (!monto.isEmpty() && (!destinatario.isEmpty() || !cuitSender.isEmpty())) {
                return TransferDTO.builder()
                    .name(destinatario)
                    .date(fecha)
                    .typeOFTransfer(tipoOperacion)
                    .cuit(cuitSender)
                    .amount(monto)
                    .bank(bankReceiver)
                    .cbuDestino(cbuDestino)
                    .cuentaDestino(cuentaDestino)
                    .build();
            }
        }

        // Si no es PREX, buscar el tipo de operación en todo el texto
        String textoCompleto = String.join(" ", lines).toLowerCase();
        if (tipoOperacion.isEmpty()) {
            if (textoCompleto.contains("comprobante de transferencia") || 
                textoCompleto.contains("transferencia enviada") || 
                textoCompleto.contains("envío de dinero") || 
                textoCompleto.contains("envio de dinero") || 
                textoCompleto.contains("transferencia realizada") || 
                (textoCompleto.contains("transferencia") && textoCompleto.contains("$"))) {
                tipoOperacion = "Transferencia";
            } else if (textoCompleto.contains("depósito") || textoCompleto.contains("deposito")) {
                tipoOperacion = "Depósito";
            }
        }

        for (int i = 0; i < lines.length; i++) {
            String linea = lines[i];
            String lower = linea.toLowerCase().trim();
            String original = linea.trim();

            // Tipo de operación
            if (tipoOperacion.isEmpty()) {
                if (lower.contains("operación") || lower.contains("operacion") || 
                    lower.contains("tipo") || lower.contains("movimiento")) {
                    if (lower.contains("transfer")) {
                        tipoOperacion = "Transferencia";
                    } else if (lower.contains("depos")) {
                        tipoOperacion = "Depósito";
                    }
                }
                if (lower.contains("de") || 
                lower.contains("hs") || 
                lower.contains("enero") || 
                lower.contains("febrero") || 
                lower.contains("marzo") || 
                lower.contains("abril") || 
                lower.contains("mayo") || 
                lower.contains("junio") || 
                lower.contains("julio") || 
                lower.contains("agosto") || 
                lower.contains("septiembre") || 
                lower.contains("octubre") || 
                lower.contains("noviembre") || 
                lower.contains("diciembre")) {
                    fecha = linea.trim();
                }
            }

            if (isPdfFormat) {
                

                if (lower.contains("prex")) {
                    // Procesar comprobante PREX
                    bankReceiver = "PREX";
                    
                    // Establecer tipo de operación primero
                    if (lower.contains("comprobante de transferencia") || 
                        lower.contains("transferencia enviada") || 
                        lower.contains("envío de dinero") || 
                        lower.contains("envio de dinero")) {
                        tipoOperacion = "Transferencia";
                    }
                    
                    for (String currentLine : lines) {
                        String currentLineLower = currentLine.toLowerCase().trim();
                        String currentLineOriginal = currentLine.trim();
                        
                        // Extraer fecha
                        if (currentLineLower.contains("de") && currentLineLower.contains("hs")) {
                            fecha = currentLineOriginal;
                        }
                        
                        // Extraer destinatario
                        if (currentLineLower.contains("enviaste a:")) {
                            destinatario = currentLineOriginal.replace("Enviaste a:", "").trim();
                        }
                        
                        // Extraer CUIT/CBU
                        if (currentLineLower.contains("cvu/cbu:")) {
                            cbuDestino = currentLineOriginal.replace("CVU/CBU:", "").trim();
                        } else if (currentLineLower.contains("cvu destino:")) {
                            cbuDestino = currentLineOriginal.replace("CVU destino:", "").trim();
                        } else if (currentLineLower.contains("cbu destino:")) {
                            cbuDestino = currentLineOriginal.replace("CBU destino:", "").trim();
                        }
                        
                        // Extraer CUIT/CUIL
                        if (currentLineLower.contains("cuit/cuil:")) {
                            cuitSender = currentLineOriginal.replace("CUIT/CUIL:", "").trim();
                        } else if (currentLineLower.contains("cuit:")) {
                            cuitSender = currentLineOriginal.replace("CUIT:", "").trim();
                        } else if (currentLineLower.contains("cuil:")) {
                            cuitSender = currentLineOriginal.replace("CUIL:", "").trim();
                        }
                        
                        // Extraer monto
                        if (currentLineLower.contains("enviaste") && currentLineLower.contains("$")) {
                            String montoTemp = currentLineOriginal.replaceAll("[^0-9.,]", "").trim();
                            if (!montoTemp.isEmpty()) {
                                monto = montoTemp;
                            }
                        } else if (currentLineLower.contains("$") && monto.isEmpty()) {
                            String montoTemp = currentLineOriginal.replaceAll("[^0-9.,]", "").trim();
                            if (!montoTemp.isEmpty()) {
                                monto = montoTemp;
                            }
                        }
                    }
                    
                    // Si aún no se ha establecido el tipo de operación y hay indicadores
                    if (tipoOperacion.isEmpty() && (lower.contains("transferencia") || lower.contains("envío"))) {
                        tipoOperacion = "Transferencia";
                    }
                    
                    // Retornar el DTO para PREX si tenemos los datos mínimos necesarios
                    if (!monto.isEmpty() && (!destinatario.isEmpty() || !cuitSender.isEmpty())) {
                        return TransferDTO.builder()
                            .name(destinatario)
                            .date(fecha)
                            .typeOFTransfer(tipoOperacion)
                            .cuit(cuitSender)
                            .amount(monto)
                            .bank(bankReceiver)
                            .cbuDestino(cbuDestino)
                            .cuentaDestino(cuentaDestino)
                            .build();
                    }
                }
                
                

                // Banco FUNDRAISER
                if (lower.contains("fundraiser")) {
                    bankReceiver = "FUNDRAISER s.a.s.";
                }

                // Normalizar el nombre del banco si se encontró
                if (!bankReceiver.isEmpty()) {
                    // Eliminar espacios múltiples
                    bankReceiver = bankReceiver.replaceAll("\\s+", " ").trim();
                    
                    // Convertir primera letra de cada palabra a mayúscula
                    String[] palabras = bankReceiver.split(" ");
                    StringBuilder nombreFormateado = new StringBuilder();
                    for (String palabra : palabras) {
                        if (!palabra.isEmpty()) {
                            if (nombreFormateado.length() > 0) nombreFormateado.append(" ");
                            nombreFormateado.append(Character.toUpperCase(palabra.charAt(0)))
                                           .append(palabra.substring(1).toLowerCase());
                        }
                    }
                    bankReceiver = nombreFormateado.toString();
                }

                // Extraer monto
                if (lower.contains("$")) {
                    monto = original.replaceAll("[^0-9.,]", "").trim();
                }
                 //Nombre
                 if (lower.startsWith("a") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    destinatario = original.replaceAll("(?i)a |destinatario:|beneficiario:", "").trim();
                }

                
                //Fecha
                // Verificar si es Mercado Pago y buscar el formato específico
                if ( lower.contains("enero") || 
                lower.contains("febrero") || 
                lower.contains("marzo") ||  
                lower.contains("abril") || 
                lower.contains("mayo") || 
                lower.contains("junio") || 
                lower.contains("julio") ||
                lower.contains("agosto") ||
                lower.contains("septiembre") ||
                lower.contains("octubre") ||
                lower.contains("noviembre") ||
                lower.contains("diciembre")){
                    fecha = extractDate(original);
                } else if (lower.contains("fecha") || lower.contains("fecha de operación") || 
                    lower.matches(".*\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}.*") || 
                    lower.matches(".*\\d{1,2}\\s+de\\s+\\w+\\s+de\\s+\\d{4}.*") 
                    ) {
                    fecha = extractDate(original);
                    // Si no se encontró fecha, intentar limpiar el texto
                    if (fecha.isEmpty()) {
                        String textoLimpio = original.replaceAll("(?i)fecha de operación:|fecha:|:", "").trim();
                        fecha = extractDate(textoLimpio);
                    }
                    // Si aún está vacío, intentar con el texto original
                    if (fecha.isEmpty()) {
                        fecha = original.trim();
                    }

                // Tipo de operación
                if (lower.contains("transferencia") || lower.contains("transferido") ||
                    lower.contains("transferiste") || lower.contains("transferir") || 
                    lower.contains("Transferencia enviada") || lower.contains("transferencia enviada")) {
                    tipoOperacion = "Transferencia";
                    } else if (lower.contains("depósito") || lower.contains("deposito")) {
                        tipoOperacion = "Depósito";
                    }
                }

                // Monto
                if (lower.startsWith("$") || lower.contains("importe") || lower.contains("monto")) {
                    monto = original.replaceAll("(?i)importe:|monto:|\\$", "").trim();
                }
                // CUIT/CUIL - Búsqueda mejorada para el emisor
                if (( lower.contains("cuit emisor") || lower.contains("cuil emisor") || 
                    (lower.contains("cuit") && (lower.contains("de:") || 
                    lower.contains("origen") || lower.contains("emisor"))) || 
                    lower.matches(".*\\d{2}-\\d{8}-\\d{1}.*"))) {
                    
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{2}-\\d{8}-\\d{1}");
                    java.util.regex.Matcher matcher = pattern.matcher(original);
                    if (matcher.find()) {
                        cuitSender = matcher.group();
                        
                    } else {
                        // Si no se encuentra el patrón directo, intentar limpiar el texto
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*", "$1").trim();
                        if (cuitTemp.matches("\\d{11}")) {
                            cuitSender = cuitTemp.substring(0, 2) + "-" + cuitTemp.substring(2, 10) + "-" + cuitTemp.substring(10);
                            
                        }
                    }
                }
                // Banco
                if (lower.contains("neblockchain") || lower.contains("neblockchain sa")) {
                    bankReceiver = "NEBLOCKCHAIN SA";
                } else if (lower.contains("banco") || lower.contains("entidad")) {
                    bankReceiver = original.replaceAll("(?i)|banco:|entidad:|destino:", "").trim();
                } 
                
                else if (lower.contains("para") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    String posibleBanco = original.replaceAll("(?i)para:|destinatario:|beneficiario:", "").trim();
                    if (!posibleBanco.isEmpty() && bankReceiver.isEmpty()) {
                        bankReceiver = posibleBanco;
                    }
                }

               
            } else {
                    // LÓGICA PARA IMÁGENES


                //Nombre
                if (lower.startsWith("a") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    destinatario = original.replaceAll("(?i)a |destinatario:|beneficiario:", "").trim();
                }

                
                //Fecha
                // Verificar si es Mercado Pago y buscar el formato específico
                if ( lower.contains("enero") || 
                lower.contains("febrero") || 
                lower.contains("marzo") ||  
                lower.contains("abril") || 
                lower.contains("mayo") || 
                lower.contains("junio") || 
                lower.contains("julio") ||
                lower.contains("agosto") ||
                lower.contains("septiembre") ||
                lower.contains("octubre") ||
                lower.contains("noviembre") ||
                lower.contains("diciembre")){
                    fecha = extractDate(original);
                } else if (lower.contains("fecha") || lower.contains("fecha de operación") || 
                    lower.matches(".*\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}.*") || 
                    lower.matches(".*\\d{1,2}\\s+de\\s+\\w+\\s+de\\s+\\d{4}.*") 
                    ) {
                    fecha = extractDate(original);
                    // Si no se encontró fecha, intentar limpiar el texto
                    if (fecha.isEmpty()) {
                        String textoLimpio = original.replaceAll("(?i)fecha de operación:|fecha:|:", "").trim();
                        fecha = extractDate(textoLimpio);
                    }
                    // Si aún está vacío, intentar con el texto original
                    if (fecha.isEmpty()) {
                        fecha = original.trim();
                    }

                // Tipo de operación
                if (lower.contains("transferencia") || lower.contains("transferido") ||
                    lower.contains("transferiste") || lower.contains("transferir") || 
                    lower.contains("Transferencia enviada") || lower.contains("transferencia enviada")) {
                    tipoOperacion = "Transferencia";
                    } else if (lower.contains("depósito") || lower.contains("deposito")) {
                        tipoOperacion = "Depósito";
                    }
                }

                // Monto
                if (lower.startsWith("$") || lower.contains("importe") || lower.contains("monto")) {
                    monto = original.replaceAll("(?i)importe:|monto:|\\$", "").trim();
                }
                // CUIT/CUIL - Búsqueda mejorada para el emisor
                if (( lower.contains("cuit emisor") || lower.contains("cuil emisor") || 
                    (lower.contains("cuit") && (lower.contains("de:") || 
                    lower.contains("origen") || lower.contains("emisor"))) || 
                    lower.matches(".*\\d{2}-\\d{8}-\\d{1}.*"))) {
                    
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{2}-\\d{8}-\\d{1}");
                    java.util.regex.Matcher matcher = pattern.matcher(original);
                    if (matcher.find()) {
                        cuitSender = matcher.group();
                        
                    } else {
                        // Si no se encuentra el patrón directo, intentar limpiar el texto
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*", "$1").trim();
                        if (cuitTemp.matches("\\d{11}")) {
                            cuitSender = cuitTemp.substring(0, 2) + "-" + cuitTemp.substring(2, 10) + "-" + cuitTemp.substring(10);
                            
                        }
                    }
                }
                // Banco
                if (lower.contains("neblockchain") || lower.contains("neblockchain sa")) {
                    bankReceiver = "NEBLOCKCHAIN SA";
                } else if (lower.contains("para") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    String posibleBanco = original.replaceAll("(?i)para:|destinatario:|beneficiario:", "").trim();
                    if (!posibleBanco.isEmpty() && bankReceiver.isEmpty()) {
                        bankReceiver = posibleBanco;
                    }
                }

                if (lower.contains("prex") || bankReceiver.equals("prex") || bankReceiver.equals("PREX")) {
                    // Procesar comprobante PREX
                    bankReceiver = "PREX";
                    
                    // Establecer tipo de operación primero
                    if (lower.contains("comprobante de transferencia") || 
                        lower.contains("transferencia enviada") || 
                        lower.contains("envío de dinero") || 
                        lower.contains("envio de dinero")) {
                        tipoOperacion = "Transferencia";
                    }
                    
                    for (String currentLine : lines) {
                        String currentLineLower = currentLine.toLowerCase().trim();
                        String currentLineOriginal = currentLine.trim();
                        
                        // Extraer fecha
                        if (currentLineLower.contains("de") && currentLineLower.contains("hs")) {
                            fecha = currentLineOriginal;
                        }
                        
                        // Extraer destinatario
                        if (currentLineLower.contains("enviaste a:")) {
                            destinatario = currentLineOriginal.replace("Enviaste a:", "").trim();
                        }
                        
                        // Extraer CUIT/CBU
                        if (currentLineLower.contains("cvu/cbu:")) {
                            cbuDestino = currentLineOriginal.replace("CVU/CBU:", "").trim();
                        } else if (currentLineLower.contains("cvu destino:")) {
                            cbuDestino = currentLineOriginal.replace("CVU destino:", "").trim();
                        } else if (currentLineLower.contains("cbu destino:")) {
                            cbuDestino = currentLineOriginal.replace("CBU destino:", "").trim();
                        }
                        
                        // Extraer CUIT/CUIL
                        if (currentLineLower.contains("cuit/cuil:")) {
                            cuitSender = currentLineOriginal.replace("CUIT/CUIL:", "").trim();
                        } else if (currentLineLower.contains("cuit:")) {
                            cuitSender = currentLineOriginal.replace("CUIT:", "").trim();
                        } else if (currentLineLower.contains("cuil:")) {
                            cuitSender = currentLineOriginal.replace("CUIL:", "").trim();
                        }
                        
                        // Extraer monto
                        if (currentLineLower.contains("enviaste") && currentLineLower.contains("$")) {
                            String montoTemp = currentLineOriginal.replaceAll("[^0-9.,]", "").trim();
                            if (!montoTemp.isEmpty()) {
                                monto = montoTemp;
                            }
                        } else if (currentLineLower.contains("$") && monto.isEmpty()) {
                            String montoTemp = currentLineOriginal.replaceAll("[^0-9.,]", "").trim();
                            if (!montoTemp.isEmpty()) {
                                monto = montoTemp;
                            }
                        }
                    }
                    
                    // Si aún no se ha establecido el tipo de operación y hay indicadores
                    if (tipoOperacion.isEmpty() && (lower.contains("transferencia") || lower.contains("envío"))) {
                        tipoOperacion = "Transferencia";
                    }
                    
                    // Retornar el DTO para PREX si tenemos los datos mínimos necesarios
                    if (!monto.isEmpty() && (!destinatario.isEmpty() || !cuitSender.isEmpty())) {
                        return TransferDTO.builder()
                            .name(destinatario)
                            .date(fecha)
                            .typeOFTransfer(tipoOperacion)
                            .cuit(cuitSender)
                            .amount(monto)
                            .bank(bankReceiver)
                            .cbuDestino(cbuDestino)
                            .cuentaDestino(cuentaDestino)
                            .build();
                    }
                }
                
            }
        }
    
        
        if (tipoOperacion.isEmpty()) {
            tipoOperacion = "No especificado";
        }

        // Validar que al menos tengamos algunos datos básicos
        if (!destinatario.isEmpty() || !cuitSender.isEmpty()) {
            TransferDTO transferencia = TransferDTO.builder()
                .name(destinatario)
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuit(cuitSender)
                .amount(monto)
                .bank(bankReceiver)
                .build();
            return transferencia;
        }
        return null;}
    

    private String extractDate(String texto) {


        
        String patronBrubank = "\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4}\\s*-\\s*\\d{2}:\\d{2}";
        java.util.regex.Pattern patternBrubank = java.util.regex.Pattern.compile(patronBrubank, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcherBrubank = patternBrubank.matcher(texto);
        
        if (matcherBrubank.find()) {
            String fechaCompleta = matcherBrubank.group();
            // Extraer solo la parte de la fecha sin la hora
            String[] partes = fechaCompleta.split("-");
            if (partes.length > 0) {
                return convertirFechaTextoANumerica(partes[0].trim());
            }
        }

        // Verificar si es un formato de fecha en texto (dd de mes de yyyy)
        String patronFechaTexto = "\\d{1,2}\\s*(?:de\\s+)?[a-záéíóúñ]+\\s*(?:de\\s+)?\\d{4}";
        java.util.regex.Pattern patternFechaTexto = java.util.regex.Pattern.compile(patronFechaTexto, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcherFechaTexto = patternFechaTexto.matcher(texto);
        
        if (matcherFechaTexto.find()) {
            String fechaTexto = matcherFechaTexto.group();
            // Primero eliminar cualquier 'de' existente y espacios extras
            fechaTexto = fechaTexto.replaceAll("(?i)\\s+de\\s+", " ").trim();
            // Separar las partes
            String[] partes = fechaTexto.split("\\s+");
            if (partes.length == 3) {
                fechaTexto = partes[0] + " de " + partes[1] + " de " + partes[2];
            } else if (partes.length == 2) {
                // Si solo hay dos partes, asumimos que son mes y año
                fechaTexto = "1 de " + partes[0] + " de " + partes[1];
            }
            return convertirFechaTextoANumerica(fechaTexto);
        }
        
        // Si no es formato Mercado Pago, usar los patrones comunes
        String[] patrones = {
            "\\d{2}/\\d{2}/\\d{4}",      // dd/mm/yyyy
            "\\d{2}-\\d{2}-\\d{4}",      // dd-mm-yyyy
            "\\d{1,2}/\\d{1,2}/\\d{4}",  // d/m/yyyy o dd/mm/yyyy
            "\\d{1,2}-\\d{1,2}-\\d{4}",  // d-m-yyyy o dd-mm-yyyy
            "\\d{2}\\.\\d{2}\\.\\d{4}",  // dd.mm.yyyy
            "\\d{1,2}\\.\\d{1,2}\\.\\d{4}"  // d.m.yyyy o dd.mm.yyyy
        };

        for (String patron : patrones) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patron);
            java.util.regex.Matcher matcher = pattern.matcher(texto);
            if (matcher.find()) {
                String fecha = matcher.group();
                // Normalizar el formato de la fecha
                fecha = fecha.replaceAll("\\." , "/");
                fecha = fecha.replaceAll("-", "/");
                
                // Asegurar que los días y meses tengan dos dígitos
                String[] partes = fecha.split("/");
                if (partes.length == 3) {
                    partes[0] = partes[0].length() == 1 ? "0" + partes[0] : partes[0];
                    partes[1] = partes[1].length() == 1 ? "0" + partes[1] : partes[1];
                    fecha = String.join("/", partes);
                }
                
                return fecha;
            }
        }
        return "";
    }

    private String convertirFechaTextoANumerica(String fechaTexto) {
        String[] meses = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
                         "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
        
        String[] partes = fechaTexto.toLowerCase().split(" de ");
        if (partes.length == 3) {
            String dia = partes[0].length() == 1 ? "0" + partes[0] : partes[0];
            String mes = "";
            String año = partes[2];
            
            for (int i = 0; i < meses.length; i++) {
                if (partes[1].equals(meses[i])) {
                    mes = String.format("%02d", i + 1);
                    break;
                }
            }
            
            if (!mes.isEmpty()) {
                return dia + "/" + mes + "/" + año;
            }
        }
        return fechaTexto;
    }

   /*  private TransferDTO processOcrText(String rawText) {
        if (!rawText.toLowerCase().contains("prex") || !rawText.toLowerCase().contains("comprobante de transferencia")) {
            return null;
        }
    
        String[] lines = rawText.split("\\r?\\n");
        String monto = "";
        String fecha = "";
        String cbuDestino = "";
        String cuentaDestino = "";
        String tipoOperacion = "Transferencia";
        String bankReceiver = "PREX";
    
        // Buscar monto
        Pattern montoPattern = Pattern.compile("Enviaste:\\s*\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcherMonto = montoPattern.matcher(rawText);
        if (matcherMonto.find()) {
            monto = matcherMonto.group(1);
        }
    
        // Buscar CBU (20 dígitos seguidos)
        Pattern cbuPattern = Pattern.compile("\\b\\d{20}\\b");
        Matcher matcherCbu = cbuPattern.matcher(rawText);
        if (matcherCbu.find()) {
            cbuDestino = matcherCbu.group();
        }
    
        // Buscar nombre de cuenta destino (lo usual: línea anterior a CBU)
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].contains(cbuDestino) && i > 0) {
                cuentaDestino = lines[i - 1].trim();
            }
        }
    
        // Buscar fecha con patrón: "11 de Abril de 2025 - 18:49hs"
        Pattern fechaPattern = Pattern.compile("\\d{1,2}\\s+de\\s+[a-zA-Záéíóúñ]+\\s+de\\s+\\d{4}\\s*-\\s*\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);
        Matcher matcherFecha = fechaPattern.matcher(rawText);
        if (matcherFecha.find()) {
            fecha = matcherFecha.group();
        }
    
        if (!monto.isEmpty() && !cbuDestino.isEmpty() && !cuentaDestino.isEmpty()) {
            return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .amount(monto)
                .bank(bankReceiver)
                .cbuDestino(cbuDestino)
                .cuentaDestino(cuentaDestino)
                .build();
        }
    
        return null;
    }
 */
    
    

// Método para obtener todas las transferencias acumuladas
public List<TransferDTO> getTransferencias() {
    return transferencias;
}
}
