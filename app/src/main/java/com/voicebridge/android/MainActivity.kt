package com.voicebridge.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.room.Room
import android.content.SharedPreferences
import com.voicebridge.android.data.SettingsStore
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.ui.HomeCompose
import com.voicebridge.android.ui.MeetingDetailCompose
import com.voicebridge.android.ui.SettingsCompose
import com.voicebridge.android.ui.PrivacyConsentCompose
import com.voicebridge.android.ui.SplashOverlay
import com.voicebridge.android.ui.theme.VoiceBridgeTheme

class MainActivity : ComponentActivity() {
    private lateinit var db: VoiceBridgeDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            VoiceBridgeDatabase::class.java,
            "voicebridge.db"
        ).build()

        setContent {
            val context = applicationContext
            var appearance by remember { mutableStateOf(SettingsStore.getAppearance(context)) }
            var hasAgreedPrivacy by remember { mutableStateOf(SettingsStore.hasAgreedToPrivacyConsent(context)) }

            // 设置页写入 SharedPreferences 后，外观/授权状态即时生效
            DisposableEffect(Unit) {
                val prefs = SettingsStore.prefs(context)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    appearance = SettingsStore.getAppearance(context)
                    hasAgreedPrivacy = SettingsStore.hasAgreedToPrivacyConsent(context)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            VoiceBridgeTheme(appearance = appearance) {
                var showSplash by remember { mutableStateOf(savedInstanceState == null) }

                if (!hasAgreedPrivacy) {
                    PrivacyConsentCompose(
                        onAgree = { hasAgreedPrivacy = true }
                    )
                } else {
                    var currentMeetingId by remember { mutableStateOf<String?>(null) }
                    var settingsSubView by remember { mutableStateOf<String?>(null) }

                    if (settingsSubView != null) {
                        SettingsCompose(
                            db = db,
                            initialSubView = settingsSubView,
                            onRevokePrivacy = {
                                hasAgreedPrivacy = false
                                settingsSubView = null
                            },
                            onBack = { settingsSubView = null }
                        )
                    } else {
                        val meetingId = currentMeetingId
                        if (meetingId != null) {
                            BackHandler { currentMeetingId = null }
                            MeetingDetailCompose(
                                meetingId = meetingId,
                                db = db,
                                onBack = { currentMeetingId = null }
                            )
                        } else {
                            HomeCompose(
                                db = db,
                                onNavigateToDetail = { id -> currentMeetingId = id },
                                onNavigateToSettings = { settingsSubView = "" },
                                onNavigateToDiagnostics = { settingsSubView = "diagnostics" }
                            )
                        }
                    }
                }

                // 品牌启动动画 — 覆盖于主内容之上，动画结束后整层移除
                if (showSplash) {
                    SplashOverlay(onFinished = { showSplash = false })
                }
            }
        }
    }
}
