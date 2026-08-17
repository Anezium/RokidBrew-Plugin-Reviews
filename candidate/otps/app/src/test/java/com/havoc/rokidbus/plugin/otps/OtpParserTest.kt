package com.havoc.rokidbus.plugin.otps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpParserTest {
    @Test
    fun detectsBankConfirmationCode() {
        assertCode(
            "482913",
            "T-Bank",
            "482913 — код для подтверждения перевода. Никому не сообщайте его.",
        )
    }

    @Test
    fun detectsRegistrationCode() {
        assertCode("731204", "Acme", "Your verification code is 731204. Do not share it.")
    }

    @Test
    fun detectsCodeFromFrenchNotification() {
        assertCode("849201", "Service", "Votre code de vérification est : 849201. Ne partagez jamais ce code.")
    }

    @Test
    fun detectsSmsBodyWhenSenderIsSeparate() {
        assertCode("560821", "+7 999 123-45-67", "Ваш код для входа: 560821")
        assertCode("918204", "Messages", "Код безопасности 918204. Никому не сообщайте.")
    }

    @Test
    fun detectsAlphanumericCode() {
        assertCode("A7K9Q2", "Acme", "Use code A7K9Q2 to finish registration")
    }

    @Test
    fun detectsGroupedNumericCode() {
        assertCode("123456", "Example", "Your confirmation code is 123 456")
    }

    @Test
    fun detectsCodeBeforeConfirmationPhrase() {
        assertCode("4281", "Bank", "Код 4281 для подтверждения платежа")
        assertCode("928144", "Example", "928144 is your verification code")
    }

    @Test
    fun choosesOtpInsteadOfMaskedCardDigits() {
        assertCode(
            "735201",
            "Bank",
            "Card ending •••• 4829. Your confirmation code is 735201.",
        )
    }

    @Test
    fun rejectsCardAndPurchaseNotification() {
        assertNull(OtpParser.detect("Bank", "Карта **4829. Покупка 1 200 RUB"))
    }

    @Test
    fun rejectsOrderNumber() {
        assertNull(OtpParser.detect("Shop", "Order #731204 has shipped"))
        assertNull(OtpParser.detect("Boutique", "Votre commande 849201 a été expédiée"))
    }

    @Test
    fun rejectsDatesTimesAndAmounts() {
        assertNull(OtpParser.detect("Calendar", "Meeting starts at 12:30 on 04/08/2026"))
        assertNull(OtpParser.detect("Bank", "Balance: 482913.00 RUB"))
    }

    @Test
    fun rejectsOperationalReference() {
        assertNull(OtpParser.detect("Bank", "Код операции 123456. Статус: выполнено"))
        assertNull(OtpParser.detect("Security", "New login detected. Reference 654321"))
    }

    @Test
    fun rejectsNonAuthenticationCodes() {
        assertNull(OtpParser.detect("Shop", "Your promo code is 731204"))
        assertNull(OtpParser.detect("Maps", "ZIP code: 84920"))
        assertNull(OtpParser.detect("App", "Error code: 560821"))
        assertNull(OtpParser.detect("Hotel", "Booking confirmation code: AB1234"))
        assertNull(OtpParser.detect("Магазин", "Ваш код скидки: 482913"))
    }

    @Test
    fun rejectsUrlAndTrackingDigits() {
        assertNull(OtpParser.detect("Browser", "Open https://example.com/verify/731204"))
        assertNull(OtpParser.detect("Delivery", "Tracking 560821 is now out for delivery"))
    }

    private fun assertCode(expected: String, vararg parts: String) {
        assertEquals(expected, OtpParser.detect(*parts)?.code)
    }
}
