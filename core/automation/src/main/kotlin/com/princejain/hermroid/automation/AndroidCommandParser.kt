package com.princejain.hermroid.automation

class AndroidCommandParser {
    fun parse(raw: String): AndroidAction? {
        val text = raw.trim()
        whatsapp.matchEntire(text)?.let { match ->
            val phone = match.groupValues[1].filter { it.isDigit() || it == '+' }
            return AndroidAction.WhatsAppMessage(phone, match.groupValues[2].trim())
        }

        return when {
            text.equals("go back", true) || text.equals("back", true) ->
                AndroidAction.Global(AndroidGlobalAction.BACK)
            text.equals("go home", true) || text.equals("home", true) ->
                AndroidAction.Global(AndroidGlobalAction.HOME)
            text.equals("open recents", true) ->
                AndroidAction.Global(AndroidGlobalAction.RECENTS)
            text.equals("open notifications", true) ->
                AndroidAction.Global(AndroidGlobalAction.NOTIFICATIONS)
            text.equals("compress files", true) || text.equals("zip files", true) ->
                AndroidAction.CompressFiles
            openApp.matches(text) ->
                AndroidAction.OpenApp(openApp.matchEntire(text)!!.groupValues[1].trim().trim('"'))
            tap.matches(text) ->
                AndroidAction.TapText(tap.matchEntire(text)!!.groupValues[1].trim().trim('"'))
            input.matches(text) ->
                AndroidAction.InputText(input.matchEntire(text)!!.groupValues[1].trim().trim('"'))
            else -> null
        }
    }

    private companion object {
        val whatsapp = Regex(
            "^(?:message|send(?: a)? message to)\\s+([+\\d][\\d\\s-]+?)\\s+on\\s+whatsapp\\s+(?:saying\\s+|:\\s*)?(.+)$",
            RegexOption.IGNORE_CASE,
        )
        val openApp = Regex("^(?:open|launch)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val tap = Regex("^(?:tap|press|click)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val input = Regex("^(?:type|enter)\\s+(.+)$", RegexOption.IGNORE_CASE)
    }
}
