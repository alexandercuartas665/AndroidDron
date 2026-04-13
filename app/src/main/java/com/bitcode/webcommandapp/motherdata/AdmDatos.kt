package com.bitcode.webcommandapp.motherdata

import com.bitcode.webcommandapp.motherdata.servicedata.WebDatos
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class AdmDatos(
    private val soapData: WebDatos = WebDatos()
) {

    var ErrorSql: String = ""

    fun SetServiceUrl(url: String) {
        if (url.isNotBlank()) {
            soapData.serviceUrl = url
        }
    }

    fun FindDataSOAP(sql: String, base: String, token: String): SoapDataSet {
        return try {
            ErrorSql = ""
            val xml = soapData.SqlSOAP(sql, base, token)
            val rows = parseRows(xml)
            SoapDataSet(rawXml = xml, rows = rows)
        } catch (ex: Exception) {
            ErrorSql = ex.message ?: "Error desconocido"
            SoapDataSet(rawXml = "", rows = emptyList())
        }
    }

    fun FindReaderSOAP(sql: String, base: String, token: String): String {
        val data = FindDataSOAP(sql, base, token)
        if (data.rows.isEmpty()) return ""
        val firstRow = data.rows.first()
        if (firstRow.isEmpty()) return ""
        return firstRow.entries.first().value
    }

    private fun parseRows(xml: String): List<Map<String, String>> {
        if (xml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xml)))

        val rows = mutableListOf<Map<String, String>>()

        val diffgramList = document.getElementsByTagNameNS("*", "diffgram")
        if (diffgramList.length > 0) {
            val diffgram = diffgramList.item(0)
            val dataRoot = firstChildElement(diffgram)
            if (dataRoot != null) {
                val rowNodes = childElements(dataRoot)
                for (rowNode in rowNodes) {
                    val row = extractFlatRow(rowNode)
                    if (row.isNotEmpty()) rows.add(row)
                }
                if (rows.isNotEmpty()) return rows
            }
        }

        val tables = document.getElementsByTagName("Table")
        for (i in 0 until tables.length) {
            val node = tables.item(i)
            if (node is Element) {
                val row = extractFlatRow(node)
                if (row.isNotEmpty()) rows.add(row)
            }
        }
        if (rows.isNotEmpty()) return rows

        val all = document.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val node = all.item(i)
            if (node is Element) {
                val row = extractFlatRow(node)
                if (row.isNotEmpty()) rows.add(row)
            }
        }

        return rows
    }

    private fun extractFlatRow(element: Element): Map<String, String> {
        val children = childElements(element)
        if (children.isEmpty()) return emptyMap()

        val row = linkedMapOf<String, String>()
        for (child in children) {
            val grandChildren = childElements(child)
            if (grandChildren.isNotEmpty()) {
                return emptyMap()
            }
            row[child.nodeName] = child.textContent?.trim().orEmpty()
        }
        return row
    }

    private fun childElements(node: Node?): List<Element> {
        if (node == null) return emptyList()
        val result = mutableListOf<Element>()
        val list = node.childNodes
        for (i in 0 until list.length) {
            val child = list.item(i)
            if (child is Element) {
                result.add(child)
            }
        }
        return result
    }

    private fun firstChildElement(node: Node?): Element? {
        return childElements(node).firstOrNull()
    }
}

data class SoapDataSet(
    val rawXml: String,
    val rows: List<Map<String, String>>
)
