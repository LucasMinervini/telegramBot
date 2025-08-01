# Telegram Doc Reader Bot

## Descripción

Este proyecto implementa un bot de Telegram que permite procesar documentos y comprobantes bancarios, así como crear grupos dinámicos entre usuarios para facilitar la comunicación y el intercambio de información.

## Funcionalidades Principales

### Procesamiento de Documentos
- Lectura y procesamiento de comprobantes bancarios (PDF e imágenes)
- Extracción de información relevante como fechas, montos, CUIT/CUIL, etc.
- Generación de archivos Excel con la información procesada



## Comandos Disponibles

### Comandos Básicos
- `/start` - Inicializar el bot y ver la lista de comandos disponibles
- Enviar documento (PDF/imagen) - Procesar comprobantes de transferencia
- `/status` - Mostrar las trasnferencias en memoria

## Implementación Técnica

### Clases Principales
- `TelegramDocBot` - Clase principal del bot que maneja los mensajes y comandos
- `TelegramGroupService` - Servicio para la gestión de grupos y su contexto
- `DocumentProcessingService` - Servicio para el procesamiento de documentos
- `TelegramFileService` - Servicio para la gestión de archivos

### Estructura de Datos
- `ClientsDTO` - Información de los clientes/usuarios
- `TransferDTO` - Información de las transferencias procesadas
