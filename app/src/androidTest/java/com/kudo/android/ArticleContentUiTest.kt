package com.kudo.android

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kudo.android.data.DisplaySettings
import com.kudo.android.ui.theme.KudoTheme
import com.kudo.android.ui.topic.RichContent
import org.junit.*
import org.junit.Assert.*

class ArticleContentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun copyingCodePreservesSpacesNewlinesAndEntitiesAndOnlyCopiesThatBlock() {
        val code = "  val x = \"<&>\"\n\tprintln(x)\n"
        compose.setContent { KudoTheme(DisplaySettings()) { Column(Modifier.padding(16.dp)) {
            RichContent("<p>正文不复制</p><pre><code>  val x = &quot;&lt;&amp;&gt;&quot;\n\tprintln(x)\n</code></pre><pre>other()</pre>", {}, {})
        } } }
        compose.onAllNodesWithContentDescription("复制代码")[0].performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(code, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onAllNodesWithContentDescription("复制代码")[1].performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("other()", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }
}
