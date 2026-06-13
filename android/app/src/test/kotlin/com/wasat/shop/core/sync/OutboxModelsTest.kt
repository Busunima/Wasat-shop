package com.wasat.shop.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Payload outbox-операции должен переживать кодирование/декодирование без потерь. */
class OutboxModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `OrderStatusOp round-trips through JSON`() {
        val op = OrderStatusOp(orderId = "ord-42", status = "SHIPPED", trackingNo = "TRK-9")
        val decoded = json.decodeFromString<OrderStatusOp>(json.encodeToString(op))
        assertEquals(op, decoded)
    }

    @Test
    fun `OrderStatusOp без трека кодируется и читается`() {
        val op = OrderStatusOp(orderId = "ord-1", status = "CONFIRMED")
        val decoded = json.decodeFromString<OrderStatusOp>(json.encodeToString(op))
        assertEquals(null, decoded.trackingNo)
        assertEquals("CONFIRMED", decoded.status)
    }
}
