package io.github.aj4abinjacob.chikku.core.model.settings

/**
 * Minimal JSON reader/writer for settings and backups, so core:model stays a pure JVM module
 * without a serialization dependency. Values map to: object -> Map<String, Any?> (insertion
 * ordered), array -> List<Any?>, string -> String, number -> Long or Double, true/false -> Boolean,
 * null -> null.
 */
object MiniJson {
    /** Nesting limit so a hostile backup file cannot overflow the stack. */
    private const val MAX_DEPTH = 64

    class ParseException(message: String) : IllegalArgumentException(message)

    /** @throws ParseException on malformed input or trailing garbage. */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readValue(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) throw ParseException("trailing data at ${parser.pos}")
        return value
    }

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(value)
            is Int, is Long, is Short, is Byte -> out.append(value)
            is Float -> writeDouble(out, value.toDouble())
            is Double -> writeDouble(out, value)
            is Enum<*> -> writeString(out, value.name)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k.toString())
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    write(out, v)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("unsupported JSON value ${value.javaClass.simpleName}")
        }
    }

    private fun writeDouble(out: StringBuilder, d: Double) {
        require(!d.isNaN() && !d.isInfinite()) { "non-finite number" }
        out.append(d)
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && s[pos].isJsonWhitespace()) pos++
        }

        private fun Char.isJsonWhitespace() = this == ' ' || this == '\n' || this == '\r' || this == '\t'

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseException("nesting too deep")
            skipWhitespace()
            if (atEnd()) throw ParseException("unexpected end")
            return when (val c = s[pos]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw ParseException("unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("bad literal at $pos")
            pos += word.length
            return value
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw ParseException("expected key at $pos")
                val key = readString()
                skipWhitespace()
                expect(':')
                map[key] = readValue(depth + 1)
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw ParseException("expected , or } at ${pos - 1}")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            pos++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list += readValue(depth + 1)
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw ParseException("expected , or ] at ${pos - 1}")
                }
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("unterminated string")
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw ParseException("bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("bad unicode escape")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: throw ParseException("bad unicode escape")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw ParseException("bad escape \\$e")
                        }
                    }
                    c < ' ' -> throw ParseException("control character in string")
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val text = s.substring(start, pos)
            val isInteger = text.none { it == '.' || it == 'e' || it == 'E' }
            return (if (isInteger) text.toLongOrNull() ?: text.toDoubleOrNull() else text.toDoubleOrNull())
                ?: throw ParseException("bad number '$text'")
        }

        private fun peek(): Char? = if (atEnd()) null else s[pos]

        private fun next(): Char? = if (atEnd()) null else s[pos++]

        private fun expect(c: Char) {
            if (next() != c) throw ParseException("expected '$c' at ${pos - 1}")
        }
    }
}
