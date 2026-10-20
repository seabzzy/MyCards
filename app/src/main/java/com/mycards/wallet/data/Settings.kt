package com.mycards.wallet.data

import android.content.Context

/** Настройки приложения. Хранятся только на телефоне. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Разрешено ли приложению обращаться к интернету (поиск оформления, ссылки из QR). */
    var onlineEnabled: Boolean
        get() = prefs.getBoolean("online", true)
        set(v) = prefs.edit().putBoolean("online", v).apply()

    init {
        // Удаляем данные от прошлой версии, если они остались.
        if (prefs.contains("api_key") || prefs.contains("model")) {
            prefs.edit().remove("api_key").remove("model").apply()
        }
    }
}
