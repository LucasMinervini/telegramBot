package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BBVATest {

    @Test
    public void testParseBBVATransfer() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia\n" +
                "Fecha: 25/05/2024 10:30:00\n" +
                "Destinatario: BANCO GALICIA Y BUENOS AIRES S.A.U.\n" +
                "Titular: JUAN PEREZ\n" +
                "Importe: $ 1.234,56\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("bbva_comprobante.pdf");

        TransferDTO transferencia = BBVA.parseBBVATransfer(textoExtraido, doc);

        assertEquals("25/05/2024 10:30:00", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("JUAN PEREZ", transferencia.getCuentaOrigen());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals("BBVA", transferencia.getBank());
        assertEquals("Destinatario: BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getName());
    }
}