package com.example.metaguardrail.data
enum class ThemeMode(val value: String, val label: String) { SYSTEM("system","System"), DARK("dark","Dark"), LIGHT("light","Light"); companion object { fun fromValue(v:String?): ThemeMode = entries.firstOrNull{it.value==v} ?: SYSTEM } }
