package com.remora.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataRedactorTest {

    // --- Card numbers ---

    @Test
    fun luhnValidCardNumber_isMasked() {
        val out = SensitiveDataRedactor.redact("Số thẻ: 4111111111111111")
        assertTrue(out.contains(SensitiveDataRedactor.MASK))
        assertFalse(out.contains("4111111111111111"))
    }

    @Test
    fun luhnValidCardNumberWithSeparators_isMasked() {
        assertTrue(
            SensitiveDataRedactor.redact("4111 1111 1111 1111").contains(SensitiveDataRedactor.MASK)
        )
        assertTrue(
            SensitiveDataRedactor.redact("4111-1111-1111-1111").contains(SensitiveDataRedactor.MASK)
        )
        assertTrue(
            SensitiveDataRedactor.redact("5555.5555.5555.4444").contains(SensitiveDataRedactor.MASK)
        )
    }

    @Test
    fun luhnInvalidLongDigits_stayVisible() {
        val out = SensitiveDataRedactor.redact("Mã số sinh viên: 1234567890123")
        assertEquals("Mã số sinh viên: 1234567890123", out)
    }

    @Test
    fun shortDigitSequences_stayVisible() {
        val input = "Điện thoại 0901234567, bài 123456, năm 2026"
        assertEquals(input, SensitiveDataRedactor.redact(input))
    }

    @Test
    fun batteryPinPercentage_isNotMasked() {
        val input = "Pin: 85%"
        assertEquals(input, SensitiveDataRedactor.redact(input))
    }

    @Test
    fun cardNumberFollowedByOtherDigits_isStillMasked() {
        val out = SensitiveDataRedactor.redact("Thanh toán 4111111111111111 số tiền 3020")
        assertTrue(out.contains(SensitiveDataRedactor.MASK))
        assertFalse(out.contains("4111111111111111"))
    }

    // --- OTP ---

    @Test
    fun otpWithContext_isMasked() {
        assertFalse(SensitiveDataRedactor.redact("OTP: 482913").contains("482913"))
        assertFalse(SensitiveDataRedactor.redact("Mã xác thực: 123456").contains("123456"))
        assertFalse(SensitiveDataRedactor.redact("Verification code 654321").contains("654321"))
    }

    @Test
    fun otpNextLineValue_isMasked() {
        val out = SensitiveDataRedactor.redact("Mã xác thực\n482913\nXin cảm ơn")
        assertFalse(out.contains("482913"))
        assertTrue(out.contains("Xin cảm ơn"))
    }

    @Test
    fun digitsWithoutOtpContext_stayVisible() {
        val out = SensitiveDataRedactor.redact("Bài 15: tính tổng 123456 + 789")
        assertEquals("Bài 15: tính tổng 123456 + 789", out)
    }

    // --- Password / PIN ---

    @Test
    fun labeledPassword_isMasked() {
        assertFalse(SensitiveDataRedactor.redact("Password: hunter2secure").contains("hunter2secure"))
        assertFalse(SensitiveDataRedactor.redact("Mật khẩu: abc123!@#").contains("abc123"))
        assertFalse(SensitiveDataRedactor.redact("PIN: 9042").contains("9042"))
    }

    @Test
    fun labeledPasswordOnNextLine_isMasked() {
        val out = SensitiveDataRedactor.redact("Mật khẩu\nmySecret123\nGhi chú học tập")
        assertFalse(out.contains("mySecret123"))
        assertTrue(out.contains("Ghi chú học tập"))
    }

    @Test
    fun passwordWordWithoutValue_isUntouched() {
        val out = SensitiveDataRedactor.redact("Change your password regularly for security")
        assertEquals("Change your password regularly for security", out)
    }

    // --- Study content must survive ---

    @Test
    fun studyContentWithOrdinaryNumbers_isUntouched() {
        val input = "Định lý Pythagore a²+b²=c². Bài tập 123456. Tổng 123 + 456 = 579. Lớp 2023-2026."
        assertEquals(input, SensitiveDataRedactor.redact(input))
    }

    @Test
    fun mathFormulasAndTheoremNames_surviveRedaction() {
        val input = "Định lý: f(x) = 1234567x + 89. Vietnames text với dấu."
        val out = SensitiveDataRedactor.redact(input)
        assertTrue(out.contains("Định lý"))
        assertTrue(out.contains("f(x)"))
        assertFalse(out.contains(SensitiveDataRedactor.MASK))
    }

    // --- Robustness ---

    @Test
    fun windowsAndMacLineEndings_areHandledWithoutCrash() {
        val out = SensitiveDataRedactor.redact("OTP: 123456\r\nPassword: abc\r\nBài học")
        assertFalse(out.contains("123456"))
        assertFalse(out.contains("abc\r"))
    }

    @Test
    fun redact_isDeterministic() {
        val input = "OTP 998877\nSố thẻ 4111111111111111\nBài 12"
        assertEquals(SensitiveDataRedactor.redact(input), SensitiveDataRedactor.redact(input))
    }

    @Test
    fun emptyText_returnsEmpty() {
        assertEquals("", SensitiveDataRedactor.redact(""))
    }
}
