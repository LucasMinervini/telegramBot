package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BrubankTest {

    @Test
    public void testParseBrubankTransfer() {
        String textoExtraido = "\n" +
                "Comprobante de Transferencia\n" +
                "Fecha: 25/05/2024\n" +
                "Tipo de Operación: Transferencia\n" +
                "Cuit/Cuil: 20-37432884-1\n" +
                "Monto Bruto: $ 19.000,00\n" +
                "Banco Receptor: no detectado";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("brubank.pdf");

        TransferDTO result = Brubank.parseBrubankTransfer(textoExtraido, doc);

        assertNotNull(result);
        assertEquals("25/05/2024", result.getDate());
        assertEquals("Transferencia", result.getTypeOFTransfer());
        assertEquals("20374328841", result.getCuit());
        assertEquals("19.000,00", result.getAmount());
    }
    
    @Test
    public void testParseBrubankTransferWithOCRErrors() {
        // Simular errores comunes de OCR
        String textoExtraido = "\n" +
                "Envío de dinero a\n" +
                "Augusto Luis Serra\n" +
                "$ 19.000,00\n" +
                "Detalle\n" +
                "Número de transacción MEC\n" +
                "Banco destino E MercadoPago\n" +
                "CBU/ Alias 0000003100051582863062\n" +
                "cur 20-37432884-1\n" +
                "Origen Caja de ahorro en pesos\n" +
                "04/05/2025"; // Fecha sin etiqueta

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("brubank2.pdf");

        TransferDTO result = Brubank.parseBrubankTransfer(textoExtraido, doc);

        assertNotNull(result);
        assertEquals("04/05/2025", result.getDate());
        assertEquals("Envío de dinero", result.getTypeOFTransfer());
        assertEquals("20374328841", result.getCuit());
        assertEquals("19.000,00", result.getAmount());
        assertEquals("Augusto Luis Serra", result.getBank());
    }
    
    @Test
    public void testParseBrubankTransferWithVariousFormats() {
        // Probar con diferentes formatos de fecha y errores de OCR
        String textoExtraido = "\n" +
                "Transferncia\n" + // Error de OCR en "Transferencia"
                "fcha: 22 de julio de 2025\n" + // Error de OCR en "fecha"
                "Envio de dinero a\n" + // Sin tilde
                "Juan Pérez\n" +
                "s 15.500,50\n" + // OCR confunde $ con s
                "cuit: 27123456789\n"; // CUIT sin guiones

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("brubank3.pdf");

        TransferDTO result = Brubank.parseBrubankTransfer(textoExtraido, doc);

        assertNotNull(result);
        assertEquals("22 de julio de 2025", result.getDate());
        assertEquals("Envío de dinero", result.getTypeOFTransfer());
        assertEquals("27123456789", result.getCuit());
        assertEquals("15.500,50", result.getAmount());
        assertEquals("Juan Perez", result.getBank());
    }
    
    @Test
    public void testParseBrubankTransferWithMinimalInfo() {
        // Probar con información mínima
        String textoExtraido = "\n" +
                "brubank\n" +
                "15/03/2025\n" +
                "$ 5.000\n" +
                "María González\n";

        Document doc = mock(Document.class);
        when(doc.getFileName()).thenReturn("brubank4.pdf");

        TransferDTO result = Brubank.parseBrubankTransfer(textoExtraido, doc);

        assertNotNull(result);
        assertEquals("15/03/2025", result.getDate());
        assertEquals("Transferencia", result.getTypeOFTransfer()); // Valor por defecto
        assertEquals("5.000,00", result.getAmount());
    }
}