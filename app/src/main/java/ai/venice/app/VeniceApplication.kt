package ai.venice.app

import ai.venice.app.audio.VoiceController
import ai.venice.app.data.SecurePreferences
import ai.venice.app.data.VeniceRepository
import ai.venice.app.data.local.VeniceDatabase
import ai.venice.app.data.remote.VeniceApiClient
import android.app.Application

class VeniceApplication : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var database: VeniceDatabase
        private set

    lateinit var repository: VeniceRepository
        private set

    lateinit var voiceController: VoiceController
        private set

    override fun onCreate() {
        super.onCreate()
        securePreferences = SecurePreferences(this)
        database = VeniceDatabase.getInstance(this)
        val apiClient = VeniceApiClient(securePreferences, cacheDir)
        repository = VeniceRepository(database.veniceDao(), apiClient, securePreferences)
        voiceController = VoiceController(this)
    }
}
