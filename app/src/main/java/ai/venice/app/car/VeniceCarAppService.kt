package ai.venice.app.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Android Auto entry point for hands-free voice chat with Venice AI.
 */
class VeniceCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return VeniceCarSession()
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return VeniceCarSession()
    }
}

class VeniceCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return VeniceCarScreen(carContext)
    }
}
