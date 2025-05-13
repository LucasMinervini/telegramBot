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
            } else if (isPdf(doc)) {
                textoExtraido = extractTextFromPdf(doc, botToken);
                if (textoExtraido.startsWith("Error") || textoExtraido.contains("protegido con contraseña")) {
                    return textoExtraido;
                }
            } else {
                return "Formato de archivo no soportado.";
            }
            
            // Mapper Transferencia 
            TransferDTO transferencia = mapearTransferencia(textoExtraido, isPdfFormat);
            this.lastTransfer = transferencia; // Guardar la transferencia

            if (transferencia != null) {
                try {
                    String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                    if (excelResult.startsWith("Error")) {
                        System.out.println("Error al generar el archivo Excel: " + excelResult);
                        return "Error al generar el archivo Excel: " + excelResult;
                    }
                    // Retornar los detalles de la transferencia
                    String formatoBase = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
                    if (transferencia.getBank().equals("PREX")) {
                        formatoBase = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s\nCBU/CVU Destino: %s\nCuenta Destino: %s";
                        return String.format(formatoBase,
                            transferencia.getDate(),
                            transferencia.getTypeOFTransfer(),
                            transferencia.getCuit(),
                            transferencia.getAmount(),
                            transferencia.getBank(),
                            transferencia.getCbuDestino(),
                            transferencia.getCuentaDestino()
                            );
                    } else {
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
    
    

    
    private File getFileFromTelegram(String fileId, String botToken) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File file = bot.execute(getFile);
        return file;
    }


    public TransferDTO getLastTransfer() {
        return this.lastTransfer;
    }

    private TransferDTO mapearTransferencia(String textoExtraido, boolean isPdfFormat) {
        String[] lineas = textoExtraido.split("\\r?\\n");
    
        String destinatario = "";
        String fecha = "";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "";
        String tipoOperacion = "";
        String cbuDestino = "";
        String cuentaDestino = "";
        boolean encontradoFecha = false;
        boolean procesandoReceptor = true; 
    
        // Convertir todo el texto a minúsculas una sola vez
        String textoLower = textoExtraido.toLowerCase();

        // Verificar primero si es un comprobante PREX antes de cualquier otro procesamiento
        if (textoLower.contains("prex")) {
            bancoReceptor = "PREX";
            tipoOperacion = "Transferencia";
        }
        
        // Si no es PREX, buscar el tipo de operación en todo el texto
        if (tipoOperacion.isEmpty()) {
            if (textoLower.contains("comprobante de transferencia") || 
                textoLower.contains("transferencia enviada") || 
                textoLower.contains("envío de dinero") || 
                textoLower.contains("envio de dinero") || 
                textoLower.contains("transferencia realizada") || 
                (textoLower.contains("transferencia") && textoLower.contains("$"))) {
                tipoOperacion = "Transferencia";
            } else if (textoLower.contains("depósito") || textoLower.contains("deposito")) {
                tipoOperacion = "Depósito";
            }
        }

        // Detectar si es un comprobante PREX
        if (textoLower.contains("prex")) {
            bancoReceptor = "PREX";
            for (String linea : lineas) {
                String lineaLower = linea.toLowerCase().trim();
                String lineaOriginal = linea.trim();

                // Extraer fecha
                if (lineaLower.contains("de") && lineaLower.contains("hs")) {
                    fecha = lineaOriginal;
                }

                // Extraer monto
                if (lineaLower.contains("enviaste:") || lineaLower.contains("$")) {
                    String montoTemp = lineaOriginal.replaceAll("[^0-9.,]", "").trim();
                    if (!montoTemp.isEmpty()) {
                        monto = montoTemp;
                    }
                }

                // Extraer destinatario
                if (lineaLower.contains("enviaste a:")) {
                    destinatario = lineaOriginal.replace("Enviaste a:", "").trim();
                }

                // Extraer CUIT/CUIL
                if (lineaLower.contains("cuit/cuil:")) {
                    cuit = lineaOriginal.replace("CUIT/CUIL:", "").trim();
                } else if (lineaLower.contains("cuit:")) {
                    cuit = lineaOriginal.replace("CUIT:", "").trim();
                } else if (lineaLower.contains("cuil:")) {
                    cuit = lineaOriginal.replace("CUIL:", "").trim();
                }

                // Extraer CBU/CVU
                if (lineaLower.contains("cvu/cbu:")) {
                    cbuDestino = lineaOriginal.replace("CVU/CBU:", "").trim();
                } else if (lineaLower.contains("cvu destino:")) {
                    cbuDestino = lineaOriginal.replace("CVU destino:", "").trim();
                } else if (lineaLower.contains("cbu destino:")) {
                    cbuDestino = lineaOriginal.replace("CBU destino:", "").trim();
                }

                // Extraer cuenta destino
                if (lineaLower.contains("cuenta destino")) {
                    cuentaDestino = lineaOriginal.replace("Cuenta destino", "").trim();
                    if (cuentaDestino.startsWith(":")) {
                        cuentaDestino = cuentaDestino.substring(1).trim();
                    }
                }
            }

            // Crear y retornar el DTO para PREX
            if (!monto.isEmpty() && (!destinatario.isEmpty() || !cuit.isEmpty())) {
                return TransferDTO.builder()
                    .name(destinatario)
                    .date(fecha)
                    .typeOFTransfer(tipoOperacion)
                    .cuit(cuit)
                    .amount(monto)
                    .bank(bancoReceptor)
                    .cbuDestino(cbuDestino)
                    .cuentaDestino(cuentaDestino)
                    .build();
            }
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
                

                if (textoLower.contains("prex")) {
                    // Procesar comprobante PREX
                    bancoReceptor = "PREX";
                    
                    // Establecer tipo de operación primero
                    if (textoLower.contains("comprobante de transferencia") || 
                        textoLower.contains("transferencia enviada") || 
                        textoLower.contains("envío de dinero") || 
                        textoLower.contains("envio de dinero")) {
                        tipoOperacion = "Transferencia";
                    }
                    
                    for (String currentLine : lineas) {
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
                            cuit = currentLineOriginal.replace("CUIT/CUIL:", "").trim();
                        } else if (currentLineLower.contains("cuit:")) {
                            cuit = currentLineOriginal.replace("CUIT:", "").trim();
                        } else if (currentLineLower.contains("cuil:")) {
                            cuit = currentLineOriginal.replace("CUIL:", "").trim();
                        }
                        
                        // Extraer cuenta destino
                        if (currentLineLower.contains("cuenta destino")) {
                            cuentaDestino = currentLineOriginal.replace("Cuenta destino", "").trim();
                            if (cuentaDestino.startsWith(":")) {
                                cuentaDestino = cuentaDestino.substring(1).trim();
                            }
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
                    if (tipoOperacion.isEmpty() && (textoLower.contains("transferencia") || textoLower.contains("envío"))) {
                        tipoOperacion = "Transferencia";
                    }
                    
                    // Retornar el DTO para PREX si tenemos los datos mínimos necesarios
                    if (!monto.isEmpty() && (!destinatario.isEmpty() || !cuit.isEmpty())) {
                        return TransferDTO.builder()
                            .name(destinatario)
                            .date(fecha)
                            .typeOFTransfer(tipoOperacion)
                            .cuit(cuit)
                            .amount(monto)
                            .bank(bancoReceptor)
                            .cbuDestino(cbuDestino)
                            .cuentaDestino(cuentaDestino)
                            .build();
                    }
                }
                
                

                // Banco FUNDRAISER
                if (textoLower.contains("fundraiser")) {
                    bancoReceptor = "FUNDRAISER s.a.s.";
                }

               

                // Solo procesar la información si estamos después de "Para"
                if (procesandoReceptor) {
                    // Extraer fecha
                    if (!encontradoFecha && (lower.contains("miércoles") || lower.contains("lunes") || 
                        lower.contains("martes") || lower.contains("jueves") || 
                        lower.contains("viernes") || lower.contains("sábado") || 
                        lower.contains("domingo"))) {
                        fecha = extraerFecha(original);
                        if (!fecha.isEmpty()) {
                            encontradoFecha = true;
                        }
                    }

                    // Procesar información del banco si aún no se ha encontrado
                    if (bancoReceptor.isEmpty()) {
                        if (lower.contains("neblockchain") || lower.contains("neblockchain sa")) {
                            bancoReceptor = "NEBLOCKCHAIN SA";
                        } else if (lower.contains("banco") || lower.contains("entidad")) {
                            bancoReceptor = original.replaceAll("(?i)banco:|entidad:|destino:", "").trim();
                        } else if (lower.contains("destinatario") || lower.contains("beneficiario")) {
                            String posibleBanco = original.replaceAll("(?i)destinatario:|beneficiario:", "").trim();
                            if (!posibleBanco.isEmpty() && !posibleBanco.equalsIgnoreCase("para")) {
                                bancoReceptor = posibleBanco;
                            }
                        }
                    }
                }
                
            
                
                // Normalizar el nombre del banco si se encontró
                if (!bancoReceptor.isEmpty()) {
                    // Eliminar espacios múltiples
                    bancoReceptor = bancoReceptor.replaceAll("\\s+", " ").trim();
                    
                    // Convertir primera letra de cada palabra a mayúscula
                    String[] palabras = bancoReceptor.split(" ");
                    StringBuilder nombreFormateado = new StringBuilder();
                    for (String palabra : palabras) {
                        if (!palabra.isEmpty()) {
                            if (nombreFormateado.length() > 0) nombreFormateado.append(" ");
                            nombreFormateado.append(Character.toUpperCase(palabra.charAt(0)))
                                           .append(palabra.substring(1).toLowerCase());
                        }
                    }
                    bancoReceptor = nombreFormateado.toString();
                }

                // CUIT/CUIL del destinatario
                if (procesandoReceptor && lower.contains("cuit") || lower.contains("cuil")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{2}-\\d{8}-\\d{1}");
                    java.util.regex.Matcher matcher = pattern.matcher(original);
                    if (matcher.find()) {
                        cuit = matcher.group();
                    } else {
                        // Intentar limpiar el texto
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*", "$1").trim();
                        if (cuitTemp.matches("\\d{11}")) {
                            cuit = cuitTemp.substring(0, 2) + "-" + cuitTemp.substring(2, 10) + "-" + cuitTemp.substring(10);
                        }
                    }
                }

                // Extraer monto
                if (lower.contains("$")) {
                    monto = original.replaceAll("[^0-9.,]", "").trim();
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
                        cuit = matcher.group();
                        
                    } else {
                        // Si no se encuentra el patrón directo, intentar limpiar el texto
                        String cuitTemp = original.replaceAll("(?i).*(?:cuit|cuil)[^0-9-]*([0-9-]+).*", "$1").trim();
                        if (cuitTemp.matches("\\d{11}")) {
                            cuit = cuitTemp.substring(0, 2) + "-" + cuitTemp.substring(2, 10) + "-" + cuitTemp.substring(10);
                            
                        }
                    }
                }
                // Banco
                if (lower.contains("neblockchain") || lower.contains("neblockchain sa")) {
                    bancoReceptor = "NEBLOCKCHAIN SA";
                } else if (lower.contains("banco") || lower.contains("entidad")) {
                    bancoReceptor = original.replaceAll("(?i)|banco:|entidad:|destino:", "").trim();
                } else if (lower.contains("mercado pago") || lower.contains("mp") || lower.contains("mercado pago s.a.") || lower.contains("mercadopago")) {
                    bancoReceptor = "Mercado Pago";
                }
                
                else if (lower.contains("para") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    String posibleBanco = original.replaceAll("(?i)para:|destinatario:|beneficiario:", "").trim();
                    if (!posibleBanco.isEmpty() && bancoReceptor.isEmpty()) {
                        bancoReceptor = posibleBanco;
                    }
                }
                
            }
        }
    
        
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
                .build();
            return transferencia;
        }
        return null;}
    

    private String extraerFecha(String texto) {


        
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
    

}
