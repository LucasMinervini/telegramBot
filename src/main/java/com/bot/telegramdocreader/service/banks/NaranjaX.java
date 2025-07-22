package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class NaranjaX {
    public static String formatNaranjaX(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        String cuitFormateado = transferencia.getCuit() != null && transferencia.getCuit().length() == 11
            ? transferencia.getCuit().replaceFirst("(\\d{2})(\\d{8})(\\d{1})", "$1-$2-$3")
            : (transferencia.getCuit() != null ? transferencia.getCuit() : "");
        // Limpiar y formatear el monto
        String montoFormateado = "";
        if (transferencia.getAmount() != null && !transferencia.getAmount().isEmpty()) {
            String montoLimpio = transferencia.getAmount().replaceAll("[^0-9,.]", "");
            montoLimpio = montoLimpio.replaceAll("^[sS$]+", ""); // Elimina cualquier 's', 'S' o '$' al inicio
            try {
                java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
                symbols.setDecimalSeparator(',');
                symbols.setGroupingSeparator('.');
                java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00", symbols);
                double valor = Double.parseDouble(montoLimpio.replace(",", "."));
                montoFormateado = df.format(valor);
            } catch (Exception e) {
                montoFormateado = montoLimpio;
            }
        }
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                cuitFormateado,
                montoFormateado,
                transferencia.getBank() != null ? transferencia.getBank() : "");
    }

    public static TransferDTO parseNaranjaXTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String tipoOperacion = "Transferencia";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "";

        if (textoExtraido.startsWith("Cuenta Origen")) {
            
        }
        // Buscar CUIT/CUIL/CUL y monto en todo el comprobante
        boolean inCuentaOrigen = false;
        boolean inCuentaDestino = false;
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            // Detectar inicio y fin de sección Cuenta Origen
            if (lower.contains("cuenta origen")) {
                inCuentaOrigen = true;
                inCuentaDestino = false;
                continue;
            }
            if (lower.contains("cuenta destino")) {
                inCuentaOrigen = false;
                inCuentaDestino = true;
                continue;
            }
            if (lower.contains("información de la operación")) {
                inCuentaDestino = false;
            }
            // Detectar fecha en formato 16/JUL/2025-11:55h (más flexible con mayúsculas/minúsculas)
            if (original.matches("(?i)\\d{2}/[a-z]{3}/\\d{4}-\\d{2}:\\d{2}\\s*h")) {
                fecha = original.split("-")[0].trim();
            }
            // También buscar fecha sin la 'h' al final
            if (fecha.isEmpty() && original.matches("(?i)\\d{2}/[a-z]{3}/\\d{4}")) {
                fecha = original.trim();
            }
            // Monto
            if ((lower.contains("importe") || lower.contains("monto")) && monto.isEmpty() || lower.startsWith("s ")) {
                String value = original.replaceAll("(?i)importe|monto", "").replace(":", "").trim();
                // Limpiar prefijos como 's', '$', etc. y espacios
                value = value.replaceAll("^[sS$'’\"\u201c\u201d]+", "").trim();
                value = value.replaceAll("[\"'’“”]+$", "").trim();
                if (!value.isEmpty()) {
                    monto =  value;
                }
            }
            if (monto.isEmpty() && lower.matches(".*\\$\\s*[0-9]+[.,]?[0-9]*.*")) {
                String value = original.replaceAll("[^0-9$.,]", "").trim();
                if (!value.isEmpty()) {
                    // Keep the $ if present, otherwise add it
                    monto = value.startsWith("$") ? value : "$" + value;
                }
            }
            if (inCuentaOrigen && cuit.isEmpty()) {
                // Buscar CUIT/CUIL/CUL en Cuenta Origen
                if (lower.contains("cuit") || lower.contains("cuil") || lower.contains("cul")) {
                    String posibleCuit = original.replaceAll("[^0-9]", "");
                    if (posibleCuit.length() == 11) {
                        cuit = posibleCuit;
                    }
                }
                // También buscar formato XX-XXXXXXXX-X
                if (cuit.isEmpty() && original.matches("\\d{2}-\\d{8}-\\d{1}")) {
                    cuit = original.replaceAll("-", "");
                }
            }
            // Capturar el titular de la cuenta destino (primera línea después de "Cuenta destino")
            if (inCuentaDestino && bancoReceptor.isEmpty() && !original.isEmpty() && 
                !lower.contains("banco") && !lower.contains("cvu") && !lower.contains("cuil") && 
                !lower.contains("cuenta") && !original.matches("\\d+.*")) {
                String titular = original.trim();
                // Limpiar prefijos no deseados como "ma", "ema", "pa", etc.
                titular = titular.replaceFirst("^(ma |ema |pa |pm |pm |pa |ma |ema )", "").trim();
                bancoReceptor = titular;
            }
        }
        if (cuit.length() == 11) {
            cuit = cuit.substring(0,2) + "-" + cuit.substring(2,10) + "-" + cuit.substring(10);
        }
        System.out.println(textoExtraido);
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuit(cuit)
                .amount(monto)
                .bank(bancoReceptor)
                .build();
    }
}