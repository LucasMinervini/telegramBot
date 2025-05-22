package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Brubank {
    public static String formatBrubank(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                transferencia.getCuit() != null ? transferencia.getCuit() : "",
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                transferencia.getBank() != null ? transferencia.getBank() : "");
    }

    public static TransferDTO parseBrubankTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String tipoOperacion = "";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "";
        String fileNameLower = doc.getFileName().toLowerCase();
        boolean isBrubank = textoExtraido.toLowerCase().contains("brubank") || fileNameLower.contains("brubank");
        if (isBrubank) {
            for (String line : lines) {
                line = line.trim();
                String lower = line.toLowerCase();
                // Fecha: buscar variantes y formatos
                if (lower.startsWith("fecha:")) {
                    fecha = line.replaceFirst("(?i)fecha:", "").trim();
                } else if (fecha.isEmpty() && lower.matches(".*\\d{2}/\\d{2}/\\d{4}.*")) {
                    fecha = line.replaceAll(".*?(\\d{2}/\\d{2}/\\d{4}).*", "$1").trim();
                } else if (fecha.isEmpty() && lower.matches(".*\\d{2}-\\d{2}-\\d{4}.*")) {
                    fecha = line.replaceAll(".*?(\\d{2}-\\d{2}-\\d{4}).*", "$1").trim();
                } else if (fecha.isEmpty() && lower.matches(".*\\d{4}/\\d{2}/\\d{2}.*")) {
                    fecha = line.replaceAll(".*?(\\d{4}/\\d{2}/\\d{2}).*", "$1").trim();
                }
                // Tipo de operación: buscar variantes
                if (lower.startsWith("tipo de operación:") || lower.startsWith("tipo de operacion:")) {
                    tipoOperacion = line.replaceFirst("(?i)tipo de operaci[oó]n:", "").trim();
                } else if (tipoOperacion.isEmpty() && (lower.contains("transferencia") || lower.contains("envío de dinero") || lower.contains("envio de dinero"))) {
                    tipoOperacion = "Transferencia";
                }
                // CUIT/CUIL: buscar variantes y sin etiqueta
                if (lower.startsWith("cuit/cuil:") || lower.startsWith("cuit:") || lower.startsWith("cuil:")) {
                    cuit = line.replaceAll("(?i)cuit/cuil:|cuit:|cuil:", "").replaceAll("[^0-9]", "").trim();
                } else if (cuit.isEmpty() && line.replaceAll("[^0-9]", "").length() == 11) {
                    cuit = line.replaceAll("[^0-9]", "").trim();
                }
                // Monto: buscar variantes y sin etiqueta
                if (lower.startsWith("monto bruto:")) {
                    monto = line.replaceFirst("(?i)monto bruto:", "").replace("$", "").replace(" ", "").trim();
                } else if (monto.isEmpty() && lower.contains("$")) {
                    monto = line.replaceAll("[^0-9.,]", "").trim();
                }
                // Banco receptor: buscar variantes y patrones
                if (lower.startsWith("banco receptor:")) {
                    bancoReceptor = line.replaceFirst("(?i)banco receptor:", "").trim();
                } else if (bancoReceptor.isEmpty() && (lower.contains("capital") || lower.contains("cocos") || lower.contains("banco") || lower.contains("capital sa"))) {
                    bancoReceptor = line.replaceAll("(?i)banco receptor:|banco|receptor|:|\u00a0", "").trim();
                } else if (bancoReceptor.isEmpty() && lower.contains("origen caja de ahorro")) {
                    bancoReceptor = "BRUBANK";
                }
            }
            if (bancoReceptor.isEmpty()) {
                bancoReceptor = "BRUBANK";
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
        return null;
    }
}