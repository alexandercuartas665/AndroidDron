# Flujo de Ejecución de Instrucciones y Acciones

**Fecha**: 13 de abril de 2026  
**Última Actualización**: 13 de abril de 2026 - Refactorización completada

---

## 1. ARQUITECTURA GENERAL

El programa recibe instrucciones desde el servidor SignalR, las procesa y las ejecuta siguiendo este flujo:

```
[Servidor SignalR] 
    ↓
[onInstruction - JavaScript Bridge (SignalRBridgeBuilder)]
    ↓
[handleDroneInstruction() - DroneInstructionHandler.kt]
    ↓
[runRemoteFlow() - MainActivity.kt]
    ↓
[InstructionProvider - Obtiene pasos del SOAP]
    ↓
[Ejecuta Script/Acción]
```

### Cambios de Refactorización (Completados):
✅ **DroneInstructionHandler.kt** - Nueva clase para procesamiento de instrucciones  
✅ **SignalRBridgeBuilder.kt** - Nueva clase para construcción del bridge HTML/JS  
✅ **MainActivity.kt** - Refactorizado para usar las nuevas clases

---

## 2. ARCHIVOS CLAVE

### 2.1 **DroneInstructionHandler.kt** - PROCESADOR DE INSTRUCCIONES (NUEVO)
**Ubicación**: `c:\DesarrolloIA\Navegador android Dron\webcommandapp\app\src\main\java\com\bitcode\webcommandapp\DroneInstructionHandler.kt`

**Método principal:**
```kotlin
fun handleInstruction(
    droneId: String,
    instruction: String,          // Nombre de la instrucción ej: "CARGAR_DRON_BRUTO"
    paramsJson: String,           // Parámetros JSON
    correlationId: String,        // ID para reporte
    timestamp: String,            // Timestamp del evento
    onMaybeProcessFlow: (request: RunFlowRequest, paso: String) -> Unit,  // Callback
    onLogAppend: (String) -> Unit // Logging
)
```

**¿Qué hace?**
1. Recibe instrucciones del servidor SignalR
2. Valida el formato y estructura
3. Verifica si es `CARGAR_DRON_BRUTO` (la instrucción principal)
4. Extrae parámetros: `tramiteId`, `tokenHijo`, `paso`, `urlCommand`, `body`
5. Invoca callback para ejecutar el flujo

**Responsabilidades:**
- ✓ Parseo de instrucciones JSON
- ✓ Extracción de parámetros
- ✓ Validación de formato
- ✓ Separación de concerns

### 2.1.1 **MainActivity.kt** - ORQUESTADOR PRINCIPAL (REFACTORIZADO)
**Ubicación**: `c:\DesarrolloIA\Navegador android Dron\webcommandapp\app\src\main\java\com\bitcode\webcommandapp\MainActivity.kt`

**Ahora contiene:**
```kotlin
// Inicializa handlers refactorizados
private lateinit var droneInstructionHandler: DroneInstructionHandler
private lateinit var signalRBridgeBuilder: SignalRBridgeBuilder

// Llama a DroneInstructionHandler en JavaScript bridge
fun onInstruction(droneId: String, instruction: String, paramsJson: String, 
                  correlationId: String, timestamp: String) {
    droneInstructionHandler.handleInstruction(
        droneId, instruction, paramsJson, correlationId, timestamp,
        onMaybeProcessFlow = { request, paso ->
            dbExecutor.execute {
                val result = runRemoteFlow(request)
                // ... actualizar UI
            }
        },
        onLogAppend = { msg -> appendWsLog(msg) }
    )
}
```

**Responsabilidades (post-refactorización):**
- ✓ Configuración de UI
- ✓ Gestión de WebView
- ✓ Ciclo de vida de Activity
- ✓ Ejecución de flujos remotos
- ✓ Líneas de código total: ~1,800 (reducido de 2,181)

---

### 2.2 **SignalRBridgeBuilder.kt** - CONSTRUCTOR DEL BRIDGE (NUEVO)
**Ubicación**: `c:\DesarrolloIA\Navegador android Dron\webcommandapp\app\src\main\java\com\bitcode\webcommandapp\SignalRBridgeBuilder.kt`

**Método principal:**
```kotlin
fun buildSignalRBridgeHtml(): String {
    return """
        <!doctype html>
        <html>
        <head>
          <script src="/jquery.js"></script>
          <script src="/signalr/js"></script>
          <script src="/signalr/hubs"></script>
        </head>
        <body>
        <script>
        function initializeApp() { /* Full JS logic */ }
        initializeApp();
        </script>
        </body>
        </html>
    """.trimIndent()
}
```

