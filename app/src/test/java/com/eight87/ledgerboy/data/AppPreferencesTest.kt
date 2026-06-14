package com.eight87.ledgerboy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.eight87.ledgerboy.data.settings.DataStoreAppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** L/M — base currency + onboarding-complete preference round-trip. */
@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {

  private fun newStore(): DataStore<Preferences> {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val file = File(ctx.cacheDir, "test-prefs-${System.nanoTime()}.preferences_pb")
    return PreferenceDataStoreFactory.create { file }
  }

  @Test
  fun `defaults to EUR and not onboarded`() = runTest {
    val prefs = DataStoreAppPreferences(newStore())
    assertEquals("EUR", prefs.baseCurrency.first())
    assertFalse(prefs.onboardingComplete.first())
  }

  @Test
  fun `base currency and onboarding flag persist`() = runTest {
    val prefs = DataStoreAppPreferences(newStore())
    prefs.setBaseCurrency("CHF")
    prefs.setOnboardingComplete(true)
    assertEquals("CHF", prefs.baseCurrency.first())
    assertTrue(prefs.onboardingComplete.first())
  }
}
