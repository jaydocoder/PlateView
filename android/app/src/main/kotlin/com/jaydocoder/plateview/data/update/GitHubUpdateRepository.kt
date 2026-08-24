package com.jaydocoder.plateview.data.update

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.jaydocoder.plateview.BuildConfig
import com.jaydocoder.plateview.domain.update.AppUpdate
import com.jaydocoder.plateview.domain.update.AppUpdateRepository
import com.jaydocoder.plateview.domain.update.AppVersion
import com.jaydocoder.plateview.domain.update.UpdateDownloadProgress
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

private const val RELEASE_ASSET_NAME = "app-release.apk"
private const val GITHUB_API_BASE_URL = "https://api.github.com/"
private const val SERVER_UPDATE_BASE_URL = "https://api.chenxiruyu.dpdns.org/updates/"

interface GitHubReleaseApi {
    @GET("repos/jaydocoder/PlateView/releases/latest")
    suspend fun latestRelease(): GitHubReleaseDto
}

interface ServerUpdateApi {
    @GET("latest.json")
    suspend fun latestUpdate(): ServerUpdateDto
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
    val apkUrl: String,
    val sha256: String,
)

@Singleton
class GitHubUpdateRepository @Inject constructor(
    private val api: GitHubReleaseApi,
    private val serverApi: ServerUpdateApi,
    @UpdateHttpClient private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) : AppUpdateRepository {
    override suspend fun findAvailableUpdate(): AppUpdate? {
        val installedVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: return null
        val serverUpdate = runCatching { serverApi.latestUpdate() }.getOrNull()
        val githubRelease = runCatching { api.latestRelease() }.getOrNull()
        val githubUpdate = githubRelease?.let { release ->
            val version = AppVersion.parse(release.tagName) ?: return@let null
            val asset = release.assets.firstOrNull { it.name == RELEASE_ASSET_NAME } ?: return@let null
            AvailableSource(version, release.tagName.removePrefix("v"), release.body.orEmpty().trim(), asset.browserDownloadUrl)
        }
        val serverSource = serverUpdate?.let { update ->
            AppVersion.parse(update.versionName)?.let { version ->
                AvailableSource(version, update.versionName.removePrefix("v"), update.releaseNotes.orEmpty().trim(), update.apkUrl)
            }
        }
        val preferred = githubUpdate ?: serverSource ?: return null
        if (preferred.version <= installedVersion) return null
        val matchingServer = serverSource?.takeIf { it.version == preferred.version }
        return AppUpdate(
            versionName = preferred.versionName,
            releaseNotes = preferred.releaseNotes.ifBlank { matchingServer?.releaseNotes.orEmpty() },
            downloadUrls = listOfNotNull(preferred.downloadUrl, matchingServer?.downloadUrl).distinct(),
            sha256 = serverUpdate?.takeIf { AppVersion.parse(it.versionName) == preferred.version }?.sha256,
        )
    }

    override suspend fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ) = ResumableApkDownloader(
        client = client,
        downloadDirectory = java.io.File(context.cacheDir, "updates"),
    ).download(update, onProgress)
}

private data class AvailableSource(
    val version: AppVersion,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

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

    @Provides
    @Singleton
    fun provideServerUpdateApi(@UpdateHttpClient client: OkHttpClient): ServerUpdateApi = Retrofit.Builder()
        .baseUrl(SERVER_UPDATE_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ServerUpdateApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateBindingModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(repository: GitHubUpdateRepository): AppUpdateRepository
}