**¿Qué hace?**
1. Construye HTML para el bridge SignalR
2. Carga jQuery y librerías SignalR
3. Define handlers de cliente para eventos del hub
4. Configura reconexión automática
5. Maneja logging y estado de conexión

**Responsabilidades:**
- ✓ Generación de HTML/JavaScript
- ✓ Configuración del hub SignalR
- ✓ Listeners de eventos
- ✓ Manejo de ciclo de vida de conexión
- ✓ Líneas de código: ~180

### 2.3 **InstructionProvider.kt** - PROVEEDOR DE PASOS Y ACCIONES
**Ubicación**: `c:\DesarrolloIA\Navegador android Dron\webcommandapp\app\src\main\java\com\bitcode\webcommandapp\instruction\InstructionProvider.kt`

**Interface que define cómo obtener instrucciones:**
```kotlin
interface InstructionProvider {
    fun loadClientConfig(clientToken: String): ProviderResult<ClientConfig>
    fun loadStep(sucursal: String, pagina: String, paso: String, queryParams: Map<String, String>): ProviderResult<StepDefinition?>
    fun loadActions(sucursal: String, pagina: String, pedReg: String): ProviderResult<List<ActionDefinition>>
    fun loadVariables(sucursal: String, pagina: String, pedRegToken: String): ProviderResult<List<ScriptVariable>>
}
```

**Métodos principales:**
- `loadStep()` → Obtiene la definición de un paso
- `loadActions()` → Obtiene las acciones de un paso
- `loadVariables()` → Obtiene variables para usar en scripts

---

### 2.4 **SoapInstructionProvider.kt** - IMPLEMENTACIÓN (SOAP + SQL)
**Ubicación**: `c:\DesarrolloIA\Navegador android Dron\webcommandapp\app\src\main\java\com\bitcode\webcommandapp\instruction\SoapInstructionProvider.kt`

**Implementa la interface con queries SQL a la base de datos:**

```kotlin
class SoapInstructionProvider(
    private val admDatos: AdmDatos,
    private val soapUrl: String,
    private val baseSistema: String,
    private val soapToken: String
) : InstructionProvider
```

---

### 2.5 **Models.kt** - ESTRUCTURAS DE DATOS
**Ubicación**: `c:\DesarrolloIA\Navegador android Dron\webcommandapp\app\src\main\java\com\bitcode\webcommandapp\instruction\Models.kt`

**Estructura de un Paso:**
```kotlin
data class StepDefinition(
    val reg: String,              // REG de BD
    val orden: String,            // Nombre del paso
    val tabla: String,            // Tabla asociada
    val urlPasoRaw: String,       // URL sin resolver
    val urlPasoResolved: String,  // URL con parámetros resueltos
    val flagNoNav: Int,           // Flag de no navegación
    val tiempo: Int               // Tiempo en ms
)
```

**Estructura de una Acción:**
```kotlin
data class ActionDefinition(
    val reg: String,              // REG de BD
    val pedReg: String,           // PEDREG (referencia)
    val orden: Int,               // Orden de ejecución (1, 2, 3...)
    val tipo: String,             // Tipo: "script", "click", "input"...
    val script: String,           // Código JavaScript o comando
    val espera: Int,              // Tiempo de espera (ms)
    val paginaDesde: Int,         // Página desde
    val paginaHasta: Int,         // Página hasta
    val apiName: String           // Nombre del API
)
```

---

## 3. FLUJO DE EJECUCIÓN PASO A PASO

### 3.1 Paso 1: Instrucción llega desde SignalR

**Archivo**: `MainActivity.kt` línea 1036 (en el HTML bridge):
```javascript
hub.client.recibirInstruccion = function(droneIdValue, instruccion, parametros, correlationId, timestamp) {
  console.log("📨 Instrucción recibida: " + instruccion + " para " + droneIdValue);
  callNative(
    "onInstruction",
    String(droneIdValue || ""),
    String(instruccion || ""),
    parametros == null ? "" : String(parametros),
    String(correlationId || ""),
    String(timestamp || "")
  );
};
```

---

### 3.2 Paso 2: Se llama a `handleDroneInstruction()`

