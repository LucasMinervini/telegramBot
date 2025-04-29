package com.bot.telegramdocreader.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

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
        } else {
            return "Formato de archivo no soportado.";
        }
    
        // Mapper Transferencia 
        TransferDTO transferencia = mapearTransferencia(textoExtraido);
        if (transferencia != null) {
            this.lastTransfer = transferencia; // Guardar la transferencia
            return transferencia.receiverDetails();
        } else {
            this.lastTransfer = null;
            return "Texto extraído:\n\n" + textoExtraido;
        }
    }
    
    
    // Método para verificar si el archivo es una imagen
    private boolean isImage(Document doc) {
        String fileName = doc.getFileName().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
    }
    
    // Método para verificar si el archivo es un PDF
    private boolean isPdf(Document doc) {
        return doc.getFileName().toLowerCase().endsWith(".pdf");
    }

    // Método para extraer texto de un archivo PDF 
    private String extractTextFromPdf(Document doc, String botToken) throws Exception {
        // Obtener el archivo desde Telegram
        File file = getFileFromTelegram(doc.getFileId(), botToken);
        URL fileUrl = new URL("https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath());
        System.out.println("Intentando descargar PDF desde: " + fileUrl);

        try (InputStream inputStream = fileUrl.openStream()) {
            PDDocument document;
            try {
                // Intentar cargar el documento con una contraseña vacía primero
                document = PDDocument.load(inputStream, "");
            } catch (InvalidPasswordException e) {
                return "El PDF está protegido con contraseña. Por favor, proporcione la contraseña correcta.";
            }

            try {
                System.out.println("PDF cargado correctamente");

                // Configurar permisos de acceso
                AccessPermission ap = new AccessPermission();
                // Permitir la extracción de contenido para poder leer el texto
                ap.setCanExtractContent(true);

                if (document.isEncrypted()) {
                    try {
                        // Configurar la política de protección estándar
                        StandardProtectionPolicy spp = new StandardProtectionPolicy("", "", ap);
                        spp.setEncryptionKeyLength(128);
                        document.protect(spp);
                        document.setAllSecurityToBeRemoved(true);
                    } catch (Exception e) {
                        document.close();
                        return "No se pudo desencriptar el PDF. El documento podría estar protegido con una contraseña diferente.";
                    }
                }

                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document).trim();

                if (text.isEmpty()) {
                    document.close();
                    return "No se pudo extraer texto del PDF. El documento podría estar vacío o tener un formato no compatible.";
                }

                System.out.println("Texto extraído exitosamente. Longitud: " + text.length());
                document.close();
                return text;

            } catch (Exception e) {
                document.close();
                System.out.println("Error al procesar el PDF: " + e.getMessage());
                e.printStackTrace();
                return "Error al procesar el PDF: " + e.getMessage();
            }
        } catch (IOException e) {
            System.out.println("Error al abrir el archivo PDF: " + e.getMessage());
            e.printStackTrace();
            return "Error al abrir el archivo PDF: " + e.getMessage();
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
    
        for (String linea : lineas) {
            String lower = linea.toLowerCase().trim();
            String original = linea.trim();
    
           if (lower.startsWith("a")) {
            destinatario= original;
           }
            
            if (lower.startsWith("$")) {
                monto = original;
            }
            if (lower.contains("cur")) {
                cuit = original.replace("CUR", "").replace("cur", "").trim();
            }
            if (lower.contains("banco destino")) {
                bancoReceptor = original.replace("Banco destino", "").replace("BANCO DESTINO", "").trim();
            }
        }
    
        if (!cuit.isEmpty() && !monto.isEmpty() && !bancoReceptor.isEmpty()) {
            TransferDTO transferencia = TransferDTO.builder()
                .name(destinatario)
                .cuit(cuit)
                .accountNumber(monto)
                .bank(bancoReceptor)
                .build();
              return transferencia;
        } else {
            return null; 
        }
    }
    

}
