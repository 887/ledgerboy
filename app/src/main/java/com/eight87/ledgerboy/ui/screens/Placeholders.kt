package com.eight87.ledgerboy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A.5 — placeholder destination body. Every top-level rail destination
 * (Overview / Accounts / Budgets / Assets / Charts) renders one of these
 * until its real surface lands in a later phase. Centred icon + title +
 * one-line description of what the screen will hold; editorial register
 * is plain and factual (no vibes copy).
 */
@Composable
fun PlaceholderScreen(
  icon: ImageVector,
  title: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(56.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 16.dp),
    )
    Text(
      text = description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}