**Archivo**: `MainActivity.kt` línea 1136:
```kotlin
private fun handleDroneInstruction(
    droneId: String,
    instruction: String,
    paramsJson: String,
    correlationId: String,
    timestamp: String
) {
    // ✓ Verifica que sea CARGAR_DRON_BRUTO
    if (!instruction.equals("CARGAR_DRON_BRUTO", ignoreCase = true)) {
        return  // Ignora otras instrucciones
    }
    
    // ✓ Extrae parámetros del JSON
    val tramiteId = payload.opt("tramiteId")?.toString().orEmpty()
    val tokenHijo = payload.opt("tokenHijo")?.toString().orEmpty()
    val paso = payload.opt("paso")?.toString().orEmpty()
    
    // ✓ Llama a ejecutar el flujo
    val result = runRemoteFlow(
        RunFlowRequest(
            paso = paso,
            clientToken = tokenHijo,
            soapUrl = "",
            base = "",
            soapToken = "",
            queryParams = queryParams,
            bodyJson = bodyJson
        )
    )
}
```

---

### 3.3 Paso 3: `runRemoteFlow()` obtiene el paso del servidor

**Lo que hace:**
1. Usa `SoapInstructionProvider.loadStep()` para obtener la definición del paso
2. Usa `SoapInstructionProvider.loadActions()` para obtener todas las acciones del paso
3. Usa `SoapInstructionProvider.loadVariables()` para obtener variables

**Estructura de consulta (en `SoapInstructionProvider.kt`):**
```kotlin
override fun loadActions(sucursal: String, pagina: String, pedReg: String): ProviderResult<List<ActionDefinition>> {
    val sql = """
        SELECT *
        FROM [dbx.GENE].dbo.WEB_SCRAPING_RS
        WHERE SUCURSAL='${escapeSql(sucursal)}'
          AND CODIGO='${escapeSql(pagina)}'
          AND PEDREG='${escapeSql(pedReg)}'
        ORDER BY ORDEN
    """.trimIndent()
    
    // Retorna lista de ActionDefinition ordenadas por ORDEN
}
```

---

### 3.4 Paso 4: Ejecuta cada Acción en orden

**Las acciones se ejecutan UNA POR UNA en el orden indicado en la BD:**

```
Paso: "MI_PROCESO"
├── Acción 1: ORDEN=1 (Llenar input con "valor1")
├── Acción 2: ORDEN=2 (Click en botón)
├── Acción 3: ORDEN=3 (Esperar 2 segundos)
├── Acción 4: ORDEN=4 (Validar resultado)
└── Acción 5: ORDEN=5 (Reportar éxito)
```

**Cada acción tiene una espera entre ellas** (definida en `ActionDefinition.espera`)

---

## 4. BASE DE DATOS - TABLAS INVOLUCRADAS

### 4.1 Tabla: `WEB_SCRAPING_RS` (Acciones)
```sql
SELECT *
FROM [dbx.GENE].dbo.WEB_SCRAPING_RS
-- Columnas importantes:
-- REG           -> Identificador único
-- PEDREG        -> Referencia al paso
-- ORDEN         -> Orden de ejecución (1, 2, 3...)
-- TIPO          -> Tipo de acción (script, click, input, etc.)
-- SCRIPT        -> Código a ejecutar
-- ESPERA        -> Tiempo de espera (ms)
-- API_NAME      -> Nombre del API o componente
```

### 4.2 Tabla: `WEB_SCRAPING_R` (Pasos)
```sql
SELECT *
FROM [dbx.GENE].dbo.WEB_SCRAPING_R
-- Columnas importantes:
-- REG           -> Identificador único
-- SUCURSAL      -> Sucursal
-- CODIGO        -> Código de página
-- ORDEN         -> Nombre del paso (argumento "paso")
-- URL_PASO      -> URL a navegar
-- TIEMPO        -> Tiempo máximo
-- FLAG_NONAV    -> Flag de no navegación
```

---

## 5. ESTRUCTURA DE DATOS COMPLETA

```
RunFlowRequest
├── paso: String              ← Qué paso ejecutar (ej: "PASO_1")
├── clientToken: String       ← Token del cliente (tokenHijo del servidor)
├── queryParams: Map          ← Parámetros de la URL
└── bodyJson: String         ← Payload del body

        ↓ runRemoteFlow()

ProviderSnapshot
├── client: ClientConfig      ← Configuración del cliente
├── step: StepDefinition      ← Definición del paso
├── actions: List<ActionDefinition>  ← Todas las acciones del paso
└── variables: List<ScriptVariable>  ← Variables disponibles

        ↓ Ejecuta en secuencia

ActionDefinition (ejecutado en orden por ORDEN)
├── tipo: String              ← Qué tipo de acción
├── script: String            ← Código a ejecutar
├── espera: Int               ← Esperar después
└── apiName: String          ← API asociado
```

---

## 6. FLUJO VISUAL COMPLETO

