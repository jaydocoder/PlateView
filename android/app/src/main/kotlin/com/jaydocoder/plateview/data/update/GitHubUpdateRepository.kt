package com.jaydocoder.plateview.data.update

import android.content.Context
import android.os.Build
import com.google.gson.annotations.SerializedName
import com.jaydocoder.plateview.BuildConfig
import com.jaydocoder.plateview.domain.update.AppUpdate
import com.jaydocoder.plateview.domain.update.AppUpdateRepository
import com.jaydocoder.plateview.domain.update.AppVersion
import com.jaydocoder.plateview.domain.update.ApkArchitecture
import com.jaydocoder.plateview.domain.update.UpdateDownloadProgress
import com.jaydocoder.plateview.domain.update.UpdateCheckResult
import com.jaydocoder.plateview.data.network.ClientRuntimePolicyRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.jaydocoder.plateview.data.network.RequestIdInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RELEASE_ASSET_PREFIX = "PlateView-"
private const val GITHUB_API_BASE_URL = "https://api.github.com/"
private const val LEGACY_SERVER_UPDATE_BASE_URL = "https://api.chenxiruyu.dpdns.org/updates/"

interface GitHubReleaseApi {
    @GET("repos/jaydocoder/PlateView/releases/latest")
    suspend fun latestRelease(): GitHubReleaseDto
}

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    val body: String?,
    val assets: List<GitHubReleaseAssetDto>,
)

data class GitHubReleaseAssetDto(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
)

data class ServerUpdateDto(
    val versionName: String,
    val releaseNotes: String? = null,
    val apkUrl: String? = null,
    val sha256: String,
    val apkPath: String? = null,
    val sizeBytes: Long? = null,
    val artifacts: Map<String, ServerArtifactDto>? = null,
)

data class ServerArtifactDto(
    val apkPath: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Singleton
class GitHubUpdateRepository @Inject constructor(
    private val api: GitHubReleaseApi,
    @UpdateHttpClient private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
    private val runtimePolicyRepository: ClientRuntimePolicyRepository,
) : AppUpdateRepository {
    override suspend fun checkForUpdate(): UpdateCheckResult {
        val installedVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: return UpdateCheckResult.UpToDate
        val architecture = ApkArchitecture.select(Build.SUPPORTED_ABIS.toList())
        val updateBaseUrl = runtimePolicyRepository.policy.value.updateBaseUrl
        val serverResult = runCatching { loadServerUpdate(updateBaseUrl) }
        val githubResult = runCatching { api.latestRelease() }
        if (serverResult.isFailure && githubResult.isFailure) return UpdateCheckResult.Unavailable
        val serverUpdate = serverResult.getOrNull()
        val available = resolveAvailableUpdate(installedVersion, githubResult.getOrNull(), serverUpdate, updateBaseUrl, architecture)
        pruneUpdateFiles(available?.versionName)
        return available?.let(UpdateCheckResult::Available) ?: UpdateCheckResult.UpToDate
    }

    override suspend fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ) = ResumableApkDownloader(
        client = client,
        downloadDirectory = java.io.File(context.cacheDir, "updates"),
    ).download(update, onProgress)

    private suspend fun loadServerUpdate(baseUrl: String): ServerUpdateDto = withContext(Dispatchers.IO) {
        val url = requireNotNull(baseUrl.toHttpUrlOrNull()?.resolve("latest.json")) { "更新服务地址无效" }
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "更新清单加载失败：HTTP ${response.code}" }
            Gson().fromJson(checkNotNull(response.body).charStream(), ServerUpdateDto::class.java)
        }
    }

    private fun pruneUpdateFiles(activeVersion: String?) {
        val directory = java.io.File(context.cacheDir, "updates")
        directory.listFiles()?.forEach { file ->
            val activePrefix = activeVersion?.let { "PlateView-$it-" }
            if (activePrefix == null || !file.name.startsWith(activePrefix)) file.delete()
        }
    }
}

private data class AvailableSource(
    val version: AppVersion,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val artifactName: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val architecture: String,
    val sourcePriority: Int,
)

