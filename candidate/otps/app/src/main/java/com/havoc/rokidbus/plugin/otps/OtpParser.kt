package com.havoc.rokidbus.plugin.otps

/**
 * Precision-first OTP classifier.
 *
 * A candidate is never accepted just because it is a short number. It must be
 * close to an authentication phrase, survive card/date/amount/reference
 * exclusions, and beat every other candidate in the same notification.
 */
object OtpParser {
    private data class Candidate(
        val raw: String,
        val start: Int,
        val end: Int,
    ) {
        val normalized: String = if (raw.all { it.isDigit() || it.isWhitespace() }) {
            raw.filter(Char::isDigit)
        } else {
            raw.replace(" ", "").uppercase()
        }
        val numeric: Boolean = normalized.all(Char::isDigit)
    }

    private data class RulePack(
        val authenticationPhrases: List<Regex>,
        val secretWarnings: List<Regex>,
    )

    private data class PhraseMatch(val start: Int, val end: Int)

    private val rulePacks = listOf(
        RulePack(
            authenticationPhrases = regexes(
                """(?:ваш|одноразовый|проверочный|секретный)\s+(?:одноразовый\s+)?(?:код|пароль)""",
                """(?:код|пароль)\s+(?:подтверждения|верификации|проверки|безопасности|из\s+(?:sms|смс)|для\s+(?:входа|авторизации|регистрации|платежа|перевода|операции))""",
                """(?:код|пароль)\s+для\s+подтверждения\s+(?:входа|регистрации|платежа|перевода|операции)""",
                """для\s+подтверждения(?:\s+(?:входа|регистрации|платежа|перевода|операции))?""",
                """(?:используйте|введите|укажите)\s+(?:этот\s+)?код""",
                """(?:ваш\s+)?код\s*(?:[:—-]|равен|это)""",
                """одноразовый\s+(?:код|пароль)""",
            ),
            secretWarnings = regexes(
                """никому\s+не\s+(?:сообщайте|передавайте|показывайте)""",
                """не\s+делитесь\s+(?:этим\s+)?кодом""",
            ),
        ),
        RulePack(
            authenticationPhrases = regexes(
                """(?:your|the)\s+(?:(?:one[- ]time|verification|confirmation|security|authentication|login|sign[- ]in)\s+)+(?:code|password|passcode|pin)""",
                """(?:verification|confirmation|authentication|login|sign[- ]in)\s+(?:code|passcode)""",
                """(?:your\s+)?(?:code|passcode)\s*(?:is|:)""",
                """(?:use|enter|type)\s+(?:the\s+)?(?:code|passcode)""",
                """(?:otp|one[- ]time password)\s*(?:is|:)""",
                """code\s+otp\s*(?:is|:)""",
            ),
            secretWarnings = regexes(
                """(?:do\s+not|don't|never)\s+(?:share|tell|forward)""",
                """keep\s+(?:this|the)\s+code\s+(?:private|secret)""",
            ),
        ),
        RulePack(
            authenticationPhrases = regexes(
                """(?:votre|le)\s+code\s+(?:de\s+)?(?:vérification|confirmation|sécurité|authentification|connexion)""",
                """code\s+(?:de\s+)?(?:vérification|confirmation|authentification)""",
                """votre\s+code\s*(?:est|:)""",
                """(?:saisissez|utilisez|entrez)\s+(?:le\s+)?code""",
                """mot\s+de\s+passe\s+(?:à|a)\s+usage\s+unique""",
                """code\s+(?:à|a)\s+usage\s+unique""",
            ),
            secretWarnings = regexes(
                """ne\s+(?:partagez|communiquez)\s+(?:jamais|pas)""",
                """gardez\s+(?:ce|le)\s+code\s+(?:secret|confidentiel)""",
            ),
        ),
    )

    private val candidatePatterns = listOf(
        Regex("""(?<![\p{L}\p{N}])(?:\d{3}[ -]\d{3}|\d{4,8})(?![\p{L}\p{N}])"""),
        Regex(
            """(?<![\p{L}\p{N}])(?=[A-Za-z0-9-]{4,12}(?![\p{L}\p{N}]))(?=[A-Za-z0-9-]*\d)(?=[A-Za-z0-9-]*[A-Za-z])[A-Za-z0-9]+(?:-[A-Za-z0-9]+)?""",
        ),
    )
    private val weakCodeWord = Regex(
        """(?iu)(?<![\p{L}\p{N}])(?:code|passcode|otp|pin|код|пароль)(?![\p{L}\p{N}])""",
    )
    private val maskedAccount = Regex(
        """(?iu)(?:card|account|carte|compte|карта|сч[её]т|оканчива(?:ется|ющейся))[^\n]{0,18}(?:[*•xх]{2,}|ending|finissant)\s*$""",
    )
    private val commerceOnly = Regex(
        """(?iu)(?:order|tracking|shipment|parcel|booking|reservation|invoice|commande|colis|réservation|facture|заказ|отправлен|доставка|бронь|накладная|чек|операци[ия])""",
    )
    private val currency = Regex(
        """(?iu)(?:[$€£₽₸]|\b(?:rub|rur|usd|eur|gbp|kzt|руб|тенге)\b)""",
    )
    private val nonAuthenticationCode = Regex(
        """(?iu)(?:promo(?:tional)?|coupon|discount|referral|postal|zip|area|error|status|tracking|booking|reservation|order|invoice)\s+(?:confirmation\s+)?code|code\s+(?:promo|postal|erreur|statut|suivi|réservation|commande)|(?:промокод|код\s+(?:купона|скидки|ошибки|статуса|отслеживания|брони|заказа))""",
    )
    private val urlMarker = Regex("""(?iu)(?:https?://|www\.|\b[a-z0-9.-]+\.(?:com|ru|fr|net|org)/)""")
    private val dateOrTime = Regex(
        """\d{1,4}[./:-]\d{1,2}(?:[./:-]\d{1,4})?""",
    )

