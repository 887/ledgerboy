package com.eight87.ledgerboy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * L/M — app-level user preferences (DataStore). Base currency is the
 * currency net worth / aggregates are reported in; onboarding-complete
 * gates the first-launch flow.
 *
 * Interface + DataStore impl so the onboarding/settings UI depends on the
 * abstraction (DIP) and tests can use an in-memory fake.
 */
interface AppPreferences {
  val baseCurrency: Flow<String>
  val onboardingComplete: Flow<Boolean>
  suspend fun setBaseCurrency(code: String)
  suspend fun setOnboardingComplete(complete: Boolean)
}

class DataStoreAppPreferences(
  private val dataStore: DataStore<Preferences>,
) : AppPreferences {
  override val baseCurrency: Flow<String> =
    dataStore.data.map { it[BASE_CURRENCY] ?: DEFAULT_BASE }
  override val onboardingComplete: Flow<Boolean> =
    dataStore.data.map { it[ONBOARDED] ?: false }

  override suspend fun setBaseCurrency(code: String) {
    dataStore.edit { it[BASE_CURRENCY] = code }
  }

  override suspend fun setOnboardingComplete(complete: Boolean) {
    dataStore.edit { it[ONBOARDED] = complete }
  }

  private companion object {
    val BASE_CURRENCY = stringPreferencesKey("base_currency")
    val ONBOARDED = booleanPreferencesKey("onboarding_complete")
    const val DEFAULT_BASE = "EUR"
  }
}
