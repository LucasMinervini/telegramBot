package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class CuentaDni {
    public static String formatCuentaDni(TransferDTO transferDTO) {
        return String.format(
                "Fecha: %s\n" +
                        "Tipo de Operación: %s\n" +
                        "Titular Origen: %s\n" +
                        "Monto: %s\n" +
                        "Banco Receptor: %s",
                transferDTO.getDate() != null ? transferDTO.getDate() : "-",
                transferDTO.getTypeOFTransfer() != null ? transferDTO.getTypeOFTransfer() : "-",
                transferDTO.getCuentaOrigen() != null && !transferDTO.getCuentaOrigen().isEmpty() ? transferDTO.getCuentaOrigen() : "-",
                transferDTO.getAmount() != null && !transferDTO.getAmount().isEmpty() ? transferDTO.getAmount() : "-",
                transferDTO.getBank() != null && !transferDTO.getBank().isEmpty() ? transferDTO.getBank() : "-"
        );
    }

    public static TransferDTO parseCuentaDniTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\r?\n");
        String fecha = "";
        String tipoOperacion = "Transferencia";
        String titularOrigen = "";
        String monto = "";
        String bancoReceptor = "";

        String section = "";

        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            String original = line.trim();

            if (lower.contains("comprobante de transferencia")) {
                tipoOperacion = "Transferencia";
            } else if (lower.equals("importe")) {
                section = "monto";
            } else if (lower.equals("origen")) {
                section = "origen";
            } else if (lower.equals("para")) {
                section = "para";
            } else if (original.matches("\\d{2}/\\d{2}/\\d{4}.*")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
                if (matcher.find()) {
                    fecha = matcher.group(1);
                }
            } else if (!original.isEmpty()) {
                switch (section) {
                    case "monto":
                        if (monto.isEmpty() && original.matches(".*\\d+.*")) {
                            monto = original.replaceAll("[^\\d.,]", "");
                            if (!monto.isEmpty()) {
                                monto = "$ " + monto;
                            }
                        }
                        break;
                    case "origen":
                        if (titularOrigen.isEmpty() && !original.matches("\\d+[.\\d]*")) {
                            titularOrigen = original;
                        }
                        break;
                    case "para":
                        if (bancoReceptor.isEmpty() && !lower.startsWith("alias:") && !lower.startsWith("cuil:")) {
                            bancoReceptor = original;
                        }
                        break;
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
