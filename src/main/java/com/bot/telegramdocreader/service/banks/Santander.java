package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Santander {
    public static String formatSantander(TransferDTO transferencia) {
        String formato = "Fecha: %s\nMonto Bruto: $ %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getAmount() != null ? transferencia.getAmount() : "");
    }

    public static TransferDTO parseSantanderTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String monto = "";
        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            // Buscar fecha (ejemplo: 27/06/2025 o Fecha de ejecución: ...)
            if (fecha.isEmpty() && (lower.contains("fecha de ejecución") || lower.contains("fecha de ejecucion") || lower.matches(".*\\d{2}/\\d{2}/\\d{4}.*"))) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(line);
                if (matcher.find()) fecha = matcher.group(1);
            }
            // Buscar monto (ejemplo: $ 415.164,00)
            if (monto.isEmpty() && (lower.contains("importe debitado") || lower.contains("monto") || lower.contains("$"))) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$ ?([0-9.,]+)").matcher(line);
                if (matcher.find()) monto = matcher.group(1);
            }
        }
        if (fecha.isEmpty() || monto.isEmpty()) {
            return null;
        }
        return TransferDTO.builder()
                .date(fecha)
                .amount(monto)
                .bank("Santander")
                .build();
    }

    public static boolean isSantander(TransferDTO transferencia) {
        return transferencia.getBank() != null && transferencia.getBank().equalsIgnoreCase("Santander");
    }

    public static boolean detectSantander(String textoExtraido) {
        String[] lines = textoExtraido.split("\\r?\\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].toLowerCase();
            if (line.contains("santander")) {
                return true;
            }
        }
        return false;
    }
}