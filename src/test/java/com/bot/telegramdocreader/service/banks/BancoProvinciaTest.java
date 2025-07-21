package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BancoProvinciaTest {

    @Test
    public void testParseBancoProvinciaTransfer() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia\n" +
                "Fecha de acreditación: 25/05/2024\n" +
                "Número de transacción: 123456789\n" +
                "Titular: JUAN PEREZ / 20-12345678-9\n" +
                "Importe: $ 1.234,56\n" +
                "Tipo de Operación: Transferencia\n" +
                "Titular cuenta destino: BANCO GALICIA Y BUENOS AIRES S.A.U.\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("bancoprovincia_comprobante.pdf");

        TransferDTO transferencia = BancoProvincia.parseBancoProvinciaTransfer(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals("Banco Galicia Y Buenos Aires S.a.u.", transferencia.getBank());
    }
}