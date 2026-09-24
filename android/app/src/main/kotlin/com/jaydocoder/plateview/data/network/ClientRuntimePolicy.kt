package com.jaydocoder.plateview.data.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.jaydocoder.plateview.BuildConfig
import com.jaydocoder.plateview.domain.update.ApkArchitecture
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import com.jaydocoder.plateview.feature.consistency.ClientCatalogState

private val Context.clientRuntimePolicyDataStore by preferencesDataStore("client_runtime_policy")
private const val DEFAULT_UPDATE_BASE_URL = "https://api.chenxiruyu.dpdns.org/updates/"

data class ClientRuntimePolicy(
    val policyRevision: Long = 0,
    val vehicleResultLimit: Int = 8,
    val workOrderResultLimit: Int = 8,
    val wechatMessageResultLimit: Int = 8,
    val apiBaseUrl: String = BuildConfig.API_BASE_URL,
    val previousApiBaseUrl: String? = null,
    val updateBaseUrl: String = DEFAULT_UPDATE_BASE_URL,
    val previousUpdateBaseUrl: String? = null,
    val cacheResetRevision: Long = 0,
)

interface ClientRuntimePolicyProvider {
    val policy: StateFlow<ClientRuntimePolicy>
    val cacheMaintenanceActive: StateFlow<Boolean>
    suspend fun clientInstanceId(): String = "default-client"
}

object DefaultClientRuntimePolicyProvider : ClientRuntimePolicyProvider {
    override val policy = kotlinx.coroutines.flow.MutableStateFlow(ClientRuntimePolicy())
    override val cacheMaintenanceActive = MutableStateFlow(false)
}

data class ClientPolicyAckRequest(
    val clientInstanceId: String,
    val appliedCacheResetRevision: Long,
    val appliedPolicyRevision: Long,
    val apiBaseUrl: String,
    val updateBaseUrl: String,
)

interface ClientPolicyApi {
    @GET("client/catalog-state")
    suspend fun catalogState(
        @Header("Authorization") authorization: String,
    ): ClientCatalogState

    @POST("client/cache-reset/ack")
    suspend fun acknowledge(
        @Header("Authorization") authorization: String,
        @Body request: ClientPolicyAckRequest,
    )
}

