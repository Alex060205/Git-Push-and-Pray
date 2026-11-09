package com.example.vitala.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vitala.ui.theme.*

private val Corner = RoundedCornerShape(4.dp)

@Composable private fun hc() = LocalHighContrast.current
@Composable private fun borderW() = if (hc()) 3.dp else 1.dp
@Composable fun borderColor() = if (hc()) Color.Black else BorderGrey
@Composable fun secondaryText() = if (hc()) Color.Black else TextSecondary
@Composable fun actionColor() = if (hc()) BrandBlueDark else BrandBlue

/** Small two-tone blue and green accent bar at the top of every screen. */
@Composable
fun AccentBar() {
    Row(Modifier.fillMaxWidth().height(5.dp)) {
        Box(Modifier.weight(0.82f).fillMaxSize().background(actionColor()))
        Box(Modifier.weight(0.18f).fillMaxSize().background(BrandGreen))
    }
}

/** Tab bar for pages opened from a tab; empty inside flows so a tap can't drop unsaved work. */
val LocalTabBar = androidx.compose.runtime.staticCompositionLocalOf<@Composable () -> Unit> { {} }

/**
 * Standard screen: accent bar, optional back row, scrolling body, and pinned buttons at the bottom.
 * One decision per screen: put the single main action in [actions].
 */
@Composable
fun Page(
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    topRight: String? = null,
    steps: Pair<Int, Int>? = null,
    bottomBar: @Composable () -> Unit = LocalTabBar.current,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        AccentBar()
        if (onBack != null || topRight != null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = actionColor())
                        Spacer(Modifier.width(4.dp))
                        Text("Back", color = actionColor(), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (topRight != null) Text(topRight, color = secondaryText(), modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (steps != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 1..steps.second) {
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (i <= steps.first) actionColor() else BorderGrey))
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, color = secondaryText(), style = MaterialTheme.typography.bodyLarge)
            content()
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) { actions() }
        bottomBar()
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick, enabled = enabled, shape = Corner,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = actionColor(), contentColor = Color.White,
            disabledContainerColor = BorderGrey, disabledContentColor = TextSecondary)
    ) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = Corner,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        border = BorderStroke(2.dp, if (enabled) actionColor() else BorderGrey),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = actionColor())
    ) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick, shape = Corner, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        border = BorderStroke(2.dp, Alert), colors = ButtonDefaults.outlinedButtonColors(contentColor = Alert)
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Text(text, color = actionColor(), textDecoration = TextDecoration.Underline, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- panels and notices

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Panel, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Corner).background(if (hc()) Color.White else color)
            .border(borderW(), borderColor(), Corner).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content
    )
}

@Composable
fun GreenPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Corner).background(GreenTint).border(borderW(), BrandGreen, Corner).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content
    )
}

@Composable
fun AmberNotice(title: String? = null, text: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().clip(Corner).background(AmberTint).border(2.dp, FocusAmber, Corner).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.WarningAmber, null, tint = AmberText, modifier = Modifier.size(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) Text(title, color = AmberText, style = MaterialTheme.typography.titleSmall)
            Text(text, color = AmberText, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

/** SF-1 style message: alert left border, icon and plain words. */
@Composable
fun SafetyPanel(text: String, title: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(Corner).border(borderW(), borderColor(), Corner).background(Color.White)
    ) {
        Box(Modifier.width(5.dp).fillMaxSize().background(Alert))
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.WarningAmber, "Warning", tint = Alert, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = Alert)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = if (hc()) Color.Black else BrandBlueDark, style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
}

/** An in-page question: short bold title, with the detail in a small line underneath. */
@Composable
fun Question(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = secondaryText())
    }
}

@Composable
fun Hint(text: String) = Text(text, color = secondaryText(), style = MaterialTheme.typography.bodySmall)

@Composable
fun Busy(show: Boolean, label: String = "Working…") {
    if (show) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), color = actionColor())
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) SafetyPanel(text)
}

// ---------------------------------------------------------------- choices

