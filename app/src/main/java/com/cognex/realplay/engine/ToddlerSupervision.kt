package com.cognex.realplay.engine

import android.content.Context

/**
 * Persists whether the adult-supervision notice has been acknowledged (Architecture §10 rule 9) —
 * shown once PER INSTALL, not once per session. Backed by a tiny SharedPreferences file.
 */
object ToddlerSupervision {
    private const val PREFS = "realplay_prefs"
    private const val KEY_ACK = "toddler_supervision_ack"

    fun isAcknowledged(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACK, false)

    fun acknowledge(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACK, true)
            .apply()
    }
}
