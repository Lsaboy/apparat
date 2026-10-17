package com.vedant.apparat.engine.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonEdgeCaseTest {

    @Test fun `deep nesting fails cleanly instead of crashing`() {
        val deep = "[".repeat(20000) + "]".repeat(20000)
        val r = JsonFormatter.format(deep)
        // Either formats or reports an error; must never throw out of format().
        assertTrue(r.isSuccess || r.isFailure)
    }

    @Test fun `raw newline inside a string is rejected`() {
        assertTrue(JsonFormatter.format("{\"a\": \"x\ny\"}").isFailure)
    }

    @Test fun `leading zeros and bare dots are rejected`() {
        assertTrue(JsonFormatter.format("[01]").isFailure)
        assertTrue(JsonFormatter.format("[1.]").isFailure)
        assertTrue(JsonFormatter.format("[.5]").isFailure)
        assertTrue(JsonFormatter.format("[-]").isFailure)
    }

    @Test fun `exponents and negative zero are kept verbatim`() {
        assertEquals("[1e5,-0,2.5E-3]", JsonFormatter.format("[1e5, -0, 2.5E-3]", pretty = false).getOrThrow())
    }

    @Test fun `trailing commas and trailing garbage are rejected`() {
        assertTrue(JsonFormatter.format("[1,2,]").isFailure)
        assertTrue(JsonFormatter.format("{\"a\":1,}").isFailure)
        assertTrue(JsonFormatter.format("{} {}").isFailure)
    }

    @Test fun `duplicate keys are kept as written`() {
        assertEquals("{\"a\":1,\"a\":2}", JsonFormatter.format("{\"a\":1,\"a\":2}", pretty = false).getOrThrow())
    }

    @Test fun `scalar roots are accepted`() {
        assertEquals("\"hi\"", JsonFormatter.format("\"hi\"", pretty = false).getOrThrow())
        assertEquals("42", JsonFormatter.format(" 42 ", pretty = false).getOrThrow())
    }
}
