// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.app.UiModeManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import com.ybblab.jiumi.adb.AirModePairingManager.PairingInput
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.ybblab.jiumi.ui.PairingTilePanel
import com.ybblab.jiumi.ui.theme.JiumiTheme

/** The system gives a clicked tile a dialog window without switching away from Settings. */
class PairingTileService : TileService() {
    private var panel: ComponentDialog? = null

    private var listening: Job? = null

    /** While the shade shows the tile, it follows pairing: lit while a code is awaited, then "Paired". */
    override fun onStartListening() {
        super.onStartListening()
        val controller = (application as JiumiApp).controller
        listening?.cancel()
        listening = MainScope().launch {
            controller.state.collect { state ->
                val localized = ContextCompat.getContextForLanguage(this@PairingTileService)
                val waiting = state.pairingState in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE, PairingState.PAIRING)
                qsTile?.apply {
                    label = localized.getText(R.string.pairing_tile_label)
                    // Set on every update: the shade otherwise keeps an icon it cached from an older version.
                    icon = Icon.createWithResource(this@PairingTileService, R.drawable.ic_pairing_tile)
                    subtitle = localized.getText(when {
                        waiting -> R.string.pairing_tile_waiting
                        state.trust == PairingTrustState.PAIRED -> R.string.pairing_paired
                        else -> R.string.pairing_tile_subtitle
                    })
                    this.state = if (waiting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openPanel) else openPanel()
    }

    override fun onDestroy() {
        listening?.cancel()
        panel?.dismiss()
        super.onDestroy()
    }

    private fun openPanel() {
        if (panel?.isShowing == true) return
        val localized = ContextCompat.getContextForLanguage(this)
        val app = application as JiumiApp
        val controller = app.controller
        // The shade can give the tile window its own configuration. Use Jiumi's appearance.
        val isDark = when (app.preferences.themeMode.value) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> when (getSystemService(UiModeManager::class.java)?.nightMode) {
                UiModeManager.MODE_NIGHT_NO -> false
                UiModeManager.MODE_NIGHT_YES -> true
                else -> Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            }
        }
        val dialog = ComponentDialog(localized, R.style.Theme_Jiumi_PairingDialog)
        val window = checkNotNull(dialog.window)
        window.setGravity(Gravity.BOTTOM)
        dialog.setContentView(ComposeView(dialog.context).apply {
            setContent {
                JiumiTheme(isDark = isDark) {
                    PairingTilePanel(
                        controller = controller,
                        onClose = dialog::dismiss
                    )
                }
            }
        })
        dialog.setCanceledOnTouchOutside(false)
        try {
            showDialog(dialog)
        } catch (error: WindowManager.BadTokenException) {
            Log.w(TAG, "Quick Settings did not grant a pairing dialog window", error)
            Toast.makeText(localized, R.string.pairing_tile_window_error, Toast.LENGTH_SHORT).show()
            return
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        panel = dialog
        if (!controller.isPaired) controller.startPairing(PairingInput.APP)
        dialog.setOnDismissListener {
            if (panel === dialog) {
                panel = null
                controller.cancelInAppPairing()
            }
        }
    }

    private companion object {
        const val TAG = "JiumiPairingTile"
    }
}
