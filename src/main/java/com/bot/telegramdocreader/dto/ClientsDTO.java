package com.bot.telegramdocreader.dto;



import lombok.Builder;
import lombok.Data;



@Data
@Builder
public class ClientsDTO {

    private Long chatId;
    private String name;
    private String cuitORcuil;
    private String cbu;

    public String clientsDetails() {
        return "ChatID: " + chatId + "\n" +
               "Nombre: " + name + "\n" +
               "CBU/CVU: " + cbu + "\n" +
               "Cuit/Cuil: " + cuitORcuil + "\n";
    }
}
