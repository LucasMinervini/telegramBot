package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class PersonalPay {
    public static String formatPersonalPay(TransferDTO transferencia) {
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

    public static TransferDTO parsePersonalPayTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String tipoOperacion = "";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "";
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            if (lower.startsWith("fecha")) {
                // Extraer la fecha después de 'Fecha'
                String[] partes = original.split(":");
                if (partes.length > 1) {
                    fecha = partes[1].trim();
                } else {
                    fecha = original.replaceAll("(?i)fecha", "").trim();
                }
                // Buscar formato de fecha válido en la línea
                if (!fecha.matches("\\d{2}/\\d{2}/\\d{4}")) {
                    String[] subpartes = fecha.split(" ");
                    for (String parte : subpartes) {
                        if (parte.matches("\\d{2}/\\d{2}/\\d{4}")) {
                            fecha = parte;
                            break;
                        }
                    }
                }
            }
            if ((lower.startsWith("cuil/cuit") || lower.startsWith("cuit/cuil") || lower.startsWith("cuit") || lower.startsWith("cuil")) && cuit.isEmpty()) {
                cuit = original.replaceAll("(?i)cuil/cuit:|cuit/cuil:|cuit:|cuil:", "").replaceAll("[^0-9]", "").trim();
            } else if (cuit.isEmpty() && original.replaceAll("[^0-9]", "").length() == 11) {
                cuit = original.replaceAll("[^0-9]", "").trim();
            }
            if (lower.contains("$") && monto.isEmpty()) {
                // Extraer monto y formatear como 3.000,00
                String montoRaw = original.replaceAll("[^0-9.,]", "").trim();
                // Si el monto es tipo 3.00000, convertir correctamente a 3.000,00
                if (montoRaw.matches("\\d{1,3}(\\.\\d{3})*(\\.\\d{2,})") || montoRaw.matches("\\d+\\.\\d{2,}") || montoRaw.matches("\\d{1,3}(\\.\\d{3})+")) {
                    // Si tiene formato 3.00000 o 3000.00
                    String montoSinPuntos = montoRaw.replace(".", "");
                    if (montoSinPuntos.length() > 2) {
                        montoSinPuntos = montoSinPuntos.substring(0, montoSinPuntos.length() - 2) + "." + montoSinPuntos.substring(montoSinPuntos.length() - 2);
                    }
                    try {
                        double montoDouble = Double.parseDouble(montoSinPuntos);
                        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
                        symbols.setGroupingSeparator('.');
                        symbols.setDecimalSeparator(',');
                        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00", symbols);
                        monto = df.format(montoDouble);
                    } catch (Exception e) {
                        monto = montoRaw;
                    }
                } else {
                    monto = montoRaw;
                }
            }
            if (lower.startsWith("recibe")) {
                bancoReceptor = original.replaceFirst("(?i)recibe", "").trim();
                if (bancoReceptor.isEmpty() && i + 1 < lines.length) {
                    bancoReceptor = lines[i + 1].trim();
                }
            } else if (bancoReceptor.isEmpty() && i > 0 && lines[i-1].toLowerCase().contains("recibe")) {
                bancoReceptor = original;
            } else if (bancoReceptor.isEmpty() && lower.contains("fargotez")) {
                bancoReceptor = original;
            }
            if (tipoOperacion.isEmpty() && (lower.contains("transferencia") || lower.contains("enviaste dinero"))) {
                tipoOperacion = "Transferencia";
            }
        }
        if (bancoReceptor.isEmpty()) {
            for (int i = 0; i < lines.length - 1; i++) {
                if (lines[i].toLowerCase().contains("recibe")) {
                    bancoReceptor = lines[i + 1].trim();
                    break;
                }
            }
        }
        if (cuit.length() == 11) {
            cuit = cuit.substring(0,2) + "-" + cuit.substring(2,10) + "-" + cuit.substring(10);
        }
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion.isEmpty() ? "Transferencia" : tipoOperacion)
                .cuit(cuit)
                .amount(monto)
                .bank(bancoReceptor)
                .build();
    }
}