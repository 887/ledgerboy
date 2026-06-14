package com.eight87.ledgerboy.data

import com.eight87.ledgerboy.data.category.CategoryRuleEngine
import com.eight87.ledgerboy.data.db.CategoryRuleEntity
import com.eight87.ledgerboy.data.db.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** G — the auto-categorization rule engine. */
class CategoryRuleEngineTest {

  private fun rule(id: String, cat: String, kind: RuleKind, pattern: String, prio: Int = 100) =
    CategoryRuleEntity(id, cat, kind, pattern, prio)

  @Test
  fun `payee contains is case-insensitive`() {
    val rules = listOf(rule("r1", "rent", RuleKind.PayeeContains, "westvale"))
    assertEquals("rent", CategoryRuleEngine.categorize("WESTVALE Properties", "a1", rules))
    assertNull(CategoryRuleEngine.categorize("Globex", "a1", rules))
  }

  @Test
  fun `payee regex matches and invalid regex never matches`() {
    val ok = listOf(rule("r1", "fuel", RuleKind.PayeeRegex, "(?i)shell|aral|esso"))
    assertEquals("fuel", CategoryRuleEngine.categorize("ARAL Station 12", "a1", ok))
    val bad = listOf(rule("r1", "x", RuleKind.PayeeRegex, "([unterminated"))
    assertNull(CategoryRuleEngine.categorize("anything", "a1", bad))
  }

  @Test
  fun `account equals matches the account id`() {
    val rules = listOf(rule("r1", "savings-cat", RuleKind.AccountEquals, "acct-savings"))
    assertEquals("savings-cat", CategoryRuleEngine.categorize(null, "acct-savings", rules))
    assertNull(CategoryRuleEngine.categorize(null, "acct-other", rules))
  }

  @Test
  fun `first matching rule wins in list order`() {
    val rules = listOf(
      rule("r1", "groceries", RuleKind.PayeeContains, "market", prio = 10),
      rule("r2", "everything", RuleKind.PayeeContains, "a", prio = 20),
    )
    assertEquals("groceries", CategoryRuleEngine.categorize("Farmers Market", "a1", rules))
  }

  @Test
  fun `no rules yields null`() {
    assertNull(CategoryRuleEngine.categorize("anything", "a1", emptyList()))
  }
}
