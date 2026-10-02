package io.github.lethe_labs.argos

import android.app.Application
import android.content.Context
import io.github.lethe_labs.argos.platform.JournalStore
import io.github.lethe_labs.argos.platform.SealKeyProvider
import io.github.lethe_labs.argos.platform.SessionStore

class ArgosApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    companion object {
        fun container(context: Context): AppContainer = (context.applicationContext as ArgosApp).container
    }
}

/**
 * Manual dependency wiring: the app is small enough that a DI framework would
 * add more build complexity than it removes.
 */
class AppContainer(app: Application) {
    val journal = JournalStore(app)

    // Lazy because it lives in credential-encrypted storage, which does not
    // exist yet when the receivers run before the first unlock.
    val sessions: SessionStore by lazy { SessionStore(app) }

    val sealKeys = SealKeyProvider()
}
