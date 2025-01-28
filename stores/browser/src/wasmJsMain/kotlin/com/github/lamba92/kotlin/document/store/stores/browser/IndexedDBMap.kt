package com.github.lamba92.kotlin.document.store.stores.browser

import com.github.lamba92.kotlin.document.store.core.PersistentMap
import com.github.lamba92.kotlin.document.store.core.SerializableEntry
import com.github.lamba92.kotlin.document.store.core.UpdateResult
import keyval.del
import keyval.delMany
import keyval.keys
import keyval.set
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A browser-based implementation of the `DataStore` that uses `IndexedDB` for persistent storage.
 *
 * The `BrowserStore` enables web applications to store and manage named maps persistently
 * in a client-side database (IndexedDB). It supports creating, retrieving, and deleting
 * persistent maps, while ensuring thread safety through synchronization mechanisms.
 *
 * Each persistent map is backed by an `IndexedDBMap`, which provides efficient key-value
 * storage and ensures data durability across browser sessions.
 *
 * This implementation is ideal for client-side scenarios where durable, structured storage
 * is required in the browser environment.
 */
public class IndexedDBMap(
    private val name: String,
    private val mutex: Mutex,
) : PersistentMap<String, String> {
    public companion object {
        private const val SEPARATOR = "."

        internal fun buildPrefix(name: String) = "$name$SEPARATOR"
    }

    private val prefixed
        get() = buildPrefix(name)

    private fun String.prefixed() = "$prefixed$this"

    override suspend fun clear(): Unit =
        keys()
            .await<JsArray<JsString>>()
            .toList()
            .filter { it.toString().startsWith(prefixed) }
            .let { delMany(it.toJsArray()).await() }

    override suspend fun size(): Long =
        keys()
            .await<JsArray<JsString>>()
            .toList()
            .filter { it.toString().startsWith(prefixed) }
            .size
            .toLong()

    override suspend fun isEmpty(): Boolean = size() == 0L

    override suspend fun get(key: String): String? = keyval.get(key.prefixed()).await<JsString?>()?.toString()

    override suspend fun put(
        key: String,
        value: String,
    ): String? = mutex.withLock { unsafePut(key, value) }

    private suspend fun IndexedDBMap.unsafePut(
        key: String,
        value: String,
    ): String? {
        val previous = get(key)
        set(key.prefixed(), value.toJsString()).await<JsAny?>()
        return previous
    }

    override suspend fun remove(key: String): String? =
        mutex.withLock {
            val previous = get(key)
            del(key.prefixed()).await<JsAny?>()
            previous
        }

    override suspend fun containsKey(key: String): Boolean = get(key) != null

    override suspend fun update(
        key: String,
        value: String,
        updater: (String) -> String,
    ): UpdateResult<String> =
        mutex.withLock {
            val oldValue = get(key)
            val newValue = oldValue?.let(updater) ?: value
            set(key.prefixed(), newValue.toJsString()).await<JsAny?>()
            UpdateResult(oldValue, newValue)
        }

    override suspend fun getOrPut(
        key: String,
        defaultValue: () -> String,
    ): String =
        mutex.withLock {
            get(key) ?: defaultValue().also { unsafePut(key, it) }
        }

    override fun entries(): Flow<Map.Entry<String, String>> =
        flow {
            keys()
                .await<JsArray<JsString>>()
                .toList()
                .asFlow()
                .filter { it.toString().startsWith(prefixed) }
                .collect { key ->
                    keyval.get(key.toString()).await<JsString?>()?.let { value ->
                        emit(SerializableEntry(key.toString().removePrefix(prefixed), value.toString()))
                    }
                }
        }
}
