package com.bot.telegramdocreader.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferDTO {
    
    private String name;
    private String cuit;
    private String amount;
    private String bank;



    public String receiverDetails() {
        return "Nombre: " + name + "\n" +
                "CUIT: " + cuit + "\n" +
                "Monto: " + amount + "\n" +
                "Banco: " + bank;
    }
}
