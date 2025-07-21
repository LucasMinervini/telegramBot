package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NaranjaXTest {

    @Test
    public void testParseNaranjaXTransfer() {
        String textoExtraido = "\n" +
                "16/JUL/2025-11:55 h\n" +
                "Cuenta Origen\n" +
                "CUIT: 20-12345678-9\n" +
                "Cuenta Destino\n" +
                "BANCO GALICIA Y BUENOS AIRES S.A.U.\n" +
                "$ 1.234,56\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("naranjax_comprobante.pdf");

        TransferDTO transferencia = NaranjaX.parseNaranjaXTransfer(textoExtraido, doc);

        assertEquals("16/JUL/2025", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("1234.56", transferencia.getAmount());
    }
}