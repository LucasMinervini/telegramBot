package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MercadoPagoTest {

    @Test
    public void testParseMercadoPagoTransfer() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia\n" +
                "lunes, 25 de mayo de 2024\n" +
                "Enviaste\n" +
                "$ 1.234,56\n" +
                "De: CUIT/CUIL 20-12345678-9\n" +
                "Para\n" +
                "BANCO GALICIA Y BUENOS AIRES S.A.U.\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("mercadopago_comprobante.pdf");

        TransferDTO transferencia = MercadoPago.parseMercadoPagoTransfer(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals("BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getName());
        assertEquals("BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getBank());
    }
}