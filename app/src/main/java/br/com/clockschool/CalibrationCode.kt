package br.com.clockschool

/**
 * Converte a diferença calibrada num código curto compartilhável (ex.: 59X-R8G).
 *
 * O valor é embaralhado por uma multiplicação modular invertível antes de virar texto,
 * então códigos de ajustes parecidos não se parecem entre si. Os 13 bits de verificação
 * fazem quase todo código digitado errado ser recusado em vez de aplicar um ajuste errado.
 */
object CalibrationCode {

    private const val BITS = 30
    private const val MASK = (1L shl BITS) - 1
    private const val MULT = 0x2E1F3B5L
    private const val XOR = 0x1A7C93DL
    private const val CHECK_BITS = 13
    private const val CHECK_MASK = (1L shl CHECK_BITS) - 1
    private const val BIAS = 43200L
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun encode(offsetMillis: Long): String {
        val seconds = (offsetMillis / 1000).coerceIn(-BIAS, BIAS)
        val payload = seconds + BIAS
        val raw = (payload shl CHECK_BITS) or checksum(payload)
        val scrambled = ((raw * MULT) and MASK) xor XOR

        val chars = CharArray(6) { index ->
            ALPHABET[((scrambled shr (5 * (5 - index))) and 31L).toInt()]
        }
        return String(chars, 0, 3) + "-" + String(chars, 3, 3)
    }

    /** Retorna a diferença em milissegundos, ou null se o código for inválido. */
    fun decode(input: String): Long? {
        val cleaned = StringBuilder()
        for (raw in input.uppercase()) {
            val c = when (raw) {
                'I', 'L' -> '1'
                'O' -> '0'
                'U' -> 'V'
                else -> raw
            }
            if (ALPHABET.indexOf(c) >= 0) cleaned.append(c)
        }
        if (cleaned.length != 6) return null

        var value = 0L
        for (c in cleaned) {
            value = (value shl 5) or ALPHABET.indexOf(c).toLong()
        }

        val raw = ((value xor XOR) * modInverse(MULT)) and MASK
        val payload = raw shr CHECK_BITS
        if (payload > 2 * BIAS) return null
        if (checksum(payload) != (raw and CHECK_MASK)) return null

        return (payload - BIAS) * 1000
    }

    private fun checksum(payload: Long): Long {
        var h = payload * 2654435761L
        h = h xor (h ushr 17)
        h *= 0x9E3779B1L
        h = h xor (h ushr 13)
        return h and CHECK_MASK
    }

    private fun modInverse(a: Long): Long {
        var x = 1L
        repeat(6) { x = (x * (2 - a * x)) and MASK }
        return x and MASK
    }
}
