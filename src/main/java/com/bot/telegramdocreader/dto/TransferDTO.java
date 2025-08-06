package com.bot.telegramdocreader.dto;




import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferDTO {
    
    private String name;
    private String cuentaOrigen;
    private String amount;
    private String cuit;
    private String cuitOriginal; // Campo para almacenar el CUIT original con letras
    private String date;
    private String bank;
    private String typeOFTransfer;
    private String accountDestiny;    
    private String cbuDestiny;    
    
    private boolean isBBVA;
    private boolean isUALA;
    private boolean isCuentaDni;


    
    // Nuevos campos para Banco Provincia
    private String transactionNumber;
    private String accountToDebit;
    private String titular;
    private String titularCuentaDestino;
    private String referencia;
    private String motivo;
      
    
    public String receiverDetails() {
        return "Nombre: " + name + "\n" +
               "Fecha: " + date + "\n" +
               "Monto: " + amount + "\n" +
               "Cuit: " + cuit + "\n" +
                "Tipo de Operación: " + typeOFTransfer + "\n" +
               "Banco: " + bank;
    }

    public boolean isBBVA(){return isBBVA;  }public void setBBVA(boolean isBBVA){this.isBBVA = isBBVA;}
    public boolean isCuentaDni() { return isCuentaDni; }
    public void setCuentaDni(boolean isCuentaDni) { this.isCuentaDni = isCuentaDni; }
    public boolean isUala() { return isUALA; }
    public void setUala(boolean isUala) { this.isUALA = isUala; }
}
