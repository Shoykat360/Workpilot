package com.example.workpilotmini.localization

import androidx.compose.runtime.mutableStateOf

enum class Language { BN, EN }

/**
 * App-wide language switch. A single Compose-observable source of truth: every string
 * pulled from [Strings] reads this during composition, so flipping it from the toggle
 * button (see DashboardScreen) immediately re-composes all visible text app-wide —
 * no restart, no per-screen state needed.
 */
object AppLanguage {
    val current = mutableStateOf(Language.BN)

    fun toggle() {
        current.value = if (current.value == Language.BN) Language.EN else Language.BN
    }
}
