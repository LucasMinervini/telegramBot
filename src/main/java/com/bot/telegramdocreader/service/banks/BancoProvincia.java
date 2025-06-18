package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class BancoProvincia {
    public static String formatBancoProvincia(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        String fecha = transferencia.getDate() != null && !transferencia.getDate().isEmpty() ? transferencia.getDate() : "-";
        String tipo = transferencia.getTypeOFTransfer() != null && !transferencia.getTypeOFTransfer().isEmpty() ? transferencia.getTypeOFTransfer() : "-";
        String cuit = transferencia.getCuit() != null && !transferencia.getCuit().isEmpty() ? transferencia.getCuit() : "-";
        String monto = transferencia.getAmount() != null && !transferencia.getAmount().isEmpty() ? transferencia.getAmount() : "-";
        String banco = transferencia.getBank() != null && !transferencia.getBank().isEmpty() ? transferencia.getBank() : "-";
        return String.format(formato, fecha, tipo, cuit, monto, banco);
    }

    public static TransferDTO parseBancoProvinciaTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\r?\n");
        String fecha = "";
        String tipoOperacion = "";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "Banco Provincia";
        String fileNameLower = doc != null && doc.getFileName() != null ? doc.getFileName().toLowerCase() : "";
        boolean isProvincia = textoExtraido.toLowerCase().contains("banco provincia") || textoExtraido.toLowerCase().contains("provincia") || fileNameLower.contains("provincia");
        if (!isProvincia) return null;
        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            String original = line.trim();
            // Fecha de acreditación o fecha
            if ((lower.contains("fecha de acreditación") || lower.contains("fecha de acreditacion")) && fecha.isEmpty()) {
                String value = original.replaceAll("(?i)fecha de acreditaci[oó]n", "").replace(":", "").trim();
                if (!value.isEmpty()) fecha = value;
            } else if (lower.matches("\\d{2}/\\d{2}/\\d{4}.*") && fecha.isEmpty()) {
                fecha = original.split(" ")[0];
            }
            // Tipo de operación
            if ((lower.contains("nueva transferencia") || lower.contains("transferencia")) && tipoOperacion.isEmpty()) {
                tipoOperacion = "Transferencia";
            }
            // Cuit/Cuil del titular cuenta destino
            if (lower.contains("titular cuenta destino") && cuit.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\s*(\\d{11})").matcher(original);
                if (matcher.find()) cuit = matcher.group(1);
            }
            // Monto
            if ((lower.contains("importe") || lower.contains("monto")) && monto.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\s*([0-9.]+,[0-9]{2})").matcher(original);
                if (matcher.find()) monto = matcher.group(1).replace(".", "").replace(",", ".");
            }
        }
        if (cuit.length() == 11) {
            cuit = cuit.substring(0,2) + "-" + cuit.substring(2,10) + "-" + cuit.substring(10);
        }
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(!tipoOperacion.isEmpty() ? tipoOperacion : "Transferencia")
                .cuit(cuit)
                .amount(monto)
                .bank(bancoReceptor)
                .build();
    }
}