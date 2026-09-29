package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.ralphthon.haruman_ralphthon.data.*

internal fun budgetTier(bought: Long, goal: Long): Int = when {
    bought > goal -> 4
    bought == goal -> 3
    bought.toDouble() / goal >= .9 -> 2
    bought.toDouble() / goal >= .7 -> 1
    else -> 0
}

internal fun pendingTotal(items: List<WishItem>, excludingId: String? = null): Long = items
    .filter { !it.isSample && it.decision == Decision.PENDING && it.id != excludingId }
    .mapNotNull { it.priceWon?.takeIf { price -> price >= 0L } }
    .fold(0L, ::saturatedAdd)

internal fun saturatedAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
internal fun periodBudgetLabel(period: YardPeriod) = if (period == YardPeriod.WEEK) "이번 주 구매 결정" else "이번 달 구매 결정"
internal fun buyPreview(item: WishItem, bought: Long, goal: Long?): String? {
    if (goal == null || item.isSample) return null
    val price = item.priceWon ?: return "가격이 정해지면 남은 목표를 계산할 수 있어요"
    val after = saturatedAdd(bought, price)
    return if (after > goal) "사면 목표를 ${money(after - goal)} 넘어요" else "사면 남은 목표 ${money((goal - bought).coerceAtLeast(0))} → ${money(goal - after)}"
}

@Composable
internal fun BudgetMiniCard(items: List<WishItem>, now: Long, period: YardPeriod, goalWon: Long?, onClick: () -> Unit) {
    if (goalWon == null) return
    val summary = remember(items, period, periodOf(now, period)) { yardSummary(items, now, period, now) }
    val spent = summary.budgetBoughtWon
    val allUnknown = summary.budgetItems.isNotEmpty() && summary.budgetUnknownCount == summary.budgetItems.size
    val ratio = (spent.toDouble() / goalWon).coerceIn(0.0, 1.0).toFloat()
    Surface(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("vault_storage_open")
        .clickable(role = Role.Button, onClick = onClick), shape = MaterialTheme.shapes.medium, color = AppColors.Surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("모아의 저장방 · ${periodBudgetLabel(period)}", style = MaterialTheme.typography.labelMedium, color = AppColors.Secondary)
            Text("${if (allUnknown) "금액 미정" else money(spent)} / ${money(goalWon)}", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth(), color = if (spent > goalWon) AppColors.Red else AppColors.Blue, trackColor = AppColors.Soft)
            Text(if (allUnknown) "상품 가격을 정하면 남은 목표를 계산해요" else if (spent > goalWon) "목표보다 ${money(spent - goalWon)} 많아요" else "${money(goalWon - spent)} 남았어요", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            if (summary.budgetUnknownCount > 0) Text("금액 미정 ${summary.budgetUnknownCount}개는 계산에서 제외했어요", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        }
    }
}

@Composable
internal fun GoalEditorDialog(goalWon: Long?, period: YardPeriod, onSave: (Long?) -> Boolean, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(goalWon?.toString().orEmpty()) }
    val amount = value.toLongOrNull()
    val valid = value.isBlank() || amount != null && amount in 1..InputLimits.PRICE_WON
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${period.label} 소비 목표") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("하루만에서 사기로 결정한 금액을 모아요. 실제 카드 지출과는 달라요.")
            OutlinedTextField(value, { value = it.replace(",", "").take(13) }, Modifier.fillMaxWidth().testTag("goal_amount"),
                label = { Text("목표 금액 (선택)") }, suffix = { Text("원") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !valid,
                supportingText = { Text(if (valid) "비워 두면 목표 없이 사용할 수 있어요" else "1원부터 ${money(InputLimits.PRICE_WON)}까지 입력해 주세요") })
        } },
        confirmButton = { TextButton(onClick = { if (onSave(if (value.isBlank()) null else amount)) onDismiss() }, enabled = valid, modifier = Modifier.testTag("goal_save")) { Text("저장하기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
