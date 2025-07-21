package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CuentaDniTest {

    @Test
    public void testParseCuentaDniTransfer() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia\n" +
                "25/05/2024\n" +
                "importe\n" +
                "$ 1.234,56\n" +
                "origen\n" +
                "JUAN PEREZ\n" +
                "para\n" +
                "BANCO GALICIA Y BUENOS AIRES S.A.U.\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("cuentadni_comprobante.pdf");

        TransferDTO transferencia = CuentaDni.parseCuentaDniTransfer(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("JUAN PEREZ", transferencia.getCuentaOrigen());
        assertEquals("$ 1.234,56", transferencia.getAmount());
        assertEquals("BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getBank());
    }
}