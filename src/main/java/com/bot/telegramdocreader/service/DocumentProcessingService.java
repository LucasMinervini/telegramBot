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
import com.bot.telegramdocreader.service.banks.Santander;
import com.bot.telegramdocreader.service.banks.Uala;
import com.bot.telegramdocreader.service.banks.BBVA;
import com.bot.telegramdocreader.service.banks.BancoProvincia;
import com.bot.telegramdocreader.service.banks.Brubank;
import com.bot.telegramdocreader.service.banks.MercadoPago;
import com.bot.telegramdocreader.service.banks.Bancor;
import com.bot.telegramdocreader.service.banks.NaranjaX;
import com.bot.telegramdocreader.service.banks.CuentaDni;

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

import com.bot.telegramdocreader.service.banks.Galicia;
import com.bot.telegramdocreader.service.banks.Macro;

@Service
public class DocumentProcessingService {

    // Constantes para mejorar mantenibilidad
    private static final int MAX_LINES_TO_CHECK = 15;
    private static final String TESSERACT_DATA_PATH = "C:\\Program Files\\Tesseract-OCR\\tessdata";
    private static final String TESSERACT_LANGUAGE = "spa";
    private static final String TELEGRAM_FILE_URL_TEMPLATE = "https://api.telegram.org/file/bot%s/%s";
    
    // Patrones de detección de bancos
    private static final String[] UALA_PATTERNS = {"uala", "ualá", "uála", "ualla", "uálá", "uála", "ualla", "ualla transferencia", "ualá transferencia", "uála transferencia", "u a l a", "u a l á", "uálá transferencia", "uála transferencia", "ualla trans", "ualá trans", "uála trans", "ualla recibo", "ualá recibo", "uála recibo", "ualla comprobante", "ualá comprobante", "uála comprobante", "transferencia ualá", "transferencia uala", "recibo ualá", "recibo uala", "comprobante ualá", "comprobante uala"};
    private static final String[] MERCADOPAGO_PATTERNS = {"mercadopago", "mpago", "mercado pago", "mercado_pago"};
    private static final String[] BANCOR_PATTERNS = {"bancor", "banco de córdoba", "banco córdoba", "cordoba", "córdoba"};
    private static final String[] PREX_PATTERNS = {"prex"};
    private static final String[] PERSONAL_PAY_PATTERNS = {"personal pay", "personalpay"};
    private static final String[] BBVA_PATTERNS = {"bbva", "b b v a", "banco bbva", "banco francés", "frances", "francés"};
    private static final String[] BANCO_PROVINCIA_PATTERNS = {"banco provincia", "provincia"};
    private static final String[] BRUBANK_PATTERNS = {"brubank"};
    private static final String[] NARANJAX_PATTERNS = {"naranjax"};
    private static final String[] MACRO_PATTERNS = {"macro","Macro","Banco Macro"};
    private static final String[] GALICIA_PATTERNS = {"gali","galiça","galicia","galiça"};
    private static final String[] SANTANDER_PATTERNS = {"santander", "santander rio", "santander río"};
    private static final String[] SANTANDER_FALLBACK_PATTERNS = {"Comprobante de transferencia", "CTA"};
    private static final String[] CUENTA_DNI_PATTERNS = {"cuenta dni", "cuentadni"};
    private static final String[] CUENTA_DNI_FALLBACK_PATTERNS = {"código de referencia", "comprobante de transferencia"};
    
    
    // Declarar el bot como un campo privado
    private TelegramDocBot bot;
    private TransferDTO lastTransfer; // Almacenar la última transferencia procesada
    private List<TransferDTO> transferencias = new ArrayList<>(); // Lista para acumular transferencias
    private TelegramFileService telegramFileService;
    

    public DocumentProcessingService(TelegramDocBot bot, TelegramFileService telegramFileService) {
        this.bot = bot;
        this.telegramFileService = telegramFileService;
    }