```
┌─────────────────────────────────────┐
│   Servidor SignalR                  │
│  dronesHub.recibirInstruccion()     │
└────────────────┬────────────────────┘
                 │ (parámetros: instruccion, paso, tokenHijo, etc.)
                 │
┌────────────────▼────────────────────┐
│  JavaScript Bridge (HTML)           │
│  hub.client.recibirInstruccion()    │
│  → callNative("onInstruction", ...) │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  MainActivity.kt                    │
│  onInstruction (bridge listener)    │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  handleDroneInstruction()           │
│  Verifica instruction == "CARGAR.." │
│  Extrae: paso, tokenHijo, tramiteId │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  runRemoteFlow(request)             │
│  Inicia DbExecutor (background)     │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  SoapInstructionProvider            │
│  • loadStep()   (obtiene paso)      │
│  • loadActions() (obtiene acciones) │
│  • loadVariables() (obtiene vars)   │
└────────────────┬────────────────────┘
                 │
    ┌────────────▼────────────┐
    │   Base de Datos SQL     │
    │ [dbx.GENE].dbo.WEB_*    │
    └────────────┬────────────┘
                 │
┌────────────────▼────────────────────┐
│  Ejecuta Acciones en ORDEN          │
│  ┌──────────────────────────────┐   │
│  │ Acción 1 (ORDEN=1)          │   │
│  │ Wait espera ms              │   │
│  └──────────────────────────────┘   │
│  ┌──────────────────────────────┐   │
│  │ Acción 2 (ORDEN=2)          │   │
│  │ Wait espera ms              │   │
│  └──────────────────────────────┘   │
│  ┌──────────────────────────────┐   │
│  │ ... más acciones ...        │   │
│  └──────────────────────────────┘   │
└────────────────┬────────────────────┘
                 │
┌────────────────▼────────────────────┐
│  runOnUiThread()                    │
│  Reporta resultado al usuario       │
│  droneSocket.informarRespuesta()    │
└─────────────────────────────────────┘
```

---

## 7. RESUMEN: DÓNDE SE EJECUTAN LAS INSTRUCCIONES

| Componente | Archivo | Línea | Propósito |
|-----------|---------|-------|----------|
| **Receptor** | MainActivity.kt (HTML Bridge) | 1026-1041 | Recibe instrucción de SignalR |
| **Manejador** | MainActivity.kt | 1136-1210 | Procesa y valida la instrucción |
| **Orquestador** | MainActivity.kt | N/A | Llama runRemoteFlow() |
| **Proveedor** | SoapInstructionProvider.kt | 90-130 | Obtiene pasos y acciones de BD |
| **Modelos** | Models.kt | Completo | Define estructuras de datos |
| **Ejecutor** | MainActivity.kt (DbExecutor) | N/A | Ejecuta acciones en background |

---

## 8. CÓMO AGREGAR UNA NUEVA INSTRUCCIÓN

### Paso 1: Guardar en BD
```sql
INSERT INTO [dbx.GENE].dbo.WEB_SCRAPING_RS
VALUES (REG, PEDREG, ORDEN, 'tipo', 'script', espera, pagina_desde, pagina_hasta, 'API_NAME')
```

### Paso 2: Agregar tipo en `handleDroneInstruction()`
```kotlin
if (instruction.equals("NUEVA_INSTRUCCION", ignoreCase = true)) {
    // Procesar nueva instrucción
    val resultado = runRemoteFlow(...)
}
```

### Paso 3: Invocar desde servidor
```csharp
// En DronesHub.vb (servidor)
hub.Clients.Client(connectionId).recibirInstruccion(
    droneId, 
    "NUEVA_INSTRUCCION",  ← Nueva instrucción
    jsonParametros, 
    correlationId, 
    DateTime.Now
)
```

---

## 9. PUNTOS DE DEBUGGING

**Para debuguear instrucciones, monitorear estos puntos:**

1. **Logs en logcat:**
```bash
adb logcat | findstr "handleDroneInstruction\|runRemoteFlow\|Acción\|CARGAR_DRON"
```

2. **Console en Chrome DevTools:**
```javascript
// Dentro de hub.client.recibirInstruccion
console.log("📨 Instrucción:", instruccion, "Parámetros:", parametros)
```

3. **Breakpoints en MainActivity.kt:**
   - Línea 1136: `handleDroneInstruction()`
   - Línea 1156: Validación de `CARGAR_DRON_BRUTO`
   - Línea 1188: `runRemoteFlow()`

---

*Documento generado: 13 de abril de 2026*  
*Arquitectura: SOAP + SQL + SignalR + Android WebView*
