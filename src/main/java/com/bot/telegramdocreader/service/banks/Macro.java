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

public static TransferDTO parserMacro(String textoExtraido, Document doc) {
    String[] lines = textoExtraido.split("\r?\n|\r");
    String fecha = "";
    String tipoOperacion = "Transferencia";
    String cuit = "";
    String monto = "";
    String bancoReceptor = "";
    for (int i = 0; i < lines.length; i++) {
        String lower = lines[i].toLowerCase().trim();
        String original = lines[i].trim();
        // Fecha: buscar línea con formato dd/mm/yyyy o dd-mm-yyyy, o línea con hora y número de operación
        if (fecha.isEmpty()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
            if (matcher.find()) {
                fecha = matcher.group(1);
            } else if (i > 0 && lines[i-1].toLowerCase().contains("macro")) {
                matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
                if (matcher.find()) fecha = matcher.group(1);
            }
        }
        
        // CUIT/CUIL/CDI: buscar línea que contenga "CUIT/CUIL/CDI" y extraer el valor
        if (cuit.isEmpty() && lower.contains("cuit/cuil/cdi")) {
            String value = original.replaceAll("(?i)CUIT/CUIL/CDI:?", "").replaceAll("[^0-9-]", "").trim();
            if (!value.isEmpty()) cuit = value;
        }
        // Monto: buscar línea que contenga "Importe" o "Monto Bruto" y extraer el valor
        if (monto.isEmpty() && (lower.contains("importe") || lower.contains("monto bruto"))) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\$\\s]*)([0-9.]+)[,.]([0-9]{2})").matcher(original);
            if (matcher.find()) {
                String entero = matcher.group(2).replace(".", "");
                String decimal = matcher.group(3);
                // Formatear con separador de miles y decimales
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
        // Banco receptor: buscar línea que contenga "Banco:" y extraer el valor
        if (bancoReceptor.isEmpty() && lower.contains("banco:")) {
            String value = original.replaceAll("(?i)Banco:", "").trim();
            if (!value.isEmpty()) bancoReceptor = value;
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

