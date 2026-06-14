package com.eight87.ledgerboy.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eight87.ledgerboy.R
import com.eight87.ledgerboy.data.LedgerRepository
import com.eight87.ledgerboy.data.budget.MonthRange
import com.eight87.ledgerboy.data.charts.ChartsData
import com.eight87.ledgerboy.data.money.CurrencyCode
import com.eight87.ledgerboy.data.money.Money
import com.eight87.ledgerboy.theme.accentFor
import com.eight87.ledgerboy.ui.settings.catalog.SettingsCard
import com.eight87.ledgerboy.ui.settings.catalog.SettingsDimens

/**
 * K — the Charts screen (rail destination). Hand-rolled `Canvas`
 * Composables (dataviz-decision.md sanctions Canvas for the custom chart
 * layer; Vico is the chosen lib for the richer interactive time-series to
 * come). v0 ships the two highest-value views: spending-by-category
 * (donut + legend) for the month, and income-vs-expense cashflow bars
 * over six months. Both read M3 tokens + the `CategoryAccent` palette so
 * the visual language matches the rest of the app.
 */
@Composable
fun ChartsScreen(
  ledger: LedgerRepository,
  modifier: Modifier = Modifier,
  now: () -> Long = { System.currentTimeMillis() },
) {
  val transactions by ledger.observeTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
  val categories by ledger.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
  val uncategorized = stringResource(R.string.transaction_uncategorized)

  val nowMs = remember { now() }
  val (monthStart, monthEnd) = remember { MonthRange.of(nowMs) }
  val categoryNames = categories.associate { it.id to it.name }

  val slices = ChartsData.spendingByCategory(
    transactions, categoryNames, monthStart, monthEnd, currencyCode = "EUR", uncategorizedLabel = uncategorized,
  )
  val cashflow = ChartsData.monthlyCashflow(transactions, nowMs, months = 6, currencyCode = "EUR")

  if (transactions.isEmpty()) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Text(stringResource(R.string.charts_empty_title), style = MaterialTheme.typography.titleMedium)
      Text(
        stringResource(R.string.charts_empty_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
    return
  }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SettingsDimens.PagePadding),
    verticalArrangement = Arrangement.spacedBy(SettingsDimens.CardSpacing),
  ) {
    Spacer(Modifier.height(4.dp))
    SettingsCard(title = stringResource(R.string.charts_spending_title)) {
      Column(Modifier.padding(16.dp)) {
        if (slices.isEmpty()) {
          Text(
            stringResource(R.string.charts_spending_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          val colors = slices.map { accentFor(it.categoryId ?: it.label).container }
          Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            DonutChart(values = slices.map { it.amountMinor }, colors = colors, modifier = Modifier.size(160.dp))
          }
          Spacer(Modifier.height(12.dp))
          slices.forEachIndexed { i, slice ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colors[i]))
              Spacer(Modifier.size(8.dp))
              Text(slice.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
              Text(Money(slice.amountMinor, CurrencyCode.EUR).format(), style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }

    SettingsCard(title = stringResource(R.string.charts_cashflow_title)) {
      Column(Modifier.padding(16.dp)) {
        CashflowBars(
          cashflow = cashflow,
          incomeColor = MaterialTheme.colorScheme.primary,
          expenseColor = MaterialTheme.colorScheme.error,
          axisColor = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
          LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.charts_income))
          Spacer(Modifier.size(16.dp))
          LegendDot(MaterialTheme.colorScheme.error, stringResource(R.string.charts_expense))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          cashflow.forEach { Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
    Spacer(Modifier.size(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun DonutChart(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier) {
  val total = values.sum().coerceAtLeast(1L)
  Canvas(modifier = modifier) {
    val stroke = Stroke(width = size.minDimension * 0.18f)
    val inset = stroke.width / 2
    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
    var startAngle = -90f
    values.forEachIndexed { i, v ->
      val sweep = (v.toFloat() / total) * 360f
      drawArc(
        color = colors[i % colors.size],
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = stroke,
      )
      startAngle += sweep
    }
  }
}

@Composable
private fun CashflowBars(
  cashflow: List<com.eight87.ledgerboy.data.charts.MonthCashflow>,
  incomeColor: Color,
  expenseColor: Color,
  axisColor: Color,
  modifier: Modifier = Modifier,
) {
  val max = cashflow.flatMap { listOf(it.incomeMinor, it.expenseMinor) }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
  Canvas(modifier = modifier) {
    val n = cashflow.size.coerceAtLeast(1)
    val groupWidth = size.width / n
    val barWidth = groupWidth * 0.30f
    val gap = groupWidth * 0.08f
    val baseline = size.height
    // axis
    drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
    cashflow.forEachIndexed { i, m ->
      val cx = i * groupWidth + groupWidth / 2
      val incH = (m.incomeMinor.toFloat() / max) * (size.height * 0.92f)
      val expH = (m.expenseMinor.toFloat() / max) * (size.height * 0.92f)
      drawRect(
        color = incomeColor,
        topLeft = Offset(cx - barWidth - gap / 2, baseline - incH),
        size = Size(barWidth, incH),
      )
      drawRect(
        color = expenseColor,
        topLeft = Offset(cx + gap / 2, baseline - expH),
        size = Size(barWidth, expH),
      )
    }
  }
}
