package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SantanderTest {

    @Test
    public void testParseSantanderTransfer() {
        String textoExtraido = "\n" +
                "Fecha de ejecución: 25/05/2024\n" +
                "Importe debitado: $ 1.234,56\n" +
                "Titular cuenta destino: BANCO GALICIA Y BUENOS AIRES S.A.U.\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("santander_comprobante.pdf");

        TransferDTO transferencia = Santander.parseSantanderTransfer(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals("Santander", transferencia.getBank());
        assertEquals(": BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getTitular());
    }
}