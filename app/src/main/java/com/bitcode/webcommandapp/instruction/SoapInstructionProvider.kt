package com.bitcode.webcommandapp.instruction

import com.bitcode.webcommandapp.motherdata.AdmDatos

class SoapInstructionProvider(
    private val admDatos: AdmDatos,
    private val soapUrl: String,
    private val baseSistema: String,
    private val soapToken: String
) : InstructionProvider {

    init {
        admDatos.SetServiceUrl(soapUrl)
    }

    override fun loadClientConfig(clientToken: String): ProviderResult<ClientConfig> {
        val sql = "SELECT REG,TOKEN,SUCURSAL,CODIGO,PREFIJO,REFERENCIA,NOMBRE,CORREO " +
            "FROM [dbx.GENE].dbo.WEB_SCRAPING_CLI " +
            "WHERE TOKEN='${escapeSql(clientToken)}'"

        val query = runQuery(sql)
        if (!query.ok) {
            return ProviderResult(error = query.error, sql = query.sql.ifBlank { sql })
        }

        val row = query.data?.firstOrNull()
            ?: return ProviderResult(data = null, error = "Token no existe", sql = sql)

        val cfg = ClientConfig(
            reg = row.value("REG"),
            token = row.value("TOKEN"),
            sucursal = row.value("SUCURSAL"),
            codigoPagina = row.value("CODIGO"),
            pedRegToken = row.value("REG"),
            prefijo = row.value("PREFIJO"),
            referencia = row.value("REFERENCIA"),
            nombre = row.value("NOMBRE"),
            correo = row.value("CORREO"),
            slack = "",
            exchange = "",
            ciclo = "0"
        )
        return ProviderResult(data = cfg, sql = sql)
    }

    override fun loadStep(
        sucursal: String,
        pagina: String,
        paso: String,
        queryParams: Map<String, String>
    ): ProviderResult<StepDefinition?> {
        val sql = "SELECT REG,URL_PASO,FLAG_TOKEN,FLAG_NONAV,TABLA,TIEMPO,ORDEN " +
            "FROM [dbx.GENE].dbo.WEB_SCRAPING_R " +
            "WHERE SUCURSAL='${escapeSql(sucursal)}' " +
            "AND CODIGO='${escapeSql(pagina)}' " +
            "AND ORDEN='${escapeSql(paso)}'"

        val query = runQuery(sql)
        if (!query.ok) {
            return ProviderResult(error = query.error, sql = query.sql.ifBlank { sql })
        }

        val row = query.data?.firstOrNull()
            ?: return ProviderResult(data = null, sql = sql)

        val flagToken = row.valueInt("FLAG_TOKEN")
        val raw = if (flagToken == 1) {
            val sqlToken = "SELECT TOP 1 token FROM [dbx.GENE].dbo.WEB_SCRAPING_TOKEN ORDER BY FECHA_REG DESC"
            val tokenRs = runQuery(sqlToken)
            if (!tokenRs.ok) {
                return ProviderResult(error = tokenRs.error, sql = sqlToken)
            }
            tokenRs.data?.firstOrNull()?.value("token").orEmpty()
        } else {
            row.value("URL_PASO")
        }
        val resolved = replaceTemplate(raw, queryParams)

        val step = StepDefinition(
            reg = row.value("REG"),
            orden = row.value("ORDEN"),
            tabla = row.value("TABLA"),
            urlPasoRaw = raw,
            urlPasoResolved = resolved,
            flagNoNav = row.valueInt("FLAG_NONAV"),
            tiempo = row.valueInt("TIEMPO")
        )
        return ProviderResult(data = step, sql = sql)
    }

    override fun loadActions(sucursal: String, pagina: String, pedReg: String): ProviderResult<List<ActionDefinition>> {
        val sql = """
            SELECT *
            FROM [dbx.GENE].dbo.WEB_SCRAPING_RS
            WHERE SUCURSAL='${escapeSql(sucursal)}'
              AND CODIGO='${escapeSql(pagina)}'
              AND PEDREG='${escapeSql(pedReg)}'
            ORDER BY ORDEN
        """.trimIndent()

        val query = runQuery(sql)
        if (!query.ok) {
            return ProviderResult(error = query.error, sql = query.sql.ifBlank { sql })
        }

        val items = query.data.orEmpty().map { row ->
            ActionDefinition(
                reg = row.value("REG"),
                pedReg = row.value("PEDREG"),
                orden = row.valueInt("ORDEN"),
                tipo = row.value("TIPO"),
                script = row.value("SCRIPT"),
                espera = row.valueInt("ESPERA"),
                paginaDesde = row.valueInt("PAGINA_DESDE"),
                paginaHasta = row.valueInt("PAGINA_HASTA"),
                apiName = row.value("API_NAME")
            )
        }

        return ProviderResult(data = items, sql = sql)
    }

    override fun loadVariables(sucursal: String, pagina: String, pedRegToken: String): ProviderResult<List<ScriptVariable>> {
        val sql = """
            SELECT *
            FROM [dbx.GENE].dbo.WEB_SCRAPING_RAV
            WHERE SUCURSAL='${escapeSql(sucursal)}'
              AND CODIGO='${escapeSql(pagina)}'
              AND PEDREG='${escapeSql(pedRegToken)}'
        """.trimIndent()

        val query = runQuery(sql)
        if (!query.ok) {
            return ProviderResult(error = query.error, sql = query.sql.ifBlank { sql })
        }

        val vars = query.data.orEmpty().map { row ->
            ScriptVariable(
                variable = row.value("VARIABLE"),
                valor = row.value("VALOR")
            )
        }

        return ProviderResult(data = vars, sql = sql)
    }

    private fun runQuery(sql: String): ProviderResult<List<Map<String, String>>> {
        val normalizedSql = sql
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val data = admDatos.FindDataSOAP(normalizedSql, baseSistema, soapToken)
        if (admDatos.ErrorSql.isNotBlank()) {
            return ProviderResult(error = admDatos.ErrorSql, sql = normalizedSql)
        }
        return ProviderResult(data = data.rows, sql = normalizedSql)
    }

    private fun replaceTemplate(template: String, queryParams: Map<String, String>): String {
        var result = template
        queryParams.forEach { (k, v) ->
            result = result.replace("@@$k@@", v)
            result = result.replace("@@${k.uppercase()}@@", v)
            result = result.replace("@@${k.lowercase()}@@", v)
        }
        return result
    }

    private fun Map<String, String>.value(name: String): String {
        return entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()
    }

    private fun Map<String, String>.valueInt(name: String): Int {
        return value(name).toIntOrNull() ?: 0
    }

    private fun escapeSql(value: String): String {
        return value.replace("'", "''")
    }
}
