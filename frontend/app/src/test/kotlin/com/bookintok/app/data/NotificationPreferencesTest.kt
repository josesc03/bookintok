package com.bookintok.app.data

import androidx.test.core.app.ApplicationProvider
import com.bookintok.app.core.legal.LegalTexts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPreferencesTest {

    @Test
    fun `each kind of notification can be turned off and the choice is kept`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = NotificationPreferences(context)
        assertTrue(preferences.allows("message") && preferences.allows("exchange"))

        preferences.setMessages(false)

        val reloaded = NotificationPreferences(context)
        assertFalse(reloaded.allows("message"))
        assertTrue(reloaded.allows("exchange"))
    }

    @Test
    fun `legal documents always name a contact`() {
        listOf(LegalTexts.privacy, LegalTexts.terms).forEach { document ->
            assertTrue(document.sections.any { section -> section.paragraphs.any { LegalTexts.email in it } })
        }
    }
}
