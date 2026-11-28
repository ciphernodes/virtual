package com.cipher.virtual.data.local

import java.io.IOException

/**
 * Pure-JVM JSON serialization and parsing codec for the fallback [WorkspaceInstance] registry.
 */
internal object WorkspaceRegistryJson {

    fun serialize(instances: List<WorkspaceInstance>): String = buildString {
        append('[')
        instances.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('{')
            append("\"instanceId\":\"").append(escapeJson(item.instanceId)).append("\",")
            append("\"label\":\"").append(escapeJson(item.label)).append("\",")
            append("\"targetPackageName\":\"").append(escapeJson(item.targetPackageName)).append("\",")
            append("\"createdAt\":").append(item.createdAt).append(',')
            append("\"isActive\":").append(item.isActive)
            if (item.workerSlot != null) {
                append(",\"workerSlot\":\"").append(escapeJson(item.workerSlot)).append('"')
            }
            append('}')
        }
        append(']')
    }

    fun parse(raw: String): List<WorkspaceInstance> {
        val trimmed = raw.trim()
        if (!trimmed.startsWith('[')) {
            throw IOException("Corrupted workspace registry: expected JSON array.")
        }
        return JsonArrayParser(trimmed).parseInstanceArray()
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> append(ch)
            }
        }
    }

    private class JsonArrayParser(private val input: String) {
        private var pos = 0

        fun parseInstanceArray(): List<WorkspaceInstance> {
            skipWhitespace()
            expect('[')
            skipWhitespace()
            val results = mutableListOf<WorkspaceInstance>()
            if (peek() == ']') {
                pos++
                return results
            }
            while (pos < input.length) {
                skipWhitespace()
                results.add(parseInstanceObject())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return results
                    }
                    else -> throw IOException("Unexpected character in workspace registry at index $pos")
                }
            }
            throw IOException("Unterminated workspace registry JSON array.")
        }

        private fun parseInstanceObject(): WorkspaceInstance {
            expect('{')
            skipWhitespace()
            var instanceId: String? = null
            var label: String? = null
            var targetPackageName: String? = null
            var createdAt = 0L
            var isActive = false
            var workerSlot: String? = null

            if (peek() == '}') {
                pos++
                throw IOException("Empty WorkspaceInstance object in registry.")
            }

            while (pos < input.length) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                when (key) {
                    "instanceId" -> instanceId = readString()
                    "label" -> label = readString()
                    "targetPackageName" -> targetPackageName = readString()
                    "createdAt" -> createdAt = readLong()
                    "isActive" -> isActive = readBoolean()
                    "workerSlot" -> workerSlot = if (peek() == 'n') {
                        skipNull()
                        null
                    } else {
                        readString().takeIf { it.isNotBlank() }
                    }
                    else -> skipValue()
                }
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        val resolvedId = instanceId
                            ?: throw IOException("Missing 'instanceId' in registry entry.")
                        val resolvedLabel = label
                            ?: throw IOException("Missing 'label' in registry entry.")
                        val resolvedPkg = targetPackageName
                            ?: throw IOException("Missing 'targetPackageName' in registry entry.")
                        return WorkspaceInstance(
                            instanceId = resolvedId,
                            label = resolvedLabel,
                            targetPackageName = resolvedPkg,
                            createdAt = createdAt,
                            isActive = isActive,
                            workerSlot = workerSlot
                        )
                    }
                    else -> throw IOException("Unexpected character in WorkspaceInstance object at index $pos")
                }
            }
            throw IOException("Unterminated WorkspaceInstance object in registry.")
        }

        private fun skipNull() {
            if (input.startsWith("null", startIndex = pos)) {
                pos += 4
            } else {
                skipValue()
            }
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? = if (pos < input.length) input[pos] else null

        private fun expect(expected: Char) {
            if (pos >= input.length || input[pos] != expected) {
                throw IOException("Expected '$expected' at index $pos")
            }
            pos++
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                if (ch == '"') {
                    return sb.toString()
                } else if (ch == '\\') {
                    if (pos >= input.length) throw IOException("Unterminated escape in JSON string.")
                    when (val esc = input[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > input.length) {
                                throw IOException("Invalid unicode escape in JSON string.")
                            }
                            val hex = input.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            throw IOException("Unterminated JSON string.")
        }

        private fun readLong(): Long {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '-')) pos++
            return input.substring(start, pos).toLongOrNull()
                ?: throw IOException("Invalid numeric value at index $start")
        }

        private fun readBoolean(): Boolean {
            return when {
                input.startsWith("true", startIndex = pos) -> {
                    pos += 4
                    true
                }
                input.startsWith("false", startIndex = pos) -> {
                    pos += 5
                    false
                }
                else -> throw IOException("Invalid boolean value at index $pos")
            }
        }

        private fun skipValue() {
            skipWhitespace()
            when (peek()) {
                '"' -> readString()
                '{' -> {
                    pos++
                    var depth = 1
                    while (pos < input.length && depth > 0) {
                        when (input[pos]) {
                            '"' -> readString()
                            '{' -> { depth++; pos++ }
                            '}' -> { depth--; pos++ }
                            else -> pos++
                        }
                    }
                }
                '[' -> {
                    pos++
                    var depth = 1
                    while (pos < input.length && depth > 0) {
                        when (input[pos]) {
                            '"' -> readString()
                            '[' -> { depth++; pos++ }
                            ']' -> { depth--; pos++ }
                            else -> pos++
                        }
                    }
                }
                else -> {
                    while (pos < input.length && input[pos] != ',' && input[pos] != '}') {
                        pos++
                    }
                }
            }
        }
    }
}
