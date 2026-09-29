package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ralphthon.haruman_ralphthon.data.Platform
import org.ralphthon.haruman_ralphthon.data.parseSupportedUrl

@Composable
fun AddSheet(initialUrl: String, defaultHours: Int, onSave: (String, Long?, Platform, String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var platform by rememberSaveable(initialUrl) { mutableStateOf(parseSupportedUrl(initialUrl)?.first ?: Platform.COUPANG) }
    var hours by rememberSaveable { mutableIntStateOf(defaultHours) }
    val parsed = parseSupportedUrl(url)
    val urlError = url.isNotBlank() && parsed == null
    val numericPrice = price.toLongOrNull()
    val priceError = price.isNotEmpty() && numericPrice == null
    val canSave = name.isNotBlank() && !urlError && !priceError
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("보관함에 넣을까요?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("사고 싶은 상품을 적고, 다시 볼 시간을 골라요", Modifier.padding(top = 8.dp, bottom = 20.dp), fontSize = 13.sp, color = AppColors.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Platform.entries.forEach { value ->
            FilterChip(selected = platform == value, onClick = { platform = value; if (parsed != null && parsed.first != value) url = "" }, label = { Text(value.label) }, modifier = Modifier.testTag("platform_${value.name.lowercase()}"))
        } }
        OutlinedTextField(name, { name = it.take(100) }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("input_name"), label = { Text("상품명") }, placeholder = { Text("어떤 상품이 마음에 들었나요?") }, singleLine = true, shape = RoundedCornerShape(12.dp))
        OutlinedTextField(price, { value -> price = value.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth().padding(top = 12.dp).testTag("input_price"), label = { Text("가격 · 선택") }, suffix = { Text("원") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp), isError = priceError)
        OutlinedTextField(url, { value -> url = value; parseSupportedUrl(value)?.let { platform = it.first } }, Modifier.fillMaxWidth().padding(top = 12.dp).testTag("input_url"), label = { Text("상품 링크 · 선택") }, placeholder = { Text("쿠팡 또는 인스타그램 링크") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, shape = RoundedCornerShape(12.dp), isError = urlError,
            supportingText = if (urlError) ({ Text("쿠팡 또는 인스타그램 링크를 입력해 주세요") }) else null)
        Text("언제 다시 생각해 볼까요?", Modifier.padding(top = 24.dp, bottom = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(3, 24, 72, 168).forEach { value ->
            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (hours == value) AppColors.BlueTint else AppColors.Soft).testTag("cooldown_$value").clickable { hours = value }, contentAlignment = Alignment.Center) {
                Text(cooldownLabel(value), fontSize = 12.sp, color = if (hours == value) AppColors.Blue else AppColors.Secondary, fontWeight = FontWeight.SemiBold)
            }
        } }
        PrimaryAction("보관함에 넣기", { onSave(name.trim(), numericPrice, platform, parsed?.second ?: "", hours) }, Modifier.fillMaxWidth().padding(top = 24.dp).testTag("save_item"), enabled = canSave)
        TextButton(onClick = { onSave(name.trim(), numericPrice, platform, parsed?.second ?: "", 0) }, enabled = canSave, modifier = Modifier.fillMaxWidth().testTag("save_without_time")) {
            Text("시간 정하지 않고 저장", fontSize = 13.sp, color = if (canSave) AppColors.Secondary else AppColors.Muted)
        }
    }
}
