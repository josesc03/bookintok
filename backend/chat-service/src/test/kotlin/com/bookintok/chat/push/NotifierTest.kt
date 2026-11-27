package com.bookintok.chat.push

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryDevices : DeviceRepository {
    val tokens = mutableMapOf<String, String>() // token -> uid

    override suspend fun register(uid: String, token: String) {
        tokens[token] = uid
    }

    override suspend fun unregister(uid: String, token: String) {
        tokens.remove(token, uid)
    }

    override suspend fun tokensOf(uid: String) = tokens.filterValues { it == uid }.keys.toList()

    override suspend fun remove(tokens: Collection<String>) {
        tokens.forEach { this.tokens.remove(it) }
    }

    override suspend fun removeAllOf(uid: String) {
        tokens.values.removeIf { it == uid }
    }
}

class NotifierTest {

    private val sent = mutableListOf<Pair<List<String>, Map<String, String>>>()

    @Test
    fun `only users without the app open are notified, on all their devices`() = runTest {
        val devices = InMemoryDevices().apply {
            register("ana", "phone")
            register("ana", "tablet")
            register("luis", "luis-phone")
        }
        val notifier = Notifier(devices, { tokens, data -> sent += tokens to data; emptyList() }, isOnline = { it == "luis" })

        notifier.notify(setOf("ana", "luis"), mapOf("body" to "hola"))

        assertEquals(listOf(listOf("phone", "tablet") to mapOf("body" to "hola")), sent)
    }

    @Test
    fun `tokens rejected by FCM are forgotten`() = runTest {
        val devices = InMemoryDevices().apply {
            register("ana", "old")
            register("ana", "new")
        }
        val notifier = Notifier(devices, { _, _ -> listOf("old") }, isOnline = { false })

        notifier.notify(setOf("ana"), emptyMap())

        assertEquals(listOf("new"), devices.tokensOf("ana"))
    }

    @Test
    fun `a device that signs in with another account moves to it`() = runTest {
        val devices = InMemoryDevices()
        devices.register("ana", "phone")
        devices.register("luis", "phone")
        devices.unregister("ana", "phone") // the old account cannot remove it anymore

        assertEquals(emptyList(), devices.tokensOf("ana"))
        assertEquals(listOf("phone"), devices.tokensOf("luis"))
    }
}
