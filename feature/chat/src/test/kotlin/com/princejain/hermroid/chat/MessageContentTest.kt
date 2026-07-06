package com.princejain.hermroid.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageContentTest {
    @Test fun `splits prose and fenced code with language`() {
        val segments = parseMessageSegments("Here you go:\n```python\nprint('hi')\n```\nDone.")

        assertEquals(
            listOf(
                MessageSegment.Prose("Here you go:"),
                MessageSegment.Code("python", "print('hi')"),
                MessageSegment.Prose("Done."),
            ),
            segments,
        )
    }

    @Test fun `keeps ordinary text and tolerates unclosed fence`() {
        assertEquals(listOf(MessageSegment.Prose("Hello")), parseMessageSegments("Hello"))
        assertEquals(
            listOf(MessageSegment.Prose("Before"), MessageSegment.Code("", "unfinished")),
            parseMessageSegments("Before\n```\nunfinished"),
        )
    }
}
