# Telegram Doc Reader Bot

## Descripción

Este proyecto implementa un bot de Telegram que permite procesar documentos y comprobantes bancarios, así como crear grupos dinámicos entre usuarios para facilitar la comunicación y el intercambio de información.

## Funcionalidades Principales

### Procesamiento de Documentos
- Lectura y procesamiento de comprobantes bancarios (PDF e imágenes)
- Extracción de información relevante como fechas, montos, CUIT/CUIL, etc.
- Generación de archivos Excel con la información procesada

### Sistema de Grupos
- Creación dinámica de grupos entre dos usuarios
- Invitaciones y confirmaciones para unirse a grupos
- Contexto específico para cada grupo (almacenamiento de información relevante)
- Procesamiento de documentos dentro de los grupos

## Comandos Disponibles

### Comandos Básicos
- `/start` - Inicializar el bot y ver la lista de comandos disponibles
- Enviar documento (PDF/imagen) - Procesar comprobantes de transferencia

### Sistema de Grupos
- `/listar_usuarios` - Ver todos los usuarios disponibles para crear grupos
- `/crear_grupo` - Crear un grupo con otro usuario (muestra ayuda si no se especifica usuario)
- `/crear_grupo NombreUsuario` - Crear un grupo con un usuario específico por nombre
- `/crear_grupo ID_Usuario` - Crear un grupo con un usuario específico por ID
- `link` - Generar enlace para invitar contactos externos al bot
- `/aceptar` - Aceptar una invitación pendiente a un grupo
- `/rechazar` - Rechazar una invitación pendiente a un grupo
- `/info` - (En grupos) Mostrar información del grupo y datos almacenados

## Flujo de Creación de Grupos

### Opción 1: Con usuarios ya registrados

#### Paso 1: Ver usuarios disponibles
```
/listar_usuarios
```
El bot mostrará una lista de todos los usuarios que han interactuado con él, incluyendo sus nombres e IDs.

#### Paso 2: Crear invitación
```
/crear_grupo Juan
```
o
```
/crear_grupo 123456789
```

### Opción 2: Invitar contactos externos

#### Paso 1: Generar enlace de invitación
```
/link
```
El bot generará un enlace único que puedes compartir con tus contactos.

#### Paso 2: Compartir enlace
Envía el enlace generado a tu contacto por WhatsApp, SMS, email, etc.

#### Paso 3: Invitación automática
Cuando tu contacto haga clic en el enlace:
- Se iniciará el bot automáticamente
- Recibirá una invitación para crear un grupo contigo
- Tú recibirás una notificación de que se unió

### Paso Final: Aceptar/Rechazar invitación
El usuario invitado recibirá un mensaje con opciones para:
- `/aceptar` - Crear el grupo
- `/rechazar` - Declinar la invitación

### Uso del grupo
Una vez creado el grupo, ambos usuarios pueden:
- Procesar documentos en el grupo
- Ver información del grupo con `/info`
- Mantener un historial compartido de transferencias

## Implementación Técnica

### Clases Principales
- `TelegramDocBot` - Clase principal del bot que maneja los mensajes y comandos
- `TelegramGroupService` - Servicio para la gestión de grupos y su contexto
- `DocumentProcessingService` - Servicio para el procesamiento de documentos
- `TelegramFileService` - Servicio para la gestión de archivos

### Estructura de Datos
- `ClientsDTO` - Información de los clientes/usuarios
- `TransferDTO` - Información de las transferencias procesadas
- `GroupContext` - Contexto específico para cada grupo

## Notas de Implementación

La API de Telegram Bot no permite crear grupos directamente, por lo que esta implementación es conceptual. En un entorno real, se necesitaría:

1. Crear el grupo manualmente o mediante la API de Telegram (no la API de Bot)
2. Generar enlaces de invitación para los usuarios
3. Registrar el grupo en el sistema una vez creado

Esta implementación simula este proceso para demostrar la lógica de negocio.