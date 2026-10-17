package com.omniforge.apparat.engine.common

/**
 * Pure Kotlin, token-preserving, lossless JSON pretty-printer and minifier.
 *
 * Resolves Audit Issue B14:
 * - Preserves numbers verbatim as raw character tokens without converting to Double/Long
 *   (no scientific notation corruption or precision loss on 20+ digit integers/decimals).
 * - Preserves unescaped slashes without converting '/' to '\/'.
 * - Preserves string content, unicode escapes, and escape sequences accurately.
 * - Formats with 4 spaces indentation when pretty=true, 0 spaces when pretty=false.
 * - Reports 1-based Line and Column on syntax errors in IllegalArgumentException.
 */
object JsonFormatter {

    fun format(jsonString: String, pretty: Boolean = true): Result<String> = runCatching {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("JSON input is empty")
        }

        val tokenizer = Tokenizer(jsonString)
        val tokens = tokenizer.tokenize()
        if (tokens.isEmpty()) {
            throw IllegalArgumentException("JSON input contains only whitespace")
        }

        val parser = Parser(tokens, pretty)
        val formatted = parser.parseAndFormat()
        formatted
    }

    private sealed class Token(val line: Int, val col: Int) {
        class LBrace(line: Int, col: Int) : Token(line, col)
        class RBrace(line: Int, col: Int) : Token(line, col)
        class LBracket(line: Int, col: Int) : Token(line, col)
        class RBracket(line: Int, col: Int) : Token(line, col)
        class Colon(line: Int, col: Int) : Token(line, col)
        class Comma(line: Int, col: Int) : Token(line, col)
        class Str(val rawText: String, line: Int, col: Int) : Token(line, col)
        class Num(val rawText: String, line: Int, col: Int) : Token(line, col)
        class Bool(val value: Boolean, line: Int, col: Int) : Token(line, col)
        class NullLiteral(line: Int, col: Int) : Token(line, col)
    }

    private class Tokenizer(private val input: String) {
        private var idx = 0
        private var line = 1
        private var col = 1

        private fun hasNext(): Boolean = idx < input.length
        private fun peek(): Char = input[idx]

        private fun advance(): Char {
            val c = input[idx++]
            if (c == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            return c
        }

        fun tokenize(): List<Token> {
            val tokens = mutableListOf<Token>()
            while (hasNext()) {
                val c = peek()
                if (c.isWhitespace()) {
                    advance()
                    continue
                }

                val curLine = line
                val curCol = col

                when (c) {
                    '{' -> { advance(); tokens.add(Token.LBrace(curLine, curCol)) }
                    '}' -> { advance(); tokens.add(Token.RBrace(curLine, curCol)) }
                    '[' -> { advance(); tokens.add(Token.LBracket(curLine, curCol)) }
                    ']' -> { advance(); tokens.add(Token.RBracket(curLine, curCol)) }
                    ':' -> { advance(); tokens.add(Token.Colon(curLine, curCol)) }
                    ',' -> { advance(); tokens.add(Token.Comma(curLine, curCol)) }
                    '"' -> { tokens.add(readString()) }
                    '-', in '0'..'9' -> { tokens.add(readNumber()) }
                    't', 'f' -> { tokens.add(readBoolean()) }
                    'n' -> { tokens.add(readNull()) }
                    else -> {
                        throw IllegalArgumentException("Line $curLine, Col $curCol: Unexpected character '$c'")
                    }
                }
            }
            return tokens
        }

        private fun readString(): Token.Str {
            val startLine = line
            val startCol = col
            val sb = StringBuilder()
            advance() // Consume opening quote
            sb.append('"')

            var closed = false
            while (hasNext()) {
                val c = advance()
                when (c) {
                    '\\' -> {
                        if (!hasNext()) {
                            throw IllegalArgumentException("Line $startLine, Col $startCol: Unterminated escape sequence in string")
                        }
                        val escape = advance()
                        when (escape) {
                            '"', '\\', 'b', 'f', 'n', 'r', 't' -> {
                                sb.append('\\').append(escape)
                            }
                            '/' -> {
                                // Slashes do not require escaping in standard JSON; preserve unescaped
                                sb.append('/')
                            }
                            'u' -> {
                                sb.append('\\').append('u')
                                for (i in 0 until 4) {
                                    if (!hasNext()) {
                                        throw IllegalArgumentException("Line $line, Col $col: Incomplete unicode escape sequence")
                                    }
                                    val hex = advance()
                                    if (hex !in '0'..'9' && hex !in 'a'..'f' && hex !in 'A'..'F') {
                                        throw IllegalArgumentException("Line $line, Col $col: Invalid hex character '$hex' in unicode escape")
                                    }
                                    sb.append(hex)
                                }
                            }
                            else -> {
                                throw IllegalArgumentException("Line $line, Col $col: Invalid escape character '\\$escape'")
                            }
                        }
                    }
                    '"' -> {
                        sb.append('"')
                        closed = true
                        break
                    }
                    '\n', '\r' -> {
                        throw IllegalArgumentException("Line $startLine, Col $startCol: Unterminated string literal (newline in string)")
                    }
                    else -> {
                        sb.append(c)
                    }
                }
            }

            if (!closed) {
                throw IllegalArgumentException("Line $startLine, Col $startCol: Unterminated string literal")
            }

            return Token.Str(sb.toString(), startLine, startCol)
        }

        private fun readNumber(): Token.Num {
            val startLine = line
            val startCol = col
            val startIdx = idx

            // Negative sign
            if (peek() == '-') {
                advance()
                if (!hasNext() || !peek().isDigit()) {
                    throw IllegalArgumentException("Line $startLine, Col $startCol: Invalid number: expected digit after '-'")
                }
            }

            // Integer part
            if (peek() == '0') {
                advance()
                if (hasNext() && peek().isDigit()) {
                    throw IllegalArgumentException("Line $startLine, Col $startCol: Invalid number: leading zeros are not allowed")
                }
            } else {
                while (hasNext() && peek().isDigit()) {
                    advance()
                }
            }

            // Fractional part
            if (hasNext() && peek() == '.') {
                advance()
                if (!hasNext() || !peek().isDigit()) {
                    throw IllegalArgumentException("Line $startLine, Col $startCol: Invalid number: expected digit after decimal point")
                }
                while (hasNext() && peek().isDigit()) {
                    advance()
                }
            }

            // Exponent part
            if (hasNext() && (peek() == 'e' || peek() == 'E')) {
                advance()
                if (hasNext() && (peek() == '+' || peek() == '-')) {
                    advance()
                }
                if (!hasNext() || !peek().isDigit()) {
                    throw IllegalArgumentException("Line $startLine, Col $startCol: Invalid number: expected digit in exponent")
                }
                while (hasNext() && peek().isDigit()) {
                    advance()
                }
            }

            val rawNum = input.substring(startIdx, idx)
            return Token.Num(rawNum, startLine, startCol)
        }

        private fun readBoolean(): Token.Bool {
            val startLine = line
            val startCol = col
            return if (input.startsWith("true", idx)) {
                repeat(4) { advance() }
                Token.Bool(true, startLine, startCol)
            } else if (input.startsWith("false", idx)) {
                repeat(5) { advance() }
                Token.Bool(false, startLine, startCol)
            } else {
                throw IllegalArgumentException("Line $startLine, Col $startCol: Unrecognized literal, expected boolean")
            }
        }

        private fun readNull(): Token.NullLiteral {
            val startLine = line
            val startCol = col
            return if (input.startsWith("null", idx)) {
                repeat(4) { advance() }
                Token.NullLiteral(startLine, startCol)
            } else {
                throw IllegalArgumentException("Line $startLine, Col $startCol: Unrecognized literal, expected null")
            }
        }
    }

    private class Parser(
        private val tokens: List<Token>,
        private val pretty: Boolean
    ) {
        private var idx = 0
        private val out = StringBuilder()
        private var indentLevel = 0

        private fun hasNext(): Boolean = idx < tokens.size
        private fun peek(): Token = tokens[idx]
        private fun advance(): Token = tokens[idx++]

        private fun indent() {
            if (pretty) {
                out.append("    ".repeat(indentLevel))
            }
        }

        private fun newline() {
            if (pretty) {
                out.append("\n")
            }
        }

        fun parseAndFormat(): String {
            parseValue()
            if (hasNext()) {
                val extra = peek()
                throw IllegalArgumentException("Line ${extra.line}, Col ${extra.col}: Unexpected token after root JSON element")
            }
            return out.toString()
        }

        private fun parseValue() {
            if (!hasNext()) {
                throw IllegalArgumentException("Unexpected end of JSON input")
            }

            when (val t = peek()) {
                is Token.LBrace -> parseObject()
                is Token.LBracket -> parseArray()
                is Token.Str -> { advance(); out.append(t.rawText) }
                is Token.Num -> { advance(); out.append(t.rawText) }
                is Token.Bool -> { advance(); out.append(if (t.value) "true" else "false") }
                is Token.NullLiteral -> { advance(); out.append("null") }
                else -> {
                    throw IllegalArgumentException("Line ${t.line}, Col ${t.col}: Unexpected token '${t.javaClass.simpleName}'")
                }
            }
        }

        private fun parseObject() {
            val open = advance() as Token.LBrace
            out.append('{')

            if (hasNext() && peek() is Token.RBrace) {
                advance()
                out.append('}')
                return
            }

            indentLevel++
            newline()

            var first = true
            while (hasNext()) {
                if (peek() is Token.RBrace) {
                    if (first) {
                        // Empty object already handled above
                    }
                    break
                }

                if (!first) {
                    val token = peek()
                    if (token !is Token.Comma) {
                        throw IllegalArgumentException("Line ${token.line}, Col ${token.col}: Expected ',' before next object entry")
                    }
                    advance() // consume comma
                    out.append(',')
                    newline()
                }

                // Check for trailing comma: e.g. { "a": 1, }
                if (hasNext() && peek() is Token.RBrace) {
                    val rBrace = peek()
                    throw IllegalArgumentException("Line ${rBrace.line}, Col ${rBrace.col}: Trailing comma is not allowed in JSON objects")
                }

                indent()

                // Key must be a String
                if (!hasNext() || peek() !is Token.Str) {
                    val badToken = if (hasNext()) peek() else open
                    throw IllegalArgumentException("Line ${badToken.line}, Col ${badToken.col}: Object keys must be double-quoted strings")
                }
                val keyToken = advance() as Token.Str
                out.append(keyToken.rawText)

                // Colon
                if (!hasNext() || peek() !is Token.Colon) {
                    val badToken = if (hasNext()) peek() else keyToken
                    throw IllegalArgumentException("Line ${badToken.line}, Col ${badToken.col}: Expected ':' after object key")
                }
                advance() // consume colon
                if (pretty) {
                    out.append(": ")
                } else {
                    out.append(':')
                }

                // Value
                parseValue()
                first = false
            }

            if (!hasNext() || peek() !is Token.RBrace) {
                throw IllegalArgumentException("Line ${open.line}, Col ${open.col}: Unclosed object, expected '}'")
            }
            advance() // consume '}'

            indentLevel--
            newline()
            indent()
            out.append('}')
        }

        private fun parseArray() {
            val open = advance() as Token.LBracket
            out.append('[')

            if (hasNext() && peek() is Token.RBracket) {
                advance()
                out.append(']')
                return
            }

            indentLevel++
            newline()

            var first = true
            while (hasNext()) {
                if (peek() is Token.RBracket) {
                    break
                }

                if (!first) {
                    val token = peek()
                    if (token !is Token.Comma) {
                        throw IllegalArgumentException("Line ${token.line}, Col ${token.col}: Expected ',' before next array element")
                    }
                    advance() // consume comma
                    out.append(',')
                    newline()
                }

                // Check for trailing comma: e.g. [ 1, 2, ]
                if (hasNext() && peek() is Token.RBracket) {
                    val rBracket = peek()
                    throw IllegalArgumentException("Line ${rBracket.line}, Col ${rBracket.col}: Trailing comma is not allowed in JSON arrays")
                }

                indent()
                parseValue()
                first = false
            }

            if (!hasNext() || peek() !is Token.RBracket) {
                throw IllegalArgumentException("Line ${open.line}, Col ${open.col}: Unclosed array, expected ']'")
            }
            advance() // consume ']'

            indentLevel--
            newline()
            indent()
            out.append(']')
        }
    }
}
