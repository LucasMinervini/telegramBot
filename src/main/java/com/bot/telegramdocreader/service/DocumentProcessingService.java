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
import com.bot.telegramdocreader.service.banks.Prex;
import com.bot.telegramdocreader.service.banks.Uala;
import com.bot.telegramdocreader.service.banks.Brubank;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Message;

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
    public String processDocument(Document doc, String botToken, Long chatId) throws Exception {
        String textoExtraido;
        boolean isPdfFormat = isPdf(doc);
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
                TransferDTO transferencia = mapperTransf(textoExtraido, false, doc);
                if (transferencia != null) {
                    lastTransfer = transferencia;
                    telegramFileService.createExcelFile(transferencia);
                    try {
                        String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                        if (excelResult.startsWith("Error")) {
                            System.out.println("Error al generar el archivo Excel: " + excelResult);
                            return "Error al generar el archivo Excel: " + excelResult;
                        }
                        // Formatear CUIT del emisor o mostrar mensaje si no hay
                        String cuitEmisor = (transferencia.getCuit() != null && !transferencia.getCuit().trim().isEmpty()) ? transferencia.getCuit() : "No hay CUIT del emisor";
                        if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("PREX")) {
                            return Prex.formatPrex(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("UALA")) {
                            return Uala.formatUala(transferencia);
                        } else {
                            String formatBase = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
                            return String.format(formatBase,
                                transferencia.getDate(),
                                transferencia.getTypeOFTransfer(),
                                cuitEmisor,
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
            TransferDTO transferencia = mapperTransf(textoExtraido, isPdfFormat, doc);
            if (transferencia != null) {
                lastTransfer = transferencia;
                // Extraer y asignar CUIT del emisor
                String cuitEmisor = extractCuitSender(textoExtraido);
                transferencia.setCuit(cuitEmisor);
                telegramFileService.createExcelFile(transferencia);
                try {
                    String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                    if (excelResult.startsWith("Error")) {
                        System.out.println("Error al generar el archivo Excel: " + excelResult);
                        return "Error al generar el archivo Excel: " + excelResult;
                    }
                    // Formatear CUIT del emisor o mostrar mensaje si no hay
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
                                transferencia.getCbuDestiny() != null ? transferencia.getCbuDestiny() : "",
                                transferencia.getAccountDestiny() != null ? transferencia.getAccountDestiny() : "");
                    } else {
                        String formatoBase = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
                        return String.format(formatoBase,
                            transferencia.getDate(),
                            transferencia.getTypeOFTransfer(),
                            cuitEmisor,
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
                                   fileName.endsWith(".heic") || 
                                   fileName.endsWith(".gif") || 
                                   fileName.endsWith(".bmp") ||
                                   fileName.endsWith(".tiff"); 
        
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
                
                try {
                    org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                    java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 300);
                    java.io.File tempImage = java.io.File.createTempFile("pdf_page_ocr", ".png");
                    javax.imageio.ImageIO.write(bim, "png", tempImage);
                    try {
                        text = com.bot.telegramdocreader.utils.ImageProcessor.extractTextFromImage(tempImage).trim();
                    } finally {
                        tempImage.delete();
                    }
                    if (text.isEmpty()) {
                        throw new IOException("No se pudo extraer texto del PDF ni mediante OCR. El documento podría estar vacío o tener un formato no compatible.");
                    }
                } catch (Exception ocrEx) {
                    throw new IOException("No se pudo extraer texto del PDF ni mediante OCR. El documento podría estar vacío o tener un formato no compatible.", ocrEx);
                }
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

    private TransferDTO mapperTransf(String textoExtraido, boolean isPdfFormat, Document doc) {
        // Detectar si es transferencia de Brubank por texto o por nombre de archivo
        String fileNameLower = doc.getFileName().toLowerCase();
        boolean esBrubank = textoExtraido.toLowerCase().contains("brubank") || fileNameLower.contains("brubank");
        if (esBrubank) {
            return Brubank.parseBrubankTransfer(textoExtraido, doc);
        }
        // Detectar si es transferencia de PREX
        boolean isPrex = false;
        String[] lines = textoExtraido.split("\r?\n");
        // Detectar si es transferencia de Ualá
        boolean isUala = false;
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String lineNormalize = lines[i].replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]", "").toLowerCase();
            if (lineNormalize.contains("uala") || containsApproxWord(lineNormalize, "uala", 1) || containsApproxWord(lineNormalize, "ualá", 1)) {
                isUala = true;
                break;
            }
        }
        if (!isUala) {
            if (fileNameLower.contains("uala")) {
                isUala = true;
            }
        }
        if(textoExtraido.matches("(?i).*\\buala\\b.*")) {
            isUala = true;
        }
        if (isUala) {
            return Uala.parseUalaTransfer(textoExtraido, doc);
        }
        if (!isPrex) {
            if (fileNameLower.contains("prex")) {
                isPrex = true;
            }
        }
        if(textoExtraido.matches("(?i).*\bprex\b.*")) {
            isPrex = true;
        }
        if (isPrex) {
            return Prex.parsePrexTransfer(textoExtraido, doc);
        }
        // Si no es ninguno, lógica genérica
        textoExtraido = textoExtraido.replaceAll("[^\\p{Print}\\s]", "").trim();
        lines = textoExtraido.split("\\r?\\n");
    
        String destinatario = "";
        String fecha = "";
        String cuitSender = "";
        String monto = "";
        String bankReceiver = "";
        String tipoOperacion = "";
       
        
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
                // Aquí ya no se procesa PREX ni BRUBANK, solo lógica genérica para PDF
                // Banco FUNDRAISER
                if (lower.contains("fundraiser")) {
                    bankReceiver = "FUNDRAISER s.a.s.";
                }
                // Normalizar el nombre del banco si se encontró
                if (!bankReceiver.isEmpty()) {
                    bankReceiver = bankReceiver.replaceAll("\\s+", " ").trim();
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
                    if (fecha.isEmpty()) {
                        String textoLimpio = original.replaceAll("(?i)fecha de operación:|fecha:|:", "").trim();
                        fecha = extractDate(textoLimpio);
                    }
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
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*$", "$1").trim();
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

                // LÓGICA PARA IMÁGENES genérica 
                if (lower.startsWith("a") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    destinatario = original.replaceAll("(?i)a |destinatario:|beneficiario:", "").trim();
                }
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
                    if (fecha.isEmpty()) {
                        String textoLimpio = original.replaceAll("(?i)fecha de operación:|fecha:|:", "").trim();
                        fecha = extractDate(textoLimpio);
                    }
                    if (fecha.isEmpty()) {
                        fecha = original.trim();
                    }
                if (lower.contains("transferencia") || lower.contains("transferido") ||
                    lower.contains("transferiste") || lower.contains("transferir") || 
                    lower.contains("Transferencia enviada") || lower.contains("transferencia enviada")) {
                    tipoOperacion = "Transferencia";
                    } else if (lower.contains("depósito") || lower.contains("deposito")) {
                        tipoOperacion = "Depósito";
                    }
                }
                if (lower.startsWith("$") || lower.contains("importe") || lower.contains("monto")) {
                    monto = original.replaceAll("(?i)importe:|monto:|\\$", "").trim();
                }
                // CUIT/CUIL - Solo buscar el del emisor
                if ((lower.contains("cuit emisor") || lower.contains("cuil emisor") ||
                    (lower.contains("cuit") && (lower.contains("de:") || lower.contains("origen") || lower.contains("emisor"))) ||
                    (lower.matches(".*\\d{2}-\\d{8}-\\d{1}.*") && (lower.contains("emisor") || lower.contains("origen"))))) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{2}-\\d{8}-\\d{1}");
                    java.util.regex.Matcher matcher = pattern.matcher(original);
                    if (matcher.find()) {
                        cuitSender = matcher.group();
                    } else {
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*$", "$1").trim();
                        if (cuitTemp.matches("\\d{11}")) {
                            cuitSender = cuitTemp.substring(0, 2) + "-" + cuitTemp.substring(2, 10) + "-" + cuitTemp.substring(10);
                        }
                    }
                }
                if (lower.contains("neblockchain") || lower.contains("neblockchain sa")) {
                    bankReceiver = "NEBLOCKCHAIN SA";
                } else if (lower.contains("para") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    String posibleBanco = original.replaceAll("(?i)para:|destinatario:|beneficiario:", "").trim();
                    if (!posibleBanco.isEmpty() && bankReceiver.isEmpty()) {
                        bankReceiver = posibleBanco;
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
                .name("") // No mostrar destinatario
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

// Método para obtener todas las transferencias acumuladas
public List<TransferDTO> getTransferencias() {
    return transferencias;
}

public void handleDocumentMessage(Message message) {
    Long chatId = message.getChatId();
    if (message.hasDocument()) {
        org.telegram.telegrambots.meta.api.objects.Document document = message.getDocument();
        String botToken = bot.getBotToken();
        try {
            String resultado = processDocument(document, botToken, chatId);
            bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), resultado));
        } catch (Exception e) {
            e.printStackTrace();
            try {
                bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "Error al procesar el documento: " + e.getMessage()));
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    } else {
        try {
            bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "No se recibió ningún documento."));
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException ex) {
            ex.printStackTrace();
        }
    }
}
private String extractCuitSender(String texto) {
    
    Pattern pattern = Pattern.compile("(?i)(?:cuit(?:\\s*del\\s*emisor)?[:\\s]*)?([0-9]{2}-?[0-9]{8}-?[0-9])");
    Matcher matcher = pattern.matcher(texto);
    while (matcher.find()) {
        String cuit = matcher.group(1);
        if (cuit != null && !cuit.isEmpty()) {
            String digits = cuit.replaceAll("[^0-9]", "");
            if (digits.length() == 11) {
                return digits.substring(0,2) + "-" + digits.substring(2,10) + "-" + digits.substring(10);
            }
        }
    }
    return null;
}


}
