package com.tivimax.player.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.ParentalPin
import com.tivimax.player.ui.design.VegaButton
import com.tivimax.player.ui.design.VegaButtonVariant
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes
import com.tivimax.player.ui.design.VegaText
import kotlinx.coroutines.delay

/**
 * G-052: Ortak ebeveyn PIN diyaloğu (Canlı TV, Film, Dizi ve TV ekranları aynısını kullanır).
 * PIN belirlenmemişse "PIN belirle" modu (PIN + PIN tekrar), belirlenmişse "PIN gir" modu.
 */
@Composable
fun ParentalUnlockDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val storedPin by viewModel.parentalPin.collectAsState()
    val pinNotSet = storedPin.isEmpty()

    var pinInput by remember { mutableStateOf("") }
    var pinRepeat by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var canInteract by remember { mutableStateOf(false) }
    val firstFieldFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) {
            for (attempt in 0 until 2) {
                withFrameNanos { }
                delay(60)
                if (firstFieldFocusRequester.requestFocusSafely()) break
            }
            delay(180)
            canInteract = true
        }
        Surface(
            shape = VegaShapes.Large,
            color = VegaColors.SurfaceElevated,
            border = BorderStroke(0.75.dp, VegaColors.Border),
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.9f)
                .onPreviewKeyEvent { event ->
                    !canInteract &&
                        event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP &&
                        when (event.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                            AndroidKeyEvent.KEYCODE_BUTTON_A -> true
                            else -> false
                        }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = VegaColors.Danger,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (pinNotSet) "Ebeveyn şifresi belirle" else "Ebeveyn kilidi",
                            color = VegaColors.TextPrimary,
                            fontSize = VegaText.SectionTitle,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (pinNotSet) "+18 içerikler için 4 haneli şifre belirle"
                            else "+18 içerikleri açmak için şifreni gir",
                            color = VegaColors.TextSecondary,
                            fontSize = VegaText.Caption
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4 Haneli Şifre Nokta Göstergesi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < pinInput.length
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        wrongPin || setupError != null -> VegaColors.Danger.copy(alpha = if (isFilled) 0.9f else 0.2f)
                                        isFilled -> VegaColors.Accent
                                        else -> VegaColors.SurfaceVariant
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = when {
                                        wrongPin || setupError != null -> VegaColors.Danger
                                        isFilled -> VegaColors.Accent
                                        else -> VegaColors.Border
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                PinField(
                    value = pinInput,
                    label = if (pinNotSet) "Şifre" else null,
                    isError = wrongPin || setupError != null,
                    onChange = { pinInput = it; wrongPin = false; setupError = null },
                    modifier = Modifier.focusRequester(firstFieldFocusRequester)
                )
                if (pinNotSet) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PinField(
                        value = pinRepeat,
                        label = "Şifre tekrar",
                        isError = setupError != null,
                        onChange = { pinRepeat = it; setupError = null }
                    )
                }
                if (wrongPin) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Hatalı şifre. Tekrar dene.", color = VegaColors.Danger, fontSize = VegaText.Caption)
                }
                setupError?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(msg, color = VegaColors.Danger, fontSize = VegaText.Caption)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VegaButton(
                        text = "İptal",
                        variant = VegaButtonVariant.SECONDARY,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    VegaButton(
                        text = if (pinNotSet) "Kaydet" else "Kilidi aç",
                        variant = VegaButtonVariant.PRIMARY,
                        onClick = {
                            if (pinNotSet) {
                                val error = ParentalPin.validateNewPin(pinInput, pinRepeat)
                                if (error == null && viewModel.createParentalPin(pinInput, pinRepeat)) {
                                    onDismiss()
                                } else {
                                    setupError = error ?: "4 rakam gir"
                                }
                            } else if (viewModel.unlockParental(pinInput)) {
                                onDismiss()
                            } else {
                                wrongPin = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinField(
    value: String,
    label: String?,
    isError: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { ch -> ch in '0'..'9' }) onChange(it) },
        isError = isError,
        singleLine = true,
        label = label?.let { { Text(it) } },
        placeholder = { Text("••••", color = VegaColors.TextMuted) },
        shape = VegaShapes.Medium,
        keyboardOptions = tvKeyboardOptions(KeyboardOptions(keyboardType = KeyboardType.NumberPassword)),
        visualTransformation = PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VegaColors.FocusBorder,
            unfocusedBorderColor = VegaColors.Border,
            errorBorderColor = VegaColors.Danger,
            focusedContainerColor = VegaColors.Surface,
            unfocusedContainerColor = VegaColors.Surface,
            focusedTextColor = VegaColors.TextPrimary,
            unfocusedTextColor = VegaColors.TextPrimary
        ),
        modifier = modifier.tvOpenKeyboardOnOk().fillMaxWidth()
    )
}

/**
 * G-052: Film/Dizi üst çubuğundaki +18 kilit düğmesi (küçük çip). Listede yetişkin kategori yoksa hiç görünmez.
 */
@Composable
fun ParentalLockChip(viewModel: MainViewModel, hasAdultCategory: Boolean, modifier: Modifier = Modifier) {
    if (!hasAdultCategory) return
    val unlocked by viewModel.isParentalUnlocked.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    ActionChipButton(
        text = "+18",
        icon = if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
        iconTint = if (unlocked) VegaColors.Danger else VegaColors.TextSecondary,
        onClick = { if (unlocked) viewModel.lockParental() else showDialog = true },
        modifier = modifier
    )
    if (showDialog) {
        ParentalUnlockDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }
}

/**
 * G-052: TV'de kategori listesinin en altındaki odaklanabilir "+18 kilitli / +18 açık" satırı.
 */
@Composable
fun ParentalLockRow(viewModel: MainViewModel, hasAdultCategory: Boolean, modifier: Modifier = Modifier) {
    if (!hasAdultCategory) return
    val unlocked by viewModel.isParentalUnlocked.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val onClick = { if (unlocked) viewModel.lockParental() else showDialog = true }

    Surface(
        shape = VegaShapes.Medium,
        color = if (isFocused) VegaColors.SurfaceElevated else VegaColors.Surface,
        border = if (!isFocused) BorderStroke(0.75.dp, VegaColors.Border.copy(alpha = 0.65f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.FocusBorder,
                shape = VegaShapes.Medium,
                onClick = onClick,
                onFocusStateChange = { isFocused = it }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = if (unlocked) VegaColors.Danger else if (isFocused) VegaColors.Accent else VegaColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (unlocked) "+18 açık" else "+18 kilitli",
                color = if (unlocked) VegaColors.Danger else if (isFocused) VegaColors.TextPrimary else VegaColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    if (showDialog) {
        ParentalUnlockDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }
}
