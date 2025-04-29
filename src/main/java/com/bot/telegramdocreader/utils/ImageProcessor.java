package com.bot.telegramdocreader.utils;

import net.sourceforge.tess4j.*;
import java.io.File;

public class ImageProcessor {

    private static final String DEFAULT_LANGUAGE = "spa";
    private static Tesseract tesseract;

    // Librería Tesseract-OCR
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
            tesseract.setPageSegMode(1);
            tesseract.setOcrEngineMode(1);
        }
        return tesseract;
    }

    public static String extractTextFromImage(File imageFile) throws TesseractException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file is null or does not exist");
        }
        
        try {
            return getInstance().doOCR(imageFile);
        } catch (TesseractException e) {
            throw new TesseractException("Error processing image: " + e.getMessage(), e);
        }
    }
}