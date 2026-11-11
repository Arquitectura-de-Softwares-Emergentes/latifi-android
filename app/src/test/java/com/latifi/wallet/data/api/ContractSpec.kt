package com.latifi.wallet.data.api

import java.io.File

/**
 * A small line reader for the copy of `latifi-api.v0.yaml` kept in `src/test/resources/contract`.
 * It understands only the layout of that file (two space indents, flow or block `required`
 * lists), which is enough to read the version, the operations and the schema properties without a
 * YAML library. The copy comes from latifi-api develop at commit bedf16e (file last changed by
 * b660b98, the 0.2.0 amendment).
 */
object ContractSpec {
    private val lines: List<String> = File("src/test/resources/contract/latifi-api.v0.yaml").readLines()

    data class Operation(
        val id: String,
        val httpMethod: String,
        /** The path as the contract writes it, with a leading slash, for example `/loans/{loanId}`. */
        val path: String,
        val queryParams: Set<String>,
    )

    data class Schema(val properties: List<String>, val required: Set<String>)

    private val HTTP_METHODS = setOf("get", "post", "put", "delete", "patch")

    private fun indentOf(line: String) = line.length - line.trimStart().length

    /** `info.version`. */
    val version: String = run {
        val info = lines.indexOfFirst { it.trimEnd() == "info:" }
        lines.drop(info + 1).first { indentOf(it) == 2 && it.trim().startsWith("version:") }
            .substringAfter("version:").trim().trim('"', '\'')
    }

    val operations: List<Operation> = run {
        val start = lines.indexOfFirst { it.trimEnd() == "paths:" }
        val end = lines.indexOfFirst { it.trimEnd() == "components:" }
        val result = mutableListOf<Operation>()
        var path = ""
        var method = ""
        var id: String? = null
        var query = mutableSetOf<String>()
        var pendingName: String? = null

        fun flush() {
            id?.let { result += Operation(it, method, path, query) }
            id = null
            query = mutableSetOf()
        }

        for (line in lines.subList(start + 1, end)) {
            val indent = indentOf(line)
            val text = line.trim()
            when {
                indent == 2 && text.startsWith("/") && text.endsWith(":") -> {
                    flush()
                    path = text.removeSuffix(":")
                }
                indent == 4 && text.removeSuffix(":") in HTTP_METHODS -> {
                    flush()
                    method = text.removeSuffix(":").uppercase()
                }
                indent == 6 && text.startsWith("operationId:") -> id = text.substringAfter(":").trim()
                indent == 8 && text.startsWith("- name:") -> pendingName = text.substringAfter(":").trim()
                indent == 10 && text.startsWith("in:") ->
                    if (text.substringAfter(":").trim() == "query") pendingName?.let { query += it }
                indent == 8 && text.startsWith("- \$ref:") && text.endsWith("/parameters/Page'") -> query += "page"
                indent == 8 && text.startsWith("- \$ref:") && text.endsWith("/parameters/Size'") -> query += "size"
            }
        }
        flush()
        result
    }

    val schemas: Map<String, Schema> = run {
        val start = lines.indexOfFirst { it.trimEnd() == "  schemas:" }
        val result = mutableMapOf<String, Schema>()
        var name: String? = null
        val body = mutableListOf<String>()

        fun flush() {
            name?.let { result[it] = readSchema(body) }
            body.clear()
        }

        for (line in lines.drop(start + 1)) {
            if (line.isBlank()) continue
            if (indentOf(line) == 4 && line.trim().endsWith(":")) {
                flush()
                name = line.trim().removeSuffix(":")
            } else {
                body += line
            }
        }
        flush()
        result
    }

    private fun readSchema(body: List<String>): Schema {
        val properties = mutableListOf<String>()
        val required = mutableSetOf<String>()
        var section = ""
        for (line in body) {
            val indent = indentOf(line)
            val text = line.trim()
            if (indent == 6) {
                section = text.substringBefore(":")
                if (section == "required" && text.contains("[")) {
                    required += text.substringAfter("[").substringBefore("]").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
                continue
            }
            if (section == "properties" && indent == 8 && text.endsWith(":")) properties += text.removeSuffix(":")
            if (section == "required" && indent >= 8 && text.startsWith("- ")) required += text.removePrefix("- ").trim()
        }
        return Schema(properties, required)
    }

}
