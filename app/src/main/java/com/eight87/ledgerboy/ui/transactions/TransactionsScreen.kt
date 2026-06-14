@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eight87.ledgerboy.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.model.Category
import com.eight87.ledgerboy.data.model.Transaction
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Sign filter for the transaction list. */
enum class TxnFilter { ALL, INCOME, EXPENSE }

/**
 * F — the transactions screen for one account. Chronological (newest
 * first), filterable by sign and payee search; tapping a row opens a
 * detail sheet that assigns / clears the category. Income is rendered in
 * the primary colour, expense in the error colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
  ledger: LedgerRepository,
  accountId: String,
  accountName: String,
  onBack: () -> Unit,
) {
  val all by ledger.observeTransactionsForAccount(accountId)
    .collectAsStateWithLifecycle(initialValue = emptyList())
  val categories by ledger.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()

  var filter by remember { mutableStateOf(TxnFilter.ALL) }
  var query by remember { mutableStateOf("") }
  var detailFor by remember { mutableStateOf<Transaction?>(null) }

  val filtered = all.filter { txn ->
    val signOk = when (filter) {
      TxnFilter.ALL -> true
      TxnFilter.INCOME -> txn.amount.minorUnits >= 0
      TxnFilter.EXPENSE -> txn.amount.minorUnits < 0
    }
    val queryOk = query.isBlank() || (txn.payee?.contains(query, ignoreCase = true) == true)
    signOk && queryOk
  }

  Scaffold(
    topBar = {
      androidx.compose.material3.TopAppBar(
        title = { Text(accountName) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(filter == TxnFilter.ALL, { filter = TxnFilter.ALL }, { Text(stringResource(R.string.transactions_filter_all)) })
        FilterChip(filter == TxnFilter.INCOME, { filter = TxnFilter.INCOME }, { Text(stringResource(R.string.transactions_filter_income)) })
        FilterChip(filter == TxnFilter.EXPENSE, { filter = TxnFilter.EXPENSE }, { Text(stringResource(R.string.transactions_filter_expense)) })
      }
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.transactions_search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )

      if (filtered.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            stringResource(R.string.transactions_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp),
          )
        }
      } else {
        val categoryName = categories.associate { it.id to it.name }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(filtered, key = { it.id }) { txn ->
            TransactionRow(txn, categoryName[txn.categoryId]) { detailFor = txn }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
      }
    }
  }

  detailFor?.let { txn ->
    TransactionDetailSheet(
      txn = txn,
      categories = categories,
      onAssign = { categoryId ->
        scope.launch { ledger.assignCategory(txn.id, categoryId) }
        detailFor = null
      },
      onDismiss = { detailFor = null },
    )
  }
}

@Composable
private fun TransactionRow(txn: Transaction, categoryName: String?, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = txn.payee ?: txn.description ?: "—",
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${formatDate(txn.bookedAtEpochMillis)} · ${categoryName ?: stringResource(R.string.transaction_uncategorized)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
      text = txn.amount.format(),
      style = MaterialTheme.typography.titleSmall,
      color = if (txn.amount.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.padding(start = 12.dp),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransactionDetailSheet(
  txn: Transaction,
  categories: List<Category>,
  onAssign: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
      Text(txn.payee ?: txn.description ?: "—", style = MaterialTheme.typography.titleMedium)
      Text(txn.amount.format(), style = MaterialTheme.typography.headlineSmall)
      txn.description?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        stringResource(R.string.transaction_detail_category),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
      )
      if (categories.isEmpty()) {
        Text(
          stringResource(R.string.transaction_detail_no_categories),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          categories.forEach { category ->
            FilterChip(
              selected = txn.categoryId == category.id,
              onClick = { onAssign(category.id) },
              label = { Text(category.name) },
            )
          }
        }
        if (txn.categoryId != null) {
          TextButton(onClick = { onAssign(null) }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.transaction_detail_clear_category))
          }
        }
      }
    }
  }
}

private fun formatDate(epochMillis: Long): String =
  DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())
