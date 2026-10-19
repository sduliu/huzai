package com.kudo.android.data

internal fun postQuoteMarkdown(topicId: Int, post: Post, selectedText: String): String {
    val author = post.author.displayName.ifBlank { post.author.username }
    val quoted = selectedText.lines().joinToString("\n") { "> $it" }
    return "引用 @$author 发表于 ${post.number} 楼：\n$quoted"
}

internal fun appendPostQuote(draft: String, quote: String): String {
    val separator = when {
        draft.isEmpty() || draft.endsWith("\n\n") -> ""
        draft.endsWith('\n') -> "\n"
        else -> "\n\n"
    }
    return "$draft$separator$quote\n\n"
}
