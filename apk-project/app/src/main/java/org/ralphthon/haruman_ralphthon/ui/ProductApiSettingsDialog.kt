package org.ralphthon.haruman_ralphthon.ui

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ralphthon.haruman_ralphthon.data.*

/** Credential input lives only in this composition, never in SavedState or the application's draft. */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
fun ProductApiSettingsDialog(
    settings: ProductApiSettings,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
    defaultConfigured: Boolean = false,
) {
    var status by remember(settings) { mutableStateOf(ProductApiSettingsStatus(false)) }
    var loaded by remember(settings) { mutableStateOf(false) }
    var baseUrl by remember(settings) { mutableStateOf("") }
    var token by remember(settings) { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val validUrl = normalizeProductApiBaseUrl(baseUrl) != null
    val sameServer = normalizeProductApiBaseUrl(baseUrl) == status.baseUrl
    val tokenValid = !token.any { it.isISOControl() } && token.length <= 4096 && (token.isNotBlank() || status.configured && sameServer)
    LaunchedEffect(settings) {
        status = withContext(Dispatchers.IO) { settings.status() }
        baseUrl = status.baseUrl.orEmpty()
        if (status.readError) error = "저장된 연결 정보를 읽지 못했어요. 주소와 토큰을 다시 입력해 주세요"
        loaded = true
    }
    Dialog(onDismissRequest = { if (!saving) { token = ""; onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !saving,
            dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = if (Build.VERSION.SDK_INT >= 26) view.importantForAutofill else 0
            if (Build.VERSION.SDK_INT >= 26) view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            onDispose {
                if (Build.VERSION.SDK_INT >= 26) view.importantForAutofill = when (previous) {
                    View.IMPORTANT_FOR_AUTOFILL_YES -> View.IMPORTANT_FOR_AUTOFILL_YES
                    View.IMPORTANT_FOR_AUTOFILL_NO -> View.IMPORTANT_FOR_AUTOFILL_NO
                    View.IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS -> View.IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS -> View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    else -> View.IMPORTANT_FOR_AUTOFILL_AUTO
                }
                token = ""
            }
        }
        CompositionLocalProvider(LocalAutofill provides null, LocalAutofillManager provides null) {
            BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
                    shape = MaterialTheme.shapes.extraLarge, color = AppColors.Surface) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).testTag("api_settings_dialog")
                        .semantics { testTagsAsResourceId = true },
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("상품 정보 연결", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.Text)
                        if (defaultConfigured) {
                            Text("앱에 기본 상품 연결이 준비되어 있어요", Modifier.testTag("api_default_connection_info"),
                                style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                            if (loaded && status.configured) Text("지금은 직접 설정한 연결을 사용하고 있어요",
                                style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                            Text("다른 연결 사용 · 선택", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = AppColors.Text)
                            Text("다른 연결을 사용하고 싶다면 주소와 토큰을 입력해 주세요",
                                style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                        } else {
                            Text("상품 링크를 보내 정보를 받아올 서버를 연결해요", style = MaterialTheme.typography.bodyMedium, color = AppColors.Secondary)
                        }
                        OutlinedTextField(baseUrl, { baseUrl = it.take(2048); error = null },
                            Modifier.fillMaxWidth().testTag("api_base_url"), enabled = loaded && !saving,
                            label = { Text("HTTPS 서버 주소") }, placeholder = { Text("https://api.example.com:8443") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            isError = baseUrl.isNotBlank() && !validUrl,
                            supportingText = { Text("주소와 포트만 입력해 주세요. 뒤에 경로를 붙이지 않아도 돼요") })
                        OutlinedTextField(token, { token = it.take(4097); error = null },
                            Modifier.fillMaxWidth().testTag("api_token"), enabled = loaded && !saving,
                            label = { Text("연결 토큰") }, singleLine = true,
                            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            isError = token.isNotEmpty() && !tokenValid,
                            supportingText = { Text(when {
                                status.configured && !sameServer -> "서버 주소를 바꿀 때는 토큰도 다시 입력해 주세요"
                                status.configured -> "비워 두면 기존 토큰을 그대로 써요"
                                else -> "받은 토큰을 입력해 주세요"
                            }) })
                        TextButton(onClick = { revealed = !revealed }, enabled = !saving,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("api_token_visibility")) { Text(if (revealed) "토큰 가리기" else "입력한 토큰 보기") }
                        Text("직접 입력한 연결 정보는 이 기기에 암호화해 보관해요", style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                        error?.let { Text(it, Modifier.testTag("api_settings_error"), style = MaterialTheme.typography.bodyMedium, color = AppColors.Red) }
                        PrimaryAction(if (saving) "저장하고 있어요" else "저장하기", {
                            if (!saving) {
                                saving = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { settings.save(baseUrl, token) }
                                    saving = false
                                    when (result) {
                                        ProductApiSettingsResult.Success -> { token = ""; onChanged(); onDismiss() }
                                        is ProductApiSettingsResult.Failure -> error = result.message
                                    }
                                }
                            }
                        }, Modifier.fillMaxWidth().testTag("api_settings_save"), enabled = loaded && !saving && validUrl && tokenValid)
                        if (defaultConfigured && (status.configured || status.readError)) {
                            Text("저장한 연결 정보를 지우면 앱의 기본 연결을 다시 사용해요", Modifier.testTag("api_default_restore_info"),
                                style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
                        }
                        if (status.configured || status.readError) TextButton(onClick = {
                            if (!saving) {
                                saving = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { settings.clear() }
                                    saving = false
                                    when (result) {
                                        ProductApiSettingsResult.Success -> { token = ""; onChanged(); onDismiss() }
                                        is ProductApiSettingsResult.Failure -> error = result.message
                                    }
                                }
                            }
                        }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("api_settings_clear")) {
                            Text(if (defaultConfigured) "기본 연결로 돌아가기" else "저장된 연결 정보 지우기", color = AppColors.Red)
                        }
                        TextButton(onClick = { token = ""; onDismiss() }, enabled = !saving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("api_settings_cancel")) { Text("닫기") }
                    }
                }
            }
        }
    }
}