    fun detect(vararg notificationParts: CharSequence?): OtpDetection? {
        val text = notificationParts
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .joinToString("\n")
            .replace('\u00A0', ' ')
        if (text.isBlank()) return null

        val phraseMatches = rulePacks.flatMap { pack ->
            pack.authenticationPhrases.flatMap { regex ->
                regex.findAll(text).map { PhraseMatch(it.range.first, it.range.last + 1) }.toList()
            }
        }
        if (phraseMatches.isEmpty()) return null

        val warningPresent = rulePacks.any { pack -> pack.secretWarnings.any { it.containsMatchIn(text) } }
        return candidatePatterns
            .flatMap { pattern ->
                pattern.findAll(text).map { Candidate(it.value, it.range.first, it.range.last + 1) }.toList()
            }
            .distinctBy { it.start to it.end }
            .mapNotNull { candidate -> score(text, candidate, phraseMatches, warningPresent) }
            .maxWithOrNull(compareBy<Pair<Candidate, Int>> { it.second }.thenBy { -it.first.start })
            ?.let { (candidate, confidence) -> OtpDetection(candidate.normalized, confidence) }
    }

    private fun score(
        text: String,
        candidate: Candidate,
        phrases: List<PhraseMatch>,
        warningPresent: Boolean,
    ): Pair<Candidate, Int>? {
        val compactLength = candidate.normalized.count(Char::isLetterOrDigit)
        if (compactLength !in 4..10 || candidate.normalized.count(Char::isDigit) < 2) return null
        if (!candidate.numeric && candidate.normalized.none(Char::isLetter)) return null
        if (candidate.numeric && candidate.normalized.length == 4 && candidate.normalized.toIntOrNull() in 1900..2099) {
            return null
        }
        if (looksLikeDateOrTime(text, candidate) || looksLikeMoney(text, candidate)) return null
        if (looksLikeMaskedAccount(text, candidate) || looksLikeUrl(text, candidate)) return null

        val local = text.substring(
            (candidate.start - 56).coerceAtLeast(0),
            (candidate.end + 56).coerceAtMost(text.length),
        )
        if (nonAuthenticationCode.containsMatchIn(local)) return null

        val closest = phrases.minByOrNull { phraseDistance(candidate, it) } ?: return null
        val distance = phraseDistance(candidate, closest)
        var confidence = when {
            distance <= 24 -> 7
            distance <= 56 -> 5
            distance <= 96 -> 2
            else -> 0
        }
        if (closest.end <= candidate.start && candidate.start - closest.end <= 16) confidence += 3
        confidence += when {
            candidate.numeric && candidate.normalized.length == 6 -> 2
            candidate.numeric -> 1
            else -> 0
        }
        if (warningPresent) confidence += 1

        if (weakCodeWord.containsMatchIn(local)) confidence += 1
        if (commerceOnly.containsMatchIn(local) && distance > 24) confidence -= 3

        return (candidate to confidence).takeIf { confidence >= MIN_CONFIDENCE }
    }

    private fun phraseDistance(candidate: Candidate, phrase: PhraseMatch): Int = when {
        candidate.end < phrase.start -> phrase.start - candidate.end
        phrase.end < candidate.start -> candidate.start - phrase.end
        else -> 0
    }

    private fun looksLikeMaskedAccount(text: String, candidate: Candidate): Boolean {
        val prefix = text.substring((candidate.start - 45).coerceAtLeast(0), candidate.start)
        return maskedAccount.containsMatchIn(prefix)
    }

    private fun looksLikeMoney(text: String, candidate: Candidate): Boolean {
        val around = text.substring(
            (candidate.start - 10).coerceAtLeast(0),
            (candidate.end + 10).coerceAtMost(text.length),
        )
        return currency.containsMatchIn(around) ||
            Regex("""\d[.,]\d{2}""").containsMatchIn(around) && candidate.numeric
    }

    private fun looksLikeDateOrTime(text: String, candidate: Candidate): Boolean {
        val around = text.substring(
            (candidate.start - 5).coerceAtLeast(0),
            (candidate.end + 5).coerceAtMost(text.length),
        )
        return dateOrTime.findAll(around).any { match ->
            val absoluteStart = (candidate.start - 5).coerceAtLeast(0) + match.range.first
            val absoluteEnd = absoluteStart + match.value.length
            candidate.start < absoluteEnd && candidate.end > absoluteStart
        }
    }

    private fun looksLikeUrl(text: String, candidate: Candidate): Boolean {
        val lineStart = text.lastIndexOf('\n', candidate.start).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', candidate.end).let { if (it < 0) text.length else it }
        return urlMarker.containsMatchIn(text.substring(lineStart, lineEnd))
    }

    private fun regexes(vararg values: String): List<Regex> =
        values.map { Regex(it, setOf(RegexOption.IGNORE_CASE)) }

    private const val MIN_CONFIDENCE = 8
}