internal fun resolveAvailableUpdate(
    installedVersion: AppVersion,
    githubRelease: GitHubReleaseDto?,
    serverUpdate: ServerUpdateDto?,
    serverBaseUrl: String = LEGACY_SERVER_UPDATE_BASE_URL,
    architecture: String = ApkArchitecture.UNIVERSAL,
): AppUpdate? {
    val githubSource = githubRelease?.let { release ->
        val version = AppVersion.parse(release.tagName) ?: return@let null
        val tag = release.tagName.removePrefix("v")
        val preferredName = "$RELEASE_ASSET_PREFIX${release.tagName}-$architecture.apk"
        val universalName = "$RELEASE_ASSET_PREFIX${release.tagName}-${ApkArchitecture.UNIVERSAL}.apk"
        val selected = release.assets.firstOrNull { it.name == preferredName }?.let { it to architecture }
            ?: release.assets.firstOrNull { it.name == universalName }?.let { it to ApkArchitecture.UNIVERSAL }
            ?: release.assets.firstOrNull { it.name == "app-release.apk" }?.let { it to ApkArchitecture.UNIVERSAL }
            ?: return@let null
        val (asset, selectedArchitecture) = selected
        AvailableSource(version, tag, release.body.orEmpty().trim(), asset.browserDownloadUrl, asset.name, architecture = selectedArchitecture, sourcePriority = 1)
    }
    val serverSource = serverUpdate?.let { update ->
        AppVersion.parse(update.versionName)?.let { version ->
            val selectedArtifact = update.artifacts?.get(architecture)?.let { it to architecture }
                ?: update.artifacts?.get(ApkArchitecture.UNIVERSAL)?.let { it to ApkArchitecture.UNIVERSAL }
            val artifact = selectedArtifact?.first
            val selectedArchitecture = selectedArtifact?.second ?: ApkArchitecture.UNIVERSAL
            val path = artifact?.apkPath?.takeIf { it.isNotBlank() && it.toHttpUrlOrNull() == null } ?: update.apkPath
                ?.takeIf { path -> path.isNotBlank() && path.toHttpUrlOrNull() == null }
            val downloadUrl = path?.let { serverBaseUrl.toHttpUrlOrNull()?.resolve(it)?.toString() }
                ?: update.apkUrl
                ?: return@let null
            val artifactName = path?.substringAfterLast('/') ?: "PlateView-${update.versionName}.apk"
            AvailableSource(
                version,
                update.versionName.removePrefix("v"),
                update.releaseNotes.orEmpty().trim(),
                downloadUrl,
                artifactName,
                artifact?.sha256 ?: update.sha256,
                artifact?.sizeBytes ?: update.sizeBytes,
                architecture = selectedArchitecture,
                sourcePriority = 0,
            )
        }
    }
    val preferred = listOfNotNull(githubSource, serverSource)
        .filter { it.version > installedVersion }
        .maxByOrNull { it.version }
        ?: return null
    val matchingSources = listOfNotNull(githubSource, serverSource)
        .filter { it.version == preferred.version }
        .sortedBy(AvailableSource::sourcePriority)
    return AppUpdate(
        versionName = preferred.versionName,
        releaseNotes = matchingSources.firstNotNullOfOrNull { it.releaseNotes.takeIf(String::isNotBlank) }.orEmpty(),
        downloadUrls = matchingSources.map(AvailableSource::downloadUrl).distinct(),
        sha256 = matchingSources.firstNotNullOfOrNull { it.sha256 },
        architecture = preferred.architecture,
        artifactName = preferred.artifactName,
        sizeBytes = matchingSources.firstNotNullOfOrNull { it.sizeBytes },
    )
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class UpdateHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateNetworkModule {
    @Provides
    @Singleton
    @UpdateHttpClient
    fun provideUpdateHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(RequestIdInterceptor())
        .addInterceptor(Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "PlateView-Android/${BuildConfig.VERSION_NAME}")
                    .build(),
            )
        })
        .build()

    @Provides
    @Singleton
    fun provideGitHubReleaseApi(@UpdateHttpClient client: OkHttpClient): GitHubReleaseApi = Retrofit.Builder()
        .baseUrl(GITHUB_API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GitHubReleaseApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateBindingModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(repository: GitHubUpdateRepository): AppUpdateRepository
}
