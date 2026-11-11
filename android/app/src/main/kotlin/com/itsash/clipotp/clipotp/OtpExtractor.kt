package com.itsash.clipotp.clipotp

import java.util.Locale
import java.util.regex.Pattern

/** Result of a successful OTP extraction. */
data class OtpMatch(val code: String, val sender: String)

/**
 * On-device regex/heuristic OTP extraction engine.
 *
 * Matches 4-8 character numeric or alphanumeric codes near an OTP-context
 * keyword, or codes that look strict enough (mixed digits/letters, or a
 * digit run bounded by non-alphanumeric chars) to stand on their own.
 * Actively excludes currency amounts, dates, phone numbers, and order IDs.
 */
object OtpExtractor {

    // Whole words only: "pin" must not match "shopping", nor "code" match "decode".
    private val KEYWORD = Pattern.compile(
        "\\b(otp|one[- ]time pass(word|code)?|codes?|verification|verify|passcode|pass code|" +
            "secret|authentication|auth code|security code|login code|access code|pin)\\b",
        Pattern.CASE_INSENSITIVE,
    )

    // 4-8 digits, or 4-8 alphanumeric with at least one digit (mixed codes like "A3F9K2").
    private val NUMERIC_CODE = Pattern.compile("\\b(\\d{4,8})\\b")
    private val ALPHANUMERIC_CODE = Pattern.compile("\\b([A-Z0-9]{4,8})\\b")

    // Exclusions: currency, dates, phone-like runs, order/tracking ids.
    private val CURRENCY_NEARBY = Pattern.compile("[$₹€£]\\s?\\d|\\d\\s?(usd|inr|eur|gbp)", Pattern.CASE_INSENSITIVE)
    private val DATE_PATTERN = Pattern.compile(
        "\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b|\\b\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}\\b",
    )
    private val ORDER_ID_NEARBY = Pattern.compile("\\b(order|awb|tracking|invoice|ref(erence)?)\\b\\s*(id|no\\.?|#)?", Pattern.CASE_INSENSITIVE)

    private const val KEYWORD_WINDOW = 40

    /**
     * Attempts to extract an OTP code from [text]. [sender] is the SMS
     * originating address or the notification's app/title label.
     *
     * With [requireKeyword] a code counts only next to an OTP keyword. Use it
     * for notifications, which come from every app: a bare 6-digit number in
     * a music, fitness or shopping notification is not an OTP.
     */
    fun extract(text: String, sender: String, requireKeyword: Boolean = false): OtpMatch? {
        if (text.isBlank()) return null
        val normalized = text.trim()
        val lower = normalized.lowercase(Locale.US)

        val candidates = mutableListOf<Pair<String, Int>>()
        collectMatches(NUMERIC_CODE, normalized, candidates)
        collectMatches(ALPHANUMERIC_CODE, normalized.uppercase(Locale.US), candidates)

        for ((code, index) in candidates) {
            // A code needs a digit: plain words like "YOUR" or "CODE" are not codes.
            if (code.none { it.isDigit() }) continue
            if (isExcluded(normalized, lower, code, index)) continue
            val hasKeyword = hasKeywordNear(lower, index)
            val looksStrict = !requireKeyword && looksStrictCode(code)
            if (hasKeyword || looksStrict) {
                return OtpMatch(code = code, sender = sender.ifBlank { "Unknown" })
            }
        }
        return null
    }

    private fun collectMatches(pattern: Pattern, haystack: String, out: MutableList<Pair<String, Int>>) {
        val matcher = pattern.matcher(haystack)
        while (matcher.find()) {
            out.add(matcher.group(1) to matcher.start(1))
        }
    }

    private fun hasKeywordNear(lower: String, index: Int): Boolean {
        val start = (index - KEYWORD_WINDOW).coerceAtLeast(0)
        val end = (index + KEYWORD_WINDOW).coerceAtMost(lower.length)
        // Transparent bounds: \b sees the text around the window, so a word cut
        // at the window edge ("s|pin") is not taken for a keyword.
        return KEYWORD.matcher(lower).region(start, end).useTransparentBounds(true).find()
    }

    /** A code is "strict" if it mixes letters and digits, or is exactly 6 digits (the most common OTP length). */
    private fun looksStrictCode(code: String): Boolean {
        val hasDigit = code.any { it.isDigit() }
        val hasLetter = code.any { it.isLetter() }
        if (hasDigit && hasLetter) return true
        return code.length == 6 && hasDigit
    }

    private fun isExcluded(original: String, lower: String, code: String, index: Int): Boolean {
        val start = (index - KEYWORD_WINDOW).coerceAtLeast(0)
        val end = (index + KEYWORD_WINDOW).coerceAtMost(original.length)
        val window = original.substring(start, end)
        val windowLower = lower.substring(start, end)

        if (CURRENCY_NEARBY.matcher(windowLower).find()) return true
        if (DATE_PATTERN.matcher(window).find() && window.contains(code)) {
            // The code itself is part of a date-like token.
            if (DATE_PATTERN.matcher(code).find()) return true
        }
        if (ORDER_ID_NEARBY.matcher(windowLower).find() && !hasKeywordNear(lower, index)) return true
        if (code.length >= 10) return true // treat as phone/tracking number, never an OTP
        return false
    }
}
