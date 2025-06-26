package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class MercadoPago {
    public static String formatMercadoPago(TransferDTO transferencia) {
        String cuit = (transferencia.getCuit() != null && !transferencia.getCuit().isEmpty()) ? transferencia.getCuit() : "no hay cuit emisor";
        String titular = (transferencia.getName() != null && !transferencia.getName().isEmpty()) ? transferencia.getName() : "no detectado";
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil Emisor: %s\nMonto Bruto: $ %s\nTitular receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                cuit,
                transferencia.getAmount()!= null? transferencia.getAmount() : "",
                titular);
    }

    public static TransferDTO parseMercadoPagoTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n|\\r");
        String fecha = "";
        String tipoOperacion = "";
        String cuitEmisor = "";
        String monto = "";
        String bancoReceptor = "Mercado Pago";
        String titular = "";
        boolean foundPara = false;
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            // Fecha (buscar línea con día de la semana y fecha larga)
            if (fecha.isEmpty() && lower.matches("^(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)[,\s].*\\d{4}.*")) {
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
            if (lower.startsWith("para")) {
                foundPara = true;
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
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(!tipoOperacion.isEmpty() ? tipoOperacion : "Transferencia")
                .cuit(cuitEmisor)
                .amount(monto)
                .name(titular)
                .bank(bancoReceptor)
                .build();
    }
}