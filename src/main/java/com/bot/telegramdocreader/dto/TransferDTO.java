package com.bot.telegramdocreader.dto;




import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferDTO {
    
    private String name;
    private String amount;
    private String cuit;
    private String date;
    private String bank;
    private String typeOFTransfer;
    private String accountDestiny;    
    private String cbuDestiny;       
     
      
    
    public String receiverDetails() {
        return "Nombre: " + name + "\n" +
               "Fecha: " + date + "\n" +
               "Monto: " + amount + "\n" +
               "Cuit: " + cuit + "\n" +
                "Tipo de Operación: " + typeOFTransfer + "\n" +
               "Banco: " + bank;
    }
}
