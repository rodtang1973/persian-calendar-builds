package com.byagowi.persiancalendar.ui.calendar.calendarpager

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.ReleaseDebugDifference.debugAssertNotNull
import com.byagowi.persiancalendar.entities.CalendarEvent
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.utils.DeviceCalendarEventsStore
import com.byagowi.persiancalendar.utils.applyWeekStartOffsetToWeekDay
import com.byagowi.persiancalendar.utils.emptyEventsStore
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.utils.getA11yDaySummary
import com.byagowi.persiancalendar.utils.getEvents
import com.byagowi.persiancalendar.utils.getInitialOfWeekDay
import com.byagowi.persiancalendar.utils.getShiftWorkTitle
import com.byagowi.persiancalendar.utils.getWeekDayName
import com.byagowi.persiancalendar.utils.isShowDeviceCalendarEvents
import com.byagowi.persiancalendar.utils.isShowWeekOfYearEnabled
import com.byagowi.persiancalendar.utils.isTalkBackEnabled
import com.byagowi.persiancalendar.utils.isWeekEnd
import com.byagowi.persiancalendar.utils.readMonthDeviceEvents
import com.byagowi.persiancalendar.utils.revertWeekStartOffsetFromWeekDay

class DaysAdapter(
    private val context: Context, private val sharedDayViewData: SharedDayViewData,
    private val calendarPager: CalendarPager
) : RecyclerView.Adapter<DaysAdapter.ViewHolder>() {

    var days = emptyList<Jdn>()
    var startingDayOfWeek: Int = 0
    var weekOfYearStart: Int = 0
    var weeksCount: Int = 0

    private var monthEvents: DeviceCalendarEventsStore = emptyEventsStore()
    private var selectedDay = -1

    fun initializeMonthEvents() {
        if (isShowDeviceCalendarEvents) monthEvents = context.readMonthDeviceEvents(days[0])
    }

    internal fun selectDay(dayOfMonth: Int) {
        val prevDay = selectedDay
        selectedDay = -1
        notifyItemChanged(prevDay)

        if (dayOfMonth == -1) return

        selectedDay = dayOfMonth + 6 + applyWeekStartOffsetToWeekDay(startingDayOfWeek)

        if (isShowWeekOfYearEnabled) selectedDay += selectedDay / 7 + 1

        notifyItemChanged(selectedDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        DayView(parent.context).also {
            it.layoutParams = sharedDayViewData.layoutParams
            it.sharedDayViewData = sharedDayViewData
        }
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(position)

    // days of week * month view rows
    override fun getItemCount(): Int = 7 * if (isShowWeekOfYearEnabled) 8 else 7

    private val todayJdn = Jdn.today

    inner class ViewHolder(itemView: DayView) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener, View.OnLongClickListener {

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View) {
            val itemDayView = (v as? DayView).debugAssertNotNull ?: return
            val jdn = itemDayView.jdn ?: return
            calendarPager.onDayClicked(jdn)
            selectDay(itemDayView.dayOfMonth)
        }

        override fun onLongClick(v: View): Boolean {
            onClick(v)
            val jdn = (v as? DayView).debugAssertNotNull?.jdn ?: return false
            calendarPager.onDayLongClicked(jdn)
            return false
        }

        fun bind(pos: Int) {
            var position = pos
            val dayView = (itemView as? DayView).debugAssertNotNull ?: return
            if (isShowWeekOfYearEnabled) {
                if (position % 8 == 0) {
                    val row = position / 8
                    if (row in 1..weeksCount) {
                        val weekNumber = formatNumber(weekOfYearStart + row - 1)
                        dayView.setNonDayOfMonthItem(
                            weekNumber, sharedDayViewData.weekNumberTextSize
                        )
                        dayView.contentDescription = if (isTalkBackEnabled)
                            context.getString(R.string.nth_week_of_year, weekNumber)
                        else weekNumber

                        dayView.isVisible = true
                    } else
                        setEmpty()
                    return
                }

                position = position - position / 8 - 1
            }

            val fixedStartingDayOfWeek = applyWeekStartOffsetToWeekDay(startingDayOfWeek)
            if (days.size < position - 6 - fixedStartingDayOfWeek) {
                setEmpty()
            } else if (position < 7) {
                val weekDayInitial = getInitialOfWeekDay(revertWeekStartOffsetFromWeekDay(position))
                dayView.setNonDayOfMonthItem(
                    getInitialOfWeekDay(revertWeekStartOffsetFromWeekDay(position)),
                    sharedDayViewData.weekDaysInitialTextSize
                )

                dayView.contentDescription = if (isTalkBackEnabled)
                    context.getString(
                        R.string.week_days_name_column,
                        getWeekDayName(revertWeekStartOffsetFromWeekDay(position))
                    )
                else weekDayInitial

                dayView.isVisible = true
                dayView.setBackgroundResource(0)
            } else {
                if (position - 7 - fixedStartingDayOfWeek >= 0) {
                    val day = days[position - 7 - fixedStartingDayOfWeek]
                    val events = monthEvents.getEvents(day)

                    val isToday = day == todayJdn

                    val dayOfMonth = position - 6 - fixedStartingDayOfWeek
                    dayView.setDayOfMonthItem(
                        isToday,
                        pos == selectedDay,
                        events.any { it !is CalendarEvent.DeviceCalendarEvent },
                        events.any { it is CalendarEvent.DeviceCalendarEvent },
                        isWeekEnd((day + startingDayOfWeek - days[0]) % 7) || events.any { it.isHoliday },
                        sharedDayViewData.digitsTextSize,
                        day, dayOfMonth, getShiftWorkTitle(day, true)
                    )

                    dayView.contentDescription = if (isTalkBackEnabled) getA11yDaySummary(
                        context, day, isToday, emptyEventsStore(),
                        withZodiac = isToday, withOtherCalendars = false, withTitle = true
                    ) else dayOfMonth.toString()

                    dayView.isVisible = true
                } else {
                    setEmpty()
                }
            }
        }

        private fun setEmpty() {
            itemView.isVisible = false
        }
    }
}
