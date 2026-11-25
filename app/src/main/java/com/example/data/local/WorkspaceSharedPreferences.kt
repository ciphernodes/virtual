package com.example.data.local

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.WeakHashMap
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Isolated, file-backed [SharedPreferences] implementation stored directly inside an instance's
 * `shared_prefs` directory (`workspaces/<instanceId>/shared_prefs/<name>.xml`).
 *
 * Uses Android's standard `<map>` XML representation with atomic `.tmp` writes and synchronized
 * in-memory caching so preference data remains strictly separated per workspace instance and is
 * completely erased whenever the instance directory is reset or deleted.
 */
class WorkspaceSharedPreferences(
    private val prefsFile: File
) : SharedPreferences {

    private val lock = Any()
    private val listeners = Collections.newSetFromMap(
        WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>()
    )
    private var values: MutableMap<String, Any?> = loadFromDisk()

    override fun getAll(): MutableMap<String, *> = synchronized(lock) {
        val copy = LinkedHashMap<String, Any?>(values.size)
        values.forEach { (k, v) ->
            copy[k] = if (v is Set<*>) LinkedHashSet(v.filterIsInstance<String>()) else v
        }
        copy
    }

    override fun getString(key: String?, defValue: String?): String? = synchronized(lock) {
        if (key == null) return defValue
        (values[key] as? String) ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) {
            if (key == null) return defValues
            val stored = values[key] as? Set<*> ?: return defValues
            LinkedHashSet(stored.filterIsInstance<String>())
        }

    override fun getInt(key: String?, defValue: Int): Int = synchronized(lock) {
        if (key == null) return defValue
        (values[key] as? Int) ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long = synchronized(lock) {
        if (key == null) return defValue
        (values[key] as? Long) ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float = synchronized(lock) {
        if (key == null) return defValue
        (values[key] as? Float) ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(lock) {
        if (key == null) return defValue
        (values[key] as? Boolean) ?: defValue
    }

    override fun contains(key: String?): Boolean = synchronized(lock) {
        key != null && values.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener == null) return
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        if (listener == null) return
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    /**
     * Clears the in-memory preference map and deletes the backing XML file and temp file.
     */
    fun deleteBackingFiles(): Boolean {
        val clearedKeys: List<String>
        val activeListeners: List<SharedPreferences.OnSharedPreferenceChangeListener>
        synchronized(lock) {
            clearedKeys = values.keys.toList()
            values = LinkedHashMap()
            activeListeners = listeners.toList()
        }
        val mainDeleted = !prefsFile.exists() || prefsFile.delete()
        val tempFile = File(prefsFile.parentFile, "${prefsFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        notifyListeners(activeListeners, clearedKeys)
        return mainDeleted
    }

    private fun notifyListeners(
        activeListeners: List<SharedPreferences.OnSharedPreferenceChangeListener>,
        changedKeys: List<String>
    ) {
        if (activeListeners.isEmpty() || changedKeys.isEmpty()) return
        val dispatch = Runnable {
            for (i in changedKeys.indices.reversed()) {
                val key = changedKeys[i]
                activeListeners.forEach { listener ->
                    listener.onSharedPreferenceChanged(this@WorkspaceSharedPreferences, key)
                }
            }
        }
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull()
        if (mainLooper != null && Looper.myLooper() != mainLooper) {
            Handler(mainLooper).post(dispatch)
        } else {
            dispatch.run()
        }
    }

    private fun loadFromDisk(): MutableMap<String, Any?> {
        if (!prefsFile.exists() || prefsFile.length() == 0L) {
            return LinkedHashMap()
        }
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val doc = factory.newDocumentBuilder().parse(prefsFile)
            val root = doc.documentElement ?: return@runCatching LinkedHashMap()
            val loaded = LinkedHashMap<String, Any?>()
            val children = root.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val element = node as Element
                val name = element.getAttribute("name")
                if (name.isEmpty()) continue
                when (element.tagName) {
                    "string" -> loaded[name] = element.textContent ?: ""
                    "int" -> element.getAttribute("value").toIntOrNull()?.let { loaded[name] = it }
                    "long" -> element.getAttribute("value").toLongOrNull()?.let { loaded[name] = it }
                    "float" -> element.getAttribute("value").toFloatOrNull()?.let { loaded[name] = it }
                    "boolean" -> element.getAttribute("value").toBooleanStrictOrNull()?.let { loaded[name] = it }
                    "set" -> {
                        val setValues = LinkedHashSet<String>()
                        val items = element.childNodes
                        for (j in 0 until items.length) {
                            val itemNode = items.item(j)
                            if (itemNode.nodeType == Node.ELEMENT_NODE && itemNode.nodeName == "string") {
                                setValues.add(itemNode.textContent ?: "")
                            }
                        }
                        loaded[name] = setValues
                    }
                }
            }
            loaded
        }.getOrDefault(LinkedHashMap())
    }

    private fun writeToDisk(snapshot: Map<String, Any?>) {
        val parent = prefsFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Unable to create shared_prefs directory: ${parent.absolutePath}")
        }
        val xml = serializeMapXml(snapshot)
        val tempFile = File(prefsFile.parentFile, "${prefsFile.name}.tmp")
        tempFile.writeText(xml, StandardCharsets.UTF_8)
        try {
            Files.move(
                tempFile.toPath(),
                prefsFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Throwable) {
            if (!tempFile.renameTo(prefsFile)) {
                tempFile.copyTo(prefsFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val editorLock = Any()
        private val modified = LinkedHashMap<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) {
                synchronized(editorLock) { modified[key] = value }
            }
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            if (key != null) {
                synchronized(editorLock) {
                    modified[key] = values?.let { LinkedHashSet(it) }
                }
            }
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) synchronized(editorLock) { modified[key] = value }
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) synchronized(editorLock) { modified[key] = value }
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) synchronized(editorLock) { modified[key] = value }
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) synchronized(editorLock) { modified[key] = value }
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) synchronized(editorLock) { modified[key] = REMOVE_SENTINEL }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            synchronized(editorLock) { clearRequested = true }
        }

        override fun commit(): Boolean {
            return commitInternal()
        }

        override fun apply() {
            commitInternal()
        }

        private fun commitInternal(): Boolean {
            val changedKeys = mutableListOf<String>()
            val activeListeners: List<SharedPreferences.OnSharedPreferenceChangeListener>
            val snapshotToWrite: Map<String, Any?>

            synchronized(lock) {
                synchronized(editorLock) {
                    if (clearRequested) {
                        if (values.isNotEmpty()) {
                            changedKeys.addAll(values.keys)
                            values.clear()
                        }
                        clearRequested = false
                    }
                    modified.forEach { (key, value) ->
                        if (value === REMOVE_SENTINEL || value == null) {
                            if (values.containsKey(key)) {
                                values.remove(key)
                                changedKeys.add(key)
                            }
                        } else {
                            val existing = values[key]
                            if (existing != value) {
                                values[key] = if (value is Set<*>) {
                                    LinkedHashSet(value.filterIsInstance<String>())
                                } else {
                                    value
                                }
                                changedKeys.add(key)
                            }
                        }
                    }
                    modified.clear()
                }
                snapshotToWrite = LinkedHashMap(values)
                activeListeners = listeners.toList()
            }

            val persisted = runCatching {
                writeToDisk(snapshotToWrite)
                true
            }.getOrDefault(false)

            if (persisted) {
                notifyListeners(activeListeners, changedKeys.distinct())
            }
            return persisted
        }
    }

    private companion object {
        private val REMOVE_SENTINEL = Any()

        fun serializeMapXml(entries: Map<String, Any?>): String = buildString {
            append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
            append("<map>\n")
            entries.forEach { (key, value) ->
                val escapedName = escapeAttr(key)
                when (value) {
                    is String -> {
                        append("    <string name=\"").append(escapedName).append("\">")
                        append(escapeText(value))
                        append("</string>\n")
                    }
                    is Int -> {
                        append("    <int name=\"").append(escapedName).append("\" value=\"")
                        append(value).append("\" />\n")
                    }
                    is Long -> {
                        append("    <long name=\"").append(escapedName).append("\" value=\"")
                        append(value).append("\" />\n")
                    }
                    is Float -> {
                        append("    <float name=\"").append(escapedName).append("\" value=\"")
                        append(value).append("\" />\n")
                    }
                    is Boolean -> {
                        append("    <boolean name=\"").append(escapedName).append("\" value=\"")
                        append(value).append("\" />\n")
                    }
                    is Set<*> -> {
                        append("    <set name=\"").append(escapedName).append("\">\n")
                        value.filterIsInstance<String>().forEach { item ->
                            append("        <string>").append(escapeText(item)).append("</string>\n")
                        }
                        append("    </set>\n")
                    }
                }
            }
            append("</map>\n")
        }

        private fun escapeAttr(raw: String): String = buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    '\n' -> append("&#10;")
                    '\r' -> append("&#13;")
                    else -> append(ch)
                }
            }
        }

        private fun escapeText(raw: String): String = buildString(raw.length) {
            for (ch in raw) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(ch)
                }
            }
        }
    }
}
