package com.example.mcp.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class InMemoryCacheServiceTest {
    @Test
    fun `evicts expired entries during writes`() {
        val cache = InMemoryCacheService()

        cache.put("stale", "value-1", Duration.ofMillis(10))
        Thread.sleep(30)
        cache.put("fresh", "value-2", Duration.ofSeconds(1))

        assertNull(cache.get<String>("stale"))
        assertEquals("value-2", cache.get<String>("fresh"))
        assertEquals(1, cache.size())
    }
}
