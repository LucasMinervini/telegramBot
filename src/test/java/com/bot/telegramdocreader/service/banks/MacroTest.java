package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MacroTest {

    @Test
    public void testParserNewMacro() {
        String textoExtraido = "\n" +
                "25/05/2024\n" +
                "20-12345678-1\n" +
                "$ 1.234,56\n" +
                "2222222222222222222222\n" +
                "BANCO GALICIA Y BUENOS AIRES S.A.U.\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("macro_comprobante.pdf");

        TransferDTO transferencia = Macro.parserNewMacro(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-1", transferencia.getCuit());
        assertEquals("1.234,56", transferencia.getAmount());
        assertEquals("BANCO GALICIA Y BUENOS AIRES S.A.U.", transferencia.getBank());
    }

    @Test
    public void testParserMacro() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia Macro\n" +
                "25/05/2024\n" +
                "CUIT/CUIL/CDI: 20-12345678-9\n" +
                "Importe: $ 1.234,56\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("macro_comprobante.pdf");

        TransferDTO transferencia = Macro.parserMacro(textoExtraido, doc);

        assertEquals("25/05/2024", transferencia.getDate());
        assertEquals("Transferencia", transferencia.getTypeOFTransfer());
        assertEquals("20-12345678-9", transferencia.getCuit());
        assertEquals("$1.234,56", transferencia.getAmount());
        assertEquals("Macro", transferencia.getBank());
    }
}