package com.example

import com.example.jarvis.security.InputValidator
import com.example.jarvis.wakeword.VoiceActivationMode
import com.example.jarvis.wakeword.WakeWordPhraseMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun inputValidatorAcceptsValidHttpsUrlsAndRejectsUnsafeSchemes() {
        assertEquals("https://openai.com", InputValidator.validateHttpUrl("openai.com").getOrNull())
        assertEquals("https://example.org/docs", InputValidator.validateHttpUrl("https://example.org/docs").getOrNull())
        assertTrue(InputValidator.validateHttpUrl("javascript:alert(1)").isFailure)
        assertTrue(InputValidator.validateHttpUrl("file:///etc/passwd").isFailure)
        assertTrue(InputValidator.validateHttpUrl("").isFailure)
    }

    @Test
    fun inputValidatorBoundsVolumeAndDetectsSensitiveAuthPatterns() {
        assertEquals(0, InputValidator.validateVolumePercent(-10))
        assertEquals(100, InputValidator.validateVolumePercent(145))
        assertEquals(65, InputValidator.validateVolumePercent(65))

        assertTrue(InputValidator.isValidPackageName("com.google.android.youtube"))
        assertFalse(InputValidator.isValidPackageName("invalid package!"))

        assertTrue(InputValidator.containsSensitiveAuthPattern("enter password 1234"))
        assertFalse(InputValidator.containsSensitiveAuthPattern("Iron Man trailer"))
    }

    @Test
    fun wakeWordPhraseMatcherDetectsJarvisAndExtractsTrailingCommand() {
        val exact = WakeWordPhraseMatcher.match("Hey JARVIS", "JARVIS")
        assertNotNull(exact)
        assertEquals("hey jarvis", exact?.detectedPhrase)
        assertNull(exact?.trailingCommand)

        val withCommand = WakeWordPhraseMatcher.match("Hey JARVIS, open YouTube!", "JARVIS")
        assertNotNull(withCommand)
        assertEquals("open youtube", withCommand?.trailingCommand)

        val unrelated = WakeWordPhraseMatcher.match("What is the weather in Delhi?", "JARVIS")
        assertNull(unrelated)
    }

    @Test
    fun voiceActivationModeDefaultsToManualOnly() {
        assertEquals(VoiceActivationMode.MANUAL_ONLY, VoiceActivationMode.fromId("UNKNOWN"))
        assertEquals(
            VoiceActivationMode.OPEN_WAKE_WORD_TFLITE,
            VoiceActivationMode.fromId("OPEN_WAKE_WORD_TFLITE")
        )
    }
}
