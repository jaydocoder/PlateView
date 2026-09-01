package com.jaydocoder.plateview.feature.auth

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore("auth_session")

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val username: String,
    val role: String,
    val userId: Long = 0L,
    val avatarVersion: Long = 0L,
    val scheduleEnabled: Boolean = false,
)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val accessToken: String, val refreshToken: String, val user: UserDto)
data class UserDto(val id: Long, val username: String, val role: String, val avatarVersion: Long, val scheduleEnabled: Boolean = false)
data class ProfileDto(val id: Long, val username: String, val role: String, val avatarVersion: Long, val hasAvatar: Boolean, val scheduleEnabled: Boolean = false)
data class ProfileUpdateRequest(
    val username: String? = null,
    val password: String? = null,
    val currentPassword: String? = null,
)

interface AuthSessionProvider {
    val session: Flow<AuthSession?>

    suspend fun logout()
}

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("auth/profile")
    suspend fun profile(@Header("Authorization") authorization: String): ProfileDto

    @POST("auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: ProfileUpdateRequest,
    )

    @Multipart
    @POST("auth/profile/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") authorization: String,
        @Part avatar: MultipartBody.Part,
    ): ProfileDto

    @GET("auth/profile/avatar")
    suspend fun downloadAvatar(@Header("Authorization") authorization: String): ResponseBody

    @POST("auth/profile/avatar/delete")
    suspend fun deleteAvatar(@Header("Authorization") authorization: String): ProfileDto
}

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AuthApi,
) : AuthSessionProvider {
    override val session: Flow<AuthSession?> = context.authDataStore.data.map { p ->
        val access = p[ACCESS] ?: return@map null
        val refresh = p[REFRESH] ?: return@map null
        AuthSession(
            accessToken = access,
            refreshToken = refresh,
            username = p[USERNAME].orEmpty(),
            role = p[ROLE].orEmpty(),
            userId = p[USER_ID] ?: return@map null,
            avatarVersion = p[AVATAR_VERSION] ?: 0L,
            scheduleEnabled = p[SCHEDULE_ENABLED] ?: false,
        )
    }

    suspend fun login(username: String, password: String) {
        val response = api.login(LoginRequest(username, password))
        context.authDataStore.edit { preferences ->
            preferences[ACCESS] = response.accessToken
            preferences[REFRESH] = response.refreshToken
            preferences[USER_ID] = response.user.id
            preferences[USERNAME] = response.user.username
            preferences[ROLE] = response.user.role
            preferences[AVATAR_VERSION] = response.user.avatarVersion
            preferences[SCHEDULE_ENABLED] = response.user.scheduleEnabled
        }
    }

    override suspend fun logout() {
        context.authDataStore.edit { it.clear() }
    }

    suspend fun updateAvatarVersion(version: Long) {
        context.authDataStore.edit { preferences -> preferences[AVATAR_VERSION] = version }
    }

    suspend fun updateProfile(accessToken: String, request: ProfileUpdateRequest) {
        api.updateProfile("Bearer $accessToken", request)
    }

    suspend fun validateSession(session: AuthSession) {
        api.profile("Bearer ${session.accessToken}")
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
        val USERNAME = stringPreferencesKey("username")
        val ROLE = stringPreferencesKey("role")
        val USER_ID = longPreferencesKey("user_id")
        val AVATAR_VERSION = longPreferencesKey("avatar_version")
        val SCHEDULE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("schedule_enabled")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingModule {
    @Binds
    @Singleton
    abstract fun bindAuthSessionProvider(repository: AuthRepository): AuthSessionProvider
}
