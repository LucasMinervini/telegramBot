package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GaliciaTest {

    @Test
    public void testParseGaliciaTransfer() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia\n" +
                "25/02/2024\n" +
                "Transferencia enviada\n" +
                "CUIT: 20-12345678-9\n" +
                "$1234\n" +
                "Para: JUAN PEREZ\n" +
                "BANCO BBVA\n" +
                "CVU: 1234567890123456789012\n" +
                "Concepto: Varios\n" +
                "N° de operación: 123456789\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("galicia_comprobante.pdf");

        TransferDTO transferencia = Galicia.parseGaliciaTransfer(textoExtraido, doc);

        assertEquals("25/02/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("1.234", transferencia.getAmount());
        assertEquals("BANCO BBVA", transferencia.getBank());
        assertEquals("1234567890123456789012", transferencia.getAccountDestiny());
        assertEquals("JUAN PEREZ", transferencia.getTitularCuentaDestino());
        assertEquals("123456789", transferencia.getTransactionNumber());
    }
}