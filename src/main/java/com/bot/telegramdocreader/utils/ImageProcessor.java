package com.bot.telegramdocreader.utils;

import net.sourceforge.tess4j.*;
import java.io.File;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Color;
import javax.imageio.ImageIO;
import java.io.IOException;

public class ImageProcessor {

    private static final String DEFAULT_LANGUAGE = "spa";
    private static Tesseract tesseract;

    // Librería Tesseract-OCR con configuración mejorada
    private static Tesseract getInstance() {
        if (tesseract == null) {
            tesseract = new Tesseract();
            String tessDataPath = "C:\\Program Files\\Tesseract-OCR\\tessdata";
            File tessDataDir = new File(tessDataPath);
            
            if (!tessDataDir.exists()) {
                throw new RuntimeException("Tesseract data directory not found at: " + tessDataPath);
            }
            
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage(DEFAULT_LANGUAGE);
            
            // Configuraciones optimizadas para documentos financieros
            tesseract.setPageSegMode(6); // Uniform block of text
            tesseract.setOcrEngineMode(3); // Default, based on what is available
            
            // Variables de configuración para mejorar la precisión
            tesseract.setTessVariable("tessedit_char_whitelist", 
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789áéíóúñÁÉÍÓÚÑ.,:-/$() ");
            tesseract.setTessVariable("preserve_interword_spaces", "1");
            tesseract.setTessVariable("user_defined_dpi", "300");
            tesseract.setTessVariable("textord_min_linesize", "2.5");
        }
        return tesseract;
    }

    public static String extractTextFromImage(File imageFile) throws TesseractException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file is null or does not exist");
        }
        
        try {
            // Preprocesar la imagen para mejorar la calidad del OCR
            BufferedImage originalImage = ImageIO.read(imageFile);
            BufferedImage processedImage = preprocessImage(originalImage);
            
            // Crear archivo temporal con la imagen procesada
            File tempFile = File.createTempFile("processed_", ".png");
            ImageIO.write(processedImage, "png", tempFile);
            
            try {
                String result = getInstance().doOCR(tempFile);
                return postprocessText(result);
            } finally {
                tempFile.delete();
            }
        } catch (IOException e) {
            throw new TesseractException("Error processing image: " + e.getMessage(), e);
        } catch (TesseractException e) {
            throw new TesseractException("Error processing image: " + e.getMessage(), e);
        }
    }
    
    /**
     * Preprocesa la imagen para mejorar la calidad del OCR
     */
    private static BufferedImage preprocessImage(BufferedImage original) {
        // Escalar la imagen si es muy pequeña
        int width = original.getWidth();
        int height = original.getHeight();
        
        // Si la imagen es muy pequeña, escalarla
        if (width < 1000 || height < 1000) {
            double scale = Math.max(1000.0 / width, 1000.0 / height);
            width = (int) (width * scale);
            height = (int) (height * scale);
        }
        
        BufferedImage processed = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = processed.createGraphics();
        
        // Configurar renderizado de alta calidad
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Dibujar la imagen escalada
        g2d.drawImage(original, 0, 0, width, height, null);
        g2d.dispose();
        
        // Aplicar mejoras de contraste
        return enhanceContrast(processed);
    }
    
    /**
     * Mejora el contraste de la imagen
     */
    private static BufferedImage enhanceContrast(BufferedImage image) {
        BufferedImage enhanced = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF; // Obtener componente rojo (en escala de grises son iguales)
                
                // Aplicar umbralización adaptativa simple
                if (gray < 128) {
                    enhanced.setRGB(x, y, Color.BLACK.getRGB());
                } else {
                    enhanced.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }
        
        return enhanced;
    }
    
    /**
     * Postprocesa el texto extraído para corregir errores comunes de OCR
     */
    private static String postprocessText(String text) {
        if (text == null) return "";
        
        return text
            // Corregir errores comunes de OCR
            .replace("0", "O").replace("1", "I").replace("5", "S")
            .replace("8", "B").replace("6", "G")
            // Corregir espacios múltiples
            .replaceAll("\\s+", " ")
            // Corregir caracteres especiales mal reconocidos
            .replace("|", "I").replace("!", "I").replace("¡", "I")
            .replace("@", "a").replace("#", "H")
            .trim();
    }
}