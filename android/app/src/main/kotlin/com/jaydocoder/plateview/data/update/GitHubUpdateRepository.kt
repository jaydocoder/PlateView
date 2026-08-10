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

@Singleton
class GitHubUpdateRepository @Inject constructor(
    private val api: GitHubReleaseApi,
    @UpdateHttpClient private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) : AppUpdateRepository {
    override suspend fun findAvailableUpdate(): AppUpdate? {
        val release = api.latestRelease()
        val releaseVersion = AppVersion.parse(release.tagName) ?: return null
        val installedVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: return null
        if (releaseVersion <= installedVersion) return null
        val asset = release.assets.firstOrNull { it.name == RELEASE_ASSET_NAME } ?: return null
        return AppUpdate(
            versionName = release.tagName.removePrefix("v"),
            releaseNotes = release.body.orEmpty().trim(),
            downloadUrl = asset.browserDownloadUrl,
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
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateBindingModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(repository: GitHubUpdateRepository): AppUpdateRepository
}
