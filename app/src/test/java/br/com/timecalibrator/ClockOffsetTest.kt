package br.com.timecalibrator

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class ClockOffsetTest {

    private val second = 1_000L
    private val minute = 60 * second
    private val hour = 60 * minute
    private val day = 24 * hour

    private lateinit var originalZone: TimeZone

    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    // normalize

    @Test
    fun `normalize keeps small offsets unchanged`() {
        assertEquals(8 * second, ClockOffset.normalize(8 * second))
        assertEquals(-134_382L, ClockOffset.normalize(-134_382L))
        assertEquals(0L, ClockOffset.normalize(0L))
    }

    @Test
    fun `normalize wraps full days to zero`() {
        assertEquals(0L, ClockOffset.normalize(day))
        assertEquals(0L, ClockOffset.normalize(-day))
        assertEquals(0L, ClockOffset.normalize(3 * day))
    }

    @Test
    fun `normalize strips whole days from larger offsets`() {
        assertEquals(5 * second, ClockOffset.normalize(day + 5 * second))
        assertEquals(-5 * second, ClockOffset.normalize(-day - 5 * second))
    }

    @Test
    fun `calibrating midnight bell at 23_59_50 is 10 s ahead, not almost a day`() {
        // Celular marca 23:59:50 quando o sinal das 00:00 toca: o relógio externo está 10 s adiantado.
        val phone = 23 * hour + 59 * minute + 50 * second
        val bell = 0L
        assertEquals(-10 * second, ClockOffset.normalize(phone - bell))
    }

    @Test
    fun `calibrating midnight bell at 00_00_10 is 10 s behind`() {
        val phone = 10 * second
        val bell = 0L
        assertEquals(10 * second, ClockOffset.normalize(phone - bell))
    }

    @Test
    fun `normalize keeps exactly plus and minus 12 h`() {
        assertEquals(12 * hour, ClockOffset.normalize(12 * hour))
        assertEquals(-12 * hour, ClockOffset.normalize(-12 * hour))
    }

    @Test
    fun `normalize folds just past 12 h to the other side`() {
        assertEquals(-12 * hour + second, ClockOffset.normalize(12 * hour + second))
        assertEquals(12 * hour - second, ClockOffset.normalize(-12 * hour - second))
    }

    // calibratedNow / millisOfDay / formatClock

    @Test
    fun `calibratedNow subtracts the offset`() {
        val now = 1_000_000_000L
        assertEquals(now - 8 * second, ClockOffset.calibratedNow(8 * second, now))
        assertEquals(now + 2 * minute, ClockOffset.calibratedNow(-2 * minute, now))
    }

    @Test
    fun `millisOfDay ignores the date`() {
        val t = 5 * day + 14 * hour + 37 * minute + 52 * second + 810
        assertEquals(14 * hour + 37 * minute + 52 * second + 810, ClockOffset.millisOfDay(t))
    }

    @Test
    fun `formatClock pads hours minutes and seconds`() {
        val t = 7 * hour + 5 * minute + 9 * second
        assertEquals("07:05:09", ClockOffset.formatClock(t))
        assertEquals("07:05", ClockOffset.formatClock(t, withSeconds = false))
    }

    @Test
    fun `example from the README shows the bell time`() {
        // Celular 17:40:08 quando o sinal das 17:40:00 toca.
        val phoneAtTap = 17 * hour + 40 * minute + 8 * second
        val offset = ClockOffset.normalize(phoneAtTap - (17 * hour + 40 * minute))
        assertEquals("17:40:00", ClockOffset.formatClock(ClockOffset.calibratedNow(offset, phoneAtTap)))
    }

    // formatHuman / formatDuration

    @Test
    fun `formatHuman picks the right units`() {
        assertEquals("12 s", ClockOffset.formatHuman(12 * second))
        assertEquals("3 min", ClockOffset.formatHuman(3 * minute))
        assertEquals("3 min 30 s", ClockOffset.formatHuman(3 * minute + 30 * second))
        assertEquals("1 h", ClockOffset.formatHuman(hour))
        assertEquals("1 h 05 min", ClockOffset.formatHuman(hour + 5 * minute))
    }

    @Test
    fun `formatHuman ignores sign and milliseconds`() {
        assertEquals("2 min 13 s", ClockOffset.formatHuman(-(2 * minute + 13 * second + 450)))
        assertEquals("0 s", ClockOffset.formatHuman(999))
    }

    @Test
    fun `formatDuration is hh_mm_ss of the absolute value`() {
        assertEquals("00:02:13", ClockOffset.formatDuration(-(2 * minute + 13 * second + 618)))
        assertEquals("12:00:00", ClockOffset.formatDuration(12 * hour))
    }
}
