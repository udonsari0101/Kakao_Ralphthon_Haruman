package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.data.Decision
import org.ralphthon.haruman_ralphthon.data.WishItem
import org.ralphthon.haruman_ralphthon.data.skippedTotal

@Composable
fun DetailScreen(
    item: WishItem,
    now: Long,
    onBack: () -> Unit,
    onOriginal: () -> Unit,
    onDecide: () -> Unit
) {
    val pending = item.decision == Decision.PENDING
    Column(Modifier.fillMaxSize().background(AppColors.Surface)) {
        DecisionBackButton(onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                ProductArtwork(item.artwork, Modifier.size(80.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
                    Text("${money(item.priceWon)} · ${item.platform.label}",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Secondary)
                    if (item.id.startsWith("sample-")) {
                        Spacer(Modifier.height(8.dp))
                        Pill("예시 상품", AppColors.Soft, AppColors.Muted)
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
            if (pending) {
                Text("다시 생각하기까지", style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                Text(if (item.ready(now)) "지금" else remaining(item, now),
                    Modifier.padding(top = 8.dp), fontSize = 34.sp, lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold, color = AppColors.Text)
                LinearProgressIndicator(
                    progress = { item.progress(now) },
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = AppColors.Blue, trackColor = AppColors.BlueTint
                )
                Text(if (item.ready(now)) "이제 마음을 다시 확인해 봐요." else "지금까지 ${(item.progress(now) * 100).toInt()}% 지났어요",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            } else {
                Pill(if (item.decision == Decision.SKIP) "구매 포기" else "구매 결정",
                    if (item.decision == Decision.SKIP) AppColors.GreenTint else AppColors.BlueTint,
                    if (item.decision == Decision.SKIP) AppColors.Green else AppColors.Blue)
                Text(if (item.decision == Decision.SKIP) "이번에는 사지 않기로 했어요." else "구매하기로 결정했어요.",
                    Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
            }
            Spacer(Modifier.height(40.dp))
            HorizontalDivider(color = AppColors.Border)
            DetailInfoRow("저장한 날", dateLabel(item.createdAt))
            DetailInfoRow("원본 플랫폼", item.platform.label)
            if (pending) DetailInfoRow("다시 보는 시간", if (item.ready(now)) "결정할 수 있어요" else timeLabel(item.cooldownUntil))
            else DetailInfoRow("결정한 날", dateLabel(item.decidedAt ?: item.createdAt))
            HorizontalDivider(color = AppColors.Border)
            if (item.id.startsWith("sample-")) {
                Text("앱을 둘러볼 수 있도록 준비한 예시예요. 직접 저장한 상품은 원본 링크를 열 수 있어요.",
                    Modifier.padding(top = 18.dp), style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted)
            }
            Spacer(Modifier.height(32.dp))
        }
        DecisionFooter {
            PrimaryAction(if (item.ready(now)) "결정하기" else "원본 보기",
                if (item.ready(now)) onDecide else onOriginal, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DecideScreen(
    item: WishItem,
    onBack: () -> Unit,
    onExtend: () -> Unit,
    onDecision: (Decision) -> Unit
) {
    Column(Modifier.fillMaxSize().background(AppColors.Surface)) {
        DecisionBackButton(onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProductArtwork(item.artwork, Modifier.size(104.dp))
            Text("${dateLabel(item.createdAt)}에 저장했어요", Modifier.padding(top = 28.dp),
                style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
            Text("잠시 생각해 봤어요.\n지금도 사고 싶나요?", Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.headlineLarge, color = AppColors.Text,
                textAlign = TextAlign.Center)
            Text(item.name, Modifier.padding(top = 24.dp), style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center, color = AppColors.Text)
            Text("${money(item.priceWon)} · ${item.platform.label}", Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary,
                textAlign = TextAlign.Center)
            if (item.id.startsWith("sample-")) {
                Spacer(Modifier.height(12.dp))
                Pill("예시 상품", AppColors.Soft, AppColors.Muted)
            }
            TextButton(onClick = onExtend, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp).testTag("extend_item")) {
                Text("조금 더 고민할래요", color = AppColors.Blue, fontWeight = FontWeight.Medium)
            }
        }
        DecisionFooter {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction("안 살래요", { onDecision(Decision.SKIP) },
                    Modifier.weight(1f).testTag("decide_skip"), weak = true)
                PrimaryAction("살래요", { onDecision(Decision.BUY) },
                    Modifier.weight(1f).testTag("decide_buy"))
            }
        }
    }
}

@Composable
fun ResultScreen(
    item: WishItem,
    items: List<WishItem>,
    now: Long,
    onHome: () -> Unit,
    onOriginal: () -> Unit
) {
    val skipped = item.decision == Decision.SKIP
    val monthlyTotal = skippedTotal(items.filter { sameMonth(it.decidedAt ?: it.createdAt, now) })
    Column(Modifier.fillMaxSize().background(AppColors.Surface)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(64.dp).background(if (skipped) AppColors.GreenTint else AppColors.BlueTint, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(if (skipped) "check" else "external", Modifier.size(30.dp), if (skipped) AppColors.Green else AppColors.Blue)
            }
            Text(if (skipped) "이번에는\n사지 않기로 했어요" else "구매하기로\n결정했어요",
                Modifier.padding(top = 28.dp), style = MaterialTheme.typography.headlineLarge,
                color = AppColors.Text, textAlign = TextAlign.Center)
            Text(if (skipped) {
                if (item.priceWon == null) "나에게 필요한 소비인지 생각해 봤어요." else "${money(item.priceWon)}을 쓰지 않기로 했어요."
            } else "원래 보던 곳에서 상품을 확인할 수 있어요.",
                Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge,
                color = AppColors.Secondary, textAlign = TextAlign.Center)
            if (skipped) {
                Column(Modifier.padding(top = 32.dp).fillMaxWidth().background(AppColors.Background, RoundedCornerShape(14.dp)).padding(20.dp)) {
                    Text("이번 달 쓰지 않기로 한 금액", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    Text(money(monthlyTotal), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.headlineMedium,
                        color = AppColors.Text)
                    if (item.priceWon == null) Text("가격을 입력하지 않은 상품은 합계에 포함하지 않아요.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }
            if (item.id.startsWith("sample-")) {
                Text("예시 상품으로 기록을 살펴보고 있어요.", Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, textAlign = TextAlign.Center)
            }
        }
        DecisionFooter {
            if (skipped) {
                PrimaryAction("보관함으로", onHome, Modifier.fillMaxWidth().testTag("result_home"))
            } else {
                PrimaryAction("원본에서 보기", onOriginal, Modifier.fillMaxWidth())
                TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("result_home")) {
                    Text("보관함으로", color = AppColors.Secondary)
                }
            }
        }
    }
}

@Composable
private fun DecisionBackButton(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "뒤로가기" }) {
            AppIcon("back")
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
        Text(value, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = AppColors.Text, textAlign = TextAlign.End)
    }
}

@Composable
private fun DecisionFooter(content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(color = AppColors.Border)
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), content = content)
}
