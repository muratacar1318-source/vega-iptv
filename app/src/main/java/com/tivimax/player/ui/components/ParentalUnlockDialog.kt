package com.tivimax.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tivimax.player.ui.MainViewModel
import com.tivimax.player.ui.ParentalPin
import com.tivimax.player.ui.design.VegaButton
import com.tivimax.player.ui.design.VegaButtonVariant
import com.tivimax.player.ui.design.VegaColors
import com.tivimax.player.ui.design.VegaShapes

/**
 * G-052: Ortak ebeveyn PIN diyaloğu (Canlı TV, Film, Dizi ve TV ekranları aynısını kullanır).
 * PIN belirlenmemişse "PIN belirle" modu (PIN + PIN tekrar), belirlenmişse "PIN gir" modu.
 * Rakam alanları TV kumandasıyla açılır (`tvOpenKeyboardOnOk`).
 */
@Composable
fun ParentalUnlockDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val storedPin by viewModel.parentalPin.collectAsState()
    val pinNotSet = storedPin.isEmpty()

    var pinInput by remember { mutableStateOf("") }
    var pinRepeat by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VegaColors.Surface,
        shape = VegaShapes.Large,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VegaColors.Danger)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (pinNotSet) "Ebeveyn PIN'i belirle" else "Ebeveyn kilidi",
                    color = VegaColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = if (pinNotSet) "+18 içerikleri korumak için 4 haneli bir PIN belirle."
                    else "+18 içerikleri açmak için PIN'ini gir.",
                    color = VegaColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                PinField(
                    value = pinInput,
                    label = if (pinNotSet) "PIN" else null,
                    isError = wrongPin || setupError != null,
                    onChange = { pinInput = it; wrongPin = false; setupError = null }
                )
                if (pinNotSet) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PinField(
                        value = pinRepeat,
                        label = "PIN tekrar",
                        isError = setupError != null,
                        onChange = { pinRepeat = it; setupError = null }
                    )
                }
                if (wrongPin) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Hatalı PIN. Tekrar dene.", color = VegaColors.Danger, fontSize = 12.sp)
                }
                setupError?.let { msg ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(msg, color = VegaColors.Danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            VegaButton(
                text = if (pinNotSet) "PIN'i kaydet" else "Kilidi aç",
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
                }
            )
        },
        dismissButton = {
            VegaButton(text = "İptal", variant = VegaButtonVariant.TEXT, onClick = onDismiss)
        }
    )
}

@Composable
private fun PinField(value: String, label: String?, isError: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { ch -> ch in '0'..'9' }) onChange(it) },
        isError = isError,
        singleLine = true,
        label = label?.let { { Text(it) } },
        placeholder = { Text("••••") },
        keyboardOptions = tvKeyboardOptions(KeyboardOptions(keyboardType = KeyboardType.NumberPassword)),
        visualTransformation = PasswordVisualTransformation(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VegaColors.Accent,
            unfocusedBorderColor = VegaColors.Border,
            errorBorderColor = VegaColors.Danger,
            focusedTextColor = VegaColors.TextPrimary,
            unfocusedTextColor = VegaColors.TextPrimary
        ),
        modifier = Modifier.tvOpenKeyboardOnOk().fillMaxWidth()
    )
}

/**
 * G-052: Film/Dizi üst çubuğundaki +18 kilit düğmesi (küçük çip). Listede yetişkin kategori yoksa hiç görünmez.
 * Kilit kapalıyken basınca PIN diyaloğu açılır; kilit açıkken basınca tekrar kilitlenir.
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
 * Yetişkin kategori yoksa görünmez. Odak hafızasına dokunmaz: liste sonunda ek bir öğedir.
 */
@Composable
fun ParentalLockRow(viewModel: MainViewModel, hasAdultCategory: Boolean, modifier: Modifier = Modifier) {
    if (!hasAdultCategory) return
    val unlocked by viewModel.isParentalUnlocked.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val onClick = { if (unlocked) viewModel.lockParental() else showDialog = true }

    Surface(
        shape = VegaShapes.Medium,
        color = VegaColors.Card,
        border = BorderStroke(1.dp, VegaColors.Border),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .dpadFocusable(
                focusedBorderColor = VegaColors.Accent,
                shape = VegaShapes.Medium,
                onClick = onClick
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
                tint = if (unlocked) VegaColors.Danger else VegaColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (unlocked) "+18 açık" else "+18 kilitli",
                color = if (unlocked) VegaColors.Danger else VegaColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    if (showDialog) {
        ParentalUnlockDialog(viewModel = viewModel, onDismiss = { showDialog = false })
    }
}
