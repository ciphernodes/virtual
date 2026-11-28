package com.cipher.virtual.data.hardware

import android.os.Build
import org.json.JSONObject
import java.util.UUID

data class DeviceProfile(
    val profileId: String = UUID.randomUUID().toString(),
    val presetKey: String,
    val displayName: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val androidVersion: String,
    val sdkInt: Int,
    val densityDpi: Int = 440,
    val isCustom: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("profileId", profileId)
        put("presetKey", presetKey)
        put("displayName", displayName)
        put("model", model)
        put("manufacturer", manufacturer)
        put("brand", brand)
        put("device", device)
        put("product", product)
        put("board", board)
        put("hardware", hardware)
        put("androidVersion", androidVersion)
        put("sdkInt", sdkInt)
        put("densityDpi", densityDpi)
        put("isCustom", isCustom)
    }

    fun toJsonString(): String = buildString {
        append('{')
        appendField("profileId", profileId); append(',')
        appendField("presetKey", presetKey); append(',')
        appendField("displayName", displayName); append(',')
        appendField("model", model); append(',')
        appendField("manufacturer", manufacturer); append(',')
        appendField("brand", brand); append(',')
        appendField("device", device); append(',')
        appendField("product", product); append(',')
        appendField("board", board); append(',')
        appendField("hardware", hardware); append(',')
        appendField("androidVersion", androidVersion); append(',')
        append("\"sdkInt\":").append(sdkInt).append(',')
        append("\"densityDpi\":").append(densityDpi).append(',')
        append("\"isCustom\":").append(isCustom)
        append('}')
    }

    companion object {
        fun fromJson(json: JSONObject): DeviceProfile {
            return fromFieldMap(
                stringLookup = { key, default ->
                    json.optString(key, "").ifBlank { default }
                },
                intLookup = { key, default ->
                    if (json.has(key)) json.optInt(key, default) else default
                },
                boolLookup = { key, default ->
                    if (json.has(key)) json.optBoolean(key, default) else default
                }
            )
        }

        fun fromJsonString(raw: String): DeviceProfile {
            val fields = parseFlatJsonObject(raw)
            return fromFieldMap(
                stringLookup = { key, default ->
                    fields[key]?.takeIf { it.isNotBlank() } ?: default
                },
                intLookup = { key, default ->
                    fields[key]?.toIntOrNull() ?: default
                },
                boolLookup = { key, default ->
                    fields[key]?.toBooleanStrictOrNull() ?: default
                }
            )
        }

        private inline fun fromFieldMap(
            stringLookup: (String, String) -> String,
            intLookup: (String, Int) -> Int,
            boolLookup: (String, Boolean) -> Boolean
        ): DeviceProfile {
            val hostModel = safeBuildField { Build.MODEL } ?: "Android"
            val hostManufacturer = safeBuildField { Build.MANUFACTURER } ?: "Android"
            val hostBrand = safeBuildField { Build.BRAND } ?: "android"
            val hostDevice = safeBuildField { Build.DEVICE } ?: "generic"
            val hostProduct = safeBuildField { Build.PRODUCT } ?: "generic"
            val hostBoard = safeBuildField { Build.BOARD } ?: "unknown"
            val hostHardware = safeBuildField { Build.HARDWARE } ?: "unknown"
            val hostRelease = safeBuildField { Build.VERSION.RELEASE } ?: "15"
            val hostSdk = runCatching { Build.VERSION.SDK_INT }.getOrDefault(35).let {
                if (it > 0) it else 35
            }

            return DeviceProfile(
                profileId = stringLookup("profileId", UUID.randomUUID().toString()),
                presetKey = stringLookup("presetKey", "Custom"),
                displayName = stringLookup("displayName", "Custom Profile"),
                model = stringLookup("model", hostModel),
                manufacturer = stringLookup("manufacturer", hostManufacturer),
                brand = stringLookup("brand", hostBrand),
                device = stringLookup("device", hostDevice),
                product = stringLookup("product", hostProduct),
                board = stringLookup("board", hostBoard),
                hardware = stringLookup("hardware", hostHardware),
                androidVersion = stringLookup("androidVersion", hostRelease),
                sdkInt = intLookup("sdkInt", hostSdk),
                densityDpi = intLookup("densityDpi", 440),
                isCustom = boolLookup("isCustom", false)
            )
        }

        private inline fun safeBuildField(reader: () -> String?): String? {
            return runCatching { reader() }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        private fun StringBuilder.appendField(key: String, value: String) {
            append('"').append(escapeJson(key)).append("\":\"")
            append(escapeJson(value)).append('"')
        }

        private fun escapeJson(value: String): String = buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }

        private fun parseFlatJsonObject(raw: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val trimmed = raw.trim()
            if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return result

            var index = 1
            val length = trimmed.length - 1

            fun skipWhitespace() {
                while (index < length && trimmed[index].isWhitespace()) index++
            }

            fun readString(): String {
                index++
                val sb = StringBuilder()
                while (index < length) {
                    val ch = trimmed[index++]
                    if (ch == '\\' && index < length) {
                        when (val escaped = trimmed[index++]) {
                            '\\', '"', '/' -> sb.append(escaped)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            else -> sb.append(escaped)
                        }
                    } else if (ch == '"') {
                        break
                    } else {
                        sb.append(ch)
                    }
                }
                return sb.toString()
            }

            while (index < length) {
                skipWhitespace()
                if (index >= length) break
                if (trimmed[index] == ',') {
                    index++
                    continue
                }
                if (trimmed[index] != '"') break
                val key = readString()
                skipWhitespace()
                if (index >= length || trimmed[index] != ':') break
                index++
                skipWhitespace()
                if (index >= length) break
                val value = if (trimmed[index] == '"') {
                    readString()
                } else {
                    val start = index
                    while (index < length && trimmed[index] != ',' && trimmed[index] != '}') {
                        index++
                    }
                    trimmed.substring(start, index).trim()
                }
                result[key] = value
            }
            return result
        }
    }
}
