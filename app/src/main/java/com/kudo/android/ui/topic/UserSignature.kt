package com.kudo.android.ui.topic

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kudo.android.data.Post
import com.kudo.android.ui.components.AvatarImage
import com.kudo.android.ui.components.mediaUrl
import com.kudo.android.ui.theme.LocalDisplaySettings

internal fun signatureBlocks(value: String): List<ArticleBlock> {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (!trimmed.contains('<')) mediaUrl(trimmed)?.takeIf {
        trimmed.startsWith("https://") || trimmed.startsWith("//") || trimmed.startsWith('/')
    }?.let { return listOf(ArticleBlock.Image(it, "用户签名图片")) }
    return parseArticle(trimmed)
}

@Composable
internal fun UserSignature(post: Post, openLink: (String) -> Unit,
    svgWebViewFactory: (Context) -> WebView = { SignatureImageWebView(it) }) {
    if (!LocalDisplaySettings.current.showSignatures || post.signature.isBlank()) return
    val blocks = remember(post.signature) { signatureBlocks(post.signature) }
    var failedImages by remember(post.signature) { mutableStateOf<Set<String>>(emptySet()) }
    val visible = remember(blocks, failedImages) { usableSignatureBlocks(blocks, failedImages) }
    if (visible.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("user-signature-${post.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SignatureContent(visible, openLink, svgWebViewFactory) { failedImages = failedImages + it }
    }
}

private fun usableSignatureBlocks(blocks: List<ArticleBlock>, failed: Set<String>): List<ArticleBlock> = blocks.mapNotNull { block ->
    when (block) {
        is ArticleBlock.Image -> block.takeUnless { it.url in failed }
        is ArticleBlock.Quote -> usableSignatureBlocks(block.blocks, failed).takeIf { it.isNotEmpty() }?.let { block.copy(blocks = it) }
        is ArticleBlock.Details -> usableSignatureBlocks(block.blocks, failed).takeIf { it.isNotEmpty() }?.let { block.copy(blocks = it) }
        is ArticleBlock.Spoiler -> usableSignatureBlocks(block.blocks, failed).takeIf { it.isNotEmpty() }?.let { block.copy(blocks = it) }
        is ArticleBlock.ListBlock -> block.items.mapNotNull { item ->
            usableSignatureBlocks(item.blocks, failed).takeIf { it.isNotEmpty() }?.let { item.copy(blocks = it) }
        }.takeIf { it.isNotEmpty() }?.let { block.copy(items = it) }
        ArticleBlock.Rule, is ArticleBlock.Poll -> null
        else -> block
    }
}

@Composable
private fun SignatureContent(blocks: List<ArticleBlock>, openLink: (String) -> Unit, svgWebViewFactory: (Context) -> WebView, failed: (String) -> Unit) {
    blocks.forEach { block -> key(block) { when (block) {
            is ArticleBlock.Spoiler -> SpoilerRegion(block, "signature-spoiler") { toggle ->
                SignatureContent(block.blocks, { toggle() }, svgWebViewFactory, failed)
            }
            is ArticleBlock.Details -> {
                var expanded by remember(block) { mutableStateOf(block.open) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(org.jsoup.Jsoup.parse(block.summary).text(), Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    if (expanded) SignatureContent(block.blocks, openLink, svgWebViewFactory, failed)
                }
            }
            is ArticleBlock.Quote -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (block.author.isNotBlank()) Text(block.author, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SignatureContent(block.blocks, openLink, svgWebViewFactory, failed)
            }
            is ArticleBlock.Callout -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SignatureContent(block.blocks, openLink, svgWebViewFactory, failed)
            }
            is ArticleBlock.Image -> {
                val svg = isSvgSignature(block.url)
                var ratio by remember(block.url) { mutableStateOf(if (svg) null else block.ratio) }
                val sizing = ratio?.let { Modifier.aspectRatio(it) }
                    ?: if (svg) Modifier.height(1.dp) else Modifier.aspectRatio(3.8f)
                BoxWithConstraints(Modifier.fillMaxWidth().then(sizing).clip(RoundedCornerShape(6.dp))
                    .semantics { contentDescription = "用户签名图片" }) {
                val pixels = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
                if (svg) SvgSignatureImage(block.url, Modifier.fillMaxSize(), svgWebViewFactory,
                    onAspectRatio = { ratio = it }, onFailure = { failed(block.url) })
                else AvatarImage(block.url, pixels, true, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    onLoaded = { if (!it) failed(block.url) },
                    onAspectRatio = { ratio = it },
                    placeholder = { Text("签名图片", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) })
                }
            }
            is ArticleBlock.TextBlock -> {
                val color = MaterialTheme.colorScheme.primary
                val article = remember(block.html, color) { parseInlineArticle(block.html, color) }
                CompositionLocalProvider(LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) = openLink(uri) }) {
                SpoilerArticleText(article, block.html, "signature-text", MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ArticleBlock.Embed -> Text(block.title, Modifier.clickable { openLink(block.url) }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            is ArticleBlock.Code -> Text(block.text, style = MaterialTheme.typography.bodySmall)
            is ArticleBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEach { item -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.checked?.let { if (it) "☑" else "☐" } ?: item.marker, style = MaterialTheme.typography.bodySmall)
                    Column(Modifier.weight(1f)) { SignatureContent(item.blocks, openLink, svgWebViewFactory, failed) }
                } }
            }
            is ArticleBlock.Table -> Unit
            is ArticleBlock.Poll -> Unit
            ArticleBlock.Rule -> Unit
        } } }
}
