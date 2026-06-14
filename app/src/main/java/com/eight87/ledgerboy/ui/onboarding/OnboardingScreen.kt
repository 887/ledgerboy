@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.eight87.ledgerboy.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.R

/**
 * L — first-launch onboarding. Plain, factual copy (KYIS): welcome +
 * the privacy posture, then pick a base currency. Finishing persists the
 * choice and flips the onboarding-complete flag so the rail takes over.
 * No vibes copy, no account-on-our-server step.
 */
private val BASE_CURRENCIES = listOf("EUR", "USD", "GBP", "CHF", "JPY", "CAD", "AUD")

@Composable
fun OnboardingScreen(
  onFinish: (baseCurrency: String) -> Unit,
) {
  var selected by remember { mutableStateOf("EUR") }
  Scaffold { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(48.dp))
      Icon(
        Icons.Filled.InsertChart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp),
      )
      Text(
        stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 16.dp),
      )
      Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
      )

      Spacer(Modifier.height(32.dp))
      Text(
        stringResource(R.string.onboarding_base_currency_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.align(Alignment.Start),
      )
      Text(
        stringResource(R.string.onboarding_base_currency_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Start).padding(top = 4.dp),
      )
      FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        BASE_CURRENCIES.forEach { code ->
          FilterChip(
            selected = selected == code,
            onClick = { selected = code },
            label = { Text(code) },
          )
        }
      }

      Spacer(Modifier.height(24.dp))
      Button(
        onClick = { onFinish(selected) },
        modifier = Modifier.fillMaxWidth(),
      ) { Text(stringResource(R.string.onboarding_get_started)) }
      Text(
        stringResource(R.string.onboarding_next_step),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
      )
    }
  }
}