@Singleton
class ClientRuntimePolicyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClientRuntimePolicyProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val validationClient = OkHttpClient.Builder().build()
    override val cacheMaintenanceActive = MutableStateFlow(false)
    override val policy: StateFlow<ClientRuntimePolicy> = context.clientRuntimePolicyDataStore.data
        .map { preferences ->
            ClientRuntimePolicy(
                policyRevision = preferences[POLICY_REVISION] ?: 0,
                vehicleResultLimit = preferences[VEHICLE_LIMIT] ?: 8,
                workOrderResultLimit = preferences[WORK_ORDER_LIMIT] ?: 8,
                wechatMessageResultLimit = preferences[WECHAT_MESSAGE_LIMIT] ?: 8,
                apiBaseUrl = preferences[API_BASE_URL] ?: BuildConfig.API_BASE_URL,
                previousApiBaseUrl = preferences[PREVIOUS_API_BASE_URL],
                updateBaseUrl = preferences[UPDATE_BASE_URL] ?: DEFAULT_UPDATE_BASE_URL,
                previousUpdateBaseUrl = preferences[PREVIOUS_UPDATE_BASE_URL],
                cacheResetRevision = preferences[CACHE_RESET_REVISION] ?: 0,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, ClientRuntimePolicy())

    suspend fun applyRemotePolicy(remote: ClientRuntimePolicy, accessToken: String) {
        require(remote.vehicleResultLimit in 0..50 && remote.workOrderResultLimit in 0..50 && remote.wechatMessageResultLimit in 0..50) {
            "服务器返回的搜索数量无效"
        }
        val current = policy.first()
        val requestedApiBaseUrl = runCatching { normalizeBaseUrl(remote.apiBaseUrl) }.getOrDefault(current.apiBaseUrl)
        val requestedUpdateBaseUrl = runCatching { normalizeBaseUrl(remote.updateBaseUrl) }.getOrDefault(current.updateBaseUrl)
        val apiBaseUrl = requestedApiBaseUrl.takeIf {
            it == current.apiBaseUrl || runCatching { validateApiEndpoint(it, accessToken) }.isSuccess
        } ?: current.apiBaseUrl
        val updateBaseUrl = requestedUpdateBaseUrl.takeIf {
            it == current.updateBaseUrl || runCatching { validateUpdateEndpoint(it) }.isSuccess
        } ?: current.updateBaseUrl
        context.clientRuntimePolicyDataStore.edit { preferences ->
            if (apiBaseUrl != current.apiBaseUrl) preferences[PREVIOUS_API_BASE_URL] = current.apiBaseUrl
            if (updateBaseUrl != current.updateBaseUrl) preferences[PREVIOUS_UPDATE_BASE_URL] = current.updateBaseUrl
            preferences[POLICY_REVISION] = remote.policyRevision
            preferences[VEHICLE_LIMIT] = remote.vehicleResultLimit
            preferences[WORK_ORDER_LIMIT] = remote.workOrderResultLimit
            preferences[WECHAT_MESSAGE_LIMIT] = remote.wechatMessageResultLimit
            preferences[API_BASE_URL] = apiBaseUrl
            preferences[UPDATE_BASE_URL] = updateBaseUrl
            preferences[CACHE_RESET_REVISION] = remote.cacheResetRevision
            if (preferences[CLIENT_INSTANCE_ID].isNullOrBlank()) preferences[CLIENT_INSTANCE_ID] = UUID.randomUUID().toString()
        }
        policy.first {
            it.policyRevision >= remote.policyRevision &&
                it.apiBaseUrl == apiBaseUrl &&
                it.updateBaseUrl == updateBaseUrl &&
                it.cacheResetRevision >= remote.cacheResetRevision
        }
    }

    fun beginCacheMaintenance() {
        cacheMaintenanceActive.value = true
    }

    fun endCacheMaintenance() {
        cacheMaintenanceActive.value = false
    }

    fun rollbackApiBaseUrl(failedBaseUrl: String) {
        scope.launch {
            context.clientRuntimePolicyDataStore.edit { preferences ->
                val active = preferences[API_BASE_URL] ?: BuildConfig.API_BASE_URL
                val previous = preferences[PREVIOUS_API_BASE_URL]
                if (active == failedBaseUrl && !previous.isNullOrBlank() && previous != active) {
                    preferences[API_BASE_URL] = previous
                    preferences[PREVIOUS_API_BASE_URL] = active
                }
            }
        }
    }

    override suspend fun clientInstanceId(): String {
        val existing = context.clientRuntimePolicyDataStore.data.first()[CLIENT_INSTANCE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.clientRuntimePolicyDataStore.edit { it[CLIENT_INSTANCE_ID] = generated }
        return generated
    }

    suspend fun appliedCacheResetRevision(userId: Long): Long =
        context.clientRuntimePolicyDataStore.data.first()[appliedCacheResetRevisionKey(userId)] ?: 0

    suspend fun markCacheResetApplied(userId: Long, revision: Long) {
        context.clientRuntimePolicyDataStore.edit { preferences ->
            val key = appliedCacheResetRevisionKey(userId)
            preferences[key] = maxOf(preferences[key] ?: 0, revision)
        }
    }

    private fun appliedCacheResetRevisionKey(userId: Long) = longPreferencesKey("applied_cache_reset_revision_$userId")

    private fun validateApiEndpoint(baseUrl: String, accessToken: String) {
        execute(baseUrl, "health")
        execute(baseUrl, "auth/profile", accessToken)
    }

    private fun validateUpdateEndpoint(baseUrl: String) {
        val manifestResponse = execute(baseUrl, "latest.json")
        val manifest = Gson().fromJson(manifestResponse, UpdateManifestProbe::class.java)
        val artifacts = manifest.artifacts ?: mapOf(
            ApkArchitecture.UNIVERSAL to UpdateArtifactProbe(
                manifest.apkPath ?: manifest.apkUrl ?: throw IOException("更新清单缺少APK路径"),
                manifest.sha256,
                manifest.sizeBytes,
            ),
        )
        listOf(ApkArchitecture.ARM64_V8A, ApkArchitecture.ARMEABI_V7A, ApkArchitecture.UNIVERSAL).forEach { architecture ->
            val artifact = artifacts[architecture] ?: throw IOException("更新清单缺少$architecture APK")
            validateUpdateArtifact(baseUrl, artifact)
        }
    }

    private fun validateUpdateArtifact(baseUrl: String, artifact: UpdateArtifactProbe) {
        val path = artifact.apkPath.takeIf { it.isNotBlank() && it.toHttpUrlOrNull() == null }
            ?: throw IOException("更新清单APK路径必须是相对路径")
        val url = baseUrl.toHttpUrlOrNull()?.resolve(path)?.toString()
            ?: throw IOException("更新清单APK路径无效")
        val request = Request.Builder().url(url).header("Range", "bytes=0-1023").build()
        validationClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful || (response.body?.contentLength() ?: 0) == 0L) throw IOException("更新APK不可下载")
        }
        val expectedSize = artifact.sizeBytes ?: throw IOException("更新清单缺少文件大小")
        val expectedSha = artifact.sha256?.takeIf(String::isNotBlank)
            ?: throw IOException("更新清单缺少文件摘要")
        validationClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("更新APK不可下载")
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            checkNotNull(response.body).byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    size += count
                }
            }
            if (expectedSize != size) throw IOException("更新APK文件大小不一致")
            val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (!actual.equals(expectedSha, ignoreCase = true)) throw IOException("更新APK摘要不一致")
        }
    }

    private fun execute(baseUrl: String, relativePath: String, accessToken: String? = null): String {
        val url = baseUrl.toHttpUrlOrNull()?.resolve(relativePath) ?: throw IOException("服务地址无效")
        val builder = Request.Builder().url(url)
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        validationClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("服务地址验证失败：HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun normalizeBaseUrl(value: String): String {
        val normalized = value.trim().let { if (it.endsWith('/')) it else "$it/" }
        requireNotNull(normalized.toHttpUrlOrNull()) { "服务地址格式无效" }
        return normalized
    }

    private data class UpdateManifestProbe(
        val apkPath: String?,
        val apkUrl: String?,
        val sha256: String?,
        val sizeBytes: Long?,
        val artifacts: Map<String, UpdateArtifactProbe>?,
    )

    private data class UpdateArtifactProbe(
        val apkPath: String,
        val sha256: String?,
        val sizeBytes: Long?,
    )

    private companion object {
        val POLICY_REVISION = longPreferencesKey("policy_revision")
        val VEHICLE_LIMIT = intPreferencesKey("vehicle_result_limit")
        val WORK_ORDER_LIMIT = intPreferencesKey("work_order_result_limit")
        val WECHAT_MESSAGE_LIMIT = intPreferencesKey("wechat_message_result_limit")
        val API_BASE_URL = stringPreferencesKey("api_base_url")
        val PREVIOUS_API_BASE_URL = stringPreferencesKey("previous_api_base_url")
        val UPDATE_BASE_URL = stringPreferencesKey("update_base_url")
        val PREVIOUS_UPDATE_BASE_URL = stringPreferencesKey("previous_update_base_url")
        val CACHE_RESET_REVISION = longPreferencesKey("cache_reset_revision")
        val CLIENT_INSTANCE_ID = stringPreferencesKey("client_instance_id")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClientRuntimePolicyModule {
    @Binds
    @Singleton
    abstract fun bindClientRuntimePolicyProvider(repository: ClientRuntimePolicyRepository): ClientRuntimePolicyProvider
}
