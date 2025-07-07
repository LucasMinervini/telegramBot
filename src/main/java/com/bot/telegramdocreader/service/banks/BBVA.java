package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class BBVA {
    public static String formatBBVA(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuenta de origen: %s\nMonto Bruto: $ %s\nBanco receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                transferencia.getCuentaOrigen() != null ? transferencia.getCuentaOrigen() : "",
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                transferencia.getName() != null && !transferencia.getName().isEmpty() ? transferencia.getName() : (transferencia.getTitularCuentaDestino() != null ? transferencia.getTitularCuentaDestino() : ""));
    }

    public static TransferDTO parseBBVATransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String tipoOperacion = "Transferencia";   
        String monto = "";
        String accountOrig = "";
        String titularDestino = "";
        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            // Fecha y hora
            if (fecha.isEmpty() && lower.matches(".*\\d{2}/\\d{2}/\\d{4}.*\\d{2}:\\d{2}:\\d{2}.*")) {
                fecha = line.trim();
            } else if (fecha.isEmpty() && lower.matches(".*\\d{2}/\\d{2}/\\d{4}.*")) {
                fecha = line.replaceAll(".*?(\\d{2}/\\d{2}/\\d{4}).*", "$1").trim();
            }
            // Monto
            if (monto.isEmpty() && lower.matches(".*\\$ ?[0-9.]+,[0-9]{2}.*")) {
                monto = line.replaceAll("[^0-9.,]", "").replaceFirst(",", ".");
            }
            // Cuenta de origen
            if (accountOrig.isEmpty() && lower.contains("cuenta de origen")) {
                accountOrig = line.replaceAll(".*? ", "").trim();
            }
            // Destinatario
            if (titularDestino.isEmpty() && lower.contains("destinatario")) {
                titularDestino = line.replaceAll(".*destinatario:? ?", "").trim();
                // Si el nombre tiene espacios, tomar solo el nombre (sin el prefijo)
                if (titularDestino.toLowerCase().startsWith("destinatario ")) {
                    titularDestino = titularDestino.substring(12).trim();
                }
            }
        }
        

        System.out.println("BBVA: " + textoExtraido);
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuentaOrigen(accountOrig)
                .amount(monto)
                .bank("BBVA")
                .name(titularDestino)
                .build();
    }
}

