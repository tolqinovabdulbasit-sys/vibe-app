package com.vibeapp.ui.pairing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.vibeapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Auto-generate code when "Generate" tab is shown
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.reset() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить устройство", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        },
        containerColor = Background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // Tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant)
                    .padding(4.dp)
            ) {
                listOf("Показать код", "Ввести код").forEachIndexed { idx, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == idx) Primary else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { selectedTab = idx; viewModel.reset() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selectedTab == idx) TextPrimary else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == idx) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    slideInHorizontally { if (targetState > initialState) it else -it } togetherWith
                            slideOutHorizontally { if (targetState > initialState) -it else it }
                },
                label = "tab_anim"
            ) { tab ->
                if (tab == 0) {
                    GenerateCodeTab(state = state, onGenerate = { viewModel.generateCode() })
                } else {
                    EnterCodeTab(state = state, onSubmit = { code -> viewModel.enterCode(code) })
                }
            }
        }
    }
}

@Composable
private fun GenerateCodeTab(state: PairingUiState, onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            is PairingUiState.Idle, is PairingUiState.Error, is PairingUiState.ExpiredCode -> {
                Text(
                    "Сгенерируйте одноразовый код, чтобы поделиться им с другим устройством.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                if (state is PairingUiState.ExpiredCode) {
                    Text("Срок действия кода истек.", color = ColorFailed, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                }

                if (state is PairingUiState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ColorFailed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Ошибка: ${state.message}",
                            color = ColorFailed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Сгенерировать код", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            is PairingUiState.GeneratingCode -> {
                CircularProgressIndicator(color = Primary)
                Spacer(Modifier.height(16.dp))
                Text("Генерация...", color = TextSecondary, fontSize = 14.sp)
            }

            is PairingUiState.ShowingCode -> {
                val minutes = state.expiresInSeconds / 60
                val seconds = state.expiresInSeconds % 60

                Text("Поделитесь этим кодом с другим устройством", color = TextSecondary, fontSize = 14.sp,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))

                // Large code display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderActive, RoundedCornerShape(20.dp))
                        .padding(vertical = 32.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.code.chunked(4).joinToString("  "),
                        color = TextPrimary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Countdown timer
                Text(
                    text = "Истекает через %02d:%02d".format(minutes, seconds),
                    color = if (state.expiresInSeconds < 60) ColorFailed else TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(24.dp))

                // QR Code
                val qrBitmap = remember(state.code) { generateQrCode(state.code) }
                qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR-код",
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Или отсканируйте QR-код", color = TextTertiary, fontSize = 12.sp)
                }
            }

            is PairingUiState.Success -> {
                Icon(Icons.Default.CheckCircle, null, tint = ColorConnected, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Сопряжено!", color = ColorConnected, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Подключено к ${state.remoteAlias}", color = TextSecondary, fontSize = 14.sp,
                    textAlign = TextAlign.Center)
            }

            else -> {}
        }
    }
}

@Composable
private fun EnterCodeTab(state: PairingUiState, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Введите код, показанный на другом устройстве.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 8) code = it.uppercase() },
            placeholder = { Text("XXXX XXXX", color = TextTertiary, fontSize = 22.sp, letterSpacing = 4.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onSubmit(code) }),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = BorderSubtle,
                cursorColor = Primary
            ),
            shape = RoundedCornerShape(14.dp),
            isError = state is PairingUiState.InvalidCode || state is PairingUiState.ExpiredCode
        )

        Spacer(Modifier.height(8.dp))

        when (state) {
            is PairingUiState.InvalidCode ->
                Text("Неверный код. Проверьте и попробуйте снова.", color = ColorFailed, fontSize = 13.sp)
            is PairingUiState.ExpiredCode ->
                Text("Срок действия кода истек. Запросите новый.", color = ColorTimeout, fontSize = 13.sp)
            is PairingUiState.Error ->
                Text(state.message, color = ColorFailed, fontSize = 13.sp)
            else -> {}
        }

        Spacer(Modifier.height(24.dp))

        when (state) {
            is PairingUiState.Verifying -> {
                CircularProgressIndicator(color = Primary)
            }
            is PairingUiState.Success -> {
                Icon(Icons.Default.CheckCircle, null, tint = ColorConnected, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Сопряжено с ${state.remoteAlias}!", color = ColorConnected,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            else -> {
                Button(
                    onClick = { focusManager.clearFocus(); onSubmit(code) },
                    enabled = code.length >= 6,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Подключиться", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun generateQrCode(content: String, size: Int = 512): Bitmap? {
    return try {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) { null }
}
