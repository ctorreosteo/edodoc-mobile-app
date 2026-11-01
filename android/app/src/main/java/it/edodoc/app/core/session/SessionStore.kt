package it.edodoc.app.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import it.edodoc.app.model.Sessione
import it.edodoc.app.model.Utente
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Dove sopravvive la sessione. Separato dalla piattaforma così il rinnovo si prova nei test. */
interface SessionStore {
    fun carica(): Sessione?
    fun salva(sessione: Sessione)
    fun cancella()
}

/** La sessione è cifrata con una chiave AES-GCM che non esce mai dall'Android Keystore. */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("sessione", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun carica(): Sessione? = try {
        prefs.getString(CHIAVE, null)?.let { json.decodeFromString<Sessione>(decifra(it)) }
    } catch (_: Exception) {
        // Chiave invalidata (ad esempio dopo un ripristino): meglio un nuovo accesso che un'app bloccata
        cancella()
        null
    }

    override fun salva(sessione: Sessione) = prefs.edit { putString(CHIAVE, cifra(json.encodeToString(sessione))) }

    override fun cancella() = prefs.edit { remove(CHIAVE) }

    private fun chiave(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generatore = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generatore.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generatore.generateKey()
    }

    private fun cifra(testo: String): String {
        val cipher = Cipher.getInstance(TRASFORMAZIONE).apply { init(Cipher.ENCRYPT_MODE, chiave()) }
        val dati = cipher.iv + cipher.doFinal(testo.toByteArray())
        return Base64.encodeToString(dati, Base64.NO_WRAP)
    }

    private fun decifra(valore: String): String {
        val dati = Base64.decode(valore, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRASFORMAZIONE)
        cipher.init(Cipher.DECRYPT_MODE, chiave(), GCMParameterSpec(128, dati, 0, LUNGHEZZA_IV))
        return String(cipher.doFinal(dati, LUNGHEZZA_IV, dati.size - LUNGHEZZA_IV))
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "edodoc_sessione"
        const val CHIAVE = "sessione"
        const val TRASFORMAZIONE = "AES/GCM/NoPadding"
        const val LUNGHEZZA_IV = 12
    }
}
