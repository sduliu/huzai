package com.kudo.android.data

import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale

data class TagIconStyle(val icon: String, val color: String = "")

internal fun parseTagIconSettings(html: String): Map<String, TagIconStyle> {
    val preload = Jsoup.parse(html).selectFirst("[data-preloaded]")?.attr("data-preloaded") ?: return emptyMap()
    val settings = JSONObject(JSONObject(preload).stringField("themeSettings").ifBlank { "{}" })
    return buildMap {
        settings.keys().asSequence().forEach { theme ->
            settings.optJSONObject(theme)?.optJSONObject("settings")?.stringField("tag_icon_list")
                ?.split('|')?.take(512)?.forEach { row ->
                    val parts = row.split(',').map(String::trim)
                    val name = parts.getOrNull(0).orEmpty()
                    val icon = parts.getOrNull(1).orEmpty()
                    val color = parts.getOrNull(2).orEmpty()
                    if (name.isNotEmpty() && icon.matches(Regex("[a-zA-Z0-9-]+"))) {
                        put(name.lowercase(Locale.ROOT), TagIconStyle(icon,
                            color.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }.orEmpty()))
                    }
                }
        }
    }
}

internal fun TopicTag.iconStyle(styles: Map<String, TagIconStyle>): TagIconStyle? =
    if (icon.isNotBlank()) TagIconStyle(icon, color)
    else styles[slug.lowercase(Locale.ROOT)] ?: styles[name.lowercase(Locale.ROOT)]
