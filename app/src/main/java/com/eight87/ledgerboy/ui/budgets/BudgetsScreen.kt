@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.eight87.ledgerboy.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.budget.MonthRange
import com.eight87.ledgerboy.data.db.BudgetEntity
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import kotlinx.coroutines.launch

/**
 * H — the Budgets screen (rail destination). Shows each category-monthly
 * budget's progress for the current month (spent / limit, a bar, over-
 * budget flagged in the error colour) and lets the user add a budget by
 * picking a category and a monthly limit.
 */
@Composable
fun BudgetsScreen(
  ledger: LedgerRepository,
  modifier: Modifier = Modifier,
  now: () -> Long = { System.currentTimeMillis() },
) {
  val (monthStart, monthEnd) = remember { MonthRange.of(now()) }
  val progress by ledger.observeBudgetProgress(monthStart, monthEnd)
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val categories by ledger.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
  val budgets by ledger.observeBudgets().collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()

  val budgetedCategoryIds = budgets.map { it.categoryId }.toSet()
  val available = categories.filterNot { it.id in budgetedCategoryIds }
  var pickedCategory by remember { mutableStateOf<String?>(null) }
  var amount by remember { mutableStateOf("") }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = SettingsDimens.PagePadding),
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(4.dp))

    if (progress.isEmpty()) {
      Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(stringResource(R.string.budgets_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
          stringResource(R.string.budgets_empty_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    } else {
      progress.forEach { p ->
        SettingsCard {
          Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(p.categoryName, style = MaterialTheme.typography.titleSmall)
              Text(
                stringResource(R.string.budgets_spent_of, p.spent.format(), p.limit.format()),
                style = MaterialTheme.typography.bodySmall,
                color = if (p.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            LinearProgressIndicator(
              progress = { p.fraction },
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
              color = if (p.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (p.over) {
              Text(
                stringResource(R.string.budgets_over),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
          }
        }
      }
    }

    // Add-budget card.
    SettingsCard(title = stringResource(R.string.budgets_add_title)) {
      Column(Modifier.padding(16.dp)) {
        if (categories.isEmpty()) {
          Text(
            stringResource(R.string.budgets_no_categories),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            available.forEach { category ->
              FilterChip(
                selected = pickedCategory == category.id,
                onClick = { pickedCategory = category.id },
                label = { Text(category.name) },
              )
            }
          }
          OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(stringResource(R.string.budgets_amount_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          )
          Button(
            enabled = pickedCategory != null && parseMajorToMinor(amount) != null,
            onClick = {
              val cat = pickedCategory ?: return@Button
              val minor = parseMajorToMinor(amount) ?: return@Button
              scope.launch {
                ledger.upsertBudget(
                  BudgetEntity(id = "budget-$cat", categoryId = cat, limitMinor = minor, currency = "EUR"),
                )
              }
              amount = ""; pickedCategory = null
            },
            modifier = Modifier.padding(top = 8.dp),
          ) { Text(stringResource(R.string.budgets_add)) }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

/** Parse a user-entered major amount ("12,50" / "12.5") into 2-digit minor units. */
internal fun parseMajorToMinor(raw: String): Long? {
  val s = raw.trim().replace(',', '.')
  if (s.isEmpty() || s.none { it.isDigit() }) return null
  val parts = s.split('.')
  if (parts.size > 2) return null
  val intPart = parts[0].ifEmpty { "0" }
  val fracRaw = parts.getOrNull(1) ?: ""
  if (!intPart.all { it.isDigit() } || !fracRaw.all { it.isDigit() }) return null
  val frac = when {
    fracRaw.length == 2 -> fracRaw
    fracRaw.length > 2 -> fracRaw.substring(0, 2)
    else -> fracRaw.padEnd(2, '0')
  }
  return (intPart + frac).toLongOrNull()
}
