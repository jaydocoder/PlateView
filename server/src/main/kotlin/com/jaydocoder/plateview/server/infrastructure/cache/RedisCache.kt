package com.jaydocoder.plateview.server.infrastructure.cache

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey
import java.net.URI
import redis.clients.jedis.JedisPooled

internal val RedisCacheKey = AttributeKey<RedisCache>("redisCache")

internal class RedisCache(private val client: JedisPooled) {
    fun get(key: String): String? = runCatching { client.get(key) }.getOrNull()

    fun put(key: String, value: String, ttlSeconds: Long) {
        runCatching { client.setex(key, ttlSeconds, value) }
    }

    fun close() = client.close()
}

internal fun Application.configureRedisCache() {
    val url = environment.config.propertyOrNull("cache.redisUrl")?.getString()?.trim().orEmpty()
    if (url.isBlank()) return
    val cache = RedisCache(JedisPooled(URI(url)))
    attributes.put(RedisCacheKey, cache)
    monitor.subscribe(ApplicationStopped) { cache.close() }
}
