package com.bitcode.webcommandapp.motherdata.servicedata

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

class WebDatos(
    var serviceUrl: String = DEFAULT_URL
) {

    fun SqlSOAP(sql: String, base: String, token: String): String {
        val endpoint = URL(serviceUrl)
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
        connection.setRequestProperty("SOAPAction", "\"http://tempuri.org/SqlSOAP\"")

        val payload = buildSoapEnvelope(sql, base, token)
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }

        val status = connection.responseCode
        val body = if (status in 200..299) {
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        } else {
            val errorBody = connection.errorStream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            } ?: ""
            throw IllegalStateException("SOAP HTTP $status: $errorBody")
        }

        return parseSqlSoapResult(body)
    }

    private fun buildSoapEnvelope(sql: String, base: String, token: String): String {
        val sqlEsc = escapeXml(sql)
        val baseEsc = escapeXml(base)
        val tokenEsc = escapeXml(token)
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                           xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <SqlSOAP xmlns="http://tempuri.org/">
                  <SQL>$sqlEsc</SQL>
                  <Base>$baseEsc</Base>
                  <Token>$tokenEsc</Token>
                </SqlSOAP>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()
    }

    private fun parseSqlSoapResult(soapResponseXml: String): String {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(soapResponseXml)))

        val nsNodes = document.getElementsByTagNameNS("*", "SqlSOAPResult")
        if (nsNodes.length > 0) {
            return nsNodes.item(0)?.textContent ?: ""
        }

        val nodes = document.getElementsByTagName("SqlSOAPResult")
        return if (nodes.length > 0) nodes.item(0)?.textContent ?: "" else ""
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    companion object {
        const val DEFAULT_URL: String = "https://app.bitcode.com.co/datos/WebDatos.asmx"
    }
}
