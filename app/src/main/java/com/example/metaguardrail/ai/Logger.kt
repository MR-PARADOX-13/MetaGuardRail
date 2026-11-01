package com.example.metaguardrail.ai

interface Logger {
    fun w(tag: String, msg: String)
    fun d(tag: String, msg: String)
}

object AndroidLogger : Logger {
    override fun w(tag: String, msg: String) { android.util.Log.w(tag, msg) }
    override fun d(tag: String, msg: String) { android.util.Log.d(tag, msg) }
}

object NoOpLogger : Logger {
    override fun w(tag: String, msg: String) {}
    override fun d(tag: String, msg: String) {}
}