/** Large tappable 0-10 grid (no slider). Selected = filled blue with a check mark. */
@Composable
fun NumberGrid(selected: Int?, onSelect: (Int) -> Unit, low: String, high: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..10).chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { n ->
                    val sel = selected == n
                    Box(
                        Modifier.weight(1f).height(64.dp).clip(Corner)
                            .background(if (sel) actionColor() else bandColor(n))
                            .border(if (sel) 3.dp else borderW(), if (sel) BrandBlueDark else borderColor(), Corner)
                            .semantics { role = Role.RadioButton; this.selected = sel; contentDescription = "$n" + if (sel) ", selected" else "" }
                            .clickable { onSelect(n) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$n", style = MaterialTheme.typography.titleLarge, color = if (sel) Color.White else TextPrimary)
                        if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(low, style = MaterialTheme.typography.bodySmall, color = secondaryText(), modifier = Modifier.weight(1f))
            Text(high, style = MaterialTheme.typography.bodySmall, color = secondaryText(), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        if (selected != null) Panel { Text("You chose: $selected", style = MaterialTheme.typography.titleSmall) }
    }
}

@Composable
fun OptionCard(selected: Boolean, title: String, onClick: () -> Unit, subtitle: String? = null, badge: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Corner)
            .background(if (selected) (if (hc()) Color.White else Color(0xFFE9F3FA)) else Color.White)
            .border(if (selected) 3.dp else borderW(), if (selected) actionColor() else borderColor(), Corner)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) Text(badge, color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(actionColor()).padding(horizontal = 10.dp, vertical = 2.dp))
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
    }
}

@Composable
fun CheckCard(checked: Boolean, title: String, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Corner).background(Color.White)
            .border(if (checked) 3.dp else borderW(), if (checked) actionColor() else borderColor(), Corner)
            .semantics { role = Role.Checkbox; selected = checked }
            .clickable { onChange(!checked) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp),
            colors = CheckboxDefaults.colors(checkedColor = actionColor()))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
    }
}

/** Selected chip = filled blue with a check mark (not colour alone). */
@Composable
fun ChoiceChip(selected: Boolean, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 56.dp).clip(Corner).background(if (selected) actionColor() else Color.White)
            .border(borderW() + 1.dp, if (selected) BrandBlueDark else borderColor(), Corner)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (selected) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = if (selected) Color.White else TextPrimary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(Corner).background(Panel).border(borderW(), borderColor(), Corner)
            .clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
        Text(if (checked) "On" else "Off", color = secondaryText(), modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = actionColor()))
    }
}

@Composable
fun ListRow(title: String, subtitle: String? = null, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(Corner).background(Panel).border(borderW(), borderColor(), Corner)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
        if (trailing != null) { trailing(); Spacer(Modifier.width(6.dp)) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = actionColor(), modifier = Modifier.size(28.dp))
    }
}

/** Checklist-style status shown next to each plan field: OK / Check / Missing (icon + word, never colour alone). */
@Composable
fun StatusPill(status: String) {
    val (label, bg, fg) = when (status) {
        "OK" -> Triple("OK", GreenTint, GreenDark)
        "CHECK" -> Triple("Check", AmberTint, AmberText)
        else -> Triple("Missing", AlertTint, Alert)
    }
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (status == "OK") { Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(2.dp)) }
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- inputs

@Composable
fun LabeledField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    placeholder: String? = null, helper: String? = null, keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true, minLines: Int = 1, password: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = singleLine, minLines = minLines,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = Corner,
            placeholder = placeholder?.let { { Text(it, color = secondaryText()) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = actionColor(), unfocusedBorderColor = if (hc()) Color.Black else Color(0xFF7A8791),
                focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
        if (helper != null) Hint(helper)
    }
}

@Composable
fun Stepper(value: String, unit: String, onMinus: () -> Unit, onPlus: () -> Unit, label: String) {
    Row(
        Modifier.fillMaxWidth().clip(Corner).background(Panel).border(borderW(), borderColor(), Corner).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleSmall) }
        StepButton(Icons.Outlined.Remove, "Less", onMinus)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 48.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = secondaryText())
        }
        StepButton(Icons.Outlined.Add, "More", onPlus)
    }
}

private fun Modifier.widthIn(min: androidx.compose.ui.unit.Dp) = this.then(Modifier.defaultMinSize(minWidth = min))

@Composable
private fun StepButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(Corner).background(Color.White).border(2.dp, actionColor(), Corner)
            .semantics { role = Role.Button; contentDescription = desc }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = actionColor(), modifier = Modifier.size(32.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val parts = initial.split(":")
    val state = rememberTimePickerState(parts[0].toIntOrNull() ?: 9, parts.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) { Text("OK", style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } },
        text = { TimePicker(state = state) }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) Alert else actionColor(), style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) } }
    )
}

fun fmtClock(hhmm: String): String = runCatching { com.example.vitala.app.AppViewModel.fmtTime(java.time.LocalTime.parse(hhmm)) }.getOrDefault(hhmm)
