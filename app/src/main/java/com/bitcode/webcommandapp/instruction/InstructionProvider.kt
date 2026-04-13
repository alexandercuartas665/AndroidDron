package com.bitcode.webcommandapp.instruction

interface InstructionProvider {
    fun loadClientConfig(clientToken: String): ProviderResult<ClientConfig>
    fun loadStep(sucursal: String, pagina: String, paso: String, queryParams: Map<String, String>): ProviderResult<StepDefinition?>
    fun loadActions(sucursal: String, pagina: String, pedReg: String): ProviderResult<List<ActionDefinition>>
    fun loadVariables(sucursal: String, pagina: String, pedRegToken: String): ProviderResult<List<ScriptVariable>>
}
