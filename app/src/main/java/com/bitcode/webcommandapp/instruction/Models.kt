package com.bitcode.webcommandapp.instruction

data class ClientConfig(
    val reg: String,
    val token: String,
    val sucursal: String,
    val codigoPagina: String,
    val pedRegToken: String,
    val prefijo: String,
    val referencia: String,
    val nombre: String,
    val correo: String,
    val slack: String,
    val exchange: String,
    val ciclo: String
)

data class StepDefinition(
    val reg: String,
    val orden: String,
    val tabla: String,
    val urlPasoRaw: String,
    val urlPasoResolved: String,
    val flagNoNav: Int,
    val tiempo: Int
)

data class ActionDefinition(
    val reg: String,
    val pedReg: String,
    val orden: Int,
    val tipo: String,
    val script: String,
    val espera: Int,
    val paginaDesde: Int,
    val paginaHasta: Int,
    val apiName: String
)

data class ScriptVariable(
    val variable: String,
    val valor: String
)

data class ProviderResult<T>(
    val data: T? = null,
    val error: String = "",
    val sql: String = ""
) {
    val ok: Boolean
        get() = error.isBlank()
}

data class ProviderSnapshot(
    val client: ClientConfig?,
    val step: StepDefinition?,
    val actions: List<ActionDefinition>,
    val variables: List<ScriptVariable>
)
