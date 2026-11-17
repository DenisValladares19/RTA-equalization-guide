package org.rtaguide.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
fun <T> OptionSelector(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rowModifier = if (label.isEmpty()) modifier else modifier.fillMaxWidth().padding(vertical = 4.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(labelOf(selected), maxLines = 1) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labelOf(option)) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    format: (Double) -> String,
    onChange: (Double) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    val steps = (((range.endInclusive - range.start) / step).toInt() - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(format(local.toDouble()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(Math.round(local / step) * step) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = steps,
        )
    }
}

/** Numeric text field that reports a value only when it parses. */
@Composable
fun NumberField(label: String, value: Double?, onValue: (Double?) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value?.let { fmt(it) } ?: "") }
    LaunchedEffect(value) {
        val parsed = text.replace(',', '.').toDoubleOrNull()
        if (parsed != value) text = value?.let { fmt(it) } ?: ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            val parsed = it.replace(',', '.').toDoubleOrNull()
            if (parsed != null || it.isBlank()) onValue(parsed)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun fmt(v: Double): String = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e7) v.toLong().toString() else v.toString()

fun formatGain(db: Double): String = when {
    db > 0 -> "+%.1f dB".format(db)
    db < 0 -> "%.1f dB".format(db)
    else -> "0 dB"
}
