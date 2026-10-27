package com.kimgdecierdo.kimp

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The few settings KIMP remembers, kept in SharedPreferences. */
object Prefs {
    private const val FILE = "settings"
    private val _theme = MutableStateFlow("System")
    val theme: StateFlow<String> = _theme

    fun load(context: Context) {
        _theme.value = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("theme", "System") ?: "System"
    }

    fun setTheme(context: Context, value: String) {
        _theme.value = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("theme", value).apply()
    }
}
