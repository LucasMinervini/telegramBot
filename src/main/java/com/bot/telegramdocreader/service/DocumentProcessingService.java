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
                    return String.format("Nombre: %s\nCUIT: %s\nMonto: $ %s\nBanco: %s", 
                        transferencia.getName(),
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
    

    
    // Método para verificar si el archivo es una imagen o captura de pantalla
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
            System.out.println("Intentando descargar PDF desde: " + fileUrl);
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
        String cuit = "";
        String monto = "";
        String bancoReceptor = "";
        boolean isPdfFormat = textoExtraido.contains("PDF") || textoExtraido.contains("pdf");
    
        for (String linea : lineas) {
            String lower = linea.toLowerCase().trim();
            String original = linea.trim();
    
            if (isPdfFormat) {
                // Lógica específica para PDFs
                if (lower.contains("De")) {
                    destinatario = original.replaceAll("(?i)De:|de:", "").trim();
                }
                if (lower.contains("mercado pago")) {
                    bancoReceptor = "Mercado Pago";
                }
                if (lower.contains("cuil") || lower.contains("cuit") ) {
                    cuit = original.replaceAll("(?i)cuit:|:", "").trim();
                }
                if (lower.startsWith("$")) {
                    monto = original.replaceAll("\\$", "").trim();
                }
            } else {
                // Lógica original para imágenes
                if (lower.startsWith("a") || lower.contains("destinatario") || lower.contains("beneficiario")) {
                    destinatario = original.replaceAll("(?i)a |destinatario:|beneficiario:", "").trim();
                }
                if (lower.startsWith("$") || lower.contains("importe") || lower.contains("monto")) {
                    monto = original.replaceAll("(?i)importe:|monto:|\\$", "").trim();
                }
                if (lower.contains("cur") || lower.contains("cuit") || lower.matches(".*\\d{2}-\\d{8}-\\d{1}.*")) {
                    cuit = original.replaceAll("(?i)CUR|CUIT:|cuit:|:", "").trim();
                }
                if (lower.contains("banco") || lower.contains("entidad")) {
                    bancoReceptor = original.replaceAll("(?i)|banco:|entidad:|destino:", "").trim();
                }
            }
        }
    
        // Validar que al menos tengamos algunos datos básicos
        if (!destinatario.isEmpty() || !cuit.isEmpty()) {
            TransferDTO transferencia = TransferDTO.builder()
                .name(destinatario)
                .cuit(cuit)
                .amount(monto)
                .bank(bancoReceptor)
                .build();
            return transferencia;
        }
        return null;
    }
    

}
