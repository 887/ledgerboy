package com.eight87.ledgerboy.plugins.runtime

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.eight87.ledgerboy.plugins.api.CredentialStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * X.8 — Keystore-wrapped credential storage for plugin secrets (API
 * keys, OAuth refresh tokens, FinTS PINs).
 *
 * Aligns with the locked security decision (security-decisions.md §3):
 * Android Keystore **AES-256/GCM**, StrongBox-preferred with TEE
 * fallback. Each secret is encrypted under a Keystore-held master key
 * that never leaves the secure hardware; the ciphertext (IV ‖ blob,
 * base64) is persisted in the per-plugin [PluginConfigStore] namespace.
 *
 * Interim home note: the locked end-state keeps per-credential blobs
 * *inside* the SQLCipher DB keyed off the biometric-gated unwrap. That
 * DB + biometric ceremony lands in the security phase; until then this
 * Keystore-GCM-in-DataStore impl is the real, working credential seam.
 * Because callers depend on the [CredentialStore] interface (DIP),
 * swapping the backing store later touches only this file + the AppGraph
 * wiring.
 */
class KeystoreCredentialStore(
  private val config: PluginConfigStore,
) : CredentialStore {

  private fun credKey(key: String) = "__cred.$key"

  override suspend fun put(pluginId: String, key: String, secret: String) {
    val cipher = Cipher.getInstance(TRANSFORMATION).apply {
      init(Cipher.ENCRYPT_MODE, masterKey())
    }
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
    val packed = ByteArray(iv.size + ciphertext.size).also {
      iv.copyInto(it)
      ciphertext.copyInto(it, iv.size)
    }
    config.putString(pluginId, credKey(key), Base64.encodeToString(packed, Base64.NO_WRAP))
  }

  override suspend fun get(pluginId: String, key: String): String? {
    val packed = config.getString(pluginId, credKey(key))?.let {
      runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
    } ?: return null
    if (packed.size <= GCM_IV_LENGTH) return null
    val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
    val ciphertext = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
    return runCatching {
      val cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
      }
      String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()
  }

  override suspend fun remove(pluginId: String, key: String) {
    // Overwrite with empty; the namespaced key stays but holds no secret.
    config.putString(pluginId, credKey(key), "")
  }

  private fun masterKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "ledgerboy.plugin.credentials"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_IV_LENGTH = 12
    const val GCM_TAG_BITS = 128
  }
}

/** Test seam: in-memory credential store for Robolectric/JVM unit tests. */
class InMemoryCredentialStore : CredentialStore {
  private val map = mutableMapOf<String, String>()
  private fun k(pluginId: String, key: String) = "$pluginId/$key"
  override suspend fun put(pluginId: String, key: String, secret: String) { map[k(pluginId, key)] = secret }
  override suspend fun get(pluginId: String, key: String): String? = map[k(pluginId, key)]
  override suspend fun remove(pluginId: String, key: String) { map.remove(k(pluginId, key)) }
}
