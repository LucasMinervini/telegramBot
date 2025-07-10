package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class CuentaDni {
    public static String formatCuentaDni(TransferDTO transferDTO) {
        String format = "Fecha: %s\n" +
                "Tipo de Operación: %s\n" +
                "Titular Origen: %s\n" +
                "Monto: %s\n" +
                "Banco Receptor: %s";
        return String.format(format,
                transferDTO.getDate() != null ? transferDTO.getDate() : "",
                transferDTO.getTypeOFTransfer() != null ? transferDTO.getTypeOFTransfer() : "",
                (transferDTO.getCuentaOrigen() != null && !transferDTO.getCuentaOrigen().isEmpty() ? transferDTO.getCuentaOrigen() : "-"),
                (transferDTO.getAmount() != null && !transferDTO.getAmount().isEmpty() ? transferDTO.getAmount().replaceAll("^\\$+", "\\$") : "-"),
                (transferDTO.getBank() != null && !transferDTO.getBank().isEmpty() ? transferDTO.getBank() : "-")
        );
    }

    public static TransferDTO parseCuentaDniTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\r?\n");
        String fecha = "";
        String tipoOperacion = "Transferencia";
        String titularOrigen = "";
        String monto = "";
        String bancoReceptor = "";
        // boolean paraSection = false;
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            // Fecha: buscar línea con formato dd/mm/yyyy
            if (fecha.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
                if (matcher.find()) {
                    fecha = matcher.group(1);
                }
            }
            // Tipo de operación: buscar "Comprobante de transferencia"
            if (tipoOperacion.isEmpty() && lower.contains("comprobante de transferencia")) {
                tipoOperacion = "Transferencia";
            }
            // Monto: buscar línea que contenga "$" y no esté vacía
            if (monto.isEmpty() && lower.contains("$")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$ ?[0-9.]+,[0-9]{2}").matcher(original);
                if (matcher.find()) {
                    monto = matcher.group();
                }
            }
            // Titular Origen: buscar después de "Origen" (salta líneas vacías y números)
            if (lower.equals("origen")) {
                int j = i + 1;
                while (j < lines.length && (lines[j].trim().isEmpty() || lines[j].trim().matches("\\d+[.\\d]*"))) j++;
                if (j < lines.length) {
                    titularOrigen = lines[j].trim();
                }
            }
            // Banco Receptor: buscar después de "Para" (salta líneas vacías y alias/cuil)
            if (lower.equals("para")) {
                int j = i + 1;
                while (j < lines.length && (lines[j].trim().isEmpty() || lines[j].toLowerCase().startsWith("alias:") || lines[j].toLowerCase().startsWith("cuil:"))) j++;
                if (j < lines.length) {
                    bancoReceptor = lines[j].trim();
                }
            }
        }
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuentaOrigen(titularOrigen)
                .amount(monto)
                .bank(bancoReceptor)
                .build();
    }
}
