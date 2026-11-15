package com.example.core

/** One UI lets the user pick Light, Dark, or follow the system. */
enum class ThemeMode(val storage: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.storage == raw?.trim()?.lowercase() } ?: SYSTEM
    }
}
