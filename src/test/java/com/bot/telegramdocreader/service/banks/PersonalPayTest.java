package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PersonalPayTest {

    @Test
    public void testParsePersonalPayTransfer() {
        String textoExtraido = "\n" +
                "Fecha: 25/05/2024\n" +
                "Transferencia\n" +
                "CUIT: 20-12345678-9\n" +
                "$ 1.234,56\n" +
                "Recibe: BANCO GALICIA Y BUENOS AIRES S.A.U.\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("personalpay_comprobante.pdf");

        TransferDTO transferencia = PersonalPay.parsePersonalPayTransfer(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals(": BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getBank());
    }
}