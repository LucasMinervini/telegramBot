package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import com.bot.telegramdocreader.config.MercadoPagoConfig;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MercadoPago {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPago.class);
    
    // Patrones compilados para mejor rendimiento
    private static final Pattern DATE_PATTERN = Pattern.compile(MercadoPagoConfig.DATE_PATTERN);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(MercadoPagoConfig.AMOUNT_PATTERN);
    private static final Pattern BANK_PATTERN = Pattern.compile(MercadoPagoConfig.BANK_PATTERN);
    private static final Pattern DAY_DATE_PATTERN = Pattern.compile(MercadoPagoConfig.DAY_DATE_PATTERN);
    public static String formatMercadoPago(TransferDTO transferencia) {
        String cuit = (transferencia.getCuit() != null && !transferencia.getCuit().isEmpty()) ? transferencia.getCuit() : "no hay cuit emisor";
        String titular = (transferencia.getName() != null && !transferencia.getName().isEmpty()) ? transferencia.getName() : "no detectado";
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                cuit,
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                titular);
    }

    /**
     * Parsea un documento de transferencia de Mercado Pago
     * @param textoExtraido Texto extraído del PDF
     * @param doc Documento original (para metadatos)
     * @return TransferDTO con la información extraída
     */
    public static TransferDTO parseMercadoPagoTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n|\\r");
        String fecha = "";
        String tipoOperacion = "";
        String cuitEmisor = "";
        String monto = "";
        String titular = "";
        String bancoReceptor = "";
        boolean foundPara = false;
        int paraIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            // Fecha (buscar línea con día de la semana y fecha larga)
            if (fecha.isEmpty() && lower.matches("^(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)[,\\s].*\\d{4}.*")) {
                fecha = original.replaceAll("\\s+a\\s+las.*", "").replaceAll(",", "").trim();
            }
            // Fecha (fallback)
            if ((lower.contains("fecha de operación") || lower.contains("fecha de operacion") || lower.startsWith("fecha")) && fecha.isEmpty()) {
                String value = original.replaceAll("(?i)fecha de operación|fecha de operacion|fecha", "").replace(":", "").trim();
                if (!value.isEmpty()) fecha = value;
            }
            if (fecha.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{2}[./-]\\d{2}[./-]\\d{4})\\b").matcher(original);
                if (matcher.find()) fecha = matcher.group(1);
            }
            // Monto
            if ((lower.contains("importe") || lower.contains("monto")) && monto.isEmpty()) {
                String value = original.replaceAll("(?i)importe|monto", "").replace(":", "").replace("$", "").replace(",", ".").trim();
                if (!value.isEmpty()) monto = value;
            }
            if (monto.isEmpty() && lower.matches(".*\\$\\s*[0-9]+[.,]?[0-9]*.*")) {
                String value = original.replaceAll("[^0-9.,]", "").trim();
                if (!value.isEmpty()) monto = value;
            }
            // Tipo de operación
            if ((lower.contains("transferencia") || lower.contains("enviaste") || lower.contains("envío") || lower.contains("comprobante de transferencia")) && tipoOperacion.isEmpty()) {
                tipoOperacion = "Transferencia";
            }
            // CUIT Emisor
            if ((lower.contains("cuit emisor") || lower.contains("cuil emisor") || lower.contains("cuit del emisor") || lower.contains("cuil del emisor") || lower.startsWith("de") || lower.startsWith("De") ) && cuitEmisor.isEmpty()) {
                String value = original.replaceAll("(?i)cuit emisor|cuil emisor|cuit del emisor|cuil del emisor", "").replace(":", "").replaceAll("[^0-9]", "").trim();
                if (value.length() == 11) {
                    cuitEmisor = value.substring(0,2) + "-" + value.substring(2,10) + "-" + value.substring(10);
                }
            }
            // Buscar CUIT emisor en línea con 'CUIT/CUIL' antes de 'Para' (emisor)
            if (cuitEmisor.isEmpty() && lower.contains("cuit/cuil") && !foundPara) {
                String value = original.replaceAll("(?i)cuit/cuil", "").replace(":", "").replaceAll("[^0-9]", "").trim();
                if (value.length() == 11) {
                    cuitEmisor = value.substring(0,2) + "-" + value.substring(2,10) + "-" + value.substring(10);
                }
            }
            // Fallback: buscar CUIT emisor en cualquier línea antes de 'Para'
            if (cuitEmisor.isEmpty() && lower.matches(".*\\d{2}-\\d{8}-\\d{1}.*") && !foundPara) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}-\\d{8}-\\d{1})").matcher(original);
                if (matcher.find()) cuitEmisor = matcher.group(1);
            }
            // Titular receptor: buscar después de 'Para'
            if (lower.startsWith("para") || lower.contains("pera")) {
                foundPara = true;
                paraIndex = i;
                continue;
            }
            if (foundPara && titular.isEmpty() && !lower.isEmpty() && !lower.contains("cuit") && !lower.contains("cvu") && !lower.contains("neblockchain") && !lower.contains("número de operación") && !lower.contains("codigo de identificacion")) {
                titular = original;
                foundPara = false;
            }
            // Alternativa: buscar línea con 'titular', 'nombre' o 'a nombre de'
            if ((lower.contains("titular") || lower.contains("nombre") || lower.contains("a nombre de")) && titular.isEmpty()) {
                String value = original.replaceAll("(?i)titular|nombre|a nombre de", "").replace(":", "").trim();
                if (!value.isEmpty()) titular = value;
            }
        }
        // Si no se encontró el titular, buscar entre las siguientes 3 líneas después de 'Para'
        if (titular.isEmpty() && paraIndex != -1) {
            for (int j = paraIndex + 1; j < Math.min(lines.length, paraIndex + 4); j++) {
                String lower = lines[j].toLowerCase().trim();
                String original = lines[j].trim();
                if (!lower.isEmpty() && !lower.contains("cuit") && !lower.contains("cvu") && !lower.contains("neblockchain") && !lower.contains("número de operación") && !lower.contains("codigo de identificacion")) {
                    titular = original;
                    break;
                }
            }
        }
        bancoReceptor = extractBancoReceptor(lines);
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(!tipoOperacion.isEmpty() ? tipoOperacion : "Transferencia")
                .cuit(cuitEmisor)
                .amount(monto)
                .name(titular)
                .bank(titular)
                .build();
    }

    /**
     * Encuentra la línea donde comienza la sección 'Para' (receptor), tolerante a OCR (ej: 'Pera')
     */
    private static int findParaSection(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            if (startsWithAny(lower, MercadoPagoConfig.SECTION_TO) || lower.startsWith("pera") || containsApproxWord(lower, "para", 1)) {
                logger.debug(MercadoPagoConfig.LOG_SECTION_FOUND, MercadoPagoConfig.SECTION_TO, i);
                return i;
            }
        }
        return -1;
    }

    // Utilidad para coincidencia aproximada (Levenshtein)
    private static boolean containsApproxWord(String text, String target, int tolerance) {
        text = text.toLowerCase();
        target = target.toLowerCase();
        int distance = org.apache.commons.lang3.StringUtils.getLevenshteinDistance(text, target);
        return distance <= tolerance;
    }

    // Devuelve true si el texto comienza con alguno de los prefijos dados
    private static boolean startsWithAny(String text, String[] prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extrae la fecha del documento
     */
    private static String extractFecha(String[] lines) {
        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            String original = line.trim();
            
            // Buscar fecha con día de la semana y convertir a formato DD/MM/YYYY
            if (DAY_DATE_PATTERN.matcher(lower).matches()) {
                String fechaTexto = original.replaceAll("\\s+a\\s+las.*", "").replaceAll(",", "").trim();
                // Convertir fecha en texto a formato numérico
                String fechaConvertida = convertirFechaTextoANumerico(fechaTexto);
                if (!fechaConvertida.isEmpty()) {
                    return fechaConvertida;
                }
                return fechaTexto;
            }
            
            // Buscar fecha explícita
            if (lower.contains("fecha de operación") || lower.contains("fecha de operacion") || lower.startsWith("fecha")) {
                String value = original.replaceAll("(?i)fecha de operación|fecha de operacion|fecha", "").replace(":", "").trim();
                if (!value.isEmpty()) return value;
            }
            
            // Buscar patrón de fecha
            Matcher matcher = DATE_PATTERN.matcher(original);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }
    
    /**
     * Convierte fecha en texto a formato DD/MM/YYYY
     */
    private static String convertirFechaTextoANumerico(String fechaTexto) {
        try {
            String lower = fechaTexto.toLowerCase();
            
            // Extraer día
            String dia = "";
            String[] partes = lower.split("\\s+");
            for (String parte : partes) {
                if (parte.matches("\\d{1,2}")) {
                    dia = parte.length() == 1 ? "0" + parte : parte;
                    break;
                }
            }
            
            // Extraer año
            String año = "";
            for (String parte : partes) {
                if (parte.matches("\\d{4}")) {
                    año = parte;
                    break;
                }
            }
            
            // Convertir mes
            String mes = "";
            if (lower.contains("enero")) mes = "01";
            else if (lower.contains("febrero")) mes = "02";
            else if (lower.contains("marzo")) mes = "03";
            else if (lower.contains("abril")) mes = "04";
            else if (lower.contains("mayo")) mes = "05";
            else if (lower.contains("junio")) mes = "06";
            else if (lower.contains("julio")) mes = "07";
            else if (lower.contains("agosto")) mes = "08";
            else if (lower.contains("septiembre") || lower.contains("setiembre")) mes = "09";
            else if (lower.contains("octubre")) mes = "10";
            else if (lower.contains("noviembre")) mes = "11";
            else if (lower.contains("diciembre")) mes = "12";
            
            if (!dia.isEmpty() && !mes.isEmpty() && !año.isEmpty()) {
                return dia + "/" + mes + "/" + año;
            }
        } catch (Exception e) {
            logger.debug("Error convirtiendo fecha: " + fechaTexto, e);
        }
        return "";
    }
    
    /**
      * Extrae el monto de la transferencia
      */
     private static String extractMonto(String[] lines) {
         for (String line : lines) {
             String lower = line.toLowerCase().trim();
             String original = line.trim();
             
             // Buscar monto explícito
             if (lower.contains("importe") || lower.contains("monto")) {
                 String value = original.replaceAll("(?i)importe|monto", "").replace(":", "").replace("$", "").replace(",", ".").trim();
                 if (!value.isEmpty()) return value;
             }
             
             // Buscar patrón de monto con $
             if (AMOUNT_PATTERN.matcher(lower).matches()) {
                 String value = original.replaceAll("[^0-9.,]", "").trim();
                 if (!value.isEmpty()) return value;
             }
         }
         return "";
     }
     
     /**
      * Extrae el tipo de operación
      */
     private static String extractTipoOperacion(String[] lines) {
         for (String line : lines) {
             String lower = line.toLowerCase();
             for (String keyword : MercadoPagoConfig.OPERATION_KEYWORDS) {
                 if (lower.contains(keyword)) {
                     return "Transferencia";
                 }
             }
         }
         return "";
     }
    
    /**
      * Extrae el CUIT del emisor
      */
     private static String extractCuitEmisor(String[] lines, int indicePara) {
         // Buscar la sección 'De' específicamente
         int indiceDeSection = -1;
         for (int i = 0; i < lines.length; i++) {
             if (lines[i].toLowerCase().trim().equals("de")) {
                 indiceDeSection = i;
                 break;
             }
         }
         // Si encontramos 'De', buscar CUIT solo en esa sección hasta 'Para'
         if (indiceDeSection != -1) {
             int endSearch = (indicePara != -1) ? indicePara : lines.length;
             for (int i = indiceDeSection + 1; i < endSearch; i++) {
                 String lower = lines[i].toLowerCase().trim();
                 String original = lines[i].trim();
                 if (lower.contains("cuit") || lower.contains("cuil")) {
                     String numbersOnly = original.replaceAll("[^0-9]", "");
                     if (numbersOnly.length() == MercadoPagoConfig.CUIT_LENGTH) {
                         boolean isExcluded = false;
                         for (String excluded : MercadoPagoConfig.EXCLUDED_KEYWORDS) {
                             if (!excluded.equals("cuit") && !excluded.equals("cuil") && lower.contains(excluded)) {
                                 isExcluded = true;
                                 break;
                             }
                         }
                         if (!isExcluded) {
                             String formattedCuit = formatCuit(numbersOnly);
                             logger.debug(MercadoPagoConfig.LOG_CUIT_DETECTED, formattedCuit, i, original);
                             return formattedCuit;
                         } else {
                             logger.debug(MercadoPagoConfig.LOG_CUIT_DISCARDED, original);
                         }
                     }
                 }
             }
         }
         // Si no se encuentra CUIT en la sección 'De', retornar vacío
         return "";
     }
     
     /**
      * Verifica si una línea está dentro de una sección específica
      */
     private static boolean isInSection(String[] lines, int currentIndex, String sectionName) {
         for (int j = Math.max(0, currentIndex - MercadoPagoConfig.SEARCH_RANGE_LINES); j <= currentIndex; j++) {
             if (lines[j].toLowerCase().trim().equals(sectionName)) {
                 return true;
             }
         }
         return false;
     }
     
     /**
      * Verifica si una línea contiene palabras clave que excluyen el CUIT
      */
     private static boolean isExcludedKeyword(String lowerText) {
         for (String excluded : MercadoPagoConfig.EXCLUDED_KEYWORDS) {
             if (lowerText.contains(excluded)) {
                 return true;
             }
         }
         return false;
     }
     
     /**
      * Formatea un CUIT en el formato XX-XXXXXXXX-X
      */
     private static String formatCuit(String numbersOnly) {
         return numbersOnly.substring(0,2) + "-" + numbersOnly.substring(2,10) + "-" + numbersOnly.substring(10);
     }
    
    /**
      * Extrae el banco receptor
      */
     private static String extractBancoReceptor(String[] lines) {
         int indicePara = findParaSection(lines);
         // Buscar explícitamente "Banco Receptor" si existe
         for (int i = 0; i < lines.length; i++) {
             String lower = lines[i].toLowerCase().trim();
             String original = lines[i].trim();
             if (lower.contains(MercadoPagoConfig.SECTION_BANK)) {
                 String value = original.replaceAll("(?i)" + MercadoPagoConfig.SECTION_BANK + ":?", "").trim();
                 if (!value.isEmpty()) {
                     return value;
                 } else {
                     return getNextValidLine(lines, i);
                 }
             }
         }
         // Buscar la primera empresa válida después de 'Para', ignorando NEBLOCKCHAIN SA y similares
         if (indicePara != -1) {
             for (int i = indicePara + 1; i < lines.length && i < indicePara + 10; i++) {
                 String lower = lines[i].toLowerCase().trim();
                 String original = lines[i].trim();
                 if (!lower.isEmpty() &&
                     !lower.contains("cuit") &&
                     !lower.contains("cuil") &&
                     !lower.contains("cvu") &&
                     !lower.contains("operación") &&
                     !lower.contains("número") &&
                     !lower.contains("codigo") &&
                     !lower.contains("mercado pago") &&
                     !lower.contains("neblockchain") &&
                     !lower.contains("identificacion") &&
                     original.length() > 3 &&
                     (lower.contains("sa") || lower.contains("srl") || lower.contains("banco") || (lower.matches(".*[a-z]+.*") && !lower.matches(".*\\d{2}-\\d{8}-\\d.*")))) {
                     // Retornar la primera empresa válida encontrada (debería ser "Fargotez Sa")
                     return original;
                 }
             }
         }
         return "";
     }
     
     /**
      * Obtiene la siguiente línea válida que no contenga palabras excluidas
      */
     private static String getNextValidLine(String[] lines, int currentIndex) {
         if (currentIndex + 1 < lines.length) {
             String nextLine = lines[currentIndex + 1].trim();
             if (!nextLine.isEmpty() && !isExcludedKeyword(nextLine.toLowerCase())) {
                 return nextLine;
             }
         }
         return "";
     }
    
   
    private static String extractTitular(String[] lines, int indicePara) {
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            
            // Procesar sección del receptor (después de 'Para')
            boolean foundPara = (indicePara != -1 && i > indicePara);
            
            // Titular receptor: buscar después de 'Para'
            if (foundPara && !lower.isEmpty() && !lower.contains("cuit") && !lower.contains("cvu") && !lower.contains("neblockchain") && !lower.contains("número de operación") && !lower.contains("codigo de identificacion")) {
                return original;
            }
            
            // Alternativa: buscar línea con 'titular', 'nombre' o 'a nombre de'
            if (lower.contains("titular") || lower.contains("nombre") || lower.contains("a nombre de")) {
                String value = original.replaceAll("(?i)titular|nombre|a nombre de", "").replace(":", "").trim();
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }
    
    private static TransferDTO createEmptyTransferDTO() {
        return TransferDTO.builder()
                .date("")
                .typeOFTransfer("Transferencia")
                .cuit("")
                .amount("")
                .name("")
                .bank("")
                .build();
    }
    

}