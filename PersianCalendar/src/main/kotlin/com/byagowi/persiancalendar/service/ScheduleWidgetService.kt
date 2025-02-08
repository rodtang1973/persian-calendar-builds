package com.byagowi.persiancalendar.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import androidx.core.text.buildSpannedString
import androidx.core.text.scale
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.entities.CalendarEvent
import com.byagowi.persiancalendar.entities.Clock
import com.byagowi.persiancalendar.entities.EventsStore
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.entities.PrayTime
import com.byagowi.persiancalendar.entities.PrayTime.Companion.get
import com.byagowi.persiancalendar.global.coordinates
import com.byagowi.persiancalendar.global.eventsRepository
import com.byagowi.persiancalendar.global.holidayString
import com.byagowi.persiancalendar.global.isShowDeviceCalendarEvents
import com.byagowi.persiancalendar.global.mainCalendar
import com.byagowi.persiancalendar.global.nothingScheduledString
import com.byagowi.persiancalendar.global.prayTimesTitles
import com.byagowi.persiancalendar.global.secondaryCalendar
import com.byagowi.persiancalendar.global.spacedColon
import com.byagowi.persiancalendar.ui.calendar.eventTextColor
import com.byagowi.persiancalendar.ui.calendar.sortEvents
import com.byagowi.persiancalendar.ui.utils.dp
import com.byagowi.persiancalendar.utils.calculatePrayTimes
import com.byagowi.persiancalendar.utils.calendar
import com.byagowi.persiancalendar.utils.eventKey
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.utils.getEnabledAlarms
import com.byagowi.persiancalendar.utils.getShiftWorkTitle
import com.byagowi.persiancalendar.utils.jdnActionKey
import com.byagowi.persiancalendar.utils.monthName
import com.byagowi.persiancalendar.utils.readTwoWeekDeviceEvents
import com.byagowi.persiancalendar.variants.debugAssertNotNull
import io.github.persiancalendar.calendar.AbstractDate
import java.util.GregorianCalendar
import kotlin.math.roundToInt

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        EventsViewFactory(this.applicationContext, intent.getIntExtra(widgetWidthCellKey, -1))
}

const val widgetWidthCellKey = "width"

