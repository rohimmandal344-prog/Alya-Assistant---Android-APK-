package com.example.device

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityHelper {

    /**
     * Helper function to detect if the app has Android Accessibility Service permissions enabled.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (AlyaAutomationService.instance != null) {
            return true
        }
        val expectedServiceName = ComponentName(context, AlyaAutomationService::class.java).flattenToString()
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
