package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Galicia {
    public static TransferDTO parseGaliciaTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\r?\n|\r");
        String fecha = "";
        String tipoOperacion = "";
        String cuitEmisor = "";
        String cuitReceptor = "";
        String monto = "";
        String bancoReceptor = "";
        String cuentaReceptora = "";
        String concepto = "";
        String nroOperacion = "";
        String titularReceptor = "";

        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            if (lower.matches("\\d{2}/\\d{2}/\\d{4}.*") || lower.matches("\\d{2}-\\d{2}-\\d{4}.*")) {
                fecha = original.split("[ .]")[0];
            }
            if (lower.contains("transferencia enviada") || lower.contains("transferencia realizada")) {
                tipoOperacion = "Transferencia";
            }
            if (lower.contains("cuit") && cuitEmisor.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("cuit[ :]*([0-9]{2}-[0-9]{8}-[0-9])", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(original);
                if (matcher.find()) {
                    cuitEmisor = matcher.group(1);
                } else {
                    matcher = java.util.regex.Pattern.compile("([0-9]{2}-[0-9]{8}-[0-9])").matcher(original);
                    if (matcher.find()) cuitEmisor = matcher.group(1);
                }
            }
            if (lower.contains("$") && monto.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$([0-9.]+)").matcher(original);
                if (matcher.find()) {
                    String montoRaw = matcher.group(1).replace(".", "");
                    // Formatear con puntos cada tres dígitos desde la derecha
                    StringBuilder sb = new StringBuilder(montoRaw);
                    int insertPos = sb.length() - 3;
                    while (insertPos > 0) {
                        sb.insert(insertPos, ".");
                        insertPos -= 3;
                    }
                    monto = sb.toString();
                }
            }
            if (lower.startsWith("para:")) {
                titularReceptor = original.replaceFirst("(?i)para:", "").trim();
                // Si la siguiente línea existe y no es vacía, la usamos como banco receptor
                if (i+1 < lines.length && !lines[i+1].trim().isEmpty()) {
                    bancoReceptor = lines[i+1].trim();
                } else {
                    bancoReceptor = titularReceptor;
                }
            }
            
            // Si la línea contiene un nombre en mayúsculas y cuit destino, lo tomamos como titular receptor
            if (titularReceptor.isEmpty() && i+1 < lines.length && lines[i].matches("^[A-ZÁÉÍÓÚÜÑa-záéíóúüñ ]+$") && lines[i+1].toLowerCase().contains("cuit")) {
                titularReceptor = original.trim();
            }
            if (lower.contains("cvu") && cuentaReceptora.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("cvu[ :]*([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(original);
                if (matcher.find()) cuentaReceptora = matcher.group(1);
            }
            if ((lower.contains("cuenta en") || lower.matches(".*\\ben\\b.*")) && bancoReceptor.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("en ([A-Za-zÁÉÍÓÚáéíóúüÜñÑ ]+)").matcher(original);
                if (matcher.find()) {
                    bancoReceptor = matcher.group(1).trim();
                } else {
                    bancoReceptor = original.replaceFirst("(?i).*en ", "").trim();
                }
            }
            if (lower.contains("concepto")) {
                concepto = original.replaceFirst("(?i)concepto", "").trim();
            }
            if (lower.contains("n° de operación") || lower.contains("n de operacion") || lower.contains("número de operación")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[0-9]{9,}").matcher(original);
                if (matcher.find()) nroOperacion = matcher.group();
            }
            System.out.println(textoExtraido);
        }
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuit(cuitEmisor)
                .amount(monto)
                .bank(bancoReceptor != null && !bancoReceptor.isEmpty() ? bancoReceptor : titularReceptor)
                .accountDestiny(cuentaReceptora)
                .titularCuentaDestino(titularReceptor)
                .motivo(concepto)
                .transactionNumber(nroOperacion)
                .build();
    }

    public static String formatGalicia(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "-",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "-",
                transferencia.getCuit() != null ? transferencia.getCuit() : "-",
                transferencia.getAmount() != null ? transferencia.getAmount() : "-",
                transferencia.getBank() != null ? transferencia.getBank() : "-");
    }
}