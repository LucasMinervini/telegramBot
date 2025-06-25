package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class BancoProvincia {
    public static String formatBancoProvincia(TransferDTO transferencia) {
        // Formato solicitado por el usuario
        String formato =  "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "-",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "-",
                transferencia.getCuit() != null ? transferencia.getCuit() : "-",
                transferencia.getAmount() != null ? transferencia.getAmount() : "-",
                transferencia.getBank() != null ? transferencia.getBank() : "-");
    }

    public static TransferDTO parseBancoProvinciaTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\r?\n");
        String fecha = "";
        String transactionNumber = "";
        String titular = "";
        String titularCuit = "";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "Banco Provincia";
        // Buscar todos los campos relevantes de forma tolerante
        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            String original = line.trim();
            if ((lower.contains("fecha de acreditación") || lower.contains("fecha de acreditacion")) && fecha.isEmpty()) {
                String value = original.replaceAll("(?i)fecha( de)? acreditaci[oó]n", "").replace(":", "").trim();
                if (!value.isEmpty()) fecha = value;
            } else if (lower.matches(".*\\d{2}/\\d{2}/\\d{4}.*") && fecha.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
                if (matcher.find()) fecha = matcher.group(1);
            }
            if ((lower.contains("número de transacción") || lower.contains("numero de transaccion")) && transactionNumber.isEmpty()) {
                transactionNumber = original.replaceAll("(?i)n[úu]mero de transacci[óo]n", "").replace(":", "").trim();
            }
            if (lower.contains("titular:") && titular.isEmpty()) {
                String value = original.replaceAll("(?i)titular:", "").trim();
                if (value.contains("/")) {
                    String[] partes = value.split("/");
                    titular = partes[0].trim();
                    titularCuit = partes.length > 1 ? partes[1].replaceAll("[^0-9]", "").trim() : "";
                } else {
                    titular = value;
                }
            }
            if ((lower.contains("importe") || lower.contains("monto")) && monto.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\s*([0-9.,]+)").matcher(original);
                if (matcher.find()) monto = matcher.group(1);
                else {
                    matcher = java.util.regex.Pattern.compile("([0-9]{1,3}(\\.[0-9]{3})*,[0-9]{2})").matcher(original);
                    if (matcher.find()) monto = matcher.group(1);
                }
            }
        }
        // Si no se encontró fecha, buscar explícitamente la línea con "fecha de acreditación" en todo el texto
        if (fecha.isEmpty()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)fecha de acreditaci[oó]n:?\\s*(\\d{2}/\\d{2}/\\d{4})").matcher(textoExtraido);
            if (matcher.find()) {
                fecha = matcher.group(1);
            }
        }
        // Extraer CUIT del titular (si no se encontró en la línea de titular)
        if (titularCuit.isEmpty() && titular.contains("/")) {
            String[] partes = titular.split("/");
            if (partes.length > 1) {
                titularCuit = partes[1].replaceAll("[^0-9]", "").trim();
            }
        }
        if (cuit.isEmpty() && !titularCuit.isEmpty()) {
            cuit = titularCuit;
        }
        // Si no se encontró CUIT, buscar en todo el texto
        if (cuit.isEmpty()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9]{2}-?[0-9]{8}-?[0-9])").matcher(textoExtraido);
            if (matcher.find()) {
                cuit = matcher.group(1).replaceAll("-", "");
            }
        }
        if (cuit.length() == 11) {
            cuit = cuit.substring(0,2) + "-" + cuit.substring(2,10) + "-" + cuit.substring(10);
        }
        if (monto.isEmpty()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\s*([0-9.,]+)").matcher(textoExtraido);
            if (matcher.find()) monto = matcher.group(1);
        }
        // Si no se encontró ningún dato, igual devolver el formato con guiones
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(transactionNumber)
                .cuit(cuit)
                .amount(monto)
                .bank(bancoReceptor)
                .build();
    }
}