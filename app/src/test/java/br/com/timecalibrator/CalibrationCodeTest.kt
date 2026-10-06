package br.com.timecalibrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CalibrationCodeTest {

    private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val halfDaySeconds = 12 * 60 * 60L

    @Test
    fun `codes have the XXX-XXX shape`() {
        val code = CalibrationCode.encode(134_000L)
        assertTrue(code, Regex("[0-9A-HJKMNP-TV-Z]{3}-[0-9A-HJKMNP-TV-Z]{3}").matches(code))
    }

    @Test
    fun `encode then decode returns the same offset for every second in range`() {
        // Varredura com passo primo cobre o intervalo inteiro sem testar 86 mil valores.
        var s = -halfDaySeconds
        while (s <= halfDaySeconds) {
            assertEquals(s * 1000, CalibrationCode.decode(CalibrationCode.encode(s * 1000)))
            s += 97
        }
    }

    @Test
    fun `round trip works at the edges`() {
        for (s in listOf(-halfDaySeconds, -1L, 0L, 1L, halfDaySeconds)) {
            assertEquals(s * 1000, CalibrationCode.decode(CalibrationCode.encode(s * 1000)))
        }
    }

    @Test
    fun `milliseconds are truncated to whole seconds`() {
        assertEquals(1_000L, CalibrationCode.decode(CalibrationCode.encode(1_500L)))
        assertEquals(-1_000L, CalibrationCode.decode(CalibrationCode.encode(-1_500L)))
        assertEquals(0L, CalibrationCode.decode(CalibrationCode.encode(999L)))
    }

    @Test
    fun `offsets beyond 12 h are clamped`() {
        assertEquals(halfDaySeconds * 1000, CalibrationCode.decode(CalibrationCode.encode(20 * 3_600_000L)))
        assertEquals(-halfDaySeconds * 1000, CalibrationCode.decode(CalibrationCode.encode(-20 * 3_600_000L)))
    }

    @Test
    fun `similar offsets produce unrelated codes`() {
        assertNotEquals(CalibrationCode.encode(8_000L).take(3), CalibrationCode.encode(9_000L).take(3))
    }

    @Test
    fun `decode accepts lowercase, spaces and a missing dash`() {
        val code = CalibrationCode.encode(-134_000L)
        val expected = -134_000L
        assertEquals(expected, CalibrationCode.decode(code.lowercase()))
        assertEquals(expected, CalibrationCode.decode(code.replace("-", "")))
        assertEquals(expected, CalibrationCode.decode(" ${code.substring(0, 3)} ${code.substring(4)} "))
    }

    @Test
    fun `decode maps look-alike letters`() {
        // I e L viram 1, O vira 0, U vira V: letras que não existem no alfabeto.
        val code = findCode { it.contains('1') }
        assertEquals(CalibrationCode.decode(code), CalibrationCode.decode(code.replace('1', 'I')))
        assertEquals(CalibrationCode.decode(code), CalibrationCode.decode(code.replace('1', 'l')))
        val zero = findCode { it.contains('0') }
        assertEquals(CalibrationCode.decode(zero), CalibrationCode.decode(zero.replace('0', 'O')))
    }

    @Test
    fun `wrong length is rejected`() {
        assertNull(CalibrationCode.decode(""))
        assertNull(CalibrationCode.decode("ABC"))
        assertNull(CalibrationCode.decode("ABC-DE"))
        assertNull(CalibrationCode.decode("ABC-DEFG"))
    }

    @Test
    fun `nearly every single-character typo is rejected`() {
        val random = Random(42)
        var typos = 0
        var accepted = 0
        repeat(200) {
            val code = CalibrationCode.encode((random.nextLong(-halfDaySeconds, halfDaySeconds + 1)) * 1000)
                .replace("-", "")
            for (pos in code.indices) {
                for (c in alphabet) {
                    if (c == code[pos]) continue
                    typos++
                    val typo = code.substring(0, pos) + c + code.substring(pos + 1)
                    if (CalibrationCode.decode(typo) != null) accepted++
                }
            }
        }
        // 13 bits de verificação: em média 1 em 8192 erros passa. Exige pelo menos 99,9% recusados.
        assertTrue("$accepted of $typos typos accepted", accepted * 1000 <= typos)
    }

    @Test
    fun `swapping two characters is rejected`() {
        val random = Random(7)
        var swaps = 0
        var accepted = 0
        repeat(500) {
            val code = CalibrationCode.encode(random.nextLong(-halfDaySeconds, halfDaySeconds + 1) * 1000)
                .replace("-", "")
            for (i in 0 until code.length - 1) {
                if (code[i] == code[i + 1]) continue
                swaps++
                val swapped = code.substring(0, i) + code[i + 1] + code[i] + code.substring(i + 2)
                if (CalibrationCode.decode(swapped) != null) accepted++
            }
        }
        assertTrue("$accepted of $swaps swaps accepted", accepted * 1000 <= swaps)
    }

    private fun findCode(predicate: (String) -> Boolean): String {
        var s = 0L
        while (true) {
            val code = CalibrationCode.encode(s * 1000)
            if (predicate(code)) return code
            s++
        }
    }
}
