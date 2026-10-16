package com.kudo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kudo.android.data.Topic
import com.kudo.android.data.compactNumber

@Composable
internal fun TopicUnreadBadge(topic: Topic) {
    val blue = Color(0xFF3D8BFF)
    if (topic.unreadPosts > 0) Text(compactNumber(topic.unreadPosts), Modifier
        .background(blue.copy(alpha = 0.12f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)
        .testTag("topic-unread-${topic.id}").semantics { contentDescription = "${topic.unreadPosts} 条新回复" },
        color = blue, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    else if (topic.unseen) Box(Modifier.padding(horizontal = 3.dp).size(7.dp).background(blue, CircleShape)
        .testTag("topic-unread-${topic.id}").semantics { contentDescription = "未看过的话题" })
}
