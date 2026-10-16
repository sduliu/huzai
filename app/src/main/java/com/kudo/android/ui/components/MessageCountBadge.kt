package com.kudo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudo.android.ui.theme.Ink
import com.kudo.android.ui.theme.LogoYellow

@Composable
fun MessageCountBadge(count: Int, modifier: Modifier = Modifier, sampling: Boolean = false) {
    if (count <= 0) return
    Box(modifier.defaultMinSize(minWidth = 16.dp, minHeight = 16.dp).background(LogoYellow, CircleShape)
        .then(if (sampling) Modifier else Modifier.testTag("message-count-badge").semantics { contentDescription = "$count 条未读消息" })
        .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else count.toString(), fontSize = 9.sp, lineHeight = 12.sp, color = Ink)
    }
}
