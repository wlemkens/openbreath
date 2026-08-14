package io.github.wlemkens.openbreath

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The two pieces of chrome more than one screen draws. They lived in Settings.kt, which is
 * still Android's because of the file picker and the intents; these two never were.
 */

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
internal fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    readout: String,
    onChangeFinished: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(readout, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}
