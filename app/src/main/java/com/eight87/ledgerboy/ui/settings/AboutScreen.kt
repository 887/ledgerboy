package com.eight87.ledgerboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.BuildConfig
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRow
import kotlinx.coroutines.launch

/**
 * A.7 — About screen. App identity, version + build SHA/date (from
 * BuildConfig), source link, license, and the open-source-licenses
 * navigator. The build row hosts the R.1 triple-tap easter egg.
 * Editorial register: plain, factual (KYIS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  onBack: () -> Unit,
  onOpenLicenses: () -> Unit,
) {
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val easterEgg = remember { EasterEggController() }
  val firstPrompt = stringResource(R.string.about_easter_first)
  val secondPrompt = stringResource(R.string.about_easter_second)
  val revealText = stringResource(R.string.app_name)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.about_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = SettingsDimens.PagePadding),
      verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
    ) {
      // App identity header.
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Filled.InsertChart,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(64.dp),
        )
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(top = 12.dp),
        )
        Text(
          text = stringResource(R.string.about_app_blurb),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
      }

      SettingsCard {
        SettingsRow(
          id = "about.version",
          icon = Icons.Outlined.Numbers,
          label = stringResource(R.string.about_version_label),
          subtitle = BuildConfig.VERSION_NAME,
        )
        SettingsRow(
          id = "about.build",
          icon = Icons.Outlined.Code,
          label = stringResource(R.string.about_build_label),
          subtitle = "${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_DATE}",
          onClick = {
            when (easterEgg.tap(System.currentTimeMillis())) {
              EasterEggController.Outcome.FirstPromptSnackbar ->
                scope.launch { snackbar.showSnackbar(firstPrompt) }
              EasterEggController.Outcome.SecondPromptSnackbar ->
                scope.launch { snackbar.showSnackbar(secondPrompt) }
              EasterEggController.Outcome.Reveal ->
                scope.launch { snackbar.showSnackbar(revealText) }
            }
          },
        )
      }

      SettingsCard {
        SettingsRow(
          id = "about.source",
          icon = Icons.Outlined.Code,
          label = stringResource(R.string.about_source_label),
          subtitle = stringResource(R.string.about_source_value),
        )
        SettingsRow(
          id = "about.license",
          icon = Icons.Outlined.Gavel,
          label = stringResource(R.string.about_license_label),
          subtitle = stringResource(R.string.about_license_value),
        )
        SettingsRow(
          id = "about.oss",
          icon = Icons.AutoMirrored.Outlined.Article,
          label = stringResource(R.string.about_oss_label),
          subtitle = stringResource(R.string.about_oss_sub),
          onClick = onOpenLicenses,
        )
      }
    }
  }
}
