package com.kudo.android.ui.topic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.kudo.android.data.GlassMode
import com.kudo.android.ui.components.*
import com.kudo.android.ui.components.liquid.LiquidButton
import com.kudo.android.ui.components.liquid.LiquidSlider
import com.kyant.backdrop.Backdrop
import kotlin.math.roundToInt

@Composable
internal fun FloorNavigationBar(
    raw: String,
    change: (String) -> Unit,
    highest: Int,
    jumping: Boolean,
    error: String?,
    jump: (Int) -> Unit,
    backdrop: Backdrop,
    mode: GlassMode,
    modifier: Modifier = Modifier,
    openingFloor: Int = 1,
    close: () -> Unit = {},
) {
    val number = raw.toIntOrNull()
    val valid = number != null && number in 1..highest
    val unchanged = number == openingFloor
    val inputStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val inputWidth = with(LocalDensity.current) {
        textMeasurer.measure("0".repeat(maxOf(2, highest.toString().length)), inputStyle).size.width.toDp() + 8.dp
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        LiquidButton(onClick = null, backdrop = backdrop, mode = mode, isInteractive = false,
            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .45f),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().testTag("floor-navigation-bar")) {
            LiquidSlider(
                value = { (number ?: 1).coerceIn(1, highest).toFloat() },
                onValueChange = { change(it.roundToInt().coerceIn(1, highest).toString()) },
                valueRange = 1f..highest.toFloat(), backdrop = backdrop,
                enabled = !jumping,
                modifier = Modifier.weight(1f).height(48.dp).testTag("floor-slider")
                    .semantics {
                        contentDescription = "选择楼层"
                        progressBarRangeInfo = ProgressBarRangeInfo((number ?: 1).coerceIn(1, highest).toFloat(), 1f..highest.toFloat())
                        if (jumping) disabled()
                        setProgress { target -> if (!jumping) { change(target.roundToInt().coerceIn(1, highest).toString()); true } else false }
                    })
            Row(verticalAlignment = Alignment.CenterVertically) {
                CommunityTextField(raw,
                    { input -> if (input.length <= 10 && input.all { it in '0'..'9' }) change(input) },
                    Modifier.width(inputWidth).padding(vertical = 8.dp).testTag("floor-input"),
                    singleLine = true, placeholder = "楼层", enabled = !jumping,
                    textStyle = inputStyle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("/$highest", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            IconButton(onClick = { if (unchanged) close() else if (valid) number?.let(jump) }, enabled = (unchanged || valid) && !jumping,
                modifier = Modifier.size(48.dp).testTag("floor-confirm")) {
                Icon(if (unchanged) Icons.Rounded.Close else Icons.Rounded.Check,
                    if (unchanged) "关闭楼层选择" else "确定楼层")
            }
        }
        if (error != null) Text(error, Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            maxLines = 2)
        else if (!valid && raw.isNotEmpty()) Text("请输入 1 至 $highest 的楼层", Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            maxLines = 2)
    }
}
