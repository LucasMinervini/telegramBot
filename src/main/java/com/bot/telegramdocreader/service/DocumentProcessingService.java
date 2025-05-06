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
import javax.imageio.ImageIO;

@Service
public class DocumentProcessingService {

    // Declarar el bot como un campo privado
    private TelegramDocBot bot;
    private TransferDTO lastTransfer; // Almacenar la última transferencia procesada

    public DocumentProcessingService(TelegramDocBot bot) {
        this.bot = bot;  
    }

    // Este método se encarga de procesar el documento recibido por el bot
    public String processDocument(Document doc, String botToken) throws Exception {
        String textoExtraido;
        
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
            } else if (isPdf(doc)) {
                textoExtraido = extractTextFromPdf(doc, botToken);
                if (textoExtraido.startsWith("Error") || textoExtraido.contains("protegido con contraseña")) {
                    return textoExtraido;
                }
            } else {
                return "Formato de archivo no soportado.";
            }
            
            // Mapper Transferencia 
            TransferDTO transferencia = mapearTransferencia(textoExtraido);
            this.lastTransfer = transferencia; // Guardar la transferencia

            if (transferencia != null) {
                try {
                    String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                    if (excelResult.startsWith("Error")) {
                        System.out.println("Error al generar el archivo Excel: " + excelResult);
                        return "Error al generar el archivo Excel: " + excelResult;
                    }
                    // Retornar los detalles de la transferencia
                    return String.format("Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s", 
                        transferencia.getDate(),
                        transferencia.getTypeOFTransfer(),
                        transferencia.getCuit(),
                        transferencia.getAmount(),
                        transferencia.getBank());
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
        return doc.getFileName().toLowerCase().endsWith(".pdf");
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
    
    

    
    private File getFileFromTelegram(String fileId, String botToken) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File file = bot.execute(getFile);
        return file;
    }


    public TransferDTO getLastTransfer() {
        return this.lastTransfer;
    }

    private TransferDTO mapearTransferencia(String textoExtraido) {
        String[] lineas = textoExtraido.split("\\r?\\n");
    
        String destinatario = "";
        String fecha = "";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "";
        String tipoOperacion = "";
        String cuentaOrigen = "";
        String cbuOrigen = "";
        boolean isPdfFormat = textoExtraido.contains("PDF") || textoExtraido.contains("pdf");
        boolean encontradoEmisor = false;
    
        // Primero buscar el tipo de operación en todo el texto
        String textoLower = textoExtraido.toLowerCase();
        if (textoLower.contains("comprobante de transferencia")) {
            tipoOperacion = "Transferencia";
        } else if (textoLower.contains("depósito") || textoLower.contains("deposito")) {
            tipoOperacion = "Depósito";
        }

        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
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
            }

            if (isPdfFormat) {


                //Nombre
                if (lower.contains("de")) {
                    destinatario = original.replaceAll("(?i)de", "").trim();
                    // Limpiar el formato específico de Mercado Pago
                    if (destinatario.startsWith(":")) {
                        destinatario = destinatario.substring(1).trim();
                    }
                    
                }

                // Extraer fecha del formato específico de Mercado Pago si aún no se encontró
                if (fecha.isEmpty() && (lower.contains("miércoles") || lower.contains("lunes") || 
                    lower.contains("martes") || lower.contains("jueves") || 
                    lower.contains("viernes") || lower.contains("sábado") || 
                    lower.contains("domingo"))) {
                    fecha = extraerFecha(original);
                    if (fecha.isEmpty()) {
                        fecha = original.trim();
                    }
                }

                // Mercado Pago
                if (lower.contains("mercado pago") || lower.contains("mp") || lower.contains("mercado pago s.a.") || lower.contains("mercadopago")) {
                    bancoReceptor = "Mercado Pago";
                }
                if (lower.contains("banco") || lower.contains("entidad")) {
                    bancoReceptor = original.replaceAll("(?i)banco:|entidad:|destino:", "").trim();    
                }
                //Galicia
                if (lower.contains("galicia") || lower.contains("galicia bancaria") || lower.contains("galicia bancaria s.a.")) {
                    bancoReceptor = "Galicia";
                }
                //BBVA
                if (lower.contains("bbva") || lower.contains("bbva bancaria") || lower.contains("bbva bancaria s.a.")) {
                    bancoReceptor = "BBVA";
                }
                //Nacion    
                if (lower.contains("nacion") || lower.contains("nacion bancaria") || lower.contains("nacion bancaria s.a.")) {
                    bancoReceptor = "Nación";   
                }
                //HSBC
                if (lower.contains("hsbc") || lower.contains("hsbc bancaria") || lower.contains("hsbc bancaria s.a.")) {
                    bancoReceptor = "HSBC";
                }
                //ITAU  
                if (lower.contains("itau") || lower.contains("itau bancaria") || lower.contains("itau bancaria s.a.")) {
                    bancoReceptor = "ITAU";
                }
                //PREX
                if (lower.contains("prex") || lower.contains("prex bancaria") || lower.contains("prex bancaria s.a.") || lower.contains("fargotez") || lower.contains("fargotez sa")) {
                    bancoReceptor = "PREX";
                    // Procesar datos específicos de Prex si están disponibles
                    if (lower.contains("enviaste") || lower.contains("enviaste a")) {
                        monto = original.replaceAll("[^0-9.,]", "").trim();
                    }
                    if (lower.contains("cvu/cbu") || lower.contains("cvu destino") || lower.contains("cbu destino")) {
                        cbuOrigen = original.replaceAll("(?i)cvu/cbu:|cvu destino:|cbu destino:|:", "").trim();
                    }
                }

                // CUIT/CUIL Del Emisor
                if (!encontradoEmisor && (lower.contains("cuit origen") || lower.contains("cuil origen") || 
                    lower.contains("cuit emisor") || lower.contains("cuil emisor") || 
                    (lower.contains("cuit") && (lower.contains("de:") || lower.contains("origen") || lower.contains("emisor"))) || 
                    lower.matches(".*\\d{2}-\\d{8}-\\d{1}.*"))) {
                    
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{2}-\\d{8}-\\d{1}");
                    java.util.regex.Matcher matcher = pattern.matcher(original);
                    if (matcher.find()) {
                        cuit = matcher.group();
                        encontradoEmisor = true;
                    } else {
                        // Si no se encuentra el patrón directo, intentar limpiar el texto
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*", "$1").trim();
                        if (cuitTemp.matches("\\d{11}")) {
                            cuit = cuitTemp.substring(0, 2) + "-" + cuitTemp.substring(2, 10) + "-" + cuitTemp.substring(10);
                            encontradoEmisor = true;
                        }
                    }
                }

                // Extraer monto
                if (lower.contains("$")) {
                    monto = original.replaceAll("[^0-9.,]", "").trim();
                }

                // Información adicional si está disponible
                if (lower.contains("cvu:")) {
                    cbuOrigen = original.replaceAll("(?i)cvu:|:", "").trim();
                }
                

                // Buscar CBU en formato numérico (22 dígitos)
                if (cbuOrigen.isEmpty() && lower.matches(".*\\d{22}.*")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{22}");
                    java.util.regex.Matcher matcher = pattern.matcher(lower);
                    if (matcher.find()) {
                        cbuOrigen = matcher.group();
                    }
                }
            } else {
                    // Lógica para imágenes

                
                //Nombre
                if (lower.startsWith("a") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    destinatario = original.replaceAll("(?i)a |destinatario:|beneficiario:", "").trim();
                }

                
                //Fecha
                // Verificar si es Mercado Pago y buscar el formato específico
                if ( lower.contains("lunes") || 
                lower.contains("martes") || 
                lower.contains("miércoles") ||  
                lower.contains("jueves") || 
                lower.contains("viernes") || 
                lower.contains("sábado") || 
                lower.contains("domingo") ||
                lower.matches(".*\\d{1,2}\\s+de\\s+[a-záéíóúñ]+\\s+de\\s+\\d{4}.*")) {
                    fecha = extraerFecha(original);
                } else if (lower.contains("fecha") || lower.contains("fecha de operación") || 
                    lower.matches(".*\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}.*") || 
                    lower.matches(".*\\d{1,2}\\s+de\\s+\\w+\\s+de\\s+\\d{4}.*") 
                    ) {
                    
                    
                    fecha = extraerFecha(original);
                    
                    // Si no se encontró fecha, intentar limpiar el texto
                    if (fecha.isEmpty()) {
                        String textoLimpio = original.replaceAll("(?i)fecha de operación:|fecha:|:", "").trim();
                        fecha = extraerFecha(textoLimpio);
                    }
                    
                    // Si aún está vacío, intentar con el texto original
                    if (fecha.isEmpty()) {
                        fecha = original.trim();
                    }

                // Tipo de operación
                if (lower.contains("transferencia") || lower.contains("transferido") ||
                    lower.contains("transferiste") || lower.contains("transferir")) {
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
                if (!encontradoEmisor && (lower.contains("cuit origen") || lower.contains("cuil origen") || 
                    lower.contains("cuit emisor") || lower.contains("cuil emisor") || 
                    (lower.contains("cuit") && (lower.contains("de:") || lower.contains("origen") || lower.contains("emisor"))) || 
                    lower.matches(".*\\d{2}-\\d{8}-\\d{1}.*"))) {
                    
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{2}-\\d{8}-\\d{1}");
                    java.util.regex.Matcher matcher = pattern.matcher(original);
                    if (matcher.find()) {
                        cuit = matcher.group();
                        encontradoEmisor = true;
                    } else {
                        // Si no se encuentra el patrón directo, intentar limpiar el texto
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*", "$1").trim();
                        if (cuitTemp.matches("\\d{11}")) {
                            cuit = cuitTemp.substring(0, 2) + "-" + cuitTemp.substring(2, 10) + "-" + cuitTemp.substring(10);
                            encontradoEmisor = true;
                        }
                    }
                }
                // Banco
                if (lower.contains("banco") || lower.contains("entidad")) {
                    bancoReceptor = original.replaceAll("(?i)|banco:|entidad:|destino:", "").trim();
                }
                if (lower.contains("mercado pago") || lower.contains("mp") || lower.contains("mercado pago s.a.") || lower.contains("mercadopago")) {
                    bancoReceptor = "Mercado Pago";
                }
                
            }
        }
    
        // Si no se identificó el tipo de operación, asignar "No especificado"
        if (tipoOperacion.isEmpty()) {
            tipoOperacion = "No especificado";
        }

        // Validar que al menos tengamos algunos datos básicos
        if (!destinatario.isEmpty() || !cuit.isEmpty()) {
            TransferDTO transferencia = TransferDTO.builder()
                .name(destinatario)
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuit(cuit)
                .amount(monto)
                .bank(bancoReceptor)
                .cuentaOrigen(cuentaOrigen)
                .cbuOrigen(cbuOrigen)
                .build();
            return transferencia;
        }
        return null;
    }

    private String extraerFecha(String texto) {
        // Verificar si es un formato de fecha en texto (dd de mes de yyyy)
        String patronFechaTexto = "\\d{1,2}\\s*(?:de\\s+)?[a-záéíóúñ]+\\s+(?:de\\s+)?\\d{4}";
        java.util.regex.Pattern patternFechaTexto = java.util.regex.Pattern.compile(patronFechaTexto, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcherFechaTexto = patternFechaTexto.matcher(texto);
        
        if (matcherFechaTexto.find()) {
            String fechaTexto = matcherFechaTexto.group();
            fechaTexto = fechaTexto.replaceAll("(\\d+)\\s+([a-záéíóúñ]+)", "$1 de $2");
            fechaTexto = fechaTexto.replaceAll("([a-záéíóúñ]+)\\s+(\\d{4})", "$1 de $2");
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
    

}
