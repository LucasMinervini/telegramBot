package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Santander {
    public static String formatSantander(TransferDTO transferencia) {
        String formato = "Fecha: %s\nMonto Bruto: $ %s\nTipo De Operación: %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "Transferencia",
                transferencia.getTitular() != null ? transferencia.getTitular() : "");
    }

    public static TransferDTO parseSantanderTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String monto = "";
        String titular = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String lower = line.toLowerCase().trim();
            
            // Buscar fecha (ejemplo: 27/06/2025 o Fecha de ejecución: ...)
            if (fecha.isEmpty() && (lower.contains("fecha de ejecución") || lower.contains("fecha de ejecucion") || lower.matches(".*\\d{2}/\\d{2}/\\d{4}.*"))) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(line);
                if (matcher.find()) fecha = matcher.group(1);
            }
            // Buscar monto (ejemplo: $ 158.826,00)
            if (monto.isEmpty() && (lower.contains("importe debitado") || lower.contains("monto") || line.contains("$"))) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9]{1,3}(?:\\.[0-9]{3})*(?:,[0-9]{2})?)").matcher(line);
                if (matcher.find()) monto = matcher.group(1);
            }
            // Buscar titular - formato 1: "Titular cuenta destino"
            if (titular.isEmpty() && lower.contains("titular cuenta destino")) {
                String[] parts = line.split("(?i)titular cuenta destino");
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    titular = parts[1].trim();
                } else {
                    // Buscar en las siguientes líneas no vacías
                    for (int j = i + 1; j < Math.min(i + 4, lines.length); j++) {
                        String nextLine = lines[j].trim();
                        if (!nextLine.isEmpty() && !nextLine.toLowerCase().contains("cuenta") && 
                            !nextLine.toLowerCase().contains("concepto") && !nextLine.toLowerCase().contains("cta")) {
                            titular = nextLine;
                            break;
                        }
                    }
                }
            }
            
            // Buscar titular - formato 4: líneas que contengan "fundra"
            if (titular.isEmpty() && lower.contains("fundra")) {
                titular = "Fundraisercle";
            }
            
            // Buscar titular - formato 2: "? Destinatario"
            if (titular.isEmpty() && lower.contains("? destinatario")) {
                String[] parts = line.split("(?i)\\? destinatario");
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    titular = parts[1].trim();
                }
            }
            
            // Buscar titular - formato 3: después de "Comprobante de transferencia"
            if (titular.isEmpty() && lower.contains("comprobante de transferencia")) {
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 2].trim();
                    if (nextLine.toLowerCase().startsWith("fundra")) {
                        titular = nextLine;
                    }
                }
            }
        }
        // Si no se encuentra fecha, usar fecha actual para Santander
        if (fecha.isEmpty()) {
            java.time.LocalDate today = java.time.LocalDate.now();
            fecha = String.format("%02d/%02d/%d", today.getDayOfMonth(), today.getMonthValue(), today.getYear());
        }
        
        if (monto.isEmpty()) {
            return null;
        }
        System.out.println(textoExtraido);
        return TransferDTO.builder()
                .date(fecha)
                .amount(monto)
                .bank(titular)
                .titular(titular)
                .build();
    }

    public static boolean detectSantander(String textoExtraido) {
        String[] lines = textoExtraido.split("\\r?\\n");
        for (int i = 0; i < Math.min(10, lines.length); i++) {
            String line = lines[i].toLowerCase().trim();
            // Remover caracteres especiales y espacios extra para una mejor detección
            String cleanLine = line.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
            if (line.contains("santander") || cleanLine.contains("santander")) {
                return true;
            }
        }
        return false;
    }
}