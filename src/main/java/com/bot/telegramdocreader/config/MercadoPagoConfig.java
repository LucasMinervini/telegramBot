package com.bot.telegramdocreader.config;

/**
 * Configuración para el parser de Mercado Pago
 * Centraliza todas las constantes y patrones utilizados en el parsing
 */
public class MercadoPagoConfig {
    
    // Configuración de búsqueda
    public static final int SEARCH_RANGE_LINES = 5;
    public static final int MAX_RETRIES = 3;
    
    // Palabras clave para identificar operaciones
    public static final String[] OPERATION_KEYWORDS = {
        "transferencia", 
        "enviaste", 
        "envío", 
        "comprobante de transferencia"
    };
    
    // Palabras que excluyen un número de ser considerado CUIT
    public static final String[] EXCLUDED_KEYWORDS = {
        "cvu", 
        "operación", 
        "codigo", 
        "número",
        "neblockchain",
        "identificacion"
    };
    
    // Días de la semana para detección de fechas
    public static final String[] DATE_DAYS = {
        "lunes", 
        "martes", 
        "miércoles", 
        "miercoles", 
        "jueves", 
        "viernes", 
        "sábado", 
        "sabado", 
        "domingo"
    };
    
    // Patrones regex
    public static final String DATE_PATTERN = "\\b(\\d{2}[./-]\\d{2}[./-]\\d{4})\\b";
    public static final String AMOUNT_PATTERN = ".*\\$\\s*[0-9]+[.,]?[0-9]*.*";
    public static final String BANK_PATTERN = ".*(banco|sa|srl|s\\.a\\.|s\\.r\\.l\\.).*";
    public static final String DAY_DATE_PATTERN = "^(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)[,\\s].*\\d{4}.*";
    
    // Secciones del documento
    public static final String SECTION_FROM = "de";
    public static final String SECTION_TO = "para";
    public static final String SECTION_BANK = "banco receptor";
    
    // Longitud esperada del CUIT
    public static final int CUIT_LENGTH = 11;
    
    // Mensajes de log
    public static final String LOG_SECTION_FOUND = "Sección '{}' encontrada en línea: {}";
    public static final String LOG_CUIT_DETECTED = "CUIT EMISOR detectado: {} en línea {}: {}";
    public static final String LOG_CUIT_DISCARDED = "CUIT descartado (contiene palabra excluida): {}";
    public static final String LOG_PARSING_ERROR = "Error parsing MercadoPago document";
}