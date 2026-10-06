package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AccessibilityPermissionDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accessibility Permission Required") },
        text = { 
            Text("Alya requires Android Accessibility Service permissions to execute screen automation, UI clicks, and gesture controls. Please enable Alya Automation in your device settings.") 
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(intent)
                    }
                }
            ) {
                Text("Open Accessibility Settings")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
