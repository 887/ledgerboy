package com.eight87.ledgerboy.data.category

import com.eight87.ledgerboy.data.db.CategoryRuleEntity
import com.eight87.ledgerboy.data.db.RuleKind

/**
 * G — the auto-categorization engine. Pure Kotlin (no Android, no Room),
 * fully unit-testable. Rules are evaluated in the order given (the DAO
 * returns them sorted by ascending priority); the first match wins.
 *
 * Match kinds:
 *  - [RuleKind.PayeeContains] — case-insensitive substring of the payee.
 *  - [RuleKind.PayeeRegex] — regex over the payee (invalid regex → never matches).
 *  - [RuleKind.AccountEquals] — exact account id.
 */
object CategoryRuleEngine {

  /** Returns the categoryId of the first matching rule, or null. */
  fun categorize(
    payee: String?,
    accountId: String,
    rules: List<CategoryRuleEntity>,
  ): String? = rules.firstOrNull { it.matches(payee, accountId) }?.categoryId

  private fun CategoryRuleEntity.matches(payee: String?, accountId: String): Boolean =
    when (kind) {
      RuleKind.PayeeContains ->
        payee != null && pattern.isNotBlank() && payee.contains(pattern, ignoreCase = true)
      RuleKind.PayeeRegex ->
        payee != null && runCatching { Regex(pattern).containsMatchIn(payee) }.getOrDefault(false)
      RuleKind.AccountEquals ->
        accountId == pattern
    }
}
