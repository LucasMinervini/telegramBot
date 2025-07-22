package com.bot.telegramdocreader.service.banks;

import com.bot.telegramdocreader.dto.TransferDTO;
import org.telegram.telegrambots.meta.api.objects.Document;

public class Brubank {
    public static String formatBrubank(TransferDTO transferencia) {
        String formato = "Fecha: %s\nTipo de Operación: %s\nCuit/Cuil: %s\nMonto Bruto: $ %s\nBanco Receptor: %s";
        return String.format(formato,
                transferencia.getDate() != null ? transferencia.getDate() : "",
                transferencia.getTypeOFTransfer() != null ? transferencia.getTypeOFTransfer() : "",
                transferencia.getCuit() != null ? transferencia.getCuit() : "",
                transferencia.getAmount() != null ? transferencia.getAmount() : "",
                transferencia.getBank() != null ? transferencia.getBank() : "");
    }

    public static TransferDTO parseBrubankTransfer(String textoExtraido, Document doc) {
        String[] lines = textoExtraido.split("\\r?\\n");
        String fecha = "";
        String tipoOperacion = "";
        String cuit = "";
        String monto = "";
        String bancoReceptor = "";
        String titularOrigen = "";
        String fileNameLower = doc.getFileName().toLowerCase();
        boolean isBrubank = textoExtraido.toLowerCase().contains("brubank") || fileNameLower.contains("brubank");
        if (isBrubank) {
            for (String line : lines) {
                line = line.trim();
                String lower = line.toLowerCase();
                // Fecha: buscar variantes y formatos
                if (lower.startsWith("fecha")) {
                    // Puede ser 'Fecha:', 'Fecha', 'Fecha 09/05/2025', etc.
                    String possibleDate = line.replaceFirst("(?i)fecha:?"," + ").trim();
                    if (!possibleDate.isEmpty()) {
                        // Buscar fecha en el resto de la línea
                        if (possibleDate.matches(".*\\d{2}/\\d{2}/\\d{4}.*")) {
                            fecha = possibleDate.replaceAll(".*?(\\d{2}/\\d{2}/\\d{4}).*", "$1").trim();
                        } else if (possibleDate.matches(".*\\d{2}-\\d{2}-\\d{4}.*")) {
                            fecha = possibleDate.replaceAll(".*?(\\d{2}-\\d{2}-\\d{4}).*", "$1").trim();
                        } else if (possibleDate.matches(".*\\d{4}/\\d{2}/\\d{2}.*")) {
                            fecha = possibleDate.replaceAll(".*?(\\d{4}/\\d{2}/\\d{2}).*", "$1").trim();
                        } else if (possibleDate.matches(".*\\d{2} de [a-záéíóú]+ de \\d{4}.*")) {
                            fecha = possibleDate.replaceAll(".*?(\\d{2} de [a-záéíóú]+ de \\d{4}).*", "$1").trim();
                        }
                    } else {
                        // Si la línea es solo 'Fecha' o 'Fecha:', buscar la siguiente línea con fecha
                        int idx = java.util.Arrays.asList(lines).indexOf(line);
                        if (idx+1 < lines.length) {
                            String next = lines[idx+1].trim();
                            if (next.matches(".*\\d{2}/\\d{2}/\\d{4}.*")) {
                                fecha = next.replaceAll(".*?(\\d{2}/\\d{2}/\\d{4}).*", "$1").trim();
                            } else if (next.matches(".*\\d{2} de [a-záéíóú]+ de \\d{4}.*")) {
                                fecha = next.replaceAll(".*?(\\d{2} de [a-záéíóú]+ de \\d{4}).*", "$1").trim();
                            } else if (next.matches(".*\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}.*")) {
                                fecha = next.replaceAll(".*?(\\d{2}/\\d{2}/\\d{4}).*", "$1").trim();
                            }
                        }
                    }
                }
                // Si no se encontró fecha aún, buscar línea con formato '22 de julio de 2025' sin etiqueta
                if (fecha.isEmpty() && lower.matches(".*\\d{2} de [a-záéíóú]+ de \\d{4}.*")) {
                    fecha = line.replaceAll(".*?(\\d{2} de [a-záéíóú]+ de \\d{4}).*", "$1").trim();
                }
                // Nuevo: buscar fecha en cualquier línea que contenga año y mes en texto, aunque no tenga día explícito
                if (fecha.isEmpty() && lower.matches(".*de [a-záéíóú]+ de \\d{4}.*")) {
                    fecha = line.replaceAll(".*?(de [a-záéíóú]+ de \\d{4}).*", "$1").trim();
                }
                // Nuevo: buscar fecha en formato dd/mm/yyyy o dd-mm-yyyy en cualquier línea
                if (fecha.isEmpty() && lower.matches(".*\\d{2}[/-]\\d{2}[/-]\\d{4}.*")) {
                    fecha = line.replaceAll(".*?(\\d{2}[/-]\\d{2}[/-]\\d{4}).*", "$1").trim();
                }
                // Tipo de operación: buscar variantes
                if (lower.contains("transferencia")) {
                    tipoOperacion = "Transferencia";
                } else if (lower.contains("envío de dinero") || lower.contains("envio de dinero")) {
                    tipoOperacion = "Envío de dinero";
                }
                // CUIT/CUIL: buscar variantes y sin etiqueta
                if (lower.startsWith("cuit/cuil:") || lower.startsWith("cuit:") || lower.startsWith("cuil:")) {
                    cuit = line.replaceAll("(?i)cuit/cuil:|cuit:|cuil:", "").replaceAll("[^0-9]", "").trim();
                } else if (cuit.isEmpty() && line.replaceAll("[^0-9]", "").length() == 11) {
                    cuit = line.replaceAll("[^0-9]", "").trim();
                }
                // Monto: buscar variantes y sin etiqueta
                if (lower.startsWith("$ ") || lower.startsWith("$")) {
                    monto = line.replaceAll("[^0-9.,]", "").trim();
                } else if (lower.startsWith("monto bruto:")) {
                    monto = line.replaceFirst("(?i)monto bruto:", "").replace("$", "").replace(" ", "").trim();
                } else if (monto.isEmpty() && lower.matches(".*\\$\\s*[0-9]+[.,]?[0-9]*.*")) {
                    monto = line.replaceAll("[^0-9.,]", "").trim();
                } else if (monto.isEmpty() && lower.matches(".*s\s*[0-9]+[.,]?[0-9]*.*")) {
                    monto = line.replaceAll("[^0-9.,]", "").trim();
                }
                // Banco receptor: buscar variantes y patrones
                if (lower.startsWith("banco receptor:")) {
                    bancoReceptor = line.replaceFirst("(?i)banco receptor:", "").trim();
                } else if (bancoReceptor.isEmpty() && lower.contains("brubank")) {
                    bancoReceptor = "Brubank";
                } else if (bancoReceptor.isEmpty() && lower.matches(".*o brubank.*")) {
                    bancoReceptor = "Brubank";
                } else if (bancoReceptor.isEmpty() && lower.contains("banco")) {
                    bancoReceptor = line.replaceAll("(?i)banco receptor:|banco|receptor|:|\u00a0", "").trim();
                }
                // Titular Origen: buscar si existe "Titular" o "Origen" y extraer el nombre
                if (lower.startsWith("titular")) {
                    titularOrigen = line.replaceFirst("(?i)titular", "").replace(":", "").trim();
                } else if (lower.startsWith("envío de dinero a ")) {
                    titularOrigen = line.substring("envío de dinero a ".length()).trim();
                } else if (lower.equals("envío de dinero a")) {
                    // Si la línea es solo 'Envío de dinero a', buscar la siguiente línea como titular
                    int idx = java.util.Arrays.asList(lines).indexOf(line);
                    if (idx+1 < lines.length) {
                        titularOrigen = lines[idx+1].trim();
                    }
                }
            }
            if (!titularOrigen.isEmpty()) {
                bancoReceptor = titularOrigen;
            } else if (bancoReceptor.isEmpty() && !titularOrigen.isEmpty()) {
                bancoReceptor = titularOrigen;
            }
            if (cuit.length() == 11) {
                cuit = cuit.substring(0,2) + "-" + cuit.substring(2,10) + "-" + cuit.substring(10);
            }
            // Formatear monto si es necesario
            if (!monto.isEmpty() && monto.matches("\\d{1,3}(\\.\\d{3})*,\\d{2}")) {
                // ya está bien
            } else if (!monto.isEmpty() && monto.matches("\\d+")) {
                monto = String.format("%s,00", monto);
            } else if (!monto.isEmpty() && monto.matches("\\d{1,3}(\\.\\d{3})*")) {
                monto = monto + ",00";
            }
            System.out.println("[DEBUG BRUBANK] Fecha detectada: " + fecha);
            return TransferDTO.builder()
                .date(fecha)
                .typeOFTransfer(!tipoOperacion.isEmpty() ? tipoOperacion : "Transferencia")
                .cuit(cuit)
                .amount(monto)
                .bank(titularOrigen)
                .build();
        }
        return null;
    }
}