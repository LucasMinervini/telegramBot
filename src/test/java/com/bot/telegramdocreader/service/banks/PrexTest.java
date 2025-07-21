package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrexTest {

    @Test
    public void testParsePrexTransfer() {
        String textoExtraido = "\n" +
                "prex\n" +
                "Enviaste: $ 1.234,56\n" +
                "25 de mayo de 2024 hs\n" +
                "Enviaste a: BANCO GALICIA Y BUENOS AIRES S.A.U.\n" +
                "CUIT Emisor: 20-12345678-9\n" +
                "CVU Destino: 1234567890123456789012\n" +
                "Cuenta Destino: 123456789\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("prex_comprobante.pdf");

        TransferDTO transferencia = Prex.parsePrexTransfer(textoExtraido, doc);

        assertEquals("BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getName());
        assertEquals("25 de mayo de 2024 hs", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals("PREX", transferencia.getBank());
        assertEquals("123456789", transferencia.getAccountDestiny());
        
    }
}