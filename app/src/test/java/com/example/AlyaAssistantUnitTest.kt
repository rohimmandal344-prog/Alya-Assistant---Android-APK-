package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.ActionImpactLevel
import com.example.core.model.AlyaError
import com.example.core.model.ConversationMessage
import com.example.core.model.DeviceActionType
import com.example.core.model.MessageRole
import com.example.core.model.MessageStatus
import com.example.core.model.VoiceState
import com.example.device.CapabilityRegistry
import com.example.device.DeviceController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlyaAssistantUnitTest {

    private lateinit var context: Context
    private lateinit var deviceController: DeviceController
    private lateinit var registry: CapabilityRegistry

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        deviceController = DeviceController(context)
        registry = CapabilityRegistry(context, deviceController)
    }

    @Test
    fun testNaturalLanguageParsing_FlashlightOn() {
        val intent = registry.parseNaturalLanguage("Please turn on flashlight")
        assertNotNull(intent)
        assertEquals(DeviceActionType.TOGGLE_FLASHLIGHT, intent?.action)
        assertEquals("true", intent?.parameters?.get("enable"))
        assertEquals(ActionImpactLevel.SAFE, intent?.impactLevel)
    }

    @Test
    fun testNaturalLanguageParsing_FlashlightOff() {
        val intent = registry.parseNaturalLanguage("flashlight off")
        assertNotNull(intent)
        assertEquals(DeviceActionType.TOGGLE_FLASHLIGHT, intent?.action)
        assertEquals("false", intent?.parameters?.get("enable"))
    }

    @Test
    fun testNaturalLanguageParsing_Volume() {
        val upIntent = registry.parseNaturalLanguage("turn volume up")
        assertNotNull(upIntent)
        assertEquals(DeviceActionType.SET_VOLUME, upIntent?.action)
        assertEquals("up", upIntent?.parameters?.get("direction"))

        val downIntent = registry.parseNaturalLanguage("volume down")
        assertNotNull(downIntent)
        assertEquals(DeviceActionType.SET_VOLUME, downIntent?.action)
        assertEquals("down", downIntent?.parameters?.get("direction"))
    }

    @Test
    fun testNaturalLanguageParsing_YouTubeSearch() {
        val intent = registry.parseNaturalLanguage("search youtube for Minecraft tutorial")
        assertNotNull(intent)
        assertEquals(DeviceActionType.SEARCH_YOUTUBE, intent?.action)
        assertEquals("minecraft tutorial", intent?.target?.lowercase())
    }

    @Test
    fun testNaturalLanguageParsing_Navigation() {
        val intent = registry.parseNaturalLanguage("navigate to Central Park")
        assertNotNull(intent)
        assertEquals(DeviceActionType.OPEN_MAPS_NAVIGATE, intent?.action)
        assertEquals("central park", intent?.target?.lowercase())
    }

    @Test
    fun testNaturalLanguageParsing_Settings() {
        val wifiIntent = registry.parseNaturalLanguage("open wifi settings")
        assertEquals(DeviceActionType.OPEN_WIFI_SETTINGS, wifiIntent?.action)

        val btIntent = registry.parseNaturalLanguage("open bluetooth settings")
        assertEquals(DeviceActionType.OPEN_BLUETOOTH_SETTINGS, btIntent?.action)

        val accessIntent = registry.parseNaturalLanguage("open accessibility settings")
        assertEquals(DeviceActionType.OPEN_ACCESSIBILITY_SETTINGS, accessIntent?.action)
    }

    @Test
    fun testNaturalLanguageParsing_Gestures() {
        val scrollIntent = registry.parseNaturalLanguage("scroll down")
        assertEquals(DeviceActionType.ACCESSIBILITY_SCROLL, scrollIntent?.action)
        assertEquals("forward", scrollIntent?.parameters?.get("direction"))

        val tapIntent = registry.parseNaturalLanguage("tap search button")
        assertEquals(DeviceActionType.ACCESSIBILITY_CLICK, tapIntent?.action)
        assertEquals("search button", tapIntent?.target)

        val typeIntent = registry.parseNaturalLanguage("type Hello World")
        assertEquals(DeviceActionType.ACCESSIBILITY_TYPE, typeIntent?.action)
        assertEquals("Hello World", typeIntent?.target)
    }

    @Test
    fun testAlyaErrorHierarchy() {
        val netErr = AlyaError.Network("Network failed", "Timeout")
        assertEquals("NETWORK", netErr.source)
        assertTrue(netErr.recoverable)
        assertTrue(netErr.retryable)

        val permErr = AlyaError.Permission("Mic permission required", "RECORD_AUDIO missing")
        assertEquals("PERMISSION", permErr.source)
        assertTrue(permErr.recoverable)

        val accessErr = AlyaError.Accessibility("Service disabled", "Instance null")
        assertEquals("ACCESSIBILITY", accessErr.source)
    }

    @Test
    fun testConversationMessageModel() {
        val msg = ConversationMessage(
            id = 1,
            role = MessageRole.USER,
            content = "Turn on flashlight",
            status = MessageStatus.SENT
        )
        assertEquals(MessageRole.USER, msg.role)
        assertEquals("Turn on flashlight", msg.content)
        assertEquals(MessageStatus.SENT, msg.status)
    }

    @Test
    fun testVoiceStateTransitions() {
        val states = VoiceState.values()
        assertTrue(states.contains(VoiceState.IDLE))
        assertTrue(states.contains(VoiceState.LISTENING))
        assertTrue(states.contains(VoiceState.THINKING))
        assertTrue(states.contains(VoiceState.SPEAKING))
        assertTrue(states.contains(VoiceState.INTERRUPTED))
    }
}
