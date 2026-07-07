package com.voicebridge.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.room.Room
import com.voicebridge.android.data.db.VoiceBridgeDatabase
import com.voicebridge.android.ui.HomeCompose
import com.voicebridge.android.ui.MeetingDetailCompose
import com.voicebridge.android.ui.SettingsCompose
import com.voicebridge.android.ui.theme.VoiceBridgeTheme

class MainActivity : ComponentActivity() {
    private lateinit var db: VoiceBridgeDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            applicationContext,
            VoiceBridgeDatabase::class.java,
            "voicebridge.db"
        ).build()

        setContent {
            VoiceBridgeTheme {
                var currentMeetingId by remember { mutableStateOf<String?>(null) }
                var settingsSubView by remember { mutableStateOf<String?>(null) }

                if (settingsSubView != null) {
                    SettingsCompose(
                        db = db,
                        initialSubView = settingsSubView,
                        onBack = { settingsSubView = null }
                    )
                } else {
                    val meetingId = currentMeetingId
                    if (meetingId != null) {
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
        }
    }
}
