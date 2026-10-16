package com.kudo.android.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Font Awesome Free glyphs from Linux.do's sprite; attribution in assets/licenses/fontawesome-icons-LICENSE.txt. */
internal object BadgeIcons {
    @Volatile private var glyphs: JSONObject? = null
    private val vectors = ConcurrentHashMap<String, ImageVector>()

    fun get(context: Context, icon: String): ImageVector {
        val name = icon.trim().removePrefix("fa-").let { if (it == "vote-up-filled") "thumbs-up" else it }
            .ifBlank { "certificate" }
        return vectors.getOrPut(name) {
            val data = glyphs ?: synchronized(this) {
                glyphs ?: context.assets.open("badge-icons.json").bufferedReader().use { JSONObject(it.readText()) }.also { glyphs = it }
            }
            val glyph = data.optJSONObject(name) ?: return@getOrPut CategoryIcons.get("award")
            val box = glyph.getJSONArray("viewBox")
            val width = box.getDouble(2).toFloat()
            val height = box.getDouble(3).toFloat()
            val scale = 24f / maxOf(width, height)
            ImageVector.Builder(name, (width * scale).dp, (height * scale).dp, width, height).apply {
                val paths = glyph.getJSONArray("paths")
                repeat(paths.length()) { addPath(PathParser().parsePathString(paths.getString(it)).toNodes(), fill = SolidColor(Color.Black)) }
            }.build()
        }
    }
}
