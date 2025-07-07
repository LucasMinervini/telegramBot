package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class BancoProvincia {
    public static String formatBancoProvincia(TransferDTO transferencia) {
        // Formato solicitado por el usuario
        String formato =  "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        String bancoReceptor = transferencia.getTitularCuentaDestino() != null && !transferencia.getTitularCuentaDestino().isEmpty() ? extraerNombreBanco(transferencia.getTitularCuentaDestino()) : "-";
        String tipoOperacion = transferencia.getTypeOFTransfer() != null && (transferencia.getTypeOFTransfer().equalsIgnoreCase("debito") || transferencia.getTypeOFTransfer().equalsIgnoreCase("transferencia")) ? transferencia.getTypeOFTransfer() : "transferencia";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "-",
                tipoOperacion,
                transferencia.getCuit() != null ? transferencia.getCuit() : "-",
                transferencia.getAmount() != null ? transferencia.getAmount() : "-",
                bancoReceptor);
    }

    public static TransferDTO parseBancoProvinciaTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\r?\n");
        String fecha = "";
        String transactionNumber = "";
        String tipoOperacion = "";
        String titular = "";
        String titularCuit = "";
        String cuit = "";
        String monto = "";
        
        String titularCuentaDestino = "";
        
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

            if (lower.contains("trasnferencia") || lower.contains("transferencia") || lower.contains("transferencia") || lower.contains("enviaste "))  {
                tipoOperacion = "Transferencia";
            }
            if (lower.contains("debito") || lower.contains("deposito") || (lower.contains("débito") || lower.contains("débito"))) {
                tipoOperacion = "debito";
            } else if (lower.contains("deposito") || lower.contains("depósito")) {
                tipoOperacion = "deposito";
            }
            if (lower.contains("titular cuenta destino") && titularCuentaDestino.isEmpty()) {
                String value = original.replaceAll("(?i)titular cuenta destino:?", "").trim();
                if (!value.isEmpty()) titularCuentaDestino = value;
            }
        }
        


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

        System.out.println(textoExtraido);
        // Si no se encontró ningún dato, igual devolver el formato con guiones
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuit(cuit)
                .amount(monto)
                .bank(extraerNombreBanco(titularCuentaDestino))
                .titularCuentaDestino(titularCuentaDestino)
                .build();
    }

private static String capitalizeWords(String str) {
    if (str == null || str.isEmpty()) return str;
    String[] words = str.toLowerCase().split(" ");
    StringBuilder sb = new StringBuilder();
    for (String word : words) {
        if (!word.isEmpty()) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
    }
    return sb.toString().trim();
}

// Agregar función auxiliar para extraer solo el nombre
private static String extraerNombreBanco(String str) {
    if (str == null || str.isEmpty()) return str;
    String nombre = str.split("/")[0].trim();
    return capitalizeWords(nombre);
}
}
