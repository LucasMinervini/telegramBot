package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Uala {
    public static String formatUala(TransferDTO transferencia) {
        String titularCuenta = (transferencia.getName() != null && !transferencia.getName().isEmpty()) ? transferencia.getName() : "no detectado";
        String bancoReceptor = "";
        if (transferencia.getAccountDestiny() != null && !transferencia.getAccountDestiny().isEmpty()) {
            bancoReceptor = transferencia.getAccountDestiny();
        } else {
            bancoReceptor = "No detectado";
        }
        String formato = "Fecha: %s\n" +
                "Tipo de Operación: %s\n" +
                "Titular Cuenta: %s\n" +
                "Monto Bruto: $ %s\n" +
                "Banco Receptor: %s";
        return String.format(formato,
                transferencia.getDate(),
                transferencia.getTypeOFTransfer(),
                titularCuenta,
                transferencia.getAmount(),
                bancoReceptor);
    }

    public static TransferDTO parseUalaTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n|\\r");
        String fecha = "";
        String tipoOperacion = "";
        String cuit = "";
        String monto = "";
        String nameDestiny = "";
        String cuentaDestinoNombre = "";
        String cuentaDestinoNumero = "";
        
        java.util.List<String> cuentasDestino = new java.util.ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            java.util.function.Predicate<String> isField = l -> {
                String ll = l.toLowerCase().trim();
                return ll.equals("fecha y hora") || ll.equals("fecha") || ll.equals("monto debitado") || ll.equals("monto") || ll.equals("cuenta destino") || ll.equals("cuit destino") || ll.equals("nombre remitente") || ll.equals("cuit remitente") || ll.equals("concepto") || ll.equals("id op.");
            };

            // Fecha y hora (más flexible)
            if ((lower.contains("fecha y hora") || lower.startsWith("fecha")) && fecha.isEmpty()) {
                String value = original.replaceAll("(?i)fecha y hora", "").replaceAll("(?i)fecha", "").replace(":", "").trim();
                if (value.isEmpty() || isField.test(value)) {
                    if (i + 1 < lines.length && !isField.test(lines[i + 1].trim())) value = lines[i + 1].trim();
                }
                if (!value.isEmpty() && !isField.test(value)) fecha = value;
            }



            // Fallback: buscar fecha en cualquier línea si sigue vacía
            if (fecha.isEmpty()) {
                // Busca fechas tipo 28/05/2025, 28-05-2025, 28.05.2025
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{2}[./-]\\d{2}[./-]\\d{4})\\b").matcher(original);
                if (matcher.find()) {
                    fecha = matcher.group(1);
                }
                // Busca fechas tipo '22 de mayo 2025'
                if (fecha.isEmpty()) {
                    java.util.regex.Matcher matcherTxt = java.util.regex.Pattern.compile("(\\d{1,2})\\s+de\\s+([a-záéíóúñ]+)\\s+\\d{4}", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(original);
                    if (matcherTxt.find()) {
                        String day = matcherTxt.group(1);
                        String mesTxt = matcherTxt.group(2).toLowerCase();
                        String year = original.replaceAll(".*(\\d{4}).*", "$1");
                        String[] month = {"enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre"};
                        int mesNum = 0;
                        for (int m = 0; m < month.length; m++) if (month[m].equals(mesTxt)) mesNum = m+1;
                        if (mesNum > 0) {
                            String mesStr = (mesNum < 10 ? "0" : "") + mesNum;
                            String diaStr = (day.length() == 1 ? "0"+day : day);
                            fecha = diaStr + "/" + mesStr + "/" + year;
                        }
                    }
                }
            }
            
            // Monto debitado (más flexible)
            if ((lower.contains("monto debitado") || lower.startsWith("monto")) && monto.isEmpty()) {
                String value = original.replaceAll("(?i)monto debitado", "").replaceAll("(?i)monto", "").replace(":", "").replace("$", "").replace(",", ",").trim();
                if (value.isEmpty() || isField.test(value)) {
                    if (i + 1 < lines.length && !isField.test(lines[i + 1].replace("$", "").trim())) value = lines[i + 1].replace("$", "").replace(",", ".").trim();
                }
                if (!value.isEmpty() && !isField.test(value)) monto = value;
            }

            // Tipo de operación
            if ((lower.contains("transferencia") || lower.contains("transferiste") || lower.contains("transferido")) && tipoOperacion.isEmpty()) {
                tipoOperacion = "Transferencia";
            } else if ((lower.contains("débito") || lower.contains("debito")) && tipoOperacion.isEmpty()) {
                tipoOperacion = "Débito";
            }

            // cuenta destino nombre y número al campo banco receptor
            if (lower.contains("cuenta destino")) {
                String value = original.replaceAll("(?i)cuenta destino", "").replace(":", "").trim();
                value = value.replaceAll("^\\s+|\\s+$", ""); // quita espacios al inicio y fin
                if (!value.isEmpty()) {
                    cuentasDestino.add(value);
                }
            }
            // CUIT destino
            if (lower.contains("cuit destino") && cuit.isEmpty()) {
                String value = original.replaceAll("(?i)cuit destino", "").replace(":", "").trim();
                if (!value.isEmpty() && !isField.test(value)) cuit = value;
            }
            // Fallback: buscar monto en cualquier línea si sigue vacío
            if (monto.isEmpty() && lower.matches(".*\\$\\s*[0-9]+[.,]?[0-9]*.*")) {
                String value = lower.replaceAll("[^0-9.,]", "").replace(",", ".");
                if (!value.isEmpty()) monto = value;
            }
        }

        // Al final, tomar el primer valor no numérico como nombre y el primer numérico como número
        cuentaDestinoNombre = "";
        cuentaDestinoNumero = "";
        nameDestiny = "";

        
        for (String val : cuentasDestino) {
            String valTrim = val.trim();
            if (cuentaDestinoNombre.isEmpty() && !valTrim.matches("\\d+")) {
                cuentaDestinoNombre = valTrim;
            }
            if (cuentaDestinoNumero.isEmpty() && valTrim.matches("\\d+")) {
                cuentaDestinoNumero = valTrim;
            }
        }
       
        
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            if (lower.contains("nombre remitente")) {
                String value = original.replaceAll("(?i)nombre remitente", "").replace(":", "").trim();
                if (!value.isEmpty()) {
                    nameDestiny = value.replaceAll("\\s+", "");
                    break;
                }
            }
        }

        if (tipoOperacion.isEmpty()) tipoOperacion = "Transferencia";
        
        TransferDTO transferencia = TransferDTO.builder()
            .date(fecha)
            .typeOFTransfer(tipoOperacion.isEmpty() ? "Transferencia" : tipoOperacion)
            .cuit(nameDestiny)
            .amount(monto)
            .bank("UALA")
            .accountDestiny(cuentaDestinoNombre)
            .name(nameDestiny)
            .build();
        return transferencia;
    }
}