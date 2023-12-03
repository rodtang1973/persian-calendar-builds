package com.byagowi.persiancalendar.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.byagowi.persiancalendar.entities.CalendarType
import com.byagowi.persiancalendar.global.enabledCalendars
import com.byagowi.persiancalendar.global.language
import com.byagowi.persiancalendar.global.mainCalendar
import com.byagowi.persiancalendar.ui.utils.ExtraLargeShapeCornerSize
import com.byagowi.persiancalendar.ui.utils.performHapticFeedbackVirtualKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CalendarsTypesView(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    var onValueChangeListener = fun(_: CalendarType) {}
    private val valueFlow = MutableStateFlow(mainCalendar)
    var value: CalendarType
        get() = valueFlow.value
        set(value) {
            valueFlow.value = value
        }

    init {
        val root = ComposeView(context)
        root.setContent {
            var current by remember { mutableStateOf(mainCalendar) }
            val scope = rememberCoroutineScope()
            remember { scope.launch { valueFlow.collect { if (value != it) value = it } } }
            onValueChangeListener(current)
            valueFlow.value = current
            CalendarsTypes(current) {
                performHapticFeedbackVirtualKey()
                current = it
            }
        }
        addView(root)
    }
}

@Composable
fun CalendarsTypes(current: CalendarType, setCurrent: (CalendarType) -> Unit) {
    // TODO: Should be scrollable?
    TabRow(
        selectedTabIndex = enabledCalendars.indexOf(current),
        divider = {},
        indicator = @Composable { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier
                    .tabIndicatorOffset(tabPositions[enabledCalendars.indexOf(current)])
                    .padding(horizontal = ExtraLargeShapeCornerSize.dp),
                height = 2.dp,
            )
        },
    ) {
        enabledCalendars.forEach { calendarType ->
            val title = stringResource(
                if (language.betterToUseShortCalendarName) calendarType.shortTitle
                else calendarType.title
            )
            Tab(
                text = { Text(title) },
                selected = current == calendarType,
                selectedContentColor = LocalTextStyle.current.color,
                onClick = { setCurrent(calendarType) },
            )
        }
    }
}