    private boolean detectAllBanks(String text, String[] patterns) {
        for (String pattern : patterns) {
            if (!text.toLowerCase().contains(pattern.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    // Este método se encarga de procesar el documento recibido por el bot
    public String processDocument(Document doc, String botToken, Long chatId) throws Exception {
        String textoExtraido;
        
        boolean isPdfFormat = isPdf(doc);
        try {
            
            if (isImage(doc)) {
                File file = getFileFromTelegram(doc.getFileId(), botToken);
                URL fileUrl = new URL(String.format(TELEGRAM_FILE_URL_TEMPLATE, botToken, file.getFilePath()));
                InputStream inputStream = fileUrl.openStream();
                BufferedImage image = ImageIO.read(inputStream);
                ITesseract instance = new Tesseract();
                instance.setDatapath(TESSERACT_DATA_PATH);
                instance.setLanguage(TESSERACT_LANGUAGE);
                instance.setPageSegMode(1);
                instance.setOcrEngineMode(1);
                textoExtraido = instance.doOCR(image);
                System.out.println(textoExtraido);


                boolean isSantander = detectBank(textoExtraido, doc.getFileName(), SANTANDER_PATTERNS) || detectAllBanks(textoExtraido, SANTANDER_FALLBACK_PATTERNS);
                if (isSantander) {
                    TransferDTO transferenciaSantander = Santander.parseSantanderTransfer(textoExtraido, doc);
                    if (transferenciaSantander != null) {
                        lastTransfer = transferenciaSantander;
                        telegramFileService.createExcelFile(transferenciaSantander);
                        return Santander.formatSantander(transferenciaSantander);
                    }
                }

                boolean isCuentaDni = detectBank(textoExtraido, doc.getFileName(), CUENTA_DNI_PATTERNS) || detectAllBanks(textoExtraido, CUENTA_DNI_FALLBACK_PATTERNS);
                if (isCuentaDni) {
                    TransferDTO transferenciaDNI = CuentaDni.parseCuentaDniTransfer(textoExtraido, doc);
                    if (transferenciaDNI != null) {
                        lastTransfer = transferenciaDNI;
                        telegramFileService.createExcelFile(transferenciaDNI);
                        return CuentaDni.formatCuentaDni(transferenciaDNI);
                    }
                }

                // --- INICIO LOG UALA ---
                boolean isUala = detectBank(textoExtraido, doc.getFileName(), UALA_PATTERNS);
                
                // --- INICIO LOG MERCADO PAGO (igual que PDF, pero más tolerante para OCR de imágenes) ---
                boolean isMercadoPago = false;
                String[] lines = textoExtraido.split("\r?\n");
                // 1. Detección clásica en primeras 10 líneas
                for (int i = 0; i < Math.min(10, lines.length); i++) {
                    String lineNormalize = lines[i].replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]", "").toLowerCase();
                    if (lineNormalize.contains("mercadopago") || lineNormalize.contains("mpago") || lineNormalize.contains("mercado pago") || containsApproxWord(lineNormalize, "mercadopago", 2) || containsApproxWord(lineNormalize, "mercado pago", 2)) {
                        isMercadoPago = true;
                        break;
                    }
                }
                // 2. Detección por nombre de archivo
                if (!isMercadoPago) {
                    String fileNameLower = doc.getFileName().toLowerCase();
                    if (fileNameLower.contains("mercadopago") || fileNameLower.contains("mpago") || fileNameLower.contains("mercado pago")) {
                        isMercadoPago = true;
                    }
                }
                // 3. Detección por regex en todo el texto (multilínea)
                if (!isMercadoPago) {
                    // Permite saltos de línea y espacios entre 'mercado' y 'pago'
                    Pattern mpMulti = Pattern.compile("(?i)m\\s*e\\s*r\\s*c\\s*a\\s*d\\s*o[\\n\\r\\s\\-_:.,]*p\\s*a\\s*g\\s*o");
                    Matcher matcher = mpMulti.matcher(textoExtraido);
                    if (matcher.find()) {
                        isMercadoPago = true;
                    }
                }
                // 4. Detección por líneas separadas ("mercado" y "pago" en líneas distintas cercanas)
                if (!isMercadoPago) {
                    for (int i = 0; i < lines.length - 1; i++) {
                        String l1 = lines[i].toLowerCase();
                        String l2 = lines[i + 1].toLowerCase();
                        if ((l1.contains("mercado") && l2.contains("pago")) || (l2.contains("mercado") && l1.contains("pago"))) {
                            isMercadoPago = true;
                            break;
                        }
                    }
                }
                // 5. Detección por regex simple (por si acaso)
                if (!isMercadoPago && textoExtraido.matches("(?i).*\\bmercado\\s*pago\\b.*")) {
                    isMercadoPago = true;
                }
                if (isMercadoPago) {
                    TransferDTO transferenciaMP = MercadoPago.parseMercadoPagoTransfer(textoExtraido, doc);
                    if (transferenciaMP != null) {
                        lastTransfer = transferenciaMP;
                        telegramFileService.createExcelFile(transferenciaMP);
                        try {
                            String excelResult = ExportExcel.exportTransferToExcel(transferenciaMP);
                            if (excelResult.startsWith("Error")) {
                                System.out.println("Error al generar el archivo Excel: " + excelResult);
                                return "Error al generar el archivo Excel: " + excelResult;
                            }
                            return MercadoPago.formatMercadoPago(transferenciaMP);
                        } catch (IOException e) {
                            System.out.println("Error al generar el archivo Excel: " + e.getMessage());
                            return "Error al generar el archivo Excel: " + e.getMessage();
                        }
                    }
                }
                // --- FIN LOG MERCADO PAGO ---
                TransferDTO transferencia = mapperTransf(textoExtraido, false, doc);
                if (isUala && transferencia != null) {
                    lastTransfer = transferencia;
                    telegramFileService.createExcelFile(transferencia);
                    try {
                        String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                        if (excelResult.startsWith("Error")) {
                            System.out.println("Error al generar el archivo Excel: " + excelResult);
                            return "Error al generar el archivo Excel: " + excelResult;
                        }
                        // Siempre usar el formato de Ualá
                        return Uala.formatUala(transferencia);
                    } catch (IOException e) {
                        System.out.println("Error al generar el archivo Excel: " + e.getMessage());
                        return "Error al generar el archivo Excel: " + e.getMessage();
                    }
                }
               
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
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("BRUBANK")) {
                            return Brubank.formatBrubank(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("Banco Provincia")) {
                            return BancoProvincia.formatBancoProvincia(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("BANCOR")) {
                            return Bancor.formatBancor(transferencia);
                        } else if (com.bot.telegramdocreader.service.banks.CuentaDni.class.getSimpleName().equals(transferencia.getClass().getSimpleName()) || (transferencia.getTypeOFTransfer() != null && transferencia.getTypeOFTransfer().equalsIgnoreCase("Transferencia") && transferencia.getCuentaOrigen() != null && !transferencia.getCuentaOrigen().isEmpty() && transferencia.getBank() != null && !transferencia.getBank().isEmpty() && transferencia.getDate() != null && !transferencia.getDate().isEmpty())) {
                            return com.bot.telegramdocreader.service.banks.CuentaDni.formatCuentaDni(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("BBVA")) {
                            return BBVA.formatBBVA(transferencia);
                        } else if(transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("NARANJAX")) {
                            return NaranjaX.formatNaranjaX(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("GALICIA")) {
                            return Galicia.formatGalicia(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("SANTANDER")) {
                            return Santander.formatSantander(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("MACRO")) {
                            return Macro.formatMacro(transferencia);
                        }
                        
                        else {
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
                }
               
 else {
                    // Si el texto extraído contiene Banco Provincia, intenta forzar el parseo y formateo
                    if (textoExtraido.toLowerCase().contains("banco provincia") || textoExtraido.toLowerCase().contains("provincia")) {
                        System.out.println("Texto extraído (Banco Provincia):\n" + textoExtraido);
                        TransferDTO transferenciaForzada = BancoProvincia.parseBancoProvinciaTransfer(textoExtraido, doc);
                        if (transferenciaForzada != null) {
                            return BancoProvincia.formatBancoProvincia(transferenciaForzada);
                        }
                    }
                    
                    return textoExtraido;
                    
                }
            } else if (isPdf(doc)) {
                textoExtraido = extractTextFromPdf(doc, botToken);
                 
                if (textoExtraido.startsWith("Error") || textoExtraido.contains("protegido con contraseña")) {
                    return textoExtraido;
                }
                boolean isMercadoPago = false;
                String[] lines = textoExtraido.split("\r?\n");
                for (int i = 0; i < Math.min(5, lines.length); i++) {
                    String lineNormalize = lines[i].replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]", "").toLowerCase();
                    if (lineNormalize.contains("mercadopago") || lineNormalize.contains("mpago") || lineNormalize.contains("mercado pago")) {
                        isMercadoPago = true;
                        break;
                    }
                }
                if (!isMercadoPago) {
                    String fileNameLower = doc.getFileName().toLowerCase();
                    if (fileNameLower.contains("mercadopago") || fileNameLower.contains("mpago") || fileNameLower.contains("mercado pago")) {
                        isMercadoPago = true;
                    }
                }
                if (textoExtraido.matches("(?i).*\\bmercado\\s*pago\\b.*")) {
                    isMercadoPago = true;
                }
                if (isMercadoPago) {
                    TransferDTO transferenciaMP = MercadoPago.parseMercadoPagoTransfer(textoExtraido, doc);
                    if (transferenciaMP != null) {
                        lastTransfer = transferenciaMP;
                        telegramFileService.createExcelFile(transferenciaMP);
                        try {
                            String excelResult = ExportExcel.exportTransferToExcel(transferenciaMP);
                            if (excelResult.startsWith("Error")) {
                                System.out.println("Error al generar el archivo Excel: " + excelResult);
                                return "Error al generar el archivo Excel: " + excelResult;
                            }
                            return MercadoPago.formatMercadoPago(transferenciaMP);
                        } catch (IOException e) {
                            System.out.println("Error al generar el archivo Excel: " + e.getMessage());
                            return "Error al generar el archivo Excel: " + e.getMessage();
                        }
                    }
                }
                TransferDTO transferencia = mapperTransf(textoExtraido, true, doc);
                
                if (transferencia != null) {
                    lastTransfer = transferencia;
                    telegramFileService.createExcelFile(transferencia);
                    try {
                        String excelResult = ExportExcel.exportTransferToExcel(transferencia);
                        if (excelResult.startsWith("Error")) {
                            System.out.println("Error al generar el archivo Excel: " + excelResult);
                            return "Error al generar el archivo Excel: " + excelResult;
                        }
                        String cuitEmisor = (transferencia.getCuit() != null && !transferencia.getCuit().trim().isEmpty()) ? transferencia.getCuit() : "No hay CUIT del emisor";
                        if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("PREX")) {
                            return Prex.formatPrex(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("UALA")) {
                            return Uala.formatUala(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("BRUBANK")) {
                            return Brubank.formatBrubank(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("Banco Provincia")) {
                            return BancoProvincia.formatBancoProvincia(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("BANCOR")) 
                        {
                            return Bancor.formatBancor(transferencia);
                        } else if(transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("BBVA")) {
                            return BBVA.formatBBVA(transferencia);
                        } else if (com.bot.telegramdocreader.service.banks.CuentaDni.class.getSimpleName().equals(transferencia.getClass().getSimpleName()) || (transferencia.getTypeOFTransfer() != null && transferencia.getTypeOFTransfer().equalsIgnoreCase("Transferencia") && transferencia.getCuentaOrigen() != null && !transferencia.getCuentaOrigen().isEmpty() && transferencia.getBank() != null && !transferencia.getBank().isEmpty() && transferencia.getDate() != null && !transferencia.getDate().isEmpty())) {
                            return com.bot.telegramdocreader.service.banks.CuentaDni.formatCuentaDni(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("NARANJAX")) {
                            return NaranjaX.formatNaranjaX(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("GALICIA")) {
                            return Galicia.formatGalicia(transferencia);
                        } else if (transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("SANTANDER")) {
                            return Santander.formatSantander(transferencia);
                        }
                        else {
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
            } else {
                return "Formato de archivo no soportado.";
            }
        } catch (Exception e) {
            System.out.println("Error en el procesamiento del documento: " + e.getMessage());
            e.printStackTrace();
            return "Error en el procesamiento del documento: " + e.getMessage();
        }
        

    }
    
    
    // Archivos De Img Soportados
    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".heic", ".gif", ".bmp", ".tiff", ".webp", ".svg"};
    
    private boolean isImage(Document doc) {
        String fileName = doc.getFileName().toLowerCase();
        String mimeType = doc.getMimeType().toLowerCase();
    
        // Verificar por extensión de archivo
        boolean isImageByExtension = false;
        for (String ext : IMAGE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                isImageByExtension = true;
                break;
            }
        }
    
        // Verificar por tipo MIME
        boolean isImageByMimeType = mimeType.startsWith("image/");
    
        
        // Si la extensión es de imagen, considerar imagen aunque el mimeType no sea image/
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
        File file = null;
        InputStream inputStream = null;
        PDDocument document = null;
        try {
            file = getFileFromTelegram(doc.getFileId(), botToken);
            URL fileUrl = new URL("https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath());
            inputStream = fileUrl.openStream();
            document = PDDocument.load(inputStream, "");
            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            // Si el texto extraído es vacío o muy corto, intentar OCR en todas las páginas
            if (text.isEmpty() || text.length() < 30) {
                StringBuilder ocrText = new StringBuilder();
                org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                int pageCount = document.getNumberOfPages();
                for (int page = 0; page < pageCount; page++) {
                    java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300);
                    java.io.File tempImage = java.io.File.createTempFile("pdf_page_ocr_" + page, ".png");
                    javax.imageio.ImageIO.write(bim, "png", tempImage);
                    try {
                        String pageOcr = com.bot.telegramdocreader.utils.ImageProcessor.extractTextFromImage(tempImage).trim();
                        ocrText.append(pageOcr).append("\n");
                    } finally {
                        tempImage.delete();
                    }
                }
                text = ocrText.toString().trim();
                if (text.isEmpty()) {
                    throw new IOException("No se pudo extraer texto del PDF ni mediante OCR. El documento podría estar vacío o tener un formato no compatible.");
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
        // Booleanos para detectar el tipo de banco
        boolean esBrubank = detectBank(textoExtraido, doc.getFileName(), BRUBANK_PATTERNS);

        boolean isBBva = detectBank(textoExtraido, doc.getFileName(), BBVA_PATTERNS) || detectBBVA(textoExtraido, doc.getFileName());
        boolean isPersonalPay = detectBank(textoExtraido, doc.getFileName(), PERSONAL_PAY_PATTERNS) || textoExtraido.toLowerCase().contains("enviaste dinero");
        boolean isMercadoPago = detectBank(textoExtraido, doc.getFileName(), MERCADOPAGO_PATTERNS) || textoExtraido.toLowerCase().contains("mercadopago") || textoExtraido.toLowerCase().contains("mercado pago") || textoExtraido.toLowerCase().contains("mpago");
        boolean isNaranjaX = detectBank(textoExtraido, doc.getFileName(), NARANJAX_PATTERNS);
        boolean isBankProvincia = detectBank(textoExtraido, doc.getFileName(), BANCO_PROVINCIA_PATTERNS) || textoExtraido.toLowerCase().contains("nueva transferencia");
        boolean bancorByContent = detectBancorByContent(textoExtraido);
        boolean isBancor = detectBank(textoExtraido, doc.getFileName(), BANCOR_PATTERNS) || bancorByContent;
        boolean isMacro = detectBank(textoExtraido, doc.getFileName(), MACRO_PATTERNS);
        boolean isGalicia = detectBank(textoExtraido, doc.getFileName(), GALICIA_PATTERNS);
        
        boolean isSantander = detectBank(textoExtraido, doc.getFileName(), SANTANDER_PATTERNS);
        boolean isCuentaDni = false;
        // Detectar Cuenta DNI por patrones característicos (más robusto)
        String textoLower = textoExtraido.toLowerCase();
        // Debe contener "comprobante de transferencia", "origen", "para", "importe" y un monto con $ y una fecha
        boolean tieneComprobante = textoLower.contains("comprobante de transferencia");
        boolean tieneOrigen = textoLower.contains("origen");
        boolean tienePara = textoLower.contains("para");
        boolean tieneImporte = textoLower.contains("importe");
        boolean tieneMonto = textoLower.matches("(?s).*[\\r\\n]\\s*\\$ ?[0-9.]+,[0-9]{2}.*");
        boolean tieneFecha = textoLower.matches("(?s).*[\\r\\n]\\s*\\d{2}/\\d{2}/\\d{4}.*");
        if ((textoLower.contains("cuenta dni") && tieneComprobante && tieneOrigen && tienePara && tieneImporte) ||
            (tieneComprobante && tieneOrigen && tienePara && tieneImporte && tieneMonto && tieneFecha)) {
            isCuentaDni = true;
        }
        if (isCuentaDni) {
            return com.bot.telegramdocreader.service.banks.CuentaDni.parseCuentaDniTransfer(textoExtraido, doc);
        }

        if (esBrubank) {
            return Brubank.parseBrubankTransfer(textoExtraido, doc);
        }
        if (isBBva) {
            return com.bot.telegramdocreader.service.banks.BBVA.parseBBVATransfer(textoExtraido, doc);
        }
        
        if (isPersonalPay) {
            return com.bot.telegramdocreader.service.banks.PersonalPay.parsePersonalPayTransfer(textoExtraido, doc);
        }
        

        if (isMercadoPago) {
            return com.bot.telegramdocreader.service.banks.MercadoPago.parseMercadoPagoTransfer(textoExtraido, doc);
        }
        
        
        
        if (isNaranjaX) {
            return com.bot.telegramdocreader.service.banks.NaranjaX.parseNaranjaXTransfer(textoExtraido, doc);
        }
        
        
        if (isGalicia) {
            return com.bot.telegramdocreader.service.banks.Galicia.parseGaliciaTransfer(textoExtraido, doc);
        }
       if (isMacro) {
            return com.bot.telegramdocreader.service.banks.Macro.parserMacro(textoExtraido, doc);
       }
        
       
        if (isBancor) {
            
            return com.bot.telegramdocreader.service.banks.Bancor.parseBancorTransfer(textoExtraido, doc);
        }
        
        
        if (isBankProvincia) {
            return com.bot.telegramdocreader.service.banks.BancoProvincia.parseBancoProvinciaTransfer(textoExtraido, doc);
        }
        if(isSantander) {
            return com.bot.telegramdocreader.service.banks.Santander.parseSantanderTransfer(textoExtraido, doc);
        }
        // Detectar si es transferencia de PREX
        boolean isPrex = false;
        String[] lines = textoExtraido.split("\r?\n");
        // Detectar si es transferencia de Ualá
        boolean isUala = detectBank(textoExtraido, doc.getFileName(), UALA_PATTERNS);
        if (isUala) {
            TransferDTO ualaTransfer = Uala.parseUalaTransfer(textoExtraido, doc);
            if (ualaTransfer != null) {
                ualaTransfer.setBank("UALA");
            }
            return ualaTransfer;
        }
        isPrex = detectBank(textoExtraido, doc.getFileName(), PREX_PATTERNS);
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

       
        boolean tieneCuit = textoCompleto.contains("cuit") || textoCompleto.matches(".*\\d{2}-\\d{8}-\\d{1}.*");
        if (tieneFecha && tieneMonto && tieneCuit) {
            // Extraer valores si están vacíos
            if (fecha.isEmpty()) {
                // Buscar línea que contenga 'Fecha' y extraer la fecha
                for (String line : lines) {
                    if (line.toLowerCase().contains("fecha")) {
                        fecha = extractDate(line);
                        if (!fecha.isEmpty()) break;
                    }
                }
                if (fecha.isEmpty()) fecha = extractDate(textoCompleto);
            }
            if (monto.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\s*([0-9.]+)").matcher(textoCompleto);
                if (matcher.find()) monto = matcher.group(1);
            }
            if (cuitSender.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}-\\d{8}-\\d{1})").matcher(textoCompleto);
                if (matcher.find()) cuitSender = matcher.group(1);
            }
            // Extraer banco receptor del campo 'Recibe' si existe
            if (bankReceiver == null || bankReceiver.trim().isEmpty()) {
                for (String line : lines) {
                    if (line.toLowerCase().contains("recibe")) {
                        String[] partes = line.split(":", 2);
                        if (partes.length == 2) {
                            bankReceiver = partes[1].trim();
                            break;
                        }
                    }
                }
                // Si no se encuentra, intentar patrón anterior
                if (bankReceiver == null || bankReceiver.trim().isEmpty()) {
                    java.util.regex.Pattern patternBanco = java.util.regex.Pattern.compile("(?:para|destinatario|beneficiario)[:\\s]*([A-Za-z0-9 .\\-]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher matcherBanco = patternBanco.matcher(textoCompleto);
                    if (matcherBanco.find()) {
                        bankReceiver = matcherBanco.group(1).trim();
                    }
                }
            }
            TransferDTO transferencia = TransferDTO.builder().build();
            transferencia.setDate(fecha);
            transferencia.setTypeOFTransfer(tipoOperacion.isEmpty() ? "Transferencia" : tipoOperacion);
            transferencia.setCuit(cuitSender);
            transferencia.setAmount(monto);
            transferencia.setBank(bankReceiver);
            return transferencia;
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
                if (lower.contains("fundraiser")) {
                    bankReceiver = "FUNDRAISER s.a.s.";
                }else {
                    if (lower.contains("cuit") || lower.contains("cuil") || lower.contains("cuit:")) {
                        cuitSender = linea.trim();
                    }
                }
            }

            if (isPdfFormat) {
                // lógica genérica para PDF

                
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
                else if (lower.contains("para") || lower.contains("pera") || lower.contains("destinatario") || lower.contains("beneficiario") || containsApproxWord(lower, "para", 1)) {
                    String posibleBanco = original.replaceAll("(?i)para:|pera:|destinatario:|beneficiario:", "").trim();
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
                    continue;
                } else if (lower.contains("para") || lower.contains("pera") || lower.contains("destinatario") || lower.contains("beneficiario") || containsApproxWord(lower, "para", 1)) {
                    String posibleBanco = original.replaceAll("(?i)para:|pera:|destinatario:|beneficiario:", "").trim();
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
            if (bankReceiver == null || bankReceiver.trim().isEmpty()) {
                bankReceiver = "Desconocido";
            }
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

/**
 * Método auxiliar para detectar bancos basado en patrones
 * @param texto Texto extraído del documento
 * @param fileName Nombre del archivo
 * @param patterns Patrones a buscar
 * @return true si se detecta el banco
 */


private boolean detectBank(String texto, String fileName, String[] patterns) {
    String textoLower = texto.toLowerCase();
    String fileNameLower = fileName.toLowerCase();
    java.util.function.Function<String, String> normalize = s -> s.replaceAll("[^a-záéíóúñ]", "");
    // Verificar en el nombre del archivo
    for (String pattern : patterns) {
        if (normalize.apply(fileNameLower).contains(normalize.apply(pattern))) {
            return true;
        }
    }
    // Verificar en las primeras líneas del texto (más robusto para BBVA)
    String[] lines = texto.split("\\r?\\n");
    for (int i = 0; i < Math.min(10, lines.length); i++) { // Aumentar a 10 líneas para Ualá
        String lineNormalize = normalize.apply(lines[i].toLowerCase());
        for (String pattern : patterns) {
            String patternNorm = normalize.apply(pattern);
            if (lineNormalize.contains(patternNorm) || containsApproxWord(lineNormalize, patternNorm, 1)) {
                return true;
            }
        }
    }
    // Verificar en todo el texto con regex
    for (String pattern : patterns) {
        if (textoLower.matches("(?i).*\\b" + pattern.replace(" ", "\\s*") + "\\b.*")) {
            return true;
        }
    }
    return false;
}

/**
 * Método auxiliar para detectar Bancor por contenido específico
 * @param texto Texto extraído del documento
 * @return true si se detecta como Bancor por contenido
 */
private boolean detectBancorByContent(String texto) {
    String textoLower = texto.toLowerCase();
    return textoLower.contains("transferencia enviada") &&
           textoLower.contains("transferiste") &&
           textoLower.contains("datos origen") &&
           textoLower.contains("datos destino") &&
           textoLower.contains("identificador de la transferencia") &&
           textoLower.contains("cód. transacción");
}
    

private boolean detectBBVA(String texto, String fileName) {
    String textoLower = texto.toLowerCase();
    String fileNameLower = fileName.toLowerCase();
    int countBBVA = 0;
    int idx = textoLower.indexOf("bbva");
    while (idx != -1) {
        countBBVA++;
        idx = textoLower.indexOf("bbva", idx + 1);
    }
    idx = fileNameLower.indexOf("bbva");
    while (idx != -1) {
        countBBVA++;
        idx = fileNameLower.indexOf("bbva", idx + 1);
    }
    boolean keywords = textoLower.contains("transferencia") || textoLower.contains("comprobante") || textoLower.contains("cuenta de origen") || textoLower.contains("cbu") || textoLower.contains("alias");
    // Detección directa por frase característica
    if (textoLower.contains("bbva móvil") || textoLower.contains("esta operación se realizó en bbva")) {
        return true;
    }
    // Si aparece 'bbva' al menos dos veces, lo detecta sin requerir keywords
    if (countBBVA >= 2) {
        return true;
    }
    // Para variantes menos comunes, requiere keywords
    return (textoLower.contains("banco francés") || textoLower.contains("francés") || textoLower.contains("frances")) && keywords;
}
    }
