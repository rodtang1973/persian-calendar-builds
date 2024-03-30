package com.byagowi.persiancalendar.entities

import androidx.core.util.lruCache
import com.byagowi.persiancalendar.generated.irregularRecurringEvents
import com.byagowi.persiancalendar.utils.calendarType
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.variants.debugAssertNotNull
import io.github.persiancalendar.calendar.AbstractDate
import io.github.persiancalendar.calendar.CivilDate
import io.github.persiancalendar.calendar.IslamicDate
import io.github.persiancalendar.calendar.NepaliDate
import io.github.persiancalendar.calendar.PersianDate
import org.jetbrains.annotations.VisibleForTesting

class IrregularCalendarEventsStore(private val eventsRepository: EventsRepository) {
    private fun createCache(type: CalendarType) =
        lruCache(1024, create = { year: Int -> generateEntry(year, type) })

    private val persianEvents = createCache(CalendarType.SHAMSI)
    private val islamicEvents = createCache(CalendarType.ISLAMIC)
    private val gregorianEvents = createCache(CalendarType.GREGORIAN)
    private val nepaliEvents = createCache(CalendarType.NEPALI)

    @Suppress("UNCHECKED_CAST")
    fun <T : CalendarEvent<*>> getEventsList(year: Int, type: CalendarType): List<T> {
        return when (type) {
            CalendarType.SHAMSI -> persianEvents[year]
            CalendarType.ISLAMIC -> islamicEvents[year]
            CalendarType.GREGORIAN -> gregorianEvents[year]
            CalendarType.NEPALI -> nepaliEvents[year]
        } as? List<T> ?: emptyList()
    }

    fun <T : CalendarEvent<out AbstractDate>> getEvents(date: AbstractDate): List<T> =
        getEventsList<T>(date.year, date.calendarType).filter { it.date == date }

    // Create actually usable irregular event of a year based on defined rules and enabled holidays
    private fun generateEntry(year: Int, type: CalendarType): List<CalendarEvent<*>> {
        return irregularRecurringEvents.filter { event ->
            if (type != when (event["calendar"]) {
                    "Gregorian" -> CalendarType.GREGORIAN
                    "Persian" -> CalendarType.SHAMSI
                    "Hijri" -> CalendarType.ISLAMIC
                    "Nepali" -> CalendarType.NEPALI
                    else -> return@filter false
                }
            ) return@filter false
            when {
                event["type"] == "International" && eventsRepository.international -> true
                event["type"] == "Iran" && eventsRepository.iranHolidays && event["holiday"] == "true" -> true
                event["type"] == "Iran" && eventsRepository.iranOthers -> true
                event["type"] == "Nepal" && eventsRepository.nepalHolidays && event["holiday"] == "true" -> true
                event["type"] == "Nepal" && eventsRepository.nepalOthers -> true
                event["type"] == "AncientIran" && eventsRepository.iranAncient -> true
                else -> false
            }
        }.mapNotNull { event ->
            val date = getDateInstance(event, year, type) ?: return@mapNotNull null
            val title = "${event["title"] ?: return@mapNotNull null} (${formatNumber(year)})"
            val isHoliday = event["holiday"] == "true"
            when (date) {
                is PersianDate -> CalendarEvent.PersianCalendarEvent(title, isHoliday, date)
                is IslamicDate -> CalendarEvent.IslamicCalendarEvent(title, isHoliday, date)
                is CivilDate -> CalendarEvent.GregorianCalendarEvent(title, isHoliday, date)
                is NepaliDate -> CalendarEvent.NepaliCalendarEvent(title, isHoliday, date)
                else -> null
            }.debugAssertNotNull
        }
    }
}

@VisibleForTesting
fun getDateInstance(event: Map<String, String>, year: Int, type: CalendarType): AbstractDate? {
    return when (event["rule"]) {
        "single event" -> {
            if (event["year"]?.toIntOrNull() != year) return null
            val month = event["month"]?.toIntOrNull() ?: return null
            val day = event["day"]?.toIntOrNull() ?: return null
            type.createDate(year, month, day)
        }

        "nth day from" -> {
            val nth = event["nth"]?.toIntOrNull() ?: return null
            val day = event["day"]?.toIntOrNull() ?: return null
            val month = event["month"]?.toIntOrNull() ?: return null
            (Jdn(type, year, month, day) + nth - 1).toCalendar(type)
        }

        "end of month" -> {
            val month = event["month"]?.toIntOrNull() ?: return null
            type.createDate(year, month, type.getMonthLength(year, month))
        }

        "last weekday of month" -> {
            val month = event["month"]?.toIntOrNull() ?: return null
            val weekDay = event["weekday"]?.toIntOrNull() ?: return null
            val offset = event["offset"]?.toIntOrNull() ?: 0
            val day = type.getLastWeekDayOfMonth(year, month, weekDay) + offset
            type.createDate(year, month, day)
        }

        "nth weekday of month" -> {
            val month = event["month"]?.toIntOrNull() ?: return null
            val weekDay = event["weekday"]?.toIntOrNull() ?: return null
            val nth = event["nth"]?.toIntOrNull() ?: return null
            val monthStartWeekDay = Jdn(type, year, month, 1).dayOfWeek
            val day =
                weekDay - monthStartWeekDay + (nth - if (monthStartWeekDay < weekDay) 1 else 0) * 7
            type.createDate(year, month, day)
        }

        else -> return null
    }
}
