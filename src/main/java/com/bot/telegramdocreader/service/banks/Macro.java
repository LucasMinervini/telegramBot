package com.bot.telegramdocreader.service.banks;

import org.telegram.telegrambots.meta.api.objects.Document;

import com.bot.telegramdocreader.dto.TransferDTO;

public class Macro {

    public static String formatMacro(TransferDTO transferDTO) {

        String format = "Fecha: %s\n" +
        "Tipo de Operación: %s\n" +
        "Cuit/Cuil: %s\n" +
        "Monto Bruto: $ %s\n" +
        "Banco Receptor: %s";

        return String.format(format, 
        transferDTO.getDate(),
        transferDTO.getTypeOFTransfer(),
        transferDTO.getCuit(),
        transferDTO.getAmount(),
        transferDTO.getBank());
}

public static TransferDTO parserNewMacro(String textoExtraido, Document doc) {
    String[] lines = textoExtraido.split("\r?\n|\r");
    String fecha = "";
    String tipoOperacion = "Transferencia";
    String cuit = "";
    String monto = "";
    String bancoReceptor = "";

    java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
    java.util.regex.Pattern cuitPattern = java.util.regex.Pattern.compile("(\\d{2}-\\d{8}-\\d{1})");
    java.util.regex.Pattern amountPattern = java.util.regex.Pattern.compile("^\\$ ([\\d.,]+)$");
    java.util.regex.Pattern cbuPattern = java.util.regex.Pattern.compile("^\\d{22}$");

    for (int i = 0; i < lines.length; i++) {
        String trimmedLine = lines[i].trim();

        if (fecha.isEmpty()) {
            java.util.regex.Matcher matcher = datePattern.matcher(trimmedLine);
            if (matcher.find()) {
                fecha = matcher.group(1);
            }
        }

        if (cuit.isEmpty()) {
            java.util.regex.Matcher matcher = cuitPattern.matcher(trimmedLine);
            if (matcher.find()) {
                cuit = matcher.group(1);
            }
        }

        if (monto.isEmpty()) {
            java.util.regex.Matcher matcher = amountPattern.matcher(trimmedLine);
            if (matcher.find()) {
                monto = matcher.group(1);
            }
        }

        if (bancoReceptor.isEmpty()) {
            java.util.regex.Matcher matcher = cbuPattern.matcher(trimmedLine);
            if (matcher.find() && i + 1 < lines.length) {
                // The next non-empty line should be the recipient's name
                for (int j = i + 1; j < lines.length; j++) {
                    if (!lines[j].trim().isEmpty()) {
                        bancoReceptor = lines[j].trim();
                        break;
                    }
                }
            }
        }
    }

    return TransferDTO.builder()
        .date(fecha)
        .typeOFTransfer(tipoOperacion)
        .cuit(cuit)
        .amount(monto)
        .bank(bancoReceptor.isEmpty() ? "Macro" : bancoReceptor) // Fallback to Macro
        .build();
}

public static TransferDTO parserMacro(String textoExtraido, Document doc) {
    String[] lines = textoExtraido.split("\r?\n|\r");
    String fecha = "";
    String tipoOperacion = "Transferencia";
    String cuit = "";
    String monto = "";
    String bancoReceptor = "Macro";

    
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            if (fecha.isEmpty()) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
                if (matcher.find()) {
                    fecha = matcher.group(1);
                } else if (i > 0 && lines[i-1].toLowerCase().contains("macro")) {
                    matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
                    if (matcher.find()) fecha = matcher.group(1);
                }
            }
            if (cuit.isEmpty() && lower.contains("cuit/cuil/cdi")) {
                String value = original.replaceAll("(?i)CUIT/CUIL/CDI:?", "").replaceAll("[^0-9-]", "").trim();
                if (!value.isEmpty()) cuit = value;
            }
            if (monto.isEmpty() && (lower.contains("importe") || lower.contains("monto bruto"))) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\$\\s]*)([0-9.]+)[,.]([0-9]{2})").matcher(original);
                if (matcher.find()) {
                    String entero = matcher.group(2).replace(".", "");
                    String decimal = matcher.group(3);
                    StringBuilder sb = new StringBuilder();
                    int len = entero.length();
                    int count = 0;
                    for (int j = len - 1; j >= 0; j--) {
                        sb.insert(0, entero.charAt(j));
                        count++;
                        if (count == 3 && j != 0) {
                            sb.insert(0, ".");
                            count = 0;
                        }
                    }
                    monto = "$" + sb.toString() + "," + decimal;
                }
            }
        }
return TransferDTO.builder()
        .date(fecha)
        .typeOFTransfer(tipoOperacion)
        .cuit(cuit)
        .amount(monto)
        .bank(bancoReceptor)
        .build();
}


}

