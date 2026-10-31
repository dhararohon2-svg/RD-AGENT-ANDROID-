package com.example.gemini

import android.app.Application
import com.telemetrydeck.sdk.TelemetryDeck

class AntiGemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TelemetryDeck.start(
            this,
            TelemetryDeck.Builder()
                .appID("0519BE48-B8FA-4B86-A920-0792E3123734")
                .namespace("com.santoshkurmi")
                .defaultUser(SimpleTelemetryId.getUniqueUserId(this))
                .testMode(BuildConfig.DEBUG)
                .showDebugLogs(BuildConfig.DEBUG)
                .sendNewSessionBeganSignal(true)
        )
    }
}
