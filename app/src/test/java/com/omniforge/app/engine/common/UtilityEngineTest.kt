package com.omniforge.app.engine.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class UtilityEngineTest {

    @Test
    fun testDigestVerification() {
        val input = "Hello, Digital Pocket Knife!"
        val sha256 = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(sha256.isNotEmpty())
        assertEquals(64, sha256.length)

        val md5 = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(32, md5.length)

        val sha1 = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(40, sha1.length)

        val sha512 = MessageDigest.getInstance("SHA-512").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(128, sha512.length)
    }

    @Test
    fun testCaseConversion() {
        val original = "hello_world_test"
        assertEquals("HELLO_WORLD_TEST", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.UPPERCASE))
        assertEquals("hello_world_test", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.LOWERCASE))
        assertEquals("Hello World Test", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.TITLE_CASE))
        assertEquals("helloWorldTest", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.CAMEL_CASE))
        assertEquals("hello-world-test", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.KEBAB_CASE))
    }
}
