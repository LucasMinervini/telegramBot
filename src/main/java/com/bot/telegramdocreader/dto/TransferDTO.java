package com.bot.telegramdocreader.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferDTO {
    
    private String name;
    private String cuit;
    private String accountNumber;
    private String bank;



    public String receiverDetails() {
        return "Nombre: " + name + "\n" +
                "CUIT: " + cuit + "\n" +
                "Número de cuenta: " + accountNumber + "\n" +
                "Banco: " + bank;
    }
}
