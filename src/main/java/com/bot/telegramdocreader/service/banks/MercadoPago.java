package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class MercadoPago {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPago.class);
    
    
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
        boolean foundPara = false;
        int paraIndex = -1;

        
        
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            // Fecha (buscar línea con día de la semana y fecha larga)
            if (fecha.isEmpty() && lower.matches("^(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)[,\\s].*\\d{4}.*")) {
                String fechaTexto = original.replaceAll("\\s+a\\s+las.*", "").replaceAll(",", "").trim();
                String fechaConvertida = convertirFechaTextoANumerico(fechaTexto);
                fecha = !fechaConvertida.isEmpty() ? fechaConvertida : fechaTexto;
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
            
            if (lower.matches("^[^a-zA-Z0-9]*para(\\s|:|$)") || lower.matches("^[^a-zA-Z0-9]*pera(\\s|:|$)")) {
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
       // bancoReceptor = extractBancoReceptor(lines);
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
            
            // Convertir mes (acepta abreviaturas)
            String mes = "";
            if (lower.contains("enero") || lower.contains("ene")) mes = "01";
            else if (lower.contains("febrero") || lower.contains("feb")) mes = "02";
            else if (lower.contains("marzo") || lower.contains("mar")) mes = "03";
            else if (lower.contains("abril") || lower.contains("abr")) mes = "04";
            else if (lower.contains("mayo") || lower.contains("may")) mes = "05";
            else if (lower.contains("junio") || lower.contains("jun")) mes = "06";
            else if (lower.contains("julio") || lower.contains("jul")) mes = "07";
            else if (lower.contains("agosto") || lower.contains("ago")) mes = "08";
            else if (lower.contains("septiembre") || lower.contains("setiembre") || lower.contains("sep")) mes = "09";
            else if (lower.contains("octubre") || lower.contains("oct")) mes = "10";
            else if (lower.contains("noviembre") || lower.contains("nov")) mes = "11";
            else if (lower.contains("diciembre") || lower.contains("dic")) mes = "12";
            
            if (!dia.isEmpty() && !mes.isEmpty() && !año.isEmpty()) {
                return dia + "/" + mes + "/" + año;
            }
        } catch (Exception e) {
            logger.debug("Error convirtiendo fecha: " + fechaTexto, e);
        }
        return "";
    }

}