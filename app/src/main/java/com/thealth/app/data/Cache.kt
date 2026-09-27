package com.thealth.app.data

// Простой TTL-кэш: табы не должны дергать бэк при каждом переключении.
// Мутации инвалидируют свои ключи явно.
object ApiCache {
    private data class E(val at: Long, val v: Any?)
    private val map = mutableMapOf<String, E>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> get(key: String, ttlMs: Long = 60_000, fetch: suspend () -> T): T {
        val now = System.currentTimeMillis()
        (map[key])?.let { if (now - it.at < ttlMs) return it.v as T }
        return fetch().also { map[key] = E(now, it) }
    }

    fun invalidate(vararg keys: String) = keys.forEach { map.remove(it) }
    fun invalidateAll() = map.clear()
}
