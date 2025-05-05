package com.bot.telegramdocreader.dto;

import java.io.IOException;

import com.bot.telegramdocreader.service.ExportExcel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SenderDTO {
    private String name;
    private String cuit;
    private String accountNumber;
    private String bank;

    public String senderDetails() throws IOException {
        String details = "Nombre: " + name + "\n" +
                "CUIT: " + cuit + "\n" +
                "Monto: " + accountNumber + "\n" +
                "Banco: " + bank;

        // Exportar a Excel
        String excelResult = ExportExcel.exportSenderToExcel(this);
        return details + "\n\n" + excelResult;
    }

}
