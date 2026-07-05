package com.princejain.hermroid.automation

import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface ActionResult {
    data class Completed(val description: String) : ActionResult
    data class Failed(val message: String) : ActionResult
    data object RequiresFilePicker : ActionResult
    data object RequiresAccessibilityPermission : ActionResult
}

class AndroidActionExecutor(private val context: Context) {
    fun execute(action: AndroidAction): ActionResult = when (action) {
        is AndroidAction.OpenApp -> openApp(action.appName)
        is AndroidAction.WhatsAppMessage -> openWhatsApp(action.phone, action.message)
        is AndroidAction.Global -> withAccessibility { service ->
            service.global(action.action)
        }
        is AndroidAction.TapText -> withAccessibility { service ->
            service.tapText(action.text)
        }
        is AndroidAction.InputText -> withAccessibility { service ->
            service.inputText(action.text)
        }
        AndroidAction.CompressFiles -> ActionResult.RequiresFilePicker
    }

    private fun openApp(name: String): ActionResult {
        val packageManager = context.packageManager
        val app = packageManager.getInstalledApplications(0).firstOrNull { info ->
            packageManager.getApplicationLabel(info).toString().equals(name, ignoreCase = true) ||
                info.packageName.equals(name, ignoreCase = true)
        } ?: return ActionResult.Failed("App not found: $name")
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return ActionResult.Failed("App cannot be launched: $name")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ActionResult.Completed("Opened ${packageManager.getApplicationLabel(app)}")
    }

    private fun openWhatsApp(phone: String, message: String): ActionResult {
        val normalized = phone.filter(Char::isDigit)
        val uri = Uri.parse("https://wa.me/$normalized").buildUpon()
            .appendQueryParameter("text", message)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent.setPackage("com.whatsapp")) }
            .recoverCatching { context.startActivity(intent.setPackage("com.whatsapp.w4b")) }
            .getOrElse { return ActionResult.Failed("WhatsApp is not installed") }
        return ActionResult.Completed("Opened WhatsApp message for $phone")
    }

    private fun withAccessibility(action: (HermroidAccessibilityService) -> Boolean): ActionResult {
        val service = HermroidAccessibilityService.current()
            ?: return ActionResult.RequiresAccessibilityPermission
        return if (action(service)) ActionResult.Completed("Android action completed")
        else ActionResult.Failed("The requested control was not available on this screen")
    }
}
