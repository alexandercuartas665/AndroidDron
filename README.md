# WebCommandApp

Proyecto Android para tablet antigua (API 21+) con:

- Navegador WebView integrado.
- Consola local para ejecutar JavaScript en la pagina actual.
- Servidor HTTP local para control remoto por IP:puerto.
- Opcion MotherData para consultas SOAP tipo PC (`FindDataSOAP` y `FindReaderSOAP`).

## Endpoints remotos

Requieren API key (`X-Api-Key` o `key` query param):

- `GET /health`
- `GET|POST /navigate?url=https://example.com`
- `GET|POST /execute?script=document.title`

Nota para `/execute`: si necesitas devolver un valor, usa `return`.
Ejemplo: `script=return document.title`

Puerto por defecto: `8080`.

## Build y run

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## Instalacion automatizada por script

```powershell
.\tools\install_debug.ps1 -ApiKey "bitcode-dev-2026"
```

## Personalizacion

API key y puerto desde propiedades de gradle:

```powershell
.\gradlew.bat assembleDebug -PremoteApiKey="tu_clave" -PremotePort=8080
```

## Pruebas remotas rapidas

```powershell
.\tools\remote_examples.ps1 -Ip "192.168.1.100" -ApiKey "bitcode-dev-2026"
```

## Consultas MotherData en la app

En la parte baja de la pantalla:

- `URL SOAP`: por defecto `https://app.bitcode.com.co/datos/WebDatos.asmx`
- `Base`: por ejemplo `AZURE`
- `Token SOAP`: token de acceso del sistema
- `SQL`: consulta a ejecutar

Botones:

- `FindDataSOAP`: ejecuta y muestra resumen de filas en el log.
- `FindReaderSOAP`: ejecuta y devuelve el primer valor de la primera fila.

Clases implementadas con nombres equivalentes a PC:

- `com.bitcode.webcommandapp.motherdata.AdmDatos`
- `com.bitcode.webcommandapp.motherdata.servicedata.WebDatos`

## InstructionProvider (opcion 1)

Implementado proveedor sobre SOAP con nombres claros:

- `com.bitcode.webcommandapp.instruction.InstructionProvider`
- `com.bitcode.webcommandapp.instruction.SoapInstructionProvider`

Modelos:

- `ClientConfig`
- `StepDefinition`
- `ActionDefinition`
- `ScriptVariable`

Prueba desde la app:

- Completa `URL SOAP`, `Base`, `Token SOAP`.
- Completa `Token cliente` (`WEB_SCRAPING_CLI.TOKEN`) y `Paso`.
- Pulsa `Probar Provider`.
- El log muestra resumen: cliente, sucursal, pagina, paso, numero de acciones y variables.
