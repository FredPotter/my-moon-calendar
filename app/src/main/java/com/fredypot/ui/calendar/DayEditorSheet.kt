package com.fredypot.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fredypot.R
import com.fredypot.domain.CyclePredictor
import com.fredypot.domain.DayLog
import com.fredypot.domain.LhTest
import com.fredypot.domain.Mucus
import com.fredypot.domain.PeriodMarking
import com.fredypot.domain.Symptom
import com.fredypot.ui.CycleUiState
import com.fredypot.ui.dayTitle
import com.fredypot.ui.label
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorSheet(
    date: LocalDate,
    today: LocalDate,
    state: CycleUiState,
    onMarkStart: () -> Unit,
    onMarkEnd: () -> Unit,
    onDeletePeriod: (Long) -> Unit,
    onSaveLog: (DayLog) -> Unit,
    onDismiss: () -> Unit,
) {
    val log = state.logs[date]
    val kind = state.prediction?.days?.get(date)
    val assumedLength = state.prediction?.stats?.averagePeriodLength ?: CyclePredictor.POPULATION_PERIOD_LENGTH.toInt()
    val period = PeriodMarking.periodAt(state.periods, date, assumedLength)
    val isFuture = date.isAfter(today)

    var symptoms by remember(date) { mutableStateOf(log?.symptoms.orEmpty()) }
    var mucus by remember(date) { mutableStateOf(log?.mucus) }
    var lhTest by remember(date) { mutableStateOf(log?.lhTest) }
    var temperatureText by rememberSaveable(date) { mutableStateOf(log?.temperature?.toString().orEmpty()) }
    val temperature = temperatureText.replace(',', '.').toDoubleOrNull()
    val temperatureError = temperatureText.isNotBlank() && (temperature == null || temperature !in 34.0..43.0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(date.dayTitle().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.headlineSmall)
            kind?.let {
                Text(
                    stringResource(it.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionTitle(R.string.editor_period)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onMarkStart, enabled = !isFuture && period?.start != date) {
                    Text(stringResource(R.string.mark_start))
                }
                FilledTonalButton(onClick = onMarkEnd, enabled = !isFuture && period?.end != date) {
                    Text(stringResource(R.string.mark_end))
                }
                if (period != null) {
                    TextButton(onClick = { onDeletePeriod(period.id) }) {
                        Text(stringResource(R.string.delete_period))
                    }
                }
            }
            if (isFuture) {
                Text(
                    stringResource(R.string.future_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(R.string.editor_symptoms)
            SymptomPicker(selected = symptoms, onChange = { symptoms = it })

            SectionTitle(R.string.editor_mucus)
            SingleChoiceDropdown(
                options = Mucus.entries,
                selected = mucus,
                label = { it.label },
                onSelect = { mucus = it },
            )

            SectionTitle(R.string.editor_lh)
            SingleChoiceDropdown(
                options = LhTest.entries,
                selected = lhTest,
                label = { it.label },
                onSelect = { lhTest = it },
            )

            SectionTitle(R.string.editor_temperature)
            OutlinedTextField(
                value = temperatureText,
                onValueChange = { temperatureText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
                placeholder = { Text("36.6") },
                isError = temperatureError,
                supportingText = if (temperatureError) {
                    { Text(stringResource(R.string.editor_temperature_error)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSaveLog(
                        DayLog(
                            date = date,
                            symptoms = symptoms,
                            mucus = mucus,
                            lhTest = lhTest,
                            temperature = temperature.takeIf { !temperatureError },
                        ),
                    )
                },
                enabled = !temperatureError,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymptomPicker(selected: Set<Symptom>, onChange: (Set<Symptom>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(R.string.editor_symptoms_placeholder),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Symptom.entries.forEach { symptom ->
                val checked = symptom in selected
                DropdownMenuItem(
                    text = { Text(stringResource(symptom.label)) },
                    // Keep the menu open so several symptoms can be picked in a row.
                    onClick = { onChange(if (checked) selected - symptom else selected + symptom) },
                    trailingIcon = {
                        if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
    if (selected.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            selected.sortedBy { it.ordinal }.forEach { symptom ->
                InputChip(
                    selected = true,
                    onClick = { onChange(selected - symptom) },
                    label = { Text(stringResource(symptom.label)) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.remove),
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SingleChoiceDropdown(
    options: List<T>,
    selected: T?,
    label: (T) -> Int,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected?.let(label) ?: R.string.not_set),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.not_set)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(label(option))) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)
