package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class NaranjaX {
    public static String formatNaranjaX(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        String cuitFormateado = transferencia.getCuit() != null && transferencia.getCuit().length() == 11
            ? transferencia.getCuit().replaceFirst("(\\d{2})(\\d{8})(\\d{1})", "$1-$2-$3")
            : (transferencia.getCuit() != null ? transferencia.getCuit() : "");
        
        // Limpiar y formatear el monto
        String montoFormateado = "";
        if (transferencia.getAmount() != null && !transferencia.getAmount().isEmpty()) {
            String montoLimpio = transferencia.getAmount().replaceAll("[^0-9,.]", "");
            montoLimpio = montoLimpio.replaceAll("^[sS$]+", ""); // Elimina cualquier 's', 'S' o '$' al inicio
            try {
                java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
                symbols.setDecimalSeparator(',');
                symbols.setGroupingSeparator('.');
                java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00", symbols);
                double valor = Double.parseDouble(montoLimpio.replace(",", "."));
                montoFormateado = df.format(valor);
            } catch (Exception e) {
                montoFormateado = montoLimpio;
            }
        }
        
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                cuitFormateado,
                montoFormateado,
                transferencia.getBank() != null ? transferencia.getBank() : "");
    }

    // Método para corregir errores comunes de OCR en fechas
    private static String corregirFechaOCR(String linea) {
        String lineaCorregida = linea.trim();
        
        // Corregir errores comunes de OCR en meses - más exhaustivo
        lineaCorregida = lineaCorregida.replaceAll("(?i)1U1", "JUL");
        lineaCorregida = lineaCorregida.replaceAll("(?i)1UL", "JUL");
        lineaCorregida = lineaCorregida.replaceAll("(?i)JU1", "JUL");
        lineaCorregida = lineaCorregida.replaceAll("(?i)1ul", "JUL");
        lineaCorregida = lineaCorregida.replaceAll("(?i)1u1", "JUL");
        lineaCorregida = lineaCorregida.replaceAll("(?i)ju1", "JUL");
        lineaCorregida = lineaCorregida.replaceAll("(?i)0CT", "OCT");
        lineaCorregida = lineaCorregida.replaceAll("(?i)D1C", "DIC");
        lineaCorregida = lineaCorregida.replaceAll("(?i)EN0", "ENE");
        lineaCorregida = lineaCorregida.replaceAll("(?i)FE8", "FEB");
        lineaCorregida = lineaCorregida.replaceAll("(?i)MA8", "MAR");
        lineaCorregida = lineaCorregida.replaceAll("(?i)A8R", "ABR");
        lineaCorregida = lineaCorregida.replaceAll("(?i)MA¥", "MAY");
        lineaCorregida = lineaCorregida.replaceAll("(?i)AG0", "AGO");
        lineaCorregida = lineaCorregida.replaceAll("(?i)5EP", "SEP");
        lineaCorregida = lineaCorregida.replaceAll("(?i)N0V", "NOV");
        
        // Detectar fecha en formato DD/MMM/YYYY-HH:MM[h] (más flexible)
        if (lineaCorregida.matches("(?i)\\d{1,2}/[a-z0-9]{3}/\\d{4}[-\\s]*\\d{1,2}:\\d{2}\\s*h?")) {
            return lineaCorregida.split("[-\\s]")[0].trim();
        }
        
        // Detectar fecha en formato DD/MMM/YYYY
        if (lineaCorregida.matches("(?i)\\d{1,2}/[a-z0-9]{3}/\\d{4}")) {
            return lineaCorregida.trim();
        }
        
        // Detectar fecha con números en lugar de letras (ej: 23/111/2025)
        if (lineaCorregida.matches("\\d{1,2}/\\d{3}/\\d{4}")) {
            String[] partes = lineaCorregida.split("/");
            if (partes.length == 3) {
                String numeroMes = partes[1];
                String mesTexto = convertirNumeroAMes(numeroMes);
                if (!mesTexto.isEmpty()) {
                    return partes[0] + "/" + mesTexto + "/" + partes[2];
                }
            }
        }
        
        // Buscar patrones de fecha más flexibles en la línea
        java.util.regex.Pattern patronFecha = java.util.regex.Pattern.compile("(?i)(\\d{1,2})/(\\w{3})/(\\d{4})");
        java.util.regex.Matcher matcher = patronFecha.matcher(lineaCorregida);
        if (matcher.find()) {
            String dia = matcher.group(1);
            String mes = matcher.group(2);
            String año = matcher.group(3);
            
            // Corregir el mes si tiene errores de OCR
            mes = corregirMesOCR(mes);
            if (!mes.isEmpty()) {
                return dia + "/" + mes + "/" + año;
            }
        }
        
        return "";
    }
    
    // Método para buscar fechas con patrones más flexibles
    private static String buscarFechaFlexible(String linea) {
        String lineaLimpia = linea.trim().toUpperCase();
        
        // Buscar cualquier patrón que parezca una fecha DD/XXX/YYYY
        java.util.regex.Pattern patronGeneral = java.util.regex.Pattern.compile("(\\d{1,2})/(\\w{2,4})/(\\d{4})");
        java.util.regex.Matcher matcher = patronGeneral.matcher(lineaLimpia);
        
        while (matcher.find()) {
            String dia = matcher.group(1);
            String mesOriginal = matcher.group(2);
            String año = matcher.group(3);
            
            // Intentar corregir el mes
            String mesCorregido = corregirMesOCR(mesOriginal);
            if (!mesCorregido.isEmpty()) {
                return dia + "/" + mesCorregido + "/" + año;
            }
            
            // Si el mes tiene 3 caracteres y contiene números, intentar más correcciones
            if (mesOriginal.length() == 3) {
                String mesIntentado = intentarCorregirMes(mesOriginal);
                if (!mesIntentado.isEmpty()) {
                    return dia + "/" + mesIntentado + "/" + año;
                }
            }
        }
        
        return "";
    }
    
    // Método para intentar corregir meses con errores más complejos
    private static String intentarCorregirMes(String mes) {
        mes = mes.toUpperCase();
        
        // Casos específicos de errores de OCR más complejos
        if (mes.contains("1") && mes.contains("U")) {
            return "JUL"; // Cualquier combinación de 1 y U probablemente sea JUL
        }
        if (mes.contains("0") && mes.contains("C")) {
            return "OCT"; // 0 y C probablemente sea OCT
        }
        if (mes.contains("D") && mes.contains("1")) {
            return "DIC"; // D y 1 probablemente sea DIC
        }
        if (mes.contains("5") && mes.contains("E")) {
            return "SEP"; // 5 y E probablemente sea SEP
        }
        if (mes.contains("N") && mes.contains("0")) {
            return "NOV"; // N y 0 probablemente sea NOV
        }
        if (mes.contains("A") && mes.contains("G")) {
            return "AGO"; // A y G probablemente sea AGO
        }
        
        return "";
    }
    
    // Método auxiliar para corregir meses con errores de OCR
    private static String corregirMesOCR(String mes) {
        mes = mes.toUpperCase();
        switch (mes) {
            case "1U1": case "1UL": case "JU1": case "1ul": case "1u1": case "ju1": return "JUL";
            case "ENE": return "ENE";
            case "FEB": case "FE8": return "FEB";
            case "MAR": case "MA8": return "MAR";
            case "ABR": case "A8R": return "ABR";
            case "MAY": case "MA¥": return "MAY";
            case "JUN": return "JUN";
            case "JUL": return "JUL";
            case "AGO": case "AG0": return "AGO";
            case "SEP": case "5EP": return "SEP";
            case "OCT": case "0CT": return "OCT";
            case "NOV": case "N0V": return "NOV";
            case "DIC": case "D1C": return "DIC";
            default: return "";
        }
    }
    
    // Método auxiliar para convertir números de mes mal reconocidos a texto
    private static String convertirNumeroAMes(String numeroMes) {
        switch (numeroMes) {
            case "111": case "1U1": case "1UL": return "JUL";
            case "001": case "0CT": return "OCT";
            case "010": case "EN0": return "ENE";
            case "020": case "FE8": return "FEB";
            case "030": case "MA8": return "MAR";
            case "040": case "A8R": return "ABR";
            case "050": case "MA¥": return "MAY";
            case "060": return "JUN";
            case "070": return "JUL";
            case "080": case "AG0": return "AGO";
            case "090": case "5EP": return "SEP";
            case "100": return "OCT";
            case "110": case "N0V": return "NOV";
            case "120": case "D1C": return "DIC";
            default: return "";
        }
    }

    public static TransferDTO parseNaranjaXTransfer(String textoExtraido, Document doc) {
        String[] lineas = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String tipoOperacion = "Transferencia";
        String cuit = "";
        String monto = "";
        String banco = "";

        // Buscar fecha en múltiples líneas con mayor robustez
        for (String linea : lineas) {
            // Intentar múltiples métodos de detección
            String fechaDetectada = corregirFechaOCR(linea);
            if (!fechaDetectada.isEmpty()) {
                fecha = fechaDetectada;
                System.out.println("DEBUG: Fecha detectada en línea: '" + linea + "' -> '" + fechaDetectada + "'");
                break;
            }
            
            // Buscar patrones adicionales más flexibles
            fechaDetectada = buscarFechaFlexible(linea);
            if (!fechaDetectada.isEmpty()) {
                fecha = fechaDetectada;
                System.out.println("DEBUG: Fecha detectada (flexible) en línea: '" + linea + "' -> '" + fechaDetectada + "'");
                break;
            }
        }
        
        if (fecha.isEmpty()) {
            System.out.println("DEBUG: No se pudo detectar fecha en ninguna línea");
        }

        // Buscar CUIT
        for (String linea : lineas) {
            if (linea.matches(".*\\b\\d{2}-\\d{8}-\\d{1}\\b.*")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{2}-\\d{8}-\\d{1})\\b");
                java.util.regex.Matcher matcher = pattern.matcher(linea);
                if (matcher.find()) {
                    cuit = matcher.group(1).replace("-", "");
                    break;
                }
            }
        }

        // Buscar monto - mejorado para detectar patrones como "s 230.100?"
        for (String linea : lineas) {
            // Patrón mejorado para detectar montos con prefijos como 's' y sufijos como '?'
            if (linea.matches(".*[sS$]?\\s*\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?[?]?.*")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([sS$]?\\s*\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?)[?]?");
                java.util.regex.Matcher matcher = pattern.matcher(linea);
                if (matcher.find()) {
                    String montoEncontrado = matcher.group(1).trim();
                    // Limpiar el monto de caracteres no deseados
                    montoEncontrado = montoEncontrado.replaceAll("^[sS$]+\\s*", ""); // Eliminar prefijos
                    if (!montoEncontrado.isEmpty()) {
                        monto = montoEncontrado;
                        System.out.println("DEBUG: Monto detectado: " + monto + " (original: " + linea + ")");
                        break;
                    }
                }
            }
        }

        // Buscar titular de la cuenta destino usando flags de sección
        boolean inCuentaDestino = false;
        for (int i = 0; i < lineas.length; i++) {
            String lower = lineas[i].toLowerCase().trim();
            String original = lineas[i].trim();
            
            // Detectar inicio y fin de sección Cuenta Destino
            if (lower.contains("cuenta destino")) {
                inCuentaDestino = true;
                continue;
            }
            if (lower.contains("información de la operación")) {
                inCuentaDestino = false;
            }
            
            // Capturar el titular de la cuenta destino (primera línea después de "Cuenta destino")
            if (inCuentaDestino && banco.isEmpty() && !original.isEmpty() && 
                !lower.contains("banco") && !lower.contains("cvu") && !lower.contains("cuil") && 
                !lower.contains("cuenta") && !original.matches("\\d+.*")) {
                String titular = original.trim();
                // Limpiar prefijos no deseados como "ma", "ema", "pa", etc.
                titular = titular.replaceFirst("^(ma |ema |pa |pm |pm |pa |ma |ema )", "").trim();
                banco = titular;
                System.out.println("DEBUG: Titular detectado: " + banco);
            }
        }

        // Si no se encontró banco, usar un valor por defecto
        if (banco.isEmpty()) {
            banco = "Banco no especificado";
        }

        TransferDTO transfer = TransferDTO.builder().build();
        transfer.setDate(fecha);
        transfer.setTypeOFTransfer(tipoOperacion);
        transfer.setCuit(cuit);
        transfer.setAmount(monto);
        transfer.setBank(banco);

        return transfer;
    }
}