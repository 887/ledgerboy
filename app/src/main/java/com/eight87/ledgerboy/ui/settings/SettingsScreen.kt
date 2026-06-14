package com.eight87.ledgerboy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRow
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRowDivider

/**
 * A.6 — Settings root. Renders inside the rail shell (the shell owns the
 * top bar showing "Settings"), so this is a plain scrollable column of
 * grouped [SettingsCard]s built from the catalog DSL.
 *
 * Most rows are "coming soon" placeholders until their owning phase
 * lands (base currency → Phase D, Plugins → Phase X/M, Import → Phase B,
 * Privacy/Security → Phase M). About navigates to the About sub-page.
 * Adding a real setting later is one [SettingsRow] swapped from a
 * coming-soon subtitle to a wired `onClick`.
 */
@Composable
fun SettingsScreen(
  onOpenAbout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val comingSoon = stringResource(R.string.settings_coming_soon)
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = SettingsDimens.PagePadding),
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(4.dp))

    SettingsCard(title = stringResource(R.string.settings_group_general)) {
      SettingsRow(
        id = "settings.base_currency",
        icon = Icons.Outlined.CurrencyExchange,
        label = stringResource(R.string.settings_base_currency),
        subtitle = comingSoon,
      )
      SettingsRowDivider()
      SettingsRow(
        id = "settings.appearance",
        icon = Icons.Outlined.Palette,
        label = stringResource(R.string.settings_appearance),
        subtitle = comingSoon,
      )
    }

    SettingsCard(title = stringResource(R.string.settings_group_connectors)) {
      SettingsRow(
        id = "settings.plugins",
        icon = Icons.Outlined.Extension,
        label = stringResource(R.string.settings_plugins),
        subtitle = comingSoon,
      )
      SettingsRowDivider()
      SettingsRow(
        id = "settings.import",
        icon = Icons.Outlined.UploadFile,
        label = stringResource(R.string.settings_import),
        subtitle = comingSoon,
      )
    }

    SettingsCard(title = stringResource(R.string.settings_group_data)) {
      SettingsRow(
        id = "settings.privacy",
        icon = Icons.Outlined.Shield,
        label = stringResource(R.string.settings_privacy),
        subtitle = comingSoon,
      )
      SettingsRowDivider()
      SettingsRow(
        id = "settings.security",
        icon = Icons.Outlined.Lock,
        label = stringResource(R.string.settings_security),
        subtitle = comingSoon,
      )
    }

    SettingsCard(title = stringResource(R.string.settings_group_about)) {
      SettingsRow(
        id = "settings.about",
        icon = Icons.Outlined.Info,
        label = stringResource(R.string.settings_about),
        subtitle = stringResource(R.string.settings_about_sub),
        onClick = onOpenAbout,
      )
    }

    Spacer(Modifier.height(8.dp))
  }
}
