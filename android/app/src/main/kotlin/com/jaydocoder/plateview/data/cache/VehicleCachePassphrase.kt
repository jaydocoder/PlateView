package com.jaydocoder.plateview.data.cache

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleCachePassphrase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getOrCreate(): ByteArray {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val encrypted = preferences.getString(PASSPHRASE, null)
        val initializationVector = preferences.getString(INITIALIZATION_VECTOR, null)
        if (encrypted != null && initializationVector != null) {
            return decrypt(encrypted, initializationVector)
        }

        val passphrase = ByteArray(PASSPHRASE_SIZE).also(SecureRandom()::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE)
        val encryptedPassphrase = cipher.doFinal(passphrase)
        check(
            preferences.edit()
                .putString(PASSPHRASE, Base64.encodeToString(encryptedPassphrase, Base64.NO_WRAP))
                .putString(INITIALIZATION_VECTOR, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit(),
        ) { "无法保存车辆缓存密钥" }
        return passphrase
    }

    private fun decrypt(encrypted: String, initializationVector: String): ByteArray = cipher(Cipher.DECRYPT_MODE, Base64.decode(initializationVector, Base64.NO_WRAP))
        .doFinal(Base64.decode(encrypted, Base64.NO_WRAP))

    private fun cipher(mode: Int, initializationVector: ByteArray? = null): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        initializationVector?.let { init(mode, key(), GCMParameterSpec(TAG_LENGTH_BITS, it)) } ?: init(mode, key())
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "plateview_vehicle_cache_key"
        const val PREFERENCES_NAME = "vehicle_cache_key"
        const val PASSPHRASE = "encrypted_passphrase"
        const val INITIALIZATION_VECTOR = "initialization_vector"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_SIZE = 32
        const val TAG_LENGTH_BITS = 128
    }
}
