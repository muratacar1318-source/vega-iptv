package com.tivimax.player.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.tivimax.player.util.LocalIsTvDevice

/**
 * G-045: TV'de yazı kutusuna gelince klavye kendiliğinden açılmasın.
 * Telefon ve tablette hiçbir şeyi değiştirmez (ayarlar olduğu gibi döner).
 */
@Composable
fun tvKeyboardOptions(base: KeyboardOptions = KeyboardOptions.Default): KeyboardOptions {
    return if (LocalIsTvDevice.current) base.copy(showKeyboardOnFocus = false) else base
}

/**
 * G-045: TV'de yazı kutusundayken kumandanın OK tuşu klavyeyi açar.
 * Telefon ve tablette hiçbir şey yapmaz.
 */
fun Modifier.tvOpenKeyboardOnOk(): Modifier = composed {
    val isTv = LocalIsTvDevice.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (!isTv) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent { event ->
            val code = event.nativeKeyEvent.keyCode
            if (code == KeyEvent.KEYCODE_DPAD_CENTER ||
                code == KeyEvent.KEYCODE_ENTER ||
                code == KeyEvent.KEYCODE_NUMPAD_ENTER
            ) {
                if (event.type == KeyEventType.KeyUp) {
                    keyboard?.show()
                }
                true
            } else {
                false
            }
        }
    }
}
