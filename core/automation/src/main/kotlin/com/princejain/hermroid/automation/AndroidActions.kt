package com.princejain.hermroid.automation

enum class ActionRisk { SAFE, INTERACTIVE, EXTERNAL_MESSAGE }

enum class AndroidGlobalAction { BACK, HOME, RECENTS, NOTIFICATIONS }

sealed interface AndroidAction {
    data class OpenApp(val appName: String) : AndroidAction
    data class Global(val action: AndroidGlobalAction) : AndroidAction
    data class TapText(val text: String) : AndroidAction
    data class InputText(val text: String) : AndroidAction
    data class WhatsAppMessage(val phone: String, val message: String) : AndroidAction
    data class SetLauncherColumns(val columns: Int) : AndroidAction
    data object CompressFiles : AndroidAction

    val risk: ActionRisk
        get() = when (this) {
            CompressFiles -> ActionRisk.SAFE
            is WhatsAppMessage -> ActionRisk.EXTERNAL_MESSAGE
            else -> ActionRisk.INTERACTIVE
        }
}

data class ActionPolicy(val trustedMode: Boolean) {
    fun requiresConfirmation(action: AndroidAction): Boolean =
        !trustedMode && action.risk != ActionRisk.SAFE
}
