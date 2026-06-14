package com.eight87.ledgerboy.ui.settings.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.ledgerboy.theme.CategoryAccent
import com.eight87.ledgerboy.theme.accentFor

/**
 * A.6 — settings catalog DSL primitives, the locked tonearmboy shape:
 * `SettingsCard` groups rows; `SettingsRow` is the canonical row with a
 * coloured-circle accent avatar (m3-expressive Phase C). Adding a
 * settings entry is one `SettingsRow(...)` call — the DSL handles the
 * card grouping, dividers, and accent derivation.
 */
object SettingsDimens {
  val PagePadding = 16.dp
  val CardCornerRadius = 16.dp
  val RowVerticalPadding = 14.dp
  val RowHorizontalPadding = 16.dp
  val IconSize = 24.dp
  val IconLabelGap = 16.dp
  val GroupTitleTopPadding = 20.dp
  val GroupTitleBottomPadding = 8.dp
  val CardSpacing = 16.dp
}

/**
 * Material 3 card hosting a group of related rows. Renders with 16-dp
 * corners and a tonal `surfaceContainerHigh` background — the
 * "sitting in the middle" pattern from Android system Settings
 * (m3-expressive gotcha 2 / pattern 1: the tier token does the lift,
 * elevation stays 0).
 */
@Composable
fun SettingsCard(
  modifier: Modifier = Modifier,
  title: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier = modifier) {
    if (title != null) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
          start = SettingsDimens.RowHorizontalPadding,
          top = SettingsDimens.GroupTitleTopPadding,
          bottom = SettingsDimens.GroupTitleBottomPadding,
        ),
      )
    }
    Card(
      shape = RoundedCornerShape(SettingsDimens.CardCornerRadius),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("settings_card"),
    ) {
      Column { content() }
    }
  }
}

/**
 * One row inside a [SettingsCard]. Always has a leading icon (the
 * Android Settings convention is "no orphan rows without icons") plus a
 * label and optional subtitle. The trailing slot hosts the affordance —
 * `Switch`, chevron, picker indicator.
 *
 * m3-expressive gotcha 3: when [accent] is null but [id] is non-null the
 * accent is auto-derived via `accentFor(id)`, so direct callers
 * (About / Licenses) get the avatar without passing it. Pass both null
 * to opt out.
 */
@Composable
fun SettingsRow(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  id: String? = null,
  subtitle: String? = null,
  trailing: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  accent: CategoryAccent? = null,
) {
  val resolvedAccent = accent ?: id?.let { accentFor(it) }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .let { if (onClick != null) it.clickable(onClick = onClick) else it }
      .padding(
        horizontal = SettingsDimens.RowHorizontalPadding,
        vertical = SettingsDimens.RowVerticalPadding,
      )
      .heightIn(min = 48.dp)
      .testTag("settings_row_${id ?: label}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (resolvedAccent != null) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(resolvedAccent.container),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = resolvedAccent.onContainer,
          modifier = Modifier.size(SettingsDimens.IconSize),
        )
      }
    } else {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(SettingsDimens.IconSize),
      )
    }
    Spacer(Modifier.size(SettingsDimens.IconLabelGap))
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (trailing != null) {
      Spacer(Modifier.size(8.dp))
      Box { trailing() }
    }
  }
}

/** Toggle row variant. Tapping anywhere flips the state. */
@Composable
fun SettingsToggleRow(
  icon: ImageVector,
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  id: String? = null,
  subtitle: String? = null,
  accent: CategoryAccent? = null,
) {
  SettingsRow(
    id = id,
    icon = icon,
    label = label,
    subtitle = subtitle,
    onClick = { onCheckedChange(!checked) },
    trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    modifier = modifier,
    accent = accent,
  )
}

/** Subtle divider between rows, indented past the icon column. */
@Composable
fun SettingsRowDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(
      start = SettingsDimens.RowHorizontalPadding +
        SettingsDimens.IconSize +
        SettingsDimens.IconLabelGap,
    ),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}
