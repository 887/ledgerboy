package com.eight87.ledgerboy.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import com.eight87.ledgerboy.ui.settings.catalog.SettingsRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A.7 — Open-source licenses sub-screen. Renders the inventory generated
 * by the Licensee Gradle plugin at build time
 * (`assets/licenses/artifacts.json`); tapping a row shows the SPDX id +
 * any canonical license text shipped at `assets/licenses/<spdx>.txt`.
 * Copied from tonearmboy; reads only string resources.
 */

@Serializable
private data class LicenseeArtifact(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val name: String? = null,
  val spdxLicenses: List<LicenseeSpdx> = emptyList(),
)

@Serializable
private data class LicenseeSpdx(
  val identifier: String,
  val name: String? = null,
  val url: String? = null,
)

data class LicenseEntry(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val spdxId: String?,
  val licenseText: String?,
)

private val licenseeJson = Json { ignoreUnknownKeys = true }

internal fun loadLicensesFromAssets(context: Context): List<LicenseEntry> {
  val raw = runCatching {
    context.assets.open("licenses/artifacts.json").bufferedReader().use { it.readText() }
  }.getOrNull() ?: return emptyList()
  val artifacts = runCatching {
    licenseeJson.decodeFromString<List<LicenseeArtifact>>(raw)
  }.getOrNull() ?: return emptyList()

  val textCache = mutableMapOf<String, String?>()
  fun resolveText(spdx: String?): String? {
    if (spdx == null) return null
    return textCache.getOrPut(spdx) {
      runCatching {
        context.assets.open("licenses/$spdx.txt").bufferedReader().use { it.readText() }
      }.getOrNull()
    }
  }

  return artifacts.map { a ->
    val spdx = a.spdxLicenses.firstOrNull()?.identifier
    LicenseEntry(
      groupId = a.groupId,
      artifactId = a.artifactId,
      version = a.version,
      spdxId = spdx,
      licenseText = resolveText(spdx),
    )
  }.sortedWith(compareBy({ it.groupId }, { it.artifactId }))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val entries = remember(context) { loadLicensesFromAssets(context) }
  var expanded by remember { mutableStateOf<LicenseEntry?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.licenses_screen_title)) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("licenses_back"),
          ) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    if (entries.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(SettingsDimens.PagePadding)
          .testTag("licenses_empty"),
      ) {
        Text(
          text = stringResource(R.string.licenses_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      return@Scaffold
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("licenses_screen"),
      contentPadding = PaddingValues(
        start = SettingsDimens.PagePadding,
        end = SettingsDimens.PagePadding,
        top = 12.dp,
        bottom = 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
    ) {
      items(entries, key = { "${it.groupId}:${it.artifactId}" }) { entry ->
        SettingsCard(title = "${entry.artifactId} ${entry.version}") {
          SettingsRow(
            id = "license.${entry.groupId}.${entry.artifactId}",
            icon = Icons.AutoMirrored.Outlined.Article,
            label = entry.groupId,
            subtitle = entry.spdxId ?: stringResource(R.string.licenses_unknown_spdx),
            onClick = { expanded = entry },
          )
        }
      }
    }
  }

  expanded?.let { entry ->
    AlertDialog(
      onDismissRequest = { expanded = null },
      title = { Text("${entry.artifactId} ${entry.version}") },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("license_dialog_body"),
        ) {
          Text(
            text = entry.spdxId ?: stringResource(R.string.licenses_unknown_spdx),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (entry.licenseText != null) {
            Text(
              modifier = Modifier.padding(top = 12.dp),
              text = entry.licenseText,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { expanded = null }) {
          Text(stringResource(R.string.licenses_dialog_close))
        }
      },
    )
  }
}
