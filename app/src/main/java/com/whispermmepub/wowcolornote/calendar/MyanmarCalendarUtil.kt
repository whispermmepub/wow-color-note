package com.whispermmepub.wowcolornote.calendar

import mmcalendar.Astro
import mmcalendar.HolidayCalculator
import mmcalendar.MyanmarDate
import java.time.LocalDate

data class MyanmarDayInfo(
    val westernDate: LocalDate,
    val myanmarYear: String,
    val monthName: String,
    val moonPhase: String,
    val fortnightDay: String,
    val weekDay: String,
    val sabbath: String,
    val holidays: List<String>
)

object MyanmarCalendarUtil {
    fun info(date: LocalDate): MyanmarDayInfo {
        val md = MyanmarDate.of(date.year, date.monthValue, date.dayOfMonth)
        val astro = Astro.of(md)
        return MyanmarDayInfo(date, md.year, md.monthName, md.moonPhase, md.fortnightDay, md.weekDay, astro.sabbath.orEmpty(), runCatching { HolidayCalculator.getHoliday(md).toList() }.getOrDefault(emptyList()))
    }
}
