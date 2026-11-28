package com.cipher.virtual.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-arms [CipherHostService] after device boot (`BOOT_COMPLETED`) or app upgrade
 * (`MY_PACKAGE_REPLACED`) so workspace synchronization stays active.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val started = CipherHostService.start(appContext, triggerReason = action)
        runCatching {
            Log.i(TAG, "Handled $action; CipherHostService start dispatched=$started")
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
