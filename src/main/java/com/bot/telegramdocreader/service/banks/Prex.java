package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.apache.commons.lang3.StringUtils;

public class Prex {
    public static String formatPrex(TransferDTO transferencia) {
        String formato = "Fecha: %s \n" +
                "Tipo de Operación: %s\n" +
                "Monto Bruto: $ %s\n" +
                "CBU/CVU Destino: %s\n" +
                "Cuenta Destino: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                transferencia.getCbuDestiny() != null ? transferencia.getCbuDestiny() : "",
                transferencia.getAccountDestiny() != null ? transferencia.getAccountDestiny() : "");
    }

    public static TransferDTO parsePrexTransfer(String textoExtraido, Document doc) {
        textoExtraido = textoExtraido.replaceAll("[^\\p{Print}\\s]", "").trim();
        String[] lines = textoExtraido.split("\\r?\\n");
        String destinatario = "";
        String fecha = "";
        String cuitSender = "";
        String monto = "";
        String bankReceiver = "PREX";
        String tipoOperacion = "Transferencia";
        String cbuDestino = "";
        String cuentaDestino = "";
        boolean cuitEmisorEncontrado = false;
        boolean isPrex = false;
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String lineaNormalizada = lines[i].replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]", "").toLowerCase();
            if (lineaNormalizada.contains("prex") || containsApproxWord(lineaNormalizada, "prex", 1)) {
                isPrex = true;
                bankReceiver = "PREX";
                break;
            }
        }
        if (!isPrex) {
            String fileName = doc.getFileName().toLowerCase();
            if (fileName.contains("prex")) {
                isPrex = true;
                bankReceiver = "PREX";
            }
        }
        if(textoExtraido.matches("(?i).*\\bprex\\b.*")) {
            isPrex = true;
            bankReceiver = "PREX";
        }
        if (isPrex) {
            tipoOperacion = "Transferencia";
            for (String line : lines) {
                String lineaLower = line.toLowerCase().trim();
                String lineaOriginal = line.trim();
                if (lineaLower.contains("enviaste:") || lineaLower.contains("enviaste $") || (lineaLower.contains("$") && monto.isEmpty())) {
                    String montoTemp = lineaOriginal.replaceAll("[^0-9.,]", "").trim();
                    if (!montoTemp.isEmpty()) {
                        monto = montoTemp;
                    }
                }
                if ((lineaLower.contains("de") && lineaLower.contains("hs") && fecha.isEmpty()) || (lineaLower.matches(".*\\d+.*de.*202\\d.*") && fecha.isEmpty())) {
                    fecha = lineaOriginal;
                }
                if (lineaLower.contains("enviaste a:") || lineaLower.contains("destinatario:")) {
                    destinatario = lineaOriginal.replaceAll("(?i)Enviaste a:|Destinatario:", "").trim();
                    if (cuentaDestino.isEmpty()) {
                        cuentaDestino = destinatario;
                    }
                }
                if (!cuitEmisorEncontrado && (lineaLower.contains("cuit emisor") || lineaLower.contains("cuil emisor") || lineaLower.contains("cuit del emisor") || lineaLower.contains("cuil del emisor") || lineaLower.contains("cuit/cuil emisor") || lineaLower.contains("cuit/cuil del emisor"))) {
                    String posibleCuit = lineaOriginal.replaceAll("[^0-9]", "");
                    if (posibleCuit.length() == 11) {
                        cuitSender = posibleCuit.substring(0,2) + "-" + posibleCuit.substring(2,10) + "-" + posibleCuit.substring(10);
                        cuitEmisorEncontrado = true;
                    }
                } else if (!cuitEmisorEncontrado && (lineaLower.matches(".*cu[il]t.*:.*") || lineaLower.contains("cuit:") || lineaLower.contains("cuil:"))) {
                    String posibleCuit = lineaOriginal.replaceAll("[^0-9]", "");
                    if (posibleCuit.length() == 11) {
                        cuitSender = posibleCuit.substring(0,2) + "-" + posibleCuit.substring(2,10) + "-" + posibleCuit.substring(10);
                        cuitEmisorEncontrado = true;
                    }
                }
                if (lineaLower.startsWith("cvu/cbu")) {
                    cbuDestino = lineaOriginal.substring(lineaOriginal.indexOf(" ") + 1).trim();
                } else if (lineaLower.matches(".*c[bv]u.*:.*") || lineaLower.contains("destino:")) {
                    cbuDestino = lineaOriginal.replaceAll("(?i)CVU/CBU:|CVU destino:|CBU destino:|Destino:", "").trim();
                }
                if (lineaLower.contains("cuenta") && lineaLower.contains("destino")) {
                    cuentaDestino = lineaOriginal.replaceAll("(?i)Cuenta destino:?|Cuenta:", "").trim();
                }
            }
            if (!monto.isEmpty() && (!destinatario.isEmpty() || !cuitSender.isEmpty())) {
                return TransferDTO.builder()
                    .name(destinatario)
                    .date(fecha)
                    .typeOFTransfer(tipoOperacion)
                    .cuit(cuitSender)
                    .amount(monto)
                    .bank(bankReceiver)
                    .cbuDestiny(cbuDestino)
                    .accountDestiny(cuentaDestino)
                    .build();
            }
        }
        return null;
    }
    // Copia de containsApproxWord para uso interno
    private static boolean containsApproxWord(String line, String targetWord, int tolerance) {
        String cleanedLine = line.toLowerCase().replaceAll("[^a-z]", "");
        targetWord = targetWord.toLowerCase();
        int distance = StringUtils.getLevenshteinDistance(cleanedLine, targetWord);
        return distance <= tolerance;
    }
}