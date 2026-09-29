package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ralphthon.haruman_ralphthon.data.MockConsumptionPatterns
import org.ralphthon.haruman_ralphthon.data.mockConsumptionSegments

/** Static example only: no product identity, network request, local record or recommendation input. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MockConsumptionPattern(modifier: Modifier = Modifier) {
    val colors = listOf(AppColors.Blue, AppColors.Muted, AppColors.Green)
    val categories = listOf("구매 결정", "보류 중", "안 사기로")
    Surface(modifier.fillMaxWidth().testTag("mock_consumption_pattern"),
        color = AppColors.Soft, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("예시 데이터", Modifier.testTag("mock_pattern_example"),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AppColors.Blue)
            Text("같은 상품, 시간에 따라\n마음이 어떻게 달라질까요?", Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text)
            Text("실제 이용자 통계가 아니에요. 같은 상품을 담은 가상 100명의 선택을 보여드려요.",
                Modifier.testTag("mock_pattern_disclaimer"), style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEachIndexed { index, label -> PatternLegend(label, colors[index]) }
            }
            Text("담은 뒤 지난 시간별 비중 · 각 막대의 합계는 100%예요",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            MockConsumptionPatterns.points.forEachIndexed { index, point ->
                Column(Modifier.fillMaxWidth().testTag("mock_pattern_point_${point.elapsedHours}")
                    .semantics(mergeDescendants = true) { stateDescription = "예시 데이터" },
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(point.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    val segments = mockConsumptionSegments(point)
                    Canvas(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
                        .testTag("mock_pattern_bar_${point.elapsedHours}")) {
                        segments.forEachIndexed { segmentIndex, segment ->
                            if (segment.endFraction > segment.startFraction) {
                                drawRect(colors[segmentIndex], Offset(size.width * segment.startFraction, 0f),
                                    Size(size.width * (segment.endFraction - segment.startFraction), size.height))
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        point.percentages.forEachIndexed { stateIndex, percent ->
                            Text("${categories[stateIndex]} $percent%", style = MaterialTheme.typography.bodySmall, color = AppColors.Text)
                        }
                    }
                }
                if (index < MockConsumptionPatterns.points.lastIndex) HorizontalDivider(color = AppColors.Border)
            }
            Text("구매 결정은 실제 주문을 뜻하지 않아요. 이 예시는 내 기록이나 추천에 반영되지 않아요.",
                Modifier.testTag("mock_pattern_note"), style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        }
    }
}

@Composable
private fun PatternLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.Text)
    }
}
