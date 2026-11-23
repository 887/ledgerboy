@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.eight87.ledgerboy.ui.categories

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.db.CategoryEntity
import com.eight87.ledgerboy.data.db.CategoryRuleEntity
import com.eight87.ledgerboy.data.db.RuleKind
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens
import kotlinx.coroutines.launch

/**
 * G — categories + auto-categorization rules management. Add categories,
 * add "payee contains → category" rules, and apply rules to existing
 * uncategorized transactions. New imports are auto-categorized by the
 * repository; this screen is where the user curates the taxonomy.
 */
@Composable
fun CategoriesScreen(
  ledger: LedgerRepository,
  onBack: () -> Unit,
  clock: () -> Long = { System.currentTimeMillis() },
) {
  val categories by ledger.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
  val rules by ledger.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }

  var newCategory by remember { mutableStateOf("") }
  var rulePattern by remember { mutableStateOf("") }
  var ruleCategoryId by remember { mutableStateOf<String?>(null) }
  val resources = LocalResources.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.categories_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
      Spacer(Modifier.height(4.dp))

      // ── Categories ──
      SettingsCard(title = stringResource(R.string.categories_section_categories)) {
        Column(Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = newCategory,
              onValueChange = { newCategory = it },
              label = { Text(stringResource(R.string.categories_add_hint)) },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Button(
              enabled = newCategory.isNotBlank(),
              onClick = {
                val name = newCategory.trim()
                val id = "cat-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') + "-" + clock()
                scope.launch { ledger.upsertCategory(CategoryEntity(id = id, name = name)) }
                newCategory = ""
              },
            ) { Text(stringResource(R.string.categories_add_button)) }
          }
          if (categories.isEmpty()) {
            Text(
              stringResource(R.string.categories_empty),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 8.dp),
            )
          } else {
            FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              categories.forEach { category ->
                FilterChip(selected = false, onClick = {}, label = { Text(category.name) })
              }
            }
          }
        }
      }

      // ── Rules ──
      SettingsCard(title = stringResource(R.string.categories_section_rules)) {
        Column(Modifier.padding(16.dp)) {
          if (categories.isEmpty()) {
            Text(
              stringResource(R.string.categories_no_rules),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            OutlinedTextField(
              value = rulePattern,
              onValueChange = { rulePattern = it },
              label = { Text(stringResource(R.string.categories_rule_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              stringResource(R.string.categories_rule_pick),
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              categories.forEach { category ->
                FilterChip(
                  selected = ruleCategoryId == category.id,
                  onClick = { ruleCategoryId = category.id },
                  label = { Text(category.name) },
                )
              }
            }
            Button(
              enabled = rulePattern.isNotBlank() && ruleCategoryId != null,
              onClick = {
                val pattern = rulePattern.trim()
                val catId = ruleCategoryId ?: return@Button
                scope.launch {
                  ledger.upsertRule(
                    CategoryRuleEntity(
                      id = "rule-" + clock(),
                      categoryId = catId,
                      kind = RuleKind.PayeeContains,
                      pattern = pattern,
                    ),
                  )
                }
                rulePattern = ""
              },
              modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringResource(R.string.categories_rule_add)) }
          }

          // Existing rules
          val catName = categories.associate { it.id to it.name }
          rules.forEach { rule ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                stringResource(R.string.categories_rule_summary, rule.pattern, catName[rule.categoryId] ?: rule.categoryId),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
              )
              IconButton(onClick = { scope.launch { ledger.deleteRule(rule.id) } }) {
                Icon(Icons.Filled.Close, contentDescription = null)
              }
            }
          }
        }
      }

      OutlinedButton(
        onClick = {
          scope.launch {
            val n = ledger.applyRulesToUncategorized()
            snackbar.showSnackbar(resources.getQuantityString(R.plurals.categories_applied, n, n))
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) { Text(stringResource(R.string.categories_apply_rules)) }

      Spacer(Modifier.height(8.dp))
    }
  }
}
