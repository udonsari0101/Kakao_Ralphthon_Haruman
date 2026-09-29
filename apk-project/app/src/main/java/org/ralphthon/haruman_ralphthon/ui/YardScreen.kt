package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.R
import org.ralphthon.haruman_ralphthon.data.*
import org.ralphthon.haruman_ralphthon.ui.mascot.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Also used by the vault's budget row. Callers provide the chosen decidedAt period. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageRoomSheet(items: List<WishItem>, periodLabel: String, onItem: (WishItem) -> Unit, onDismiss: () -> Unit) {
    val actual = items.filter { !it.isSample && it.decision == Decision.BUY }.sortedByDescending { it.decidedAt }
    val total = actual.mapNotNull { it.decisionPriceWon?.takeIf { price -> price >= 0 } }.fold(0L) { acc, price -> if (Long.MAX_VALUE - acc < price) Long.MAX_VALUE else acc + price }
    val unknown = actual.count { it.decisionPriceWon == null || it.decisionPriceWon < 0 }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = AppColors.Background) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp).testTag("storage_room_sheet"), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Mascot(MascotPose.MOA_HAPPY, size = 56.dp, breathing = false)
                        Text("모아의 저장방", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text, modifier = Modifier.weight(1f))
                    }
                    Text(periodLabel, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                    Text("${actual.size}개 · ${yardAmount(total, unknown, actual.size)}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Text("이 기간에 사기로 결정한 상품이에요. 실제 주문·결제 내역과는 달라요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    if (unknown > 0) Text("금액 미정 ${unknown}개는 합계에서 뺐어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                }
            }
            if (actual.isEmpty()) item { EmptyState("저장방에 자리가 넉넉해요", "사기로 결정한 상품이 여기에 모여요", MascotPose.MOA_SLEEP, compact = true) }
            items(actual, key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(AppColors.Surface)) {
                    ProductRow(item, item.decidedAt ?: item.createdAt) { onItem(item) }
                    Text("결정할 때 ${money(item.decisionPriceWon)} · ${item.decidedAt?.let(::dateLabel) ?: "결정 날짜 없음"}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                    HorizontalDivider(color = AppColors.Border, thickness = 3.dp)
                }
            }
            item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("storage_room_close")) { Text("닫기") } }
        }
    }
}

private fun calendarAt(time: Long) = Calendar.getInstance().apply { timeInMillis = time }
private fun yardAmount(amount: Long, unknownCount: Int, count: Int): String =
    if (count > 0 && unknownCount == count) "금액 미정" else money(amount)

private fun dayStart(time: Long): Long = calendarAt(time).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun yardPeriodLabel(at: Long, period: YardPeriod): String {
    if (period == YardPeriod.MONTH) return SimpleDateFormat("yyyy년 M월", Locale.KOREA).format(Date(at))
    val end = calendarAt(at).apply { add(Calendar.DAY_OF_MONTH, 6) }.timeInMillis
    val format = SimpleDateFormat("M.d", Locale.KOREA)
    val year = calendarAt(at).get(Calendar.YEAR)
    return "$year · ${format.format(Date(at))} – ${format.format(Date(end))}"
}
