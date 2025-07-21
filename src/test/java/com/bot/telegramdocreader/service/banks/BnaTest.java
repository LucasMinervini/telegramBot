package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BnaTest {

    @Test
    public void testParseBnaTransfer() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia\n" +
                "Fecha: 25/05/2024\n" +
                "CUIT/CUIL/CDI: 20-12345678-9\n" +
                "Importe: $ 1.234,56\n" +
                "Banco: BANCO GALICIA Y BUENOS AIRES S.A.U.\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("bna_comprobante.pdf");

        TransferDTO transferencia = Bna.parserBna(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("$1.234,56", transferencia.getAmount());
        assertEquals("BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getBank());
    }
}