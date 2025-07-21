package com.bot.telegramdocreader.service.banks;


import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BancorTest {

@Test
public void testParseBancorTransfer(){
    String textoExtraido = "Transferiste\n" +
            "$ 100,00\n" +
            "Fecha\n" +
            "25 de junio de 2025 - 09:02 hs\n" +
            "Para\n" +
            "JUAN PEREZ\n" +
            "CUIT/CUIL 20123456789\n" +
            "Banco\n" +
            "BANCO DE LA NACION ARGENTINA";

    Document doc = mock(Document.class);
    when(doc.getFileName()).thenReturn("bancor_comprobante.pdf");

    TransferDTO trasferencia = Bancor.parseBancorTransfer(textoExtraido,doc);


    
    assertEquals("100,00", trasferencia.getAmount());
    assertEquals("25 de junio de 2025", trasferencia.getDate());
    assertEquals("Transferencia", trasferencia.getTypeOFTransfer());
    assertEquals("20-12345678-9", trasferencia.getCuit());
}

}
