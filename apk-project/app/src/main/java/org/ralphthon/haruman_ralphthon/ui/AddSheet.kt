package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.data.Desire
import org.ralphthon.haruman_ralphthon.data.InputSource
import org.ralphthon.haruman_ralphthon.data.InputLimits
import org.ralphthon.haruman_ralphthon.ui.mascot.*
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSheet(
    draft: ProductDraft,
    processing: Boolean,
    errorMessage: String?,
    retryAllowed: Boolean,
    onChange: (ProductDraft) -> Unit,
    onModeChange: (String) -> Unit,
    onRequest: () -> Unit,
    onSave: () -> Unit,
    onCancelRequest: () -> Unit,
    budgetPreview: String? = null,
) {
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editing = draft.editingId != null
    val manual = draft.mode == "manual" || editing
    fun save() { focus.clearFocus(); keyboard?.hide(); onSave() }
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (editing) "상품 정보를 수정해요" else "마음에 든 상품을 담아 봐요", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AppColors.Text)
        Text(if (editing) "고친 내용은 이 상품에만 저장돼요" else "링크로 불러오거나 직접 적을 수 있어요", color = AppColors.Secondary, fontSize = 14.sp)
        MascotMessage(MascotPose.MOA_RECEIVE, if (draft.inputSource == InputSource.LINK_SHARE) "링크를 받았어요! 함께 담아 볼까요?" else "마음에 든 상품을 모아 둘게요!", size = 56.dp)
        if (!editing) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = !manual, onClick = { onModeChange("link") }, label = { Text("링크로 추가") }, modifier = Modifier.testTag("mode_link").heightIn(min = 48.dp))
                FilterChip(selected = manual, onClick = { onModeChange("manual") }, label = { Text("직접 입력") }, modifier = Modifier.testTag("mode_manual").heightIn(min = 48.dp))
            }
        }
        if (!manual) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CharacterArt(if (processing) "loading" else if (errorMessage != null) "needsReview" else "idle", Modifier.size(64.dp))
                Text(if (processing) "상품 정보를 불러오고 있어요" else "공유한 상품 링크도\n바로 담을 수 있어요", color = AppColors.Secondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = draft.url, onValueChange = { onChange(draft.copy(url = it)) },
                modifier = Modifier.fillMaxWidth().testTag("input_url"), label = { Text("상품 링크") },
                placeholder = { Text("상품 페이지 주소를 붙여넣어 주세요") }, enabled = !processing,
                isError = draft.url.length > InputLimits.URL,
                supportingText = if (draft.url.length > InputLimits.URL) {
                    { Text("링크가 너무 길어요. 짧은 공유 링크를 넣거나 직접 입력해 주세요") }
                } else null,
                minLines = 2, maxLines = 3, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(14.dp),
            )
            TextButton(onClick = { clipboard.getText()?.text?.let { onChange(draft.copy(url = it)) } }, enabled = !processing, modifier = Modifier.testTag("paste_link").heightIn(min = 48.dp)) {
                AppIcon("link", Modifier.size(18.dp), AppColors.Blue)
                Spacer(Modifier.width(8.dp)); Text("붙여넣기")
            }
            DesireChoices(draft.desire, !processing) { onChange(draft.copy(desire = it)) }
            CooldownChoices(draft.hours, !processing) { onChange(draft.copy(hours = it)) }
            if (errorMessage != null) EntryNotice(errorMessage, "needsReview")
            if (processing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("link_loading"), color = AppColors.Blue)
                Text("조금 걸릴 수 있어요. 직접 입력으로 바꿔도 괜찮아요", color = AppColors.Secondary, fontSize = 13.sp)
                OutlinedButton(onClick = onCancelRequest, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("불러오기 취소") }
            } else {
                PrimaryAction(if (retryAllowed) "다시 시도" else "링크로 추가", { focus.clearFocus(); keyboard?.hide(); onRequest() }, Modifier.fillMaxWidth().testTag("request_link"), enabled = draft.url.isNotBlank() && draft.url.length <= InputLimits.URL)
            }
            TextButton(onClick = { onModeChange("manual") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("manual_fallback")) { Text("직접 입력해서 저장") }
        } else {
            draft.reviewMessage?.let { EntryNotice(it, "needsReview") }
            OutlinedTextField(
                draft.name, { onChange(draft.copy(name = it.take(200))) }, Modifier.fillMaxWidth().testTag("input_name"),
                label = { Text("상품명") }, placeholder = { Text("어떤 상품이 마음에 들었나요?") },
                maxLines = 3, shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                draft.price, { onChange(draft.copy(price = it.replace(",", "").take(13))) }, Modifier.fillMaxWidth().testTag("input_price"),
                label = { Text("가격 · 선택") }, suffix = { Text("원") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp), isError = draft.priceError,
                supportingText = { Text(if (draft.priceError) "0 이상의 원 단위 숫자를 입력해 주세요" else "비워 두면 가격 미정으로 저장해요") },
            )
            budgetPreview?.let { Text(it, color = AppColors.Secondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("add_budget_preview")) }
            OutlinedTextField(
                draft.description, { onChange(draft.copy(description = it.take(5000))) }, Modifier.fillMaxWidth().testTag("input_description"),
                label = { Text("상품 설명 · 선택") }, placeholder = { Text("상품의 특징을 적어 주세요") },
                minLines = 2, maxLines = 5, shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                draft.userMemo, { onChange(draft.copy(userMemo = it.take(2000))) }, Modifier.fillMaxWidth().testTag("input_memo"),
                label = { Text("나의 메모 · 선택") }, placeholder = { Text("왜 사고 싶은지 적어 볼까요?") },
                minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp),
            )
            if (draft.url.isNotBlank()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Soft).padding(14.dp)) {
                    Text("가져온 링크", color = AppColors.Secondary, fontSize = 12.sp)
                    Text(draft.url, color = AppColors.Secondary, fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                    if (!editing) TextButton(onClick = { onChange(draft.copy(url = "")) }) { Text("링크 없이 저장") }
                }
            }
            if (draft.imageUrl != null) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("상품 사진 숨기기", Modifier.weight(1f), color = AppColors.Text, fontSize = 14.sp)
                    Switch(checked = draft.imageHidden, onCheckedChange = { onChange(draft.copy(imageHidden = it)) }, modifier = Modifier.testTag("hide_product_image"))
                }
            }
            DesireChoices(draft.desire, true) { onChange(draft.copy(desire = it)) }
            if (!editing) CooldownChoices(draft.hours, true) { onChange(draft.copy(hours = it)) }
            if (errorMessage != null) EntryNotice(errorMessage, "needsReview")
            PrimaryAction(if (editing) "수정 저장" else "보관함에 저장", ::save, Modifier.fillMaxWidth().testTag("save_item"), enabled = draft.canSave && !processing)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesireChoices(selected: Desire?, enabled: Boolean, onChange: (Desire?) -> Unit) {
    Column {
        Text("얼마나 갖고 싶나요? · 선택", color = AppColors.Secondary, fontSize = 14.sp)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Desire.entries.forEach { desire ->
                FilterChip(selected = selected == desire, enabled = enabled, onClick = { onChange(if (selected == desire) null else desire) }, label = { Text(desire.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("desire_${desire.name.lowercase(Locale.ROOT)}"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CooldownChoices(hours: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Column {
        Text("언제 다시 생각해 볼까요?", Modifier.padding(top = 8.dp, bottom = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(3, 24, 72, 168, 0).forEach { value ->
                FilterChip(selected = hours == value, enabled = enabled, onClick = { onChange(value) }, label = { Text(if (value == 0) "바로 생각하기" else cooldownLabel(value)) }, modifier = Modifier.testTag("cooldown_$value").heightIn(min = 48.dp))
            }
        }
    }
}

@Composable
private fun EntryNotice(text: String, state: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppColors.BlueTint).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(if (state == "needsReview") "info" else "check", Modifier.size(20.dp), AppColors.Blue)
        Text(text, color = AppColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
