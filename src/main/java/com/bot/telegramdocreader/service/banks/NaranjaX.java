package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class NaranjaX {
    public static String formatNaranjaX(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        String cuitFormateado = transferencia.getCuit() != null && transferencia.getCuit().length() == 11
            ? transferencia.getCuit().replaceFirst("(\\d{2})(\\d{8})(\\d{1})", "$1-$2-$3")
            : (transferencia.getCuit() != null ? transferencia.getCuit() : "");
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                cuitFormateado,
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
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
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            // Detectar inicio y fin de sección Cuenta Origen
            if (lower.contains("cuenta origen")) {
                inCuentaOrigen = true;
                continue;
            }
            if (lower.contains("cuenta destino")) {
                inCuentaOrigen = false;
            }
            // Detectar fecha en formato 24/JUN/2025-14:18 h
            if (original.matches("\\d{2}/[A-Z]{3}/\\d{4}-\\d{2}:\\d{2} h")) {
                fecha = original.split("-")[0].trim();
            }
            // Buscar monto: cualquier línea que contenga un valor monetario
            if (monto.isEmpty()) {
                String montoLinea = lines[i].replaceAll("^[^0-9]*", "").replaceAll("[^0-9.,]", "").replace(",", ".").trim();
                if (montoLinea.matches("\\d+[.,]?\\d*")) {
                    monto = montoLinea;
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
            if (lower.contains("banco virtual") && bancoReceptor.isEmpty()) {
                bancoReceptor = original.replaceAll("[()]+", "").trim();
            }
            if (bancoReceptor.isEmpty() && lower.contains("fargotez")) {
                bancoReceptor = original.replaceAll("[()]+", "").trim();
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