private class EventsViewFactory(
    val context: Context,
    val widthCells: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private object Spacer
    private object NextTime
    private data class Header(
        val day: Jdn,
        val date: AbstractDate,
        val secondaryDate: AbstractDate?,
        val withMonth: Boolean
    )

    private data class Item(
        val value: Any,
        val day: Jdn,
        val date: AbstractDate,
        val secondaryDate: AbstractDate?,
        val today: Boolean,
        val first: Boolean
    )

    private val enabledAlarms = getEnabledAlarms(context)
    private val items = run {
        val today = Jdn.today()
        val deviceEvents = if (isShowDeviceCalendarEvents.value) {
            context.readTwoWeekDeviceEvents(today)
        } else EventsStore.empty()
        val days = (today..<today + 14).toList()
        val dates = days.map { it on mainCalendar }
        val secondaryDates = secondaryCalendar?.let { calendar -> days.map { it on calendar } }
        var monthChange = false
        var secondaryMonthChange = false
        days.map {
            it to sortEvents(eventsRepository?.getEvents(it, deviceEvents) ?: emptyList())
        }.flatMapIndexed { i, (day, events) ->
            val items = buildList {
                val shiftWorkTitle = getShiftWorkTitle(day)
                if (shiftWorkTitle != null) add(shiftWorkTitle)
                if (events.isEmpty() && shiftWorkTitle == null && i == 0) {
                    add(nothingScheduledString)
                } else addAll(events)
                if (enabledAlarms.isNotEmpty() && i == 0) add(NextTime)
            }
            val date = dates[i]
            val secondaryDate = secondaryDates?.let { it[i] }
            when {
                i != 0 && items.isEmpty() -> listOf()
                dates[0].month != date.month && !monthChange -> {
                    monthChange = true
                    if (secondaryDates?.get(0)?.month != secondaryDate?.month) {
                        secondaryMonthChange = true
                    }
                    listOf(Header(day, date, secondaryDate, true))
                }

                secondaryDates?.get(0)?.month != secondaryDate?.month && !secondaryMonthChange -> {
                    secondaryMonthChange = true
                    listOf(Header(day, date, secondaryDate, true))
                }

                else -> listOf(Header(day, date, secondaryDate, false))
            } + items.mapIndexed { j, item -> Item(item, day, date, secondaryDate, i == 0, j == 0) }
        }
    } + listOf(Spacer)

    override fun onCreate() = Unit
    override fun onDestroy() = Unit
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDataSetChanged() = Unit
    override fun getCount(): Int = items.size
    override fun getViewAt(position: Int): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_schedule_item)
        if (widthCells > 3) {
            row.setViewVisibility(R.id.start_padding, View.VISIBLE)
            row.setViewVisibility(R.id.middle_padding, View.VISIBLE)
            row.setViewVisibility(R.id.end_padding, View.VISIBLE)
        } else {
            row.setViewVisibility(R.id.start_padding, View.GONE)
            row.setViewVisibility(R.id.middle_padding, View.GONE)
            row.setViewVisibility(R.id.end_padding, View.GONE)
        }
        val entry = items[position]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            row.setBoolean(R.id.event_background, "setClipToOutline", true)
            row.setViewOutlinePreferredRadius(
                R.id.event_background,
                12f,
                TypedValue.COMPLEX_UNIT_DIP
            )
        }
        row.setInt(R.id.event, "setTextColor", Color.WHITE)

        if (entry == Spacer) {
            row.setOnClickFillInIntent(R.id.widget_schedule_item_root, Intent())
            row.setViewVisibility(R.id.spacer, View.VISIBLE)
            row.setViewVisibility(R.id.header, View.GONE)
            row.setViewVisibility(R.id.event_parent, View.GONE)
            return row
        }

        (entry as? Header)?.let { header ->
            val weekDayName = header.secondaryDate?.let {
                val secondaryDayOfMonth = formatNumber(it.dayOfMonth, it.calendar.preferredDigits)
                "${header.day.weekDayNameInitials}($secondaryDayOfMonth)"
            } ?: header.day.weekDayName
            if (position == 0 && widthCells < 3) {
                row.setTextViewText(R.id.day_of_month, formatNumber(header.date.dayOfMonth))
                row.setTextViewText(R.id.highlight, weekDayName)
                row.setViewVisibility(R.id.highlight, View.VISIBLE)
                row.setViewVisibility(R.id.weekday_name, View.GONE)
                row.setViewVisibility(R.id.top_space, View.VISIBLE)
                row.setViewVisibility(R.id.day_of_month, View.VISIBLE)
                row.setViewVisibility(R.id.bigger_month_name, View.GONE)
            } else if (widthCells > 2) {
                row.setViewVisibility(R.id.weekday_name, View.GONE)
                row.setViewVisibility(R.id.top_space, View.VISIBLE)
                row.setViewVisibility(R.id.highlight, View.GONE)
                row.setViewVisibility(R.id.day_of_month, View.GONE)
                if (header.withMonth || position == 0) {
                    val dp = context.resources.dp
                    val topSpacePx = (if (header.secondaryDate == null) 12 else 6)
                        .let { (it * dp).roundToInt() }
                    val bottomSpacePx = (if (header.secondaryDate == null) 4 else 12)
                        .let { (it * dp).roundToInt() }
                    val monthTitle = buildSpannedString {
                        append(header.date.monthName + "\n")
                        header.secondaryDate?.let { scale(.9f) { append(it.monthName) } }
                    }
                    row.setTextViewText(R.id.bigger_month_name, monthTitle)
                    row.setViewVisibility(R.id.bigger_month_name, View.VISIBLE)
                    row.setViewPadding(R.id.bigger_month_name, 0, topSpacePx, 0, bottomSpacePx)
                } else {
                    row.setViewVisibility(R.id.bigger_month_name, View.GONE)
                }
            } else {
                row.setTextViewText(R.id.day_of_month, formatNumber(header.date.dayOfMonth))
                row.setTextViewText(R.id.weekday_name, weekDayName)
                row.setViewVisibility(R.id.weekday_name, View.VISIBLE)
                row.setViewVisibility(R.id.top_space, View.GONE)
                row.setViewVisibility(R.id.day_of_month, View.VISIBLE)
                if (header.withMonth) {
                    val monthTitle = buildSpannedString {
                        append(header.date.monthName)
                        header.secondaryDate?.let { scale(.9f) { append(" (${it.monthName})") } }
                    }
                    row.setTextViewText(R.id.highlight, monthTitle)
                    row.setViewVisibility(R.id.highlight, View.VISIBLE)
                } else row.setViewVisibility(R.id.highlight, View.GONE)
                row.setViewVisibility(R.id.bigger_month_name, View.GONE)
            }
            row.setViewVisibility(R.id.spacer, View.GONE)
            row.setViewVisibility(R.id.header, View.VISIBLE)
            row.setViewVisibility(R.id.event_parent, View.GONE)
            val clickIntent = Intent().putExtra(jdnActionKey, header.day.value)
            row.setOnClickFillInIntent(R.id.widget_schedule_item_root, clickIntent)
            return row
        }

        val item = (entry as? Item).debugAssertNotNull ?: return row
        val event = item.value as? CalendarEvent<*>
        if (item.value == NextTime) {
            val (title, color) = getNextEnabledTime(enabledAlarms)
            row.setTextViewText(R.id.event, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                row.setInt(R.id.event_background, "setBackgroundColor", color)
                row.setInt(R.id.event, "setTextColor", eventTextColor(color))
                row.setInt(R.id.event_time, "setTextColor", eventTextColor(color))
                row.setBoolean(R.id.event_background, "setClipToOutline", true)
                row.setViewOutlinePreferredRadius(
                    R.id.event_background, 12f, TypedValue.COMPLEX_UNIT_DIP
                )
            } else {
                val background = R.drawable.widget_schedule_item_time
                row.setInt(R.id.event_background, "setBackgroundResource", background)
            }
            row.setViewVisibility(R.id.event_time, View.GONE)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (item.value is String) {
                    row.setColorAttr(
                        R.id.event,
                        "setTextColor",
                        android.R.attr.colorAccent,
                    )
                    row.setColorAttr(
                        R.id.event_time,
                        "setTextColor",
                        android.R.attr.colorAccent,
                    )
                    row.setInt(
                        R.id.event_background,
                        "setBackgroundResource",
                        R.drawable.widget_nothing_scheduled,
                    )
                } else if (event is CalendarEvent<*>) {
                    if (event is CalendarEvent.DeviceCalendarEvent) {
                        val background =
                            if (event.color.isEmpty()) Color.GRAY else event.color.toLong().toInt()
                        row.setInt(R.id.event_background, "setBackgroundColor", background)
                        row.setInt(R.id.event, "setTextColor", eventTextColor(background))
                        row.setInt(R.id.event_time, "setTextColor", eventTextColor(background))
                    } else {
                        if (event.isHoliday) row.setColorAttr(
                            R.id.event_background,
                            "setBackgroundColor",
                            android.R.attr.colorAccent,
                        ) else row.setInt(
                            R.id.event_background,
                            "setBackgroundResource",
                            R.drawable.widget_nothing_scheduled,
                        )
                        val textColor = if (event.isHoliday) android.R.attr.colorForegroundInverse
                        else android.R.attr.colorForeground
                        row.setColorAttr(R.id.event, "setTextColor", textColor)
                        row.setColorAttr(R.id.event_time, "setTextColor", textColor)
                    }
                }
            } else {
                val background = when {
                    event?.isHoliday == true -> R.drawable.widget_schedule_item_holiday
                    event is CalendarEvent.DeviceCalendarEvent -> R.drawable.widget_schedule_item_event

                    else -> R.drawable.widget_schedule_item_default
                }
                row.setInt(R.id.event_background, "setBackgroundResource", background)
            }
            val title = when {
                event?.isHoliday == true -> "[$holidayString] ${event.title}"
                event is CalendarEvent<*> -> event.title
                item.value is String -> item.value.toString()
                else -> ""
            }
            row.setTextViewText(R.id.event, title)
            (event as? CalendarEvent.DeviceCalendarEvent)?.time?.let {
                row.setTextViewText(R.id.event_time, it)
                row.setViewVisibility(R.id.event_time, View.VISIBLE)
            } ?: row.setViewVisibility(R.id.event_time, View.GONE)
        }

        row.setViewVisibility(R.id.spacer, View.GONE)
        row.setViewVisibility(R.id.header, View.GONE)
        row.setViewVisibility(R.id.event_parent, View.VISIBLE)
        if (widthCells > 2) {
            if (item.first) {
                if (item.today) {
                    row.setViewVisibility(R.id.today, View.VISIBLE)
                    row.setViewVisibility(R.id.day, View.GONE)
                } else {
                    row.setViewVisibility(R.id.today, View.GONE)
                    row.setViewVisibility(R.id.day, View.VISIBLE)
                }
                val title = buildSpannedString {
                    append(item.day.weekDayNameInitials)
                    item.secondaryDate?.let {
                        append(formatNumber(item.date.dayOfMonth) + "\n")
                        scale(.75f) {
                            append("(${formatNumber(it.dayOfMonth, it.calendar.preferredDigits)})")
                        }
                    } ?: append("\n${formatNumber(item.date.dayOfMonth)}")
                }
                row.setTextViewText(if (item.today) R.id.today else R.id.day, title)
            } else {
                row.setViewVisibility(R.id.day, View.GONE)
                row.setViewVisibility(R.id.today, View.GONE)
            }
        } else row.setViewVisibility(R.id.day_wrapper, View.GONE)
        val clickIntent = if (event is CalendarEvent.DeviceCalendarEvent) {
            Intent().putExtra(eventKey, event.id)
        } else Intent().putExtra(jdnActionKey, item.day.value)
        row.setOnClickFillInIntent(R.id.widget_schedule_item_root, clickIntent)
        return row
    }

    private fun getNextEnabledTime(enabledAlarms: Set<PrayTime>): Pair<String, Int> {
        if (enabledAlarms.isEmpty()) return "" to 0
        val time = GregorianCalendar()
        val now = Clock(time)
        return coordinates.value?.calculatePrayTimes(time)?.let { times ->
            val next = enabledAlarms.firstOrNull { times[it] > now } ?: enabledAlarms.first()
            (prayTimesTitles[next]
                ?: "") + spacedColon + times[next].toFormattedString() to next.tint.toArgb()
        } ?: ("" to 0)
    }
}
