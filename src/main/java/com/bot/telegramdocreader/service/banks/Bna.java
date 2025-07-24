package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Bna {
    public static String formatBna(TransferDTO transferDTO) {

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

public static TransferDTO parserBna(String textoExtraido, Document doc) {
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
            } else if (i > 0 && lines[i-1].toLowerCase().contains("bna")) {
                matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(original);
                if (matcher.find()) fecha = matcher.group(1);
            }
        }
        // Buscar línea que solo contenga "Fecha" y extraer el valor de la siguiente línea
        if (fecha.isEmpty() && lower.equals("fecha")) {
            if (i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}/\\d{2}/\\d{4})").matcher(nextLine);
                if (matcher.find()) {
                    fecha = matcher.group(1);
                }
            }
        }
        // Tipo de operación: siempre "Transferencia"
        // CUIT/CUIL/CDI: buscar línea que contenga "CUIT/CUIL/CDI" y extraer el valor
        if (cuit.isEmpty() && lower.contains("cuit/cuil/cdi")) {
            String value = original.replaceAll("(?i)CUIT/CUIL/CDI:?", "").replaceAll("[^0-9-]", "").trim();
            if (!value.isEmpty()) cuit = value;
        }
        // Monto: buscar línea que contenga "Importe" o "Monto Bruto" y extraer el valor
        if (monto.isEmpty() && (lower.contains("importe") || lower.contains("monto"))) {
            // Primero intentamos con el patrón original
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
            } else {
                // Si no encuentra con el patrón anterior, buscar en las líneas siguientes
                for (int j = i + 1; j < Math.min(i + 5, lines.length); j++) {
                    String nextLine = lines[j].trim();
                    if (!nextLine.isEmpty()) {
                        matcher = java.util.regex.Pattern.compile("([\\$\\s]*)([0-9.]+)[,.]([0-9]{2})").matcher(nextLine);
                        if (matcher.find()) {
                            String entero = matcher.group(2).replace(".", "");
                            String decimal = matcher.group(3);
                            // Formatear con separador de miles y decimales
                            StringBuilder sb = new StringBuilder();
                            int len = entero.length();
                            int count = 0;
                            for (int k = len - 1; k >= 0; k--) {
                                sb.insert(0, entero.charAt(k));
                                count++;
                                if (count == 3 && k != 0) {
                                    sb.insert(0, ".");
                                    count = 0;
                                }
                            }
                            monto = "$" + sb.toString() + "," + decimal;
                            break;
                        }
                    }
                }
            }
        }
        // Banco receptor: buscar línea que contenga "Destinatario" y extraer el valor de la siguiente línea
        if ( lower.contains("destinatario")) {
            if (i + 1 < lines.length) {
                // Extraer solo el nombre del banco sin incluir CUIT ni monto
                bancoReceptor = lines[i + 1].trim();
            }
        }
        
        // Limpiar el banco receptor si contiene información adicional como CUIT o monto
        if (bancoReceptor != null && !bancoReceptor.isEmpty()) {
            // Eliminar cualquier texto después de "cuit" o "monto"
            if (bancoReceptor.toLowerCase().contains("cuit")) {
                bancoReceptor = bancoReceptor.replaceAll("(?i)\\s+cuit.*", "").trim();
            }
            if (bancoReceptor.toLowerCase().contains("monto")) {
                bancoReceptor = bancoReceptor.replaceAll("(?i)\\s+monto.*", "").trim();
            }
        }
        System.out.println(textoExtraido);
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
