/*package com.bot.telegramdocreader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import com.bot.telegramdocreader.bot.TelegramDocBot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentProcessingServiceTest {

    private DocumentProcessingService service;
    private TelegramDocBot mockBot;

    @BeforeEach
    void setUp() {
        // Creamos un mock de TelegramDocBot para la inyección
        mockBot = mock(TelegramDocBot.class);
        service = new DocumentProcessingService(mockBot);  // Inyectamos el mockBot en el constructor
    }

    @Test
    void testProcessDocument_returnsExpectedText() throws Exception {
        // Mock del objeto Document
        Document mockDoc = mock(Document.class);
        when(mockDoc.getFileId()).thenReturn("12345"); 
        when(mockDoc.getFileName()).thenReturn("sample.pdf");

        // Mock del objeto File
        File mockFile = mock(File.class);
        when(mockFile.getFilePath()).thenReturn("path/to/sample.pdf");

        when(mockBot.execute(any(GetFile.class))).thenReturn(mockFile);

        // Ejecutar el método
        String result = service.processDocument(mockDoc, "defaultArgument"); 

        // Verificar resultado
        assertNotNull(result);
        assertTrue(result.contains("Texto simulado extraído del documento"), "El resultado no contiene el texto esperado");
        System.out.println("Resultado: " + result);
    }

    @Test
    void testProcessDocument_nullDocument_returnsErrorMessage() throws Exception {
        // Validamos que se lance la excepción correctamente si el documento es null
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            service.processDocument(null, "defaultArgument");  // Debes pasar también el token
        });

        // Comprobamos que el mensaje de la excepción sea el esperado
        String expectedMessage = "El documento o el fileId no pueden ser nulos";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage));  
        System.out.println("⚠️ Resultado con documento null: " + actualMessage);
    }

    @Test
    void testProcessDocument_documentWithNullFileName_returnsErrorMessage() throws Exception {
        Document mockDoc = mock(Document.class);
        when(mockDoc.getFileId()).thenReturn("12345");  
        when(mockDoc.getFileName()).thenReturn(null);  

        // Ejecutar el método
        String result = service.processDocument(mockDoc, "defaultArgument");  
        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("error"));
        System.out.println("⚠️ Resultado con fileName null: " + result);
    }

    @Test
    void testProcessDocument_documentWithEmptyFileName_returnsErrorMessage() throws Exception {
        Document mockDoc = mock(Document.class);
        when(mockDoc.getFileId()).thenReturn("12345");  
        when(mockDoc.getFileName()).thenReturn("");  

        // Ejecutar el método
        String result = service.processDocument(mockDoc, "defaultArgument");  // Asegúrate de pasar el token

        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("false"));
        System.out.println("⚠️ Resultado con fileName vacío: " + result);
    }

    @Test
    void testProcessDocument_documentWithNullFileId_returnsErrorMessage() throws Exception {
        Document mockDoc = mock(Document.class);
        when(mockDoc.getFileId()).thenReturn(null);  // Simulamos un fileId nulo
        when(mockDoc.getFileName()).thenReturn("sample.pdf");

        // Ejecutar el método
        String result = service.processDocument(mockDoc, "defaultArgument");  // Asegúrate de pasar el token

        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("error"));
        System.out.println("⚠️ Resultado con fileId null: " + result);
    }
}

*/