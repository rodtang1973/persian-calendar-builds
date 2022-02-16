package com.byagowi.persiancalendar.ui.preferences

import android.app.Activity
import android.text.style.TextAppearanceSpan
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.byagowi.persiancalendar.ui.utils.sp

fun showTypographyDemoDialog(activity: Activity) {
    val text = buildSpannedString {
        textAppearances.forEach { (appearanceName, appearanceId) ->
            val textAppearance = TextAppearanceSpan(activity, appearanceId)
            inSpans(textAppearance) { append(appearanceName) }
            append(" (${textAppearance.textSize / 1.sp}sp)")
            appendLine()
        }
    }
    AlertDialog.Builder(activity).setView(TextView(activity).also { it.text = text }).show()
}

private val textAppearances = listOf(
    "DisplayLarge" to com.google.android.material.R.style.TextAppearance_Material3_DisplayLarge,
    "DisplayMedium" to com.google.android.material.R.style.TextAppearance_Material3_DisplayMedium,
    "DisplaySmall" to com.google.android.material.R.style.TextAppearance_Material3_DisplaySmall,
    "HeadlineLarge" to com.google.android.material.R.style.TextAppearance_Material3_HeadlineLarge,
    "HeadlineMedium" to com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium,
    "HeadlineSmall" to com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall,
    "TitleLarge" to com.google.android.material.R.style.TextAppearance_Material3_TitleLarge,
    "TitleMedium" to com.google.android.material.R.style.TextAppearance_Material3_TitleMedium,
    "TitleSmall" to com.google.android.material.R.style.TextAppearance_Material3_TitleSmall,
    "BodyLarge" to com.google.android.material.R.style.TextAppearance_Material3_BodyLarge,
    "BodyMedium" to com.google.android.material.R.style.TextAppearance_Material3_BodyMedium,
    "BodySmall" to com.google.android.material.R.style.TextAppearance_Material3_BodySmall,
    "LabelLarge" to com.google.android.material.R.style.TextAppearance_Material3_LabelLarge,
    "LabelMedium" to com.google.android.material.R.style.TextAppearance_Material3_LabelMedium,
    "LabelSmall" to com.google.android.material.R.style.TextAppearance_Material3_LabelSmall
)
