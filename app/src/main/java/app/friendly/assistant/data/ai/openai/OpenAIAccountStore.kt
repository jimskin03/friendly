package app.friendly.assistant.data.ai.openai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
internal data class OpenAIStoredAccount(
    val id: String,
    val clientId: String,
    val subject: String = "",
    val email: String = "",
    val name: String = "",
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("id_token") val idToken: String = "",
    val scopes: List<String> = emptyList(),
    val accessExpiresAt: Long = 0L,
    val earliestRefreshAt: Long? = null,
) {
    val isConnected: Boolean get() = accessToken.isNotBlank() && subject.isNotBlank()

    override fun toString(): String =
        "OpenAIStoredAccount(id=$id, clientId=$clientId, subject=${subject.isNotBlank()}, " +
            "email=$email, connected=$isConnected, scopes=$scopes)"
}

data class OpenAIAccountSummary(
    val id: String,
    val subject: String,
    val email: String,
    val name: String,
    val isConnected: Boolean,
)

class OpenAIAccountStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "openai-accounts.enc"))
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var vault = OpenAIAccountVault()
    private var loaded = false
    private val mutableAccounts = MutableStateFlow<List<OpenAIAccountSummary>>(emptyList())
    val accounts: StateFlow<List<OpenAIAccountSummary>> = mutableAccounts

    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock { loadIfNeeded() }
    }

    internal suspend fun list(): List<OpenAIStoredAccount> = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadIfNeeded()
            vault.accounts
        }
    }

    internal suspend fun get(id: String): OpenAIStoredAccount? = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadIfNeeded()
            vault.accounts.firstOrNull { it.id == id }
        }
    }

    internal suspend fun hostId(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadIfNeeded()
            if (vault.hostId.isBlank()) persist(vault.withHostId())
            vault.hostId
        }
    }

    internal suspend fun save(account: OpenAIStoredAccount) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadIfNeeded()
            val next = vault.copy(accounts = vault.accounts.filterNot { it.id == account.id } + account)
            persist(next)
        }
    }

    internal suspend fun remove(id: String): OpenAIStoredAccount? = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadIfNeeded()
            val removed = vault.accounts.firstOrNull { it.id == id }
            if (removed != null) persist(vault.copy(accounts = vault.accounts.filterNot { it.id == id }))
            removed
        }
    }

    private fun loadIfNeeded() {
        if (loaded) return
        if (file.baseFile.exists()) {
            try {
                require(file.baseFile.length() <= MAX_VAULT_BYTES) { "OpenAI account vault is too large" }
                val encoded = file.openRead().use { it.readBytes() }
                vault = decrypt(encoded)
            } catch (error: Exception) {
                throw IllegalStateException("OpenAI account storage could not be opened. Reconnect the account to continue.", error)
            }
        }
        loaded = true
        mutableAccounts.value = vault.accounts.map { it.summary() }
    }

    private fun encrypt(plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decrypt(encoded: ByteArray): OpenAIAccountVault {
        require(encoded.size > 1 + GCM_IV_BYTES + GCM_TAG_BYTES) { "Invalid OpenAI account vault" }
        val ivSize = encoded[0].toInt() and 0xff
        require(ivSize == GCM_IV_BYTES && encoded.size > 1 + ivSize + GCM_TAG_BYTES) {
            "Invalid OpenAI account vault header"
        }
        val iv = encoded.copyOfRange(1, 1 + ivSize)
        val ciphertext = encoded.copyOfRange(1 + ivSize, encoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        return json.decodeFromString(plaintext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun OpenAIAccountVault.withHostId(): OpenAIAccountVault =
        if (hostId.isNotBlank()) this else copy(hostId = "urn:uuid:${UUID.randomUUID()}")

    private fun OpenAIStoredAccount.summary() = OpenAIAccountSummary(id, subject, email, name, isConnected)

    private fun persist(next: OpenAIAccountVault, ensureHostId: Boolean = true) {
        val record = if (ensureHostId) next.withHostId() else next
        val encrypted = encrypt(json.encodeToString(OpenAIAccountVault.serializer(), record))
        val output = file.startWrite()
        try {
            output.write(encrypted)
            file.finishWrite(output)
            vault = record
            mutableAccounts.value = record.accounts.map { it.summary() }
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    @Serializable
    private data class OpenAIAccountVault(
        val version: Int = 1,
        val hostId: String = "",
        val accounts: List<OpenAIStoredAccount> = emptyList(),
    )

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "friendly-openai-oauth-vault-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val MAX_VAULT_BYTES = 1 shl 20
    }
}
