package com.vedant.apparat.engine.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonFormatterTest {

    @Test
    fun testLargeNumbersPreservedWithoutPrecisionLoss() {
        // 30-digit integer and high-precision decimal
        val rawNumber = "123456789012345678901234567890"
        val rawDecimal = "0.123456789012345678901234567890"
        val input = """{"largeInt": $rawNumber, "largeDec": $rawDecimal}"""

        val minified = JsonFormatter.format(input, pretty = false).getOrThrow()
        assertEquals("""{"largeInt":$rawNumber,"largeDec":$rawDecimal}""", minified)

        val pretty = JsonFormatter.format(input, pretty = true).getOrThrow()
        assertTrue(pretty.contains("\"largeInt\": $rawNumber"))
        assertTrue(pretty.contains("\"largeDec\": $rawDecimal"))
    }

    @Test
    fun testSlashesPreservedUnescaped() {
        val input = """{"url": "https://example.com/api/v1/resource"}"""
        val result = JsonFormatter.format(input, pretty = false).getOrThrow()
        assertEquals("""{"url":"https://example.com/api/v1/resource"}""", result)

        val inputWithEscapedSlash = """{"url": "https:\/\/example.com\/api"}"""
        val unescapedResult = JsonFormatter.format(inputWithEscapedSlash, pretty = false).getOrThrow()
        assertEquals("""{"url":"https://example.com/api"}""", unescapedResult)
    }

    @Test
    fun testNestedStructuresAndIndentation() {
        val input = """
        {
          "name": "Apparat",
          "features": ["pdf", "image", "video"],
          "meta": {
            "active": true,
            "version": 1,
            "owner": null
          }
        }
        """.trimIndent()

        val pretty = JsonFormatter.format(input, pretty = true).getOrThrow()
        val expected = """
        {
            "name": "Apparat",
            "features": [
                "pdf",
                "image",
                "video"
            ],
            "meta": {
                "active": true,
                "version": 1,
                "owner": null
            }
        }
        """.trimIndent()
        assertEquals(expected, pretty)

        val minified = JsonFormatter.format(input, pretty = false).getOrThrow()
        val expectedMinified = """{"name":"Apparat","features":["pdf","image","video"],"meta":{"active":true,"version":1,"owner":null}}"""
        assertEquals(expectedMinified, minified)
    }

    @Test
    fun testEmptyObjectsAndArrays() {
        val input = """{"emptyObj": {}, "emptyArr": []}"""
        val minified = JsonFormatter.format(input, pretty = false).getOrThrow()
        assertEquals("""{"emptyObj":{},"emptyArr":[]}""", minified)

        val pretty = JsonFormatter.format(input, pretty = true).getOrThrow()
        val expectedPretty = """
        {
            "emptyObj": {},
            "emptyArr": []
        }
        """.trimIndent()
        assertEquals(expectedPretty, pretty)
    }

    @Test
    fun testStringEscapes() {
        val input = """{"escaped": "Line 1\nLine 2\tTabbed \"Quotes\" and \\Backslash\\"}"""
        val result = JsonFormatter.format(input, pretty = false).getOrThrow()
        assertEquals("""{"escaped":"Line 1\nLine 2\tTabbed \"Quotes\" and \\Backslash\\"}""", result)
    }

    @Test
    fun testUnicodeEscapes() {
        val input = """{"unicode": "\u0041\u0042\u0043"}""" // ABC
        val result = JsonFormatter.format(input, pretty = false).getOrThrow()
        assertEquals("""{"unicode":"\u0041\u0042\u0043"}""", result)
    }

    @Test
    fun testRootArray() {
        val input = """[1, 2, "three", true, null]"""
        val pretty = JsonFormatter.format(input, pretty = true).getOrThrow()
        val expected = """
        [
            1,
            2,
            "three",
            true,
            null
        ]
        """.trimIndent()
        assertEquals(expected, pretty)
    }

    @Test
    fun testSyntaxErrorLineAndColumnReporting() {
        // Missing comma
        val missingComma = """
        {
            "a": 1
            "b": 2
        }
        """.trimIndent()
        val res1 = JsonFormatter.format(missingComma)
        assertTrue(res1.isFailure)
        val err1 = res1.exceptionOrNull()?.message ?: ""
        assertTrue(err1.startsWith("Line 3, Col"))
        assertTrue(err1.contains("Expected ','"))

        // Trailing comma in object
        val trailingComma = """{"a": 1,}"""
        val res2 = JsonFormatter.format(trailingComma)
        assertTrue(res2.isFailure)
        val err2 = res2.exceptionOrNull()?.message ?: ""
        assertTrue(err2.startsWith("Line 1, Col"))
        assertTrue(err2.contains("Trailing comma"))

        // Unclosed string
        val unclosedStr = """{"key": "unclosed}"""
        val res3 = JsonFormatter.format(unclosedStr)
        assertTrue(res3.isFailure)
        val err3 = res3.exceptionOrNull()?.message ?: ""
        assertTrue(err3.startsWith("Line 1, Col"))
        assertTrue(err3.contains("Unterminated string"))

        // Unexpected token after root
        val extraToken = """{"a": 1} [1, 2]"""
        val res4 = JsonFormatter.format(extraToken)
        assertTrue(res4.isFailure)
        val err4 = res4.exceptionOrNull()?.message ?: ""
        assertTrue(err4.contains("Unexpected token after root"))
    }
}
