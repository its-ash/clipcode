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

    private val KEYWORDS = listOf(
        "otp", "one-time password", "one time password", "code", "verification",
        "verify", "passcode", "pass code", "secret", "authentication", "auth code",
        "security code", "login code", "access code", "pin",
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
    private val PHONE_LIKE = Pattern.compile("\\b\\d{10,}\\b")

    private const val KEYWORD_WINDOW = 40

    /**
     * Attempts to extract an OTP code from [text]. [sender] is the SMS
     * originating address or the notification's app/title label.
     */
    fun extract(text: String, sender: String): OtpMatch? {
        if (text.isBlank()) return null
        val normalized = text.trim()
        val lower = normalized.lowercase(Locale.US)

        if (PHONE_LIKE.matcher(normalized).find() && !hasNearbyKeyword(lower, PHONE_LIKE, normalized)) {
            // long digit runs without a keyword nearby are likely phone numbers; keep scanning
            // shorter runs below rather than bailing out entirely.
        }

        val candidates = mutableListOf<Pair<String, Int>>()
        collectMatches(NUMERIC_CODE, normalized, candidates)
        collectMatches(ALPHANUMERIC_CODE, normalized.uppercase(Locale.US), candidates)

        for ((code, index) in candidates.sortedByDescending { it.second == it.second }) {
            if (isExcluded(normalized, lower, code, index)) continue
            val hasKeyword = hasKeywordNear(lower, index)
            val looksStrict = looksStrictCode(code)
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

    private fun hasNearbyKeyword(lower: String, pattern: Pattern, original: String): Boolean {
        val matcher = pattern.matcher(original)
        if (!matcher.find()) return false
        return hasKeywordNear(lower, matcher.start())
    }

    private fun hasKeywordNear(lower: String, index: Int): Boolean {
        val start = (index - KEYWORD_WINDOW).coerceAtLeast(0)
        val end = (index + KEYWORD_WINDOW).coerceAtMost(lower.length)
        val window = lower.substring(start, end)
        return KEYWORDS.any { window.contains(it) }
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
