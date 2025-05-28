package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Uala {
    public static TransferDTO parseUalaTransfer(String textoExtraido, Document doc) {
        String date = "";
        String typeOfOperation = "Transferencia";
        String cuitDestiny = "";
        String amount = "";
        String accountDestiny = "";
        String nameRemitent = "";
        String bank = "UALA";
        String cbuDestiny = "";
        String[] ualaLines = textoExtraido.split("\r?\n");
        for (String line : ualaLines) {
            String lower = line.toLowerCase().trim();
            if (lower.contains("fecha y hora")) {
                date = line.replaceAll("(?i)fecha y hora", "").replace(":", "").trim();
            } else if (lower.contains("monto debitado")) {
                amount = line.replaceAll("(?i)monto debitado", "").replace(":", "").replace("$", "").trim();
            } else if (lower.matches("cuenta destino\\s*[0-9]+.*")) {
                cbuDestiny = line.replaceAll("(?i)cuenta destino", "").replace(":", "").trim();
            } else if (lower.contains("cuenta destino") && accountDestiny.isEmpty()) {
                accountDestiny = line.replaceAll("(?i)cuenta destino", "").replace(":", "").trim();
            } else if (lower.contains("cuit destino")) {
                cuitDestiny = line.replaceAll("(?i)cuit destino", "").replace(":", "").trim();
            } else if (lower.contains("nombre remitente")) {
                nameRemitent = line.replaceAll("(?i)nombre remitente", "").replace(":", "").trim();
            }else if (lower.contains("cuit remitente")) {
                cuitDestiny = line.replaceAll("(?i)cuit remitente", "").replace(":", "").trim();
            } 
        }
        return TransferDTO.builder()
            .name(nameRemitent)
            .date(date)
            .typeOFTransfer(typeOfOperation)
            .cuit(cuitDestiny)
            .amount(amount)
            .bank(bank)
            .accountDestiny(accountDestiny)
            .cbuDestiny(cbuDestiny)
            .build();
    }
    public static String formatUala(TransferDTO transferencia) {
        String formato = "Fecha: %s \n" +
                "Tipo de Operación: %s\n" +
                "Cuit/Cuil: %s\n" +
                "Monto Bruto: $ %s\n" +
                "Banco receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                transferencia.getCuit() != null ? transferencia.getCuit() : "",
                transferencia.getAmount()!= null? transferencia.getAmount() : "",
                transferencia.getBank() != null ? transferencia.getBank() : "");
    }
}