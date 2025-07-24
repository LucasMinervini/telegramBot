package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Bancor {
    
    private static boolean isValidMoneyFormat(String line) {
        // Un monto válido debe:
        // 1. Contener el símbolo $
        // 2. Tener formato monetario típico
        // 3. No ser una línea de cuenta (CA $, CVU, etc.)
        if (!line.contains("$")) return false;
        
        // Descartar líneas que claramente son cuentas
        String lowerLine = line.toLowerCase();
        if (lowerLine.contains("ca $") || lowerLine.contains("cvu") || 
            lowerLine.contains("cuenta") || lowerLine.contains("banco")) {
            
            return false;
        }
        
        // REGLA ESPECÍFICA: Rechazar líneas que empiecen con "CA $" seguido de muchos números
        if (line.trim().toUpperCase().startsWith("CA $")) {
            
            return false;
        }
        
        // Extraer la parte después del símbolo $
        String afterDollar = line.substring(line.indexOf("$") + 1).trim();
        
        
        // REGLA CRÍTICA: Si hay más de 6 dígitos consecutivos sin separadores, es una cuenta
        String soloNumeros = afterDollar.replaceAll("[^0-9]", "");
        if (soloNumeros.length() > 6) {
            return false;
        }
        
        // REGLA ADICIONAL: Si contiene más de 10 dígitos en total, es una cuenta
        if (soloNumeros.length() > 10) {
            return false;
        }
        
        // Verificar patrones típicos de dinero:
        // 1. Con puntos y comas: $800.000,00
        if (afterDollar.matches("\\d{1,3}(\\.\\d{3})*,\\d{2}")) {
            
            return true;
        }
        
        // 2. Solo con puntos: $800.000
        if (afterDollar.matches("\\d{1,3}(\\.\\d{3})+")) {
           
            return true;
        }
        
        // 3. Solo con comas: $800,00
        if (afterDollar.matches("\\d+,\\d{2}")) {
            
            return true;
        }
        
        // 4. Solo números sin separadores (máximo 4 dígitos para evitar cuentas)
        if (afterDollar.matches("\\d{1,4}")) {
            
            return true;
        }
        
        // 5. Números con espacios como separadores (formato alternativo)
        if (afterDollar.matches("\\d{1,3}(\\s\\d{3})*")) {
            
            return true;
        }
        
        
        return false;
    }
    public static String formatBancor(TransferDTO transferencia) {
        String cuit = (transferencia.getCuit() != null && !transferencia.getCuit().isEmpty()) ? transferencia.getCuit() : "no hay cuit emisor";
        String bancoReceptor = (transferencia.getBank() != null && !transferencia.getBank().isEmpty()) ? transferencia.getBank() : "no detectado";
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                cuit,
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                bancoReceptor);
    }

    public static TransferDTO parseBancorTransfer(String textoExtraido, Document doc) {
        
        
        String[] lines = textoExtraido.split("\\r?\\n|\\r");
        String fecha = "";
        String tipoOperacion = "";
        String cuitEmisor = "";
        String cuitReceptor = "";
        String monto = "";
        String bancoReceptor = "";
        String titular = "";
        
        
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            
            // Fecha - buscar patrón "25 de junio de 2025" o "dd/mm/yyyy"
            if (fecha.isEmpty()) {
                // Patrón: "25 de junio de 2025 - 09:02 hs"
                java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("(\\d{1,2}\\s+de\\s+\\w+\\s+de\\s+\\d{4})");
                java.util.regex.Matcher dateMatcher = datePattern.matcher(original);
                if (dateMatcher.find()) {
                    fecha = dateMatcher.group(1);
                    
                }
                // Patrón alternativo: "dd/mm/yyyy" o "dd-mm-yyyy"
                else if (original.matches(".*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}).*")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})");
                    java.util.regex.Matcher matcher = pattern.matcher(original);
                    if (matcher.find()) {
                        fecha = matcher.group(1);
                    }
                }
            }
            
            // Tipo de operación - buscar varios patrones
            if (tipoOperacion.isEmpty()) {
                if (lower.contains("transferencia enviada") || lower.contains("transferiste") || 
                    lower.contains("transferencia") || lower.contains("envío") || lower.contains("envio")) {
                    tipoOperacion = "Transferencia";
                    
                }
            }
            
            // Monto - buscar específicamente después de "Transferiste"
            if (lower.contains("transferiste") && monto.isEmpty()) {
                
                // Buscar en las siguientes 5 líneas el monto (ampliado el rango)
                for (int j = i + 1; j < Math.min(i + 6, lines.length); j++) {
                    String nextLine = lines[j].trim();
                    
                    if (nextLine.contains("$")) {
                        
                        // Verificar que sea un monto válido (debe tener formato monetario)
                        if (isValidMoneyFormat(nextLine)) {
                            monto = nextLine.trim();
                            
                            break;
                        }
                    }
                }
            }
            
            // Verificar si hay otras líneas con $ que puedan estar interfiriendo
            if (original.contains("$") && !lower.contains("transferiste")) {
                
                // Solo procesar si no tenemos monto aún y no es una línea de cuenta
                if (monto.isEmpty() && !lower.contains("ca $") && !lower.contains("cvu") && !lower.contains("cuenta")) {
                    // Verificar que sea un monto válido (debe tener formato monetario)
                    if (isValidMoneyFormat(original)) {
                        monto = original;
                    }
                }
            }
            
            // CUIT Emisor - buscar en sección "Datos origen"
            if (cuitEmisor.isEmpty() && lower.contains("datos origen")) {
                
                // Buscar "CUIT/CUIL" después de "Datos origen"
                for (int j = i; j < Math.min(i + 10, lines.length); j++) {
                    String origenLine = lines[j].toLowerCase().trim();
                    if (origenLine.contains("cuit/cuil")) {
                        
                        // Buscar en la siguiente línea el CUIT
                        if (j + 1 < lines.length) {
                            String cuitLine = lines[j + 1].trim();
                            
                            // Evitar líneas que contengan "CA $", "CVU", "Banco"
                            if (!cuitLine.toLowerCase().contains("ca $") && 
                                !cuitLine.toLowerCase().contains("cvu") && 
                                !cuitLine.toLowerCase().contains("banco") && 
                                !cuitLine.toLowerCase().contains("cuenta")) {
                                String numbersOnly = cuitLine.replaceAll("[^0-9]", "");
                                
                                if (numbersOnly.length() == 11 && numbersOnly.matches("\\d{11}")) {
                                    cuitEmisor = numbersOnly.substring(0,2) + "-" + numbersOnly.substring(2,10) + "-" + numbersOnly.substring(10);
                                    
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            
            // CUIT Emisor - búsqueda alternativa (mantener la lógica original como respaldo)
            if (cuitEmisor.isEmpty()) {
                String numbersOnly = original.replaceAll("[^0-9]", "");
                
                
                if (numbersOnly.length() == 11 && numbersOnly.matches("\\d{11}") && 
                    !lower.contains("cvu") && !lower.contains("banco") && !lower.contains("cuenta") &&
                    !lower.contains("ca $")) {
                    
                    
                    
                    // Estrategia simplificada: el primer CUIT válido que encontremos antes de "datos destino" es el emisor
                    boolean esEmisor = true;
                    
                    // Solo verificar si estamos después de "datos destino"
                    for (int j = 0; j < i; j++) {
                        if (lines[j].toLowerCase().contains("datos destino")) {
                            esEmisor = false;
                            
                            break;
                        }
                    }
                    
                    // También verificar si hay "datos destino" muy cerca hacia arriba (misma sección)
                    for (int j = Math.max(0, i-5); j < i; j++) {
                        if (lines[j].toLowerCase().contains("datos destino")) {
                            esEmisor = false;
                            
                            break;
                        }
                    }
                    
                    if (esEmisor) {
                        cuitEmisor = numbersOnly.substring(0,2) + "-" + numbersOnly.substring(2,10) + "-" + numbersOnly.substring(10);
                        
                    } 
                }
            }
            
            // CUIT Receptor - buscar en sección "Datos destino" bajo "CUIT/CUIL"
            if (cuitReceptor.isEmpty() && lower.contains("datos destino")) {
                
                // Buscar "CUIT/CUIL" después de "Datos destino"
                for (int j = i; j < Math.min(i + 10, lines.length); j++) {
                    String destLine = lines[j].toLowerCase().trim();
                    if (destLine.contains("cuit/cuil")) {
                        
                        // Buscar en las siguientes líneas el CUIT
                        for (int k = j + 1; k < Math.min(j + 3, lines.length); k++) {
                            String cuitLine = lines[k].trim();
                            
                            // Evitar líneas que contengan "CA $", "CVU", "Banco"
                            if (!cuitLine.toLowerCase().contains("ca $") && !cuitLine.toLowerCase().contains("cvu") && 
                                !cuitLine.toLowerCase().contains("banco") && !cuitLine.toLowerCase().contains("cuenta")) {
                                String numbersOnly = cuitLine.replaceAll("[^0-9]", "");
                                
                                if (numbersOnly.length() == 11 && numbersOnly.matches("\\d{11}")) {
                                    cuitReceptor = numbersOnly.substring(0,2) + "-" + numbersOnly.substring(2,10) + "-" + numbersOnly.substring(10);
                                   
                                    break;
                                }
                            }
                        }
                        if (!cuitReceptor.isEmpty()) break;
                    }
                }
            }
            
            // Banco Receptor - buscar después de "Banco" en sección destino
            if (bancoReceptor.isEmpty() && lower.contains("banco") && !lower.contains("bancor")) {
                
                String value = original.replaceAll("(?i)banco:?", "").trim();
                if (!value.isEmpty() && value.length() > 3) {
                    bancoReceptor = value;
                    
                } else if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1].trim();
                    
                    if (!nextLine.isEmpty() && !nextLine.toLowerCase().contains("cvu") && 
                        !nextLine.toLowerCase().contains("cuit") && nextLine.length() > 5) {
                        bancoReceptor = nextLine;
                        
                    }
                }
            }
            
            // Búsqueda específica del banco en sección "Datos destino"
            if (bancoReceptor.isEmpty() && lower.contains("datos destino")) {
                
                // Buscar "Banco" después de "Datos destino"
                for (int j = i; j < Math.min(i + 10, lines.length); j++) {
                    String destLine = lines[j].toLowerCase().trim();
                    if (destLine.equals("banco")) {
                        
                        // Buscar en la siguiente línea el nombre del banco
                        if (j + 1 < lines.length) {
                            String bancoLine = lines[j + 1].trim();
                            
                            // Verificar que no sea CVU, CUIT, etc.
                            if (!bancoLine.toLowerCase().contains("cvu") && 
                                !bancoLine.toLowerCase().contains("cuit") && 
                                !bancoLine.matches(".*\\d{10,}.*") && 
                                bancoLine.length() > 5) {
                                bancoReceptor = bancoLine;
                                
                                break;
                            }
                        }
                    }
                }
            }
            
            // Titular receptor - buscar nombre después de "a"
            if (titular.isEmpty() && lower.trim().equals("a") && i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                
                if (!nextLine.isEmpty() && !nextLine.toLowerCase().contains("cuit") && 
                    !nextLine.toLowerCase().contains("banco") && !nextLine.toLowerCase().contains("datos") &&
                    !nextLine.matches(".*\\d{5,}.*") && nextLine.length() > 2) { // Evitar líneas con muchos números
                    titular = nextLine;
                    
                }
            }
            
            // Buscar después del monto, generalmente aparece el nombre del receptor
            if (titular.isEmpty() && !monto.isEmpty() && original.contains("$") && i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                if (!nextLine.isEmpty() && !nextLine.toLowerCase().contains("datos") && !nextLine.toLowerCase().contains("cuit") && 
                    !nextLine.toLowerCase().contains("banco") && !nextLine.matches(".*\\d{10,}.*") && nextLine.length() > 3) {
                    titular = nextLine;
                    
                }
            }
            
            // Alternativa: buscar nombre en "Datos origen" bajo "Nombre y apellido"
            if (titular.isEmpty() && lower.contains("nombre y apellido") && i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                if (!nextLine.isEmpty()) {
                    titular = nextLine;
                    
                }
            }
        }
        
        // El CUIT emisor ya se busca en el bucle principal
        
        // Si no se detectó el monto, intentar búsquedas más específicas
        if (monto.isEmpty()) {
            
            
            // PRIORIDAD 1: Buscar formato específico $XXX.XXX,XX
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.matches(".*\\$\\s*\\d{1,3}(\\.\\d{3})*,\\d{2}.*") && 
                    !line.toLowerCase().contains("ca $") && !line.toLowerCase().contains("cvu") &&
                    !line.toLowerCase().contains("cuenta")) {
                    monto = line.trim();
                    break;
                }
            }
            
            // PRIORIDAD 2: Buscar otros formatos monetarios válidos
            if (monto.isEmpty()) {
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    String lowerLine = line.toLowerCase();
                    if (line.contains("$") && (line.contains(".") || line.contains(",")) &&
                        !lowerLine.contains("ca $") && !lowerLine.contains("cuenta") && 
                        !lowerLine.contains("cvu") && !lowerLine.contains("banco")) {
                        if (isValidMoneyFormat(line)) {
                            monto = line.trim();
                            break;
                        }
                    }
                }
            }
            
            // PRIORIDAD 3: Buscar cualquier línea válida con $
            if (monto.isEmpty()) {
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    String lowerLine = line.toLowerCase();
                    if (line.contains("$") && !lowerLine.contains("ca $") && !lowerLine.contains("cuenta") && 
                        !lowerLine.contains("cvu") && !lowerLine.contains("banco")) {
                        // Verificar que sea un monto válido (debe tener formato monetario)
                        if (isValidMoneyFormat(line)) {
                            monto = line.trim();
                            
                            break;
                        }
                    }
                }
            }
        }
        
        // Validación final del monto - rechazar si parece número de cuenta
        if (!monto.isEmpty()) {
            String montoNumeros = monto.replaceAll("[^0-9]", "");
            if (montoNumeros.length() > 10) {
                
                monto = "";
            }
        }
        
        System.out.println("[DEBUG BANCOR] === RESULTADO FINAL ===");
        System.out.println("[DEBUG BANCOR] Fecha: " + fecha);
        System.out.println("[DEBUG BANCOR] Tipo Operación: " + tipoOperacion);
        System.out.println("[DEBUG BANCOR] CUIT Emisor: " + cuitEmisor);
        System.out.println("[DEBUG BANCOR] CUIT Receptor: " + cuitReceptor);
        System.out.println("[DEBUG BANCOR] Monto: " + monto);
        System.out.println("[DEBUG BANCOR] Banco Receptor: " + bancoReceptor);
        System.out.println("[DEBUG BANCOR] Titular: " + titular);
        System.out.println("[DEBUG BANCOR] === FIN RESULTADO ===");
        
        
        TransferDTO transfer =  TransferDTO.builder().build();
        transfer.setDate(fecha.isEmpty() ? null : fecha);
        transfer.setTypeOFTransfer(tipoOperacion.isEmpty() ? null : tipoOperacion);
        transfer.setCuit(cuitEmisor.isEmpty() ? null : cuitEmisor);
        transfer.setAmount(monto.isEmpty() ? null : monto.replace("$", "").trim());
        transfer.setBank(bancoReceptor.isEmpty() ? null : bancoReceptor);
        transfer.setTitular(titular.isEmpty() ? null : titular);
        
        return transfer;
    }
}