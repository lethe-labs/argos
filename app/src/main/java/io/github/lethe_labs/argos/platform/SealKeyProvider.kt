package io.github.lethe_labs.argos.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Holds the seal key in the Android Keystore, where it cannot be exported.
 *
 * A fresh key is generated at every departure, so a code written down for a
 * previous trip can never validate a later one.
 */
class SealKeyProvider {

    fun rotate() {
        runCatching { keyStore().deleteEntry(ALIAS) }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        }.generateKey()
    }

    /**
     * Null when the key is gone. Deliberately never generates one here: on
     * return, a missing key is evidence that the app was reset, and must not be
     * papered over with a new key.
     */
    fun hmacOrNull(data: ByteArray): ByteArray? = runCatching {
        val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return null
        Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data)
    }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "argos_seal"
    }
}
