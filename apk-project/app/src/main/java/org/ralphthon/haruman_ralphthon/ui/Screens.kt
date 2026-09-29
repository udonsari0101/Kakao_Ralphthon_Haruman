package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun money(value: Long?) = value?.let { String.format(Locale.KOREA, "%,d원", it) } ?: "가격 미정"
fun dateLabel(value: Long): String = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(value))
fun timeLabel(value: Long): String = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA).format(Date(value))
fun sameMonth(value: Long, now: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = value }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
}
fun remaining(item: WishItem, now: Long): String {
    val minutes = (item.remainingMillis(now) + 59_999) / 60_000
    return when {
        minutes <= 0 -> "지금"
        minutes >= 1440 -> "${minutes / 1440}일 ${minutes % 1440 / 60}시간"
        minutes >= 60 -> "${minutes / 60}시간 ${minutes % 60}분"
        else -> "${minutes}분"
    }
}
fun cooldownLabel(hours: Int) = when(hours) { 0 -> "시간 없이"; 3 -> "3시간 뒤"; 24 -> "내일"; 72 -> "3일 뒤"; 168 -> "일주일 뒤"; else -> "${hours}시간 뒤" }

@Composable fun BottomTabs(route: String, onChange: (String) -> Unit) {
    Surface(color = AppColors.Surface) {
        Column {
            HorizontalDivider(color = AppColors.Border, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp).padding(horizontal = 14.dp)) {
                listOf(Triple("vault", "box", "보관함"), Triple("history", "history", "기록"), Triple("my", "person", "MY")).forEach { (id, icon, label) ->
                    val color = if (route == id) AppColors.Blue else AppColors.Muted
                    Column(Modifier.weight(1f).fillMaxHeight().testTag("tab_$id").clickable { onChange(id) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        AppIcon(icon, Modifier.size(23.dp), color)
                        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable fun Onboarding(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.Blue), contentAlignment = Alignment.Center) { AppIcon("box", Modifier.size(17.dp), Color.White) }
            Text("하루만", Modifier.padding(start = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(272.dp)) {
                Box(Modifier.size(202.dp).align(Alignment.Center).border(1.dp, AppColors.Blue.copy(alpha = .15f), RoundedCornerShape(48.dp)))
                Surface(Modifier.width(139.dp).align(Alignment.TopStart).padding(top = 24.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp, color = Color.White) {
                    Column(Modifier.padding(16.dp)) { Text("Instagram", fontSize = 11.sp, color = AppColors.Muted); Text("마음에 든 조명", Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Surface(Modifier.width(139.dp).align(Alignment.BottomEnd).padding(bottom = 22.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp, color = Color.White) {
                    Column(Modifier.padding(16.dp)) { Text("Coupang", fontSize = 11.sp, color = AppColors.Muted); Text("갖고 싶던 헤드폰", Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Box(Modifier.size(92.dp).align(Alignment.Center).clip(RoundedCornerShape(28.dp)).background(AppColors.Blue), contentAlignment = Alignment.Center) { AppIcon("box", Modifier.size(42.dp), Color.White) }
            }
        }
        Text("사고 싶은 마음,\n잠시 넣어두세요.", fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        Text("여러 곳에서 발견한 상품을 모아두고,\n조금 뒤 다시 결정해요.", Modifier.padding(top = 16.dp, bottom = 32.dp), color = AppColors.Secondary, fontSize = 15.sp, lineHeight = 25.sp)
        PrimaryAction("시작하기", onStart, Modifier.fillMaxWidth().testTag("onboarding_start"))
    }
}

@Composable fun VaultScreen(items: List<WishItem>, now: Long, onAdd: () -> Unit, onItem: (WishItem) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val active = items.filter { it.decision == Decision.PENDING }
    val ready = active.filter { it.ready(now) }
    val cooling = active.filterNot { it.ready(now) }
    val monthly = skippedTotal(items.filter { it.decidedAt?.let { time -> sameMonth(time, now) } == true })
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("보관함", Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onAdd, modifier = Modifier.size(44.dp).clip(CircleShape).background(AppColors.Blue).testTag("add_item").semantics { contentDescription = "상품 저장" }) { AppIcon("plus", Modifier.size(22.dp), Color.White) }
            }
            Text("지금 ${active.size}개의 소비를 고민하고 있어요", Modifier.padding(top = 12.dp), fontSize = 14.sp, color = AppColors.Secondary)
            Text("이번 달 ${money(monthly)}을 쓰지 않기로 했어요", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = AppColors.Muted)
            Row(Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Soft).padding(4.dp)) {
                listOf("전체", "고민 중", "결정 필요").forEachIndexed { i, text ->
                    Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(9.dp)).background(if (filter == i) Color.White else Color.Transparent).testTag("filter_$i").clickable { filter = i }, contentAlignment = Alignment.Center) {
                        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (filter == i) AppColors.Text else AppColors.Muted)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (filter != 1 && ready.isNotEmpty()) ItemSection("다시 결정할 시간이에요", "잠시 미뤄둔 마음을 다시 살펴봐요", ready, now, onItem)
            if (filter != 2 && cooling.isNotEmpty()) ItemSection("고민 중이에요", "정해둔 시간 뒤에 다시 생각해 봐요", cooling, now, onItem)
            if ((filter == 1 && cooling.isEmpty()) || (filter == 2 && ready.isEmpty()) || active.isEmpty()) EmptyState(if (filter == 2) "아직은 고민할 시간이에요" else "사고 싶은 마음을 담아보세요", if (filter == 2) "시간이 지나면 여기서 다시 결정할 수 있어요." else "쿠팡이나 인스타그램 링크를 저장해 보세요.")
            if (items.any { it.id.startsWith("sample-") }) Text("처음 둘러볼 수 있도록 예시 상품을 담아뒀어요", Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), fontSize = 11.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable fun ItemSection(title: String, subtitle: String, items: List<WishItem>, now: Long, onItem: (WishItem) -> Unit) {
    Column(Modifier.padding(bottom = 28.dp)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, Modifier.padding(top = 5.dp), fontSize = 12.sp, color = AppColors.Muted)
        }
        Column(Modifier.background(AppColors.Surface)) { items.forEachIndexed { i, item ->
            ProductRow(item, now) { onItem(item) }
            if (i < items.lastIndex) HorizontalDivider(Modifier.padding(start = 96.dp), color = AppColors.Border, thickness = .5.dp)
        } }
    }
}
@Composable fun ProductRow(item: WishItem, now: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("item_${item.id}").clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProductArtwork(item.artwork, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(money(item.priceWon), Modifier.padding(top = 5.dp), fontSize = 13.sp, color = AppColors.Secondary)
            Text(item.platform.label, Modifier.padding(top = 2.dp), fontSize = 11.sp, color = AppColors.Muted)
        }
        Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.End) {
            when (item.decision) {
                Decision.BUY -> Pill("구매 결정", AppColors.Soft, AppColors.Secondary)
                Decision.SKIP -> Pill("안 샀어요", AppColors.GreenTint, AppColors.Green)
                Decision.PENDING -> if (item.ready(now)) {
                    Pill("결정 필요", AppColors.BlueTint, AppColors.Blue)
                    Text("결정하기 ›", Modifier.padding(top = 8.dp), fontSize = 11.sp, color = AppColors.Blue, fontWeight = FontWeight.Medium)
                } else {
                    AppIcon("clock", Modifier.size(14.dp), AppColors.Blue)
                    Text(remaining(item, now), Modifier.padding(top = 5.dp), fontSize = 11.sp, color = AppColors.Blue, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
@Composable fun Pill(text: String, background: Color, color: Color) { Text(text, Modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 8.dp, vertical = 5.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
@Composable fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(AppColors.BlueTint), contentAlignment = Alignment.Center) { AppIcon("box", Modifier.size(30.dp), AppColors.Blue) }
        Text(title, Modifier.padding(top = 22.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, Modifier.padding(top = 10.dp), fontSize = 13.sp, color = AppColors.Muted, textAlign = TextAlign.Center, lineHeight = 21.sp)
    }
}

@Composable fun HistoryScreen(items: List<WishItem>, now: Long, onItem: (WishItem) -> Unit) {
    val decided = items.filter { it.decision != Decision.PENDING }.sortedByDescending { it.decidedAt }
    val monthly = decided.filter { sameMonth(it.decidedAt ?: it.createdAt, now) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(20.dp)) {
            Text("소비 기록", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp)) {
                Text("이번 달, 한 번 더 생각한 소비", fontSize = 12.sp, color = AppColors.Muted)
                Text(money(skippedTotal(monthly)), Modifier.padding(top = 12.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
                Text("쓰지 않기로 결정한 금액이에요", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = AppColors.Secondary)
                HorizontalDivider(Modifier.padding(vertical = 20.dp), color = AppColors.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${items.count { sameMonth(it.createdAt, now) }}개 저장", fontSize = 14.sp, color = AppColors.Secondary)
                    Text("${monthly.count { it.decision == Decision.SKIP }}개 포기 · ${monthly.count { it.decision == Decision.BUY }}개 구매 결정", fontSize = 13.sp, color = AppColors.Secondary)
                }
            }
        }
        if (decided.isEmpty()) EmptyState("기록이 차곡차곡 쌓일 거예요", "보관한 상품을 다시 생각하고 결정해 보세요.")
        else ItemSection("최근 결정", "나의 선택을 한눈에 살펴봐요", decided, now, onItem)
    }
}

@Composable fun MyScreen(hours: Int, onDefault: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("MY", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 28.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(AppColors.BlueTint), contentAlignment = Alignment.Center) { AppIcon("box", Modifier.size(26.dp), AppColors.Blue) }
            Column(Modifier.padding(start = 14.dp)) { Text("나만의 소비 보관함", fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("내 속도대로, 하루만 더 생각해요", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = AppColors.Muted) }
        }
        Text("쿨다운", Modifier.padding(top = 32.dp, bottom = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
        SettingRow("기본 다시 보기 시간", cooldownLabel(hours), "clock", onDefault)
        Text("데이터", Modifier.padding(top = 30.dp, bottom = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
        SettingRow("내 기록 내보내기", "", "download", onExport)
        Spacer(Modifier.height(8.dp))
        SettingRow("모든 기록 삭제", "", "trash", onDelete, destructive = true)
        Text("하루만 1.0.0", Modifier.fillMaxWidth().padding(top = 42.dp), fontSize = 12.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
        Text("기록은 이 기기에 보관돼요", Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp), fontSize = 12.sp, color = AppColors.Muted, textAlign = TextAlign.Center)
    }
}
@Composable fun SettingRow(label: String, value: String, icon: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, Modifier.size(20.dp), if (destructive) AppColors.Red else AppColors.Secondary)
        Text(label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 14.sp, color = if (destructive) AppColors.Red else AppColors.Text)
        Text(value, fontSize = 13.sp, color = AppColors.Muted)
        AppIcon("chevron", Modifier.padding(start = 8.dp).size(16.dp), AppColors.Muted)
    }
}
