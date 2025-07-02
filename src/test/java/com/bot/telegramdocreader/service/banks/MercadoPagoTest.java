package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para el parser de Mercado Pago
 */
class MercadoPagoTest {

    @Test
    @DisplayName("Debe extraer correctamente el CUIT del emisor")
    void testExtractCuitEmisor() {
        String textoEjemplo = """
            Comprobante de transferencia
            De
            Juan Pérez
            CUIT/CUIL: 27-31602563-9
            Para
            María González
            CUIT/CUIL: 30-71883962-5
            Banco Receptor: Fargotez Sa
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoEjemplo, null);
        
        assertEquals("27-31602563-9", result.getCuit(), "Debe detectar el CUIT del emisor correctamente");
        assertNotEquals("30-71883962-5", result.getCuit(), "No debe confundir con el CUIT del receptor");
    }
    
    @Test
    @DisplayName("Debe extraer correctamente el titular receptor")
    void testExtractTitularReceptor() {
        String textoEjemplo = """
            Comprobante de transferencia
            De
            Juan Pérez
            Para
            María González
            CUIT/CUIL: 30-71883962-5
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoEjemplo, null);
        
        assertEquals("María González", result.getName(), "Debe detectar el titular receptor correctamente");
    }
    
    @Test
    @DisplayName("Debe extraer correctamente el monto")
    void testExtractMonto() {
        String textoEjemplo = """
            Comprobante de transferencia
            Monto: $ 830.000
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoEjemplo, null);
        
        assertEquals("830.000", result.getAmount(), "Debe extraer el monto correctamente");
    }
    
    @Test
    @DisplayName("Debe extraer correctamente la fecha")
    void testExtractFecha() {
        String textoEjemplo = """
            Miércoles 25 de junio de 2025
            Comprobante de transferencia
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoEjemplo, null);
        
        assertEquals("Miércoles 25 de junio de 2025", result.getDate(), "Debe extraer la fecha correctamente");
    }
    
    @Test
    @DisplayName("Debe extraer correctamente el banco receptor")
    void testExtractBancoReceptor() {
        String textoEjemplo = """
            Comprobante de transferencia
            Banco Receptor: Fargotez Sa
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoEjemplo, null);
        
        assertEquals("Fargotez Sa", result.getBank(), "Debe extraer el banco receptor correctamente");
    }
    
    @Test
    @DisplayName("Debe manejar documentos malformados sin fallar")
    void testDocumentoMalformado() {
        String textoMalformado = """
            Texto sin estructura
            Números aleatorios: 12345678901
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoMalformado, null);
        
        assertNotNull(result, "Debe retornar un objeto válido incluso con texto malformado");
        assertEquals("Transferencia", result.getTypeOFTransfer(), "Debe tener tipo de transferencia por defecto");
    }
    
    @Test
    @DisplayName("No debe confundir CVU con CUIT")
    void testNoCVUConfusion() {
        String textoConCVU = """
            De
            Juan Pérez
            CVU: 12345678901234567890
            CUIT/CUIL: 27-31602563-9
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoConCVU, null);
        
        assertEquals("27-31602563-9", result.getCuit(), "Debe ignorar CVU y detectar solo CUIT válido");
    }
    
    @Test
    @DisplayName("Debe formatear correctamente el CUIT")
    void testFormatoCUIT() {
        String textoEjemplo = """
            De
            Juan Pérez
            CUIT/CUIL: 27316025639
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoEjemplo, null);
        
        assertEquals("27-31602563-9", result.getCuit(), "Debe formatear CUIT con guiones correctamente");
    }
    
    @Test
    @DisplayName("Debe extraer correctamente información del caso real del usuario")
    void testCasoRealUsuario() {
        String textoExtraido = """
            Comprobante de transferencia 
            Miércoles, 25 de junio de 2025 a las 08:22 hs 
            $ 830.000 
            Motivo: Varios 
            De 
            Jesica Natalia Espindola 
            CUIT/CUIL: 27-31602563-9 
            Mercado Pago 
            CVU: 0000003100026999961245 
            Para 
            Fargotez Sa 
            CUIT/CUIL: 30-71883962-5 
            NEBLOCKCHAIN SA 
            CVU: 0000163805040012975175 
            Número de operación de Mercado Pago 
            116291810842 
            Código de identificación 
            JMQKYZ9QOZRMEXL82V50P3
            """;
        
        TransferDTO result = MercadoPago.parseMercadoPagoTransfer(textoExtraido, null);
        
        assertEquals("27-31602563-9", result.getCuit(), "Debe extraer el CUIT del emisor (Jesica Natalia Espindola)");
        assertEquals("Fargotez Sa", result.getBank(), "Debe extraer el banco receptor (Fargotez Sa)");
        assertEquals("830.000", result.getAmount(), "Debe extraer el monto correctamente");
        assertTrue(result.getDate().contains("25 de junio de 2025"), "Debe extraer la fecha correctamente");
    }
}