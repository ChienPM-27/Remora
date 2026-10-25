package com.remora.util

/**
 * Masks sensitive values found in AI-extracted text (payment card numbers, contextual OTP
 * codes, labeled passwords/PINs) before anything is persisted to Room, embedded, or logged.
 *
 * Limitation (accepted): redaction runs after OCR/extraction, so secrets visible in the
 * image itself still reach the AI gateway with the extraction request. This protects
 * on-device storage, embeddings and logs only; on-device pre-upload redaction is future work.
 */
object SensitiveDataRedactor {

    const val MASK = "[REDACTED]"

    // Label words that introduce a secret on the same line or alone on their own line.
    private const val LABELS =
        """password|passcode|mật\s*khẩu|mat\s*khau|pin|otp|mã\s*xác\s*(?:thực|thức)|xác\s*(?:thực|thức)|ma\s*xac\s*thuc|xac\s*thuc|verification\s*code|security\s*code"""

    private val labeledValue = Regex("""(?i)\b(?:$LABELS)\b\s*[:=\-]\s*(\S.*)""")
    private val labelOnlyLine = Regex("""(?i)^\s*(?:$LABELS)\b\s*[:=\-]?\s*$""")
    private val otpKeyword = Regex(
        """(?i)(?:^|\W)(?:otp|mã\s*xác\s*(?:thực|thức)|xác\s*(?:thực|thức)|ma\s*xac\s*thuc|xac\s*thuc|verification\s*code|security\s*code)(?:$|\W)"""
    )
    private val digitRun = Regex("""\b\d{4,8}\b""")
    // "Pin: 85%" on a phone screenshot is battery level, not a PIN.
    private val pinLabel = Regex("""(?i)^pin$""")
    private val shortNumberOrPercent = Regex("""^\d{1,3}%?$""")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        return redactLabeledSecrets(redactCardNumbers(text))
    }

    /**
     * Scans maximal digit runs (single ` .,-` separators allowed between digits) and masks any
     * 13-19 digit window that passes Luhn, longest and leftmost first. Luhn keeps ordinary
     * study numbers (IDs, phones, years) visible.
     */
    internal fun redactCardNumbers(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (!text[i].isDigit()) {
                sb.append(text[i])
                i++
                continue
            }
            var j = i
            val digitPositions = mutableListOf<Int>()
            while (j < text.length) {
                if (text[j].isDigit()) {
                    digitPositions += j
                    j++
                } else if (text[j] in " .,-" && j + 1 < text.length && text[j + 1].isDigit()) {
                    j++
                } else {
                    break
                }
            }
            sb.append(maskLuhnWindow(text.substring(i, j), digitPositions.map { it - i }))
            i = j
        }
        return sb.toString()
    }

    /** Replaces the first Luhn-valid 13-19 digit window with [MASK], or returns the run unchanged. */
    private fun maskLuhnWindow(run: String, digitPositions: List<Int>): String {
        for (len in 19 downTo 13) {
            for (start in 0..digitPositions.size - len) {
                val digits = buildString {
                    for (k in start until start + len) append(run[digitPositions[k]])
                }
                if (luhnValid(digits)) {
                    val from = digitPositions[start]
                    val to = digitPositions[start + len - 1]
                    return run.substring(0, from) + MASK + run.substring(to + 1)
                }
            }
        }
        return run
    }

    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** Per-line pass: masks labeled values, labeled next lines, and digits on OTP lines. */
    internal fun redactLabeledSecrets(text: String): String {
        val lines = text.split(Regex("\r?\n")).toMutableList()
        for (i in lines.indices) {
            var line = labeledValue.replace(lines[i]) { m ->
                val label = m.value.substring(0, m.groups[1]!!.range.first)
                val value = m.groups[1]!!.value
                val bareLabel = label.trim().trimEnd(':', '=', '-').trim()
                if (pinLabel.matches(bareLabel) && shortNumberOrPercent.matches(value)) m.value
                else label + MASK
            }
            if (otpKeyword.containsMatchIn(line)) {
                line = digitRun.replace(line, MASK)
            }
            lines[i] = line
            if (labelOnlyLine.matches(line)) {
                var next = -1
                for (k in i + 1 until lines.size) {
                    if (lines[k].isNotBlank()) {
                        next = k
                        break
                    }
                }
                if (next != -1 && lines[next].length <= 64 && !labelOnlyLine.matches(lines[next])) {
                    lines[next] = MASK
                }
            }
        }
        return lines.joinToString("\n")
    }
}
