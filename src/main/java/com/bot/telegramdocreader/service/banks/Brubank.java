package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Brubank {
    
    public static String formatBrubank(TransferDTO transferencia) {
        // Verificar si hay un CUIT original (con letras) y un CUIT corregido
        String cuit;
        String cuitOriginal = transferencia.getCuitOriginal();
        String cuitCorregido = transferencia.getCuit();
        
        if (cuitCorregido != null && !cuitCorregido.isEmpty()) {
            if (cuitOriginal != null && !cuitOriginal.isEmpty() && !cuitOriginal.equals(cuitCorregido)) {
                // Si hay un CUIT original diferente del corregido, mostrar ambos
                cuit = "No hay CUIT del emisor (" + cuitOriginal + ")";
            } else {
                // Si solo hay CUIT corregido
                cuit = cuitCorregido;
            }
        } else {
            // Si no hay CUIT
            cuit = "No hay CUIT del emisor";
            if (cuitOriginal != null && !cuitOriginal.isEmpty()) {
                cuit += " (" + cuitOriginal + ")";
            }
        }
        
        // Formatear el banco receptor
        String bancoReceptor = "";
        if (transferencia.getBank() != null && !transferencia.getBank().isEmpty()) {
            String banco = transferencia.getBank();
            
            // Formatear con primera letra mayúscula
            String[] palabras = banco.split("\\s+");
            StringBuilder bancoFormateado = new StringBuilder();
            for (String palabra : palabras) {
                if (!palabra.isEmpty()) {
                    bancoFormateado.append(palabra.substring(0, 1).toUpperCase());
                    if (palabra.length() > 1) {
                        bancoFormateado.append(palabra.substring(1).toLowerCase());
                    }
                    bancoFormateado.append(" ");
                }
            }
            bancoReceptor = bancoFormateado.toString().trim();
        }
        
        // Formatear el monto para asegurar que tenga el formato correcto
        String monto = transferencia.getAmount() != null ? transferencia.getAmount() : "";
        
        // Eliminar el símbolo $ si ya está presente en el monto
        if (monto.startsWith("$")) {
            monto = monto.substring(1).trim();
        }
        
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                cuit,
                monto,
                bancoReceptor);
    }

    public static TransferDTO parseBrubankTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String tipoOperacion = "Transferencia";
        String monto = "";
        String cuentaOrigen = "";
        String destinatario = "";
        String cuit = "";
        String cuitOriginal = ""; // Para guardar el CUIT con letras antes de convertirlo
        String transactionNumber = "";
        String bancoDestino = "";
        String titularDestino = "";
        
        for (int i = 0; i < lines.length; i++) {
            String lower = lines[i].toLowerCase().trim();
            String original = lines[i].trim();
            
            // Fecha - buscar patrones de fecha con hora (formato específico de Brubank)
            // Incluye soporte para fechas con letras O y S en lugar de 0 y 5
            if (fecha.isEmpty() && original.matches(".*[0-9OS]{2}/[0-9OS]{2}/[0-9OS]{4}.*[0-9OS]{2}:[0-9OS]{2}.*")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9OS]{2}/[0-9OS]{2}/[0-9OS]{4})").matcher(original);
                if (matcher.find()) {
                    String fechaRaw = matcher.group(1);
                    // Reemplazar O por 0 y S por 5
                    fechaRaw = fechaRaw.replace('O', '0').replace('S', '5');
                    fecha = fechaRaw;
                }
            }
            
            // Fecha alternativa sin hora
            if (fecha.isEmpty() && original.matches(".*[0-9OS]{2}/[0-9OS]{2}/[0-9OS]{4}.*")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9OS]{2}/[0-9OS]{2}/[0-9OS]{4})").matcher(original);
                if (matcher.find()) {
                    String fechaRaw = matcher.group(1);
                    // Reemplazar O por 0 y S por 5
                    fechaRaw = fechaRaw.replace('O', '0').replace('S', '5');
                    fecha = fechaRaw;
                }
            }
            
            // Fecha extremadamente corrupta - buscar patrones como "24 de junio de 2025"
            if (fecha.isEmpty() && (lower.contains("junio") || lower.contains("julio") || lower.contains("agosto") || 
                                   lower.contains("enero") || lower.contains("febrero") || lower.contains("marzo") ||
                                   lower.contains("abril") || lower.contains("mayo") || lower.contains("septiembre") ||
                                   lower.contains("octubre") || lower.contains("noviembre") || lower.contains("diciembre"))) {
                // Extraer día, mes y año del texto
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})\\s+de\\s+(\\w+)\\s+de\\s+(\\d{4})").matcher(lower);
                if (matcher.find()) {
                    String dia = matcher.group(1);
                    String mes = matcher.group(2);
                    String año = matcher.group(3);
                    
                    // Convertir mes a número
                    String mesNumero = "";
                    switch (mes) {
                        case "enero": mesNumero = "01"; break;
                        case "febrero": mesNumero = "02"; break;
                        case "marzo": mesNumero = "03"; break;
                        case "abril": mesNumero = "04"; break;
                        case "mayo": mesNumero = "05"; break;
                        case "junio": mesNumero = "06"; break;
                        case "julio": mesNumero = "07"; break;
                        case "agosto": mesNumero = "08"; break;
                        case "septiembre": mesNumero = "09"; break;
                        case "octubre": mesNumero = "10"; break;
                        case "noviembre": mesNumero = "11"; break;
                        case "diciembre": mesNumero = "12"; break;
                    }
                    
                    if (!mesNumero.isEmpty()) {
                        // Formatear día con cero inicial si es necesario
                        if (dia.length() == 1) dia = "0" + dia;
                        fecha = dia + "/" + mesNumero + "/" + año;
                    }
                }
            }
            
            // Monto - buscar patrones de dinero (incluyendo formatos especiales con letras I y O)
            if (monto.isEmpty()) {
                // Buscar patrones como "$ 214.600,00" específicamente
                if (original.matches(".*\\$\\s*[0-9]{3}\\.[0-9]{3},[0-9]{2}.*")) {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\s*([0-9]{3}\\.[0-9]{3},[0-9]{2})").matcher(original);
                    if (matcher.find()) {
                        monto = matcher.group(1);
                    }
                }
                // Buscar patrones como "$ I37.OOO,OO" donde I=1 y O=0
                else if (original.matches(".*\\$\\s*[I0-9][0-9IO.,]+.*")) {
                    String montoRaw = original.replaceAll(".*\\$\\s*([I0-9][0-9IO.,]+).*", "$1").trim();
                    // Reemplazar I por 1 y O por 0
                    montoRaw = montoRaw.replace('I', '1').replace('O', '0');
                    monto = montoRaw;
                }
                // Patrón normal de dinero
                else if (original.matches(".*\\$\\s*[0-9.,]+.*")) {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\s*([0-9.,]+)").matcher(original);
                    if (matcher.find()) {
                        monto = matcher.group(1);
                    }
                }
            }
            
            // Si aún no se encontró monto, buscar en líneas que contengan "Monto" y extraer números de líneas cercanas
            if (monto.isEmpty() && (lower.contains("monto") || lower.contains("importe"))) {
                // Buscar números en la misma línea o líneas cercanas
                String[] allLines = textoExtraido.split("\\r?\\n");
                for (int j = Math.max(0, i-2); j < Math.min(allLines.length, i+3); j++) {
                    String lineaVecina = allLines[j].trim();
                    
                    // Buscar patrones de dinero con $ explícito
                    if (lineaVecina.matches(".*\\$\\s*[0-9.,]+.*")) {
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\s*([0-9.,]+)").matcher(lineaVecina);
                        if (matcher.find()) {
                            monto = matcher.group(1);
                            break;
                        }
                    }
                    
                   
                    if (lineaVecina.matches(".*[0-9]{2,3}\\.[0-9]{3},[0-9]{2}.*")) {
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9]{2,3}\\.[0-9]{3},[0-9]{2})").matcher(lineaVecina);
                        if (matcher.find()) {
                            monto = matcher.group(1);
                            break;
                        }
                    }
                    
                    // Buscar números que parezcan montos sin punto de miles
                    if (lineaVecina.matches(".*[0-9]{4,6},[0-9]{2}.*")) {
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9]{4,6},[0-9]{2})").matcher(lineaVecina);
                        if (matcher.find()) {
                            String montoTemp = matcher.group(1);
                            // Formatear con punto de miles si es necesario
                            if (montoTemp.length() >= 7) { // XXXXX,XX -> XX.XXX,XX
                                String parteEntera = montoTemp.substring(0, montoTemp.length() - 3);
                                String parteDecimal = montoTemp.substring(montoTemp.length() - 3);
                                if (parteEntera.length() > 3) {
                                    monto = parteEntera.substring(0, parteEntera.length() - 3) + "." + 
                                           parteEntera.substring(parteEntera.length() - 3) + parteDecimal;
                                } else {
                                    monto = parteEntera + parteDecimal;
                                }
                            } else {
                                monto = montoTemp;
                            }
                            break;
                        }
                    }
                }
            }
            
            // Buscar nombre del titular destino (formato: "Nombre Apellido")
            if (titularDestino.isEmpty() && original.matches("[A-Za-záéíóúÁÉÍÓÚüÜñÑ]+(\\s[A-Za-záéíóúÁÉÍÓÚüÜñÑ]+){1,3}") && 
                !lower.contains("brubank") && !lower.contains("caja") && !lower.contains("ahorro") && 
                !lower.contains("pesos") && !lower.contains("cuenta") && !lower.contains("detalle") && 
                !lower.contains("operación") && !lower.contains("operacion")) {
                titularDestino = original;
            }
            
            // Banco destino o empresa - detección genérica de empresas
            if (bancoDestino.isEmpty()) {
                // Buscar patrones genéricos de empresas (SA, SRL, etc.)
                if (!lower.isEmpty() && 
                    (original.contains(" SA") || original.contains(" S.A.") || 
                     original.contains(" SRL") || original.contains(" S.R.L.") ||
                     original.contains(" LTDA") || original.contains(" LIMITADA") ||
                     original.contains(" CORP") || original.contains(" INC") ||
                     original.contains(" LLC")) && 
                    !lower.equals("brubank") && 
                    !lower.equals("detalle") && 
                    !lower.equals("operación") && 
                    !lower.equals("operacion") && 
                    !lower.equals("fecha") && 
                    !lower.equals("monto") && 
                    !lower.equals("número de transacción") && 
                    !lower.equals("numero de transaccion") && 
                    !lower.matches(".*[0-9OS]{2}/[0-9OS]{2}/[0-9OS]{4}.*") && 
                    !lower.matches(".*\\$.*") && 
                    !lower.matches(".*[0-9OS]{2}:[0-9OS]{2}.*") &&
                    !original.matches(".*[0-9]{2}-[0-9]{8}-[0-9].*") && // Excluir patrones de CUIT
                    !original.matches(".*[0-9]{11,}.*")) { // Excluir números largos como CBU
                    bancoDestino = original.trim();
                }
                // Buscar nombres que estén completamente en mayúsculas (probable empresa)
                else if (original.toUpperCase().equals(original) && 
                         original.length() > 5 && // Mínimo 6 caracteres
                         original.matches("^[A-ZÁÉÍÓÚÜÑ\\s]+$") && // Solo letras y espacios
                         !lower.contains("brubank") && 
                         !lower.contains("transferencia") && 
                         !lower.contains("detalle") && 
                         !lower.contains("operación") && 
                         !lower.contains("operacion") && 
                         !lower.contains("fecha") && 
                         !lower.contains("monto") && 
                         !lower.contains("número") && 
                         !lower.contains("numero") &&
                         !original.matches(".*[0-9]{2}-[0-9]{8}-[0-9].*") && // Excluir CUITs
                         !original.matches(".*[0-9]{11,}.*") && // Excluir números largos
                         !isPersonName(original)) { // Excluir nombres de personas
                    bancoDestino = original.trim();
                }
            }
            
            // Verificar si hay un nombre de empresa en la línea actual
            if (bancoDestino.isEmpty() && original.toUpperCase().equals(original) && original.length() > 3 && 
                !lower.contains("brubank") && !lower.contains("transferencia") && 
                !lower.contains("fecha") && !lower.contains("monto") && 
                !lower.matches(".*\\$.*") && !lower.matches(".*[0-9OS]{2}:[0-9OS]{2}.*") &&
                !original.matches(".*[0-9]{2}-[0-9]{8}-[0-9].*") && // Excluir CUITs
                !original.matches(".*[0-9]{11,}.*")) { // Excluir números largos como CBU
                bancoDestino = original;
            }
            
            // Número de transacción - buscar códigos alfanuméricos o patrones específicos
            if (transactionNumber.isEmpty()) {
                if (original.matches("^[A-Z]{3}\\s[A-Z]{3}$")) {
                    transactionNumber = original;
                } else if (lower.contains("transacción") || lower.contains("transaccion")) {
                    // Extraer el número de transacción si está en la línea
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[a-zA-Z0-9]{3,}").matcher(original);
                    if (matcher.find()) {
                        transactionNumber = matcher.group();
                    }
                }
            }
            
            // CUIT/CUIL - buscar patrones que pueden contener letras I, O, S, G en lugar de números 1, 0, 5, 6
            if (cuit.isEmpty() && original.matches(".*[0-9IOSG]{2}-[0-9IOSG]{8}-[0-9IOSG].*")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9IOSG]{2}-[0-9IOSG]{8}-[0-9IOSG])").matcher(original);
                if (matcher.find()) {
                    cuitOriginal = matcher.group(1); // Guardar el CUIT original con letras
                    // Reemplazar I por 1, O por 0, S por 5, G por 6
                    String cuitRaw = cuitOriginal
                        .replace('I', '1')
                        .replace('O', '0')
                        .replace('S', '5')
                        .replace('G', '6');
                    cuit = cuitRaw;
                }
            }
            
            // CUIT sin guiones
            if (cuit.isEmpty() && original.matches(".*[0-9IOSG]{11}.*") && (lower.contains("cuit") || lower.contains("cuil"))) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9IOSG]{11})").matcher(original);
                if (matcher.find()) {
                    cuitOriginal = matcher.group(1); // Guardar el CUIT original con letras
                    // Reemplazar I por 1, O por 0, S por 5, G por 6
                    String cuitRaw = cuitOriginal
                        .replace('I', '1')
                        .replace('O', '0')
                        .replace('S', '5')
                        .replace('G', '6');
                    cuit = cuitRaw.substring(0,2) + "-" + cuitRaw.substring(2,10) + "-" + cuitRaw.substring(10);
                    cuitOriginal = cuitOriginal.substring(0,2) + "-" + cuitOriginal.substring(2,10) + "-" + cuitOriginal.substring(10);
                }
            }
            
            // Buscar específicamente el patrón 27-44IO44GI-O
            if (cuit.isEmpty() && original.matches(".*\\d{2}-\\d{2}[IO0-9]{2}\\d{2}[GI0-9]{2}-[O0-9].*")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{2}-\\d{2}[IO0-9]{2}\\d{2}[GI0-9]{2}-[O0-9])").matcher(original);
                if (matcher.find()) {
                    cuitOriginal = matcher.group(1); // Guardar el CUIT original con letras
                    // Reemplazar I por 1, O por 0, G por 6
                    String cuitRaw = cuitOriginal
                        .replace('I', '1')
                        .replace('O', '0')
                        .replace('G', '6');
                    cuit = cuitRaw;
                }
            }
            
            // Tipo de operación
            if (lower.contains("transferencia") || lower.contains("envío") || lower.contains("pago")) {
                tipoOperacion = "Transferencia";
            }
        }
        
        System.out.println("Brubank: " + textoExtraido);
        
        // Si no se encontró un banco destino pero sí un titular, usar el titular como banco
        // PERO solo si el titular no es un CUIT
        if (bancoDestino.isEmpty() && !titularDestino.isEmpty()) {
            // Verificar que el titular no sea un CUIT (formato XX-XXXXXXXX-X)
            if (!titularDestino.matches(".*[0-9]{2}-[0-9]{8}-[0-9].*")) {
                bancoDestino = titularDestino;
            }
        }
        
        // Formatear el monto para asegurar que tenga el formato correcto (137.000,00)
        if (!monto.isEmpty()) {
            // Eliminar posibles espacios
            monto = monto.replaceAll("\\s+", "");
            
            // Si el monto no tiene coma decimal pero tiene punto, asumimos que es un formato como 137.000
            if (!monto.contains(",") && monto.contains(".")) {
                monto = monto + ",00";
            }
            // Si el monto no tiene punto ni coma, asumimos que es un número entero
            else if (!monto.contains(",") && !monto.contains(".")) {
                // Formatear con separador de miles y decimales
                try {
                    int valor = Integer.parseInt(monto);
                    monto = String.format("%,d,00", valor).replace(",", ".");
                } catch (NumberFormatException e) {
                    // Si no se puede parsear, dejamos el monto como está
                }
            }
        }
        
        // Si no se encontró fecha, usar la fecha de hoy como default
        if (fecha.isEmpty()) {
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            fecha = today.format(formatter);
            System.out.println("No se encontró fecha en el documento, usando fecha de hoy: " + fecha);
        }
        
        return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(tipoOperacion)
                .cuentaOrigen(cuentaOrigen)
                .amount(monto)
                .bank(bancoDestino)  // Usa el banco destino o el titular como fallback
                .name(destinatario)
                .titularCuentaDestino(titularDestino)
                .cuit(cuit)
                .cuitOriginal(cuitOriginal)  // Añadir el CUIT original con letras
                .transactionNumber(transactionNumber)
                .build();
    }
    
    /**
     * Método para detectar si un texto es un nombre de persona física
     * @param text El texto a analizar
     * @return true si parece ser un nombre de persona, false si no
     */
    private static boolean isPersonName(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        
        String[] words = text.trim().split("\\s+");
        
        // Si tiene exactamente 2-4 palabras y cada una empieza con mayúscula, podría ser nombre de persona
        if (words.length >= 2 && words.length <= 4) {
            // Lista de nombres comunes argentinos para detectar nombres de personas
            String[] nombresComunes = {
                "MARTIN", "ALBERTO", "CARLOS", "JUAN", "JOSE", "LUIS", "MIGUEL", "ANTONIO", 
                "FRANCISCO", "MANUEL", "PEDRO", "RAFAEL", "ANGEL", "ALEJANDRO", "DIEGO",
                "MARIA", "ANA", "CARMEN", "LAURA", "ELENA", "PATRICIA", "ROSA", "MONICA",
                "SILVIA", "CLAUDIA", "ADRIANA", "GABRIELA", "SUSANA", "BEATRIZ", "NADIA",
                "ANTONELLA", "TORRES", "GARCIA", "RODRIGUEZ", "LOPEZ", "MARTINEZ", "GONZALEZ",
                "FERNANDEZ", "PEREZ", "SANCHEZ", "ROMERO", "SOSA", "CONTRERAS", "SILVA",
                "MENDEZ", "RUIZ", "ALVAREZ", "FLORES", "HERRERA", "MEDINA", "MORALES",
                "POZZI", "ROSSI", "FERRARI", "BRUNO", "COSTA", "GRECO"
            };
            
            // Verificar si alguna palabra coincide con nombres/apellidos comunes
            for (String word : words) {
                for (String nombreComun : nombresComunes) {
                    if (word.toUpperCase().equals(nombreComun)) {
                        return true;
                    }
                }
            }
            
            // Si no coincide con nombres comunes pero tiene el patrón típico de nombre de persona
            // (2-3 palabras, cada una con primera letra mayúscula y resto minúscula)
            boolean esPatronNombre = true;
            for (String word : words) {
                if (!word.matches("^[A-ZÁÉÍÓÚÜÑ][a-záéíóúüñ]+$")) {
                    esPatronNombre = false;
                    break;
                }
            }
            
            // Si tiene patrón de nombre y no contiene palabras típicas de empresas
            if (esPatronNombre && !text.toUpperCase().contains("SA") && 
                !text.toUpperCase().contains("SRL") && !text.toUpperCase().contains("LTDA") &&
                !text.toUpperCase().contains("CORP") && !text.toUpperCase().contains("INC")) {
                return true;
            }
        }
        
        return false;
    }
}