package com.princejain.hermroid.chat

sealed interface MessageSegment {
    data class Prose(val text: String) : MessageSegment
    data class Code(val language: String, val code: String) : MessageSegment
}

fun parseMessageSegments(source: String): List<MessageSegment> {
    if ("```" !in source) return listOf(MessageSegment.Prose(source.trim()))
    val result = mutableListOf<MessageSegment>()
    var cursor = 0
    while (cursor < source.length) {
        val opening = source.indexOf("```", cursor)
        if (opening < 0) {
            source.substring(cursor).trim().takeIf(String::isNotEmpty)?.let { result += MessageSegment.Prose(it) }
            break
        }
        source.substring(cursor, opening).trim().takeIf(String::isNotEmpty)?.let { result += MessageSegment.Prose(it) }
        val headerEnd = source.indexOf('\n', opening + 3)
        if (headerEnd < 0) {
            result += MessageSegment.Code("", source.substring(opening + 3).trim())
            break
        }
        val language = source.substring(opening + 3, headerEnd).trim()
        val closing = source.indexOf("```", headerEnd + 1)
        if (closing < 0) {
            result += MessageSegment.Code(language, source.substring(headerEnd + 1).trim())
            break
        }
        result += MessageSegment.Code(language, source.substring(headerEnd + 1, closing).trimEnd())
        cursor = closing + 3
    }
    return result.ifEmpty { listOf(MessageSegment.Prose(source.trim())) }
}
