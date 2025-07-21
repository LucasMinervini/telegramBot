package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UalaTest {

    @Test
    public void testParseUalaTransfer() {
        String textoExtraido = "\n" +
                "Fecha y Hora: 25/05/2024\n" +
                "Transferiste\n" +
                "Monto debitado: $ 1.234,56\n" +
                "Cuenta Destino: BANCO GALICIA Y BUENOS AIRES S.A.U.\n" +
                "CUIT Destino: 20-12345678-9\n" +
                "Nombre Remitente: JUAN PEREZ\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("uala_comprobante.pdf");

        TransferDTO transferencia = Uala.parseUalaTransfer(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("JUANPEREZ", transferencia.getCuit());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals("UALA", transferencia.getBank());
        assertEquals("BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getAccountDestiny());
        assertEquals("JUANPEREZ", transferencia.getName());
    }
}