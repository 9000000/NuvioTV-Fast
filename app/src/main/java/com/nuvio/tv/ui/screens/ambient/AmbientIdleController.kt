package com.nuvio.tv.ui.screens.ambient

import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AmbientIdleController(
    private val uptimeMillisProvider: () -> Long = { SystemClock.uptimeMillis() }
) {
    private val _lastInteractionAt = MutableStateFlow(uptimeMillisProvider())
    val lastInteractionAt: StateFlow<Long> = _lastInteractionAt.asStateFlow()

    private val _isShowing = MutableStateFlow(false)
    val isShowing: StateFlow<Boolean> = _isShowing.asStateFlow()

    @Volatile
    var allowVolumeKeysWhileShowing: Boolean = false

    private var swallowedKeyCode: Int? = null

    fun onInteraction() {
        _lastInteractionAt.value = uptimeMillisProvider()
    }

    fun show() {
        _isShowing.value = true
    }

    fun dismiss() {
        _isShowing.value = false
        onInteraction()
    }

    fun consumeKeyEvent(event: KeyEvent): Boolean {
        if (_isShowing.value && allowVolumeKeysWhileShowing && isAmbientVolumeKey(event.keyCode)) {
            return false
        }
        onInteraction()
        val continuesSwallowedPress = event.keyCode == swallowedKeyCode &&
            (event.action == KeyEvent.ACTION_UP || event.repeatCount > 0)
        if (continuesSwallowedPress) {
            if (event.action == KeyEvent.ACTION_UP) swallowedKeyCode = null
            return true
        }
        swallowedKeyCode = null
        if (_isShowing.value && event.action == KeyEvent.ACTION_DOWN) {
            swallowedKeyCode = event.keyCode
            dismiss()
            return true
        }
        return false
    }
}

internal fun isAmbientVolumeKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
