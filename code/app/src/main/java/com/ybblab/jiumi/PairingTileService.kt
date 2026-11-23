// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

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
import com.ybblab.jiumi.ui.PairingTilePanel
import com.ybblab.jiumi.ui.theme.JiumiTheme

/** The system gives a clicked tile a dialog window without switching away from Settings. */
class PairingTileService : TileService() {
    private var panel: ComponentDialog? = null

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = ContextCompat.getContextForLanguage(this@PairingTileService).getText(R.string.pairing_tile_label)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openPanel) else openPanel()
    }

    override fun onDestroy() {
        panel?.dismiss()
        super.onDestroy()
    }

    private fun openPanel() {
        if (panel?.isShowing == true) return
        val localized = ContextCompat.getContextForLanguage(this)
        val controller = (application as JiumiApp).controller
        val dialog = ComponentDialog(localized, R.style.Theme_Jiumi_PairingDialog)
        val window = checkNotNull(dialog.window)
        window.setGravity(Gravity.BOTTOM)
        dialog.setContentView(ComposeView(dialog.context).apply {
            setContent {
                JiumiTheme {
                    PairingTilePanel(
                        controller = controller,
                        onClose = dialog::dismiss,
                        onMove = { atTop -> window.setGravity(if (atTop) Gravity.TOP else Gravity.BOTTOM) }
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
