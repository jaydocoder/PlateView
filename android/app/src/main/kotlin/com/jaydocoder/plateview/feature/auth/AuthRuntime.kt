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
import com.jaydocoder.plateview.domain.workorder.WorkOrderRepository
import com.jaydocoder.plateview.data.network.ClientPolicyApi
import com.jaydocoder.plateview.data.network.ClientRuntimePolicy

private val Context.authDataStore by preferencesDataStore("auth_session")

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val username: String,
    val role: String,
    val userId: Long = 0L,
    val avatarVersion: Long = 0L,
    val scheduleEnabled: Boolean = false,
    val updatePolicy: String = "OPTIONAL",
    val wechatWorkOrderAccessEnabled: Boolean = false,
)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val accessToken: String, val refreshToken: String, val user: UserDto, val runtimePolicy: ClientRuntimePolicy)
data class UserDto(val id: Long, val username: String, val role: String, val avatarVersion: Long, val scheduleEnabled: Boolean = false, val updatePolicy: String = "OPTIONAL", val wechatWorkOrderAccessEnabled: Boolean = false)
data class ProfileDto(val id: Long, val username: String, val role: String, val avatarVersion: Long, val hasAvatar: Boolean, val scheduleEnabled: Boolean = false, val updatePolicy: String = "OPTIONAL", val wechatWorkOrderAccessEnabled: Boolean = false, val runtimePolicy: ClientRuntimePolicy? = null)
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
    private val workOrderRepository: WorkOrderRepository,
    private val runtimeCoordinator: ClientRuntimeCoordinator,
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
            updatePolicy = p[UPDATE_POLICY] ?: "OPTIONAL",
            wechatWorkOrderAccessEnabled = p[WECHAT_WORK_ORDER_ACCESS] ?: false,
        )
    }

    suspend fun login(username: String, password: String) {
        val response = api.login(LoginRequest(username, password))
        if (!response.user.wechatWorkOrderAccessEnabled) {
            workOrderRepository.clear(response.user.id)
        }
        context.authDataStore.edit { preferences ->
            preferences[ACCESS] = response.accessToken
            preferences[REFRESH] = response.refreshToken
            preferences[USER_ID] = response.user.id
            preferences[USERNAME] = response.user.username
            preferences[ROLE] = response.user.role
            preferences[AVATAR_VERSION] = response.user.avatarVersion
            preferences[SCHEDULE_ENABLED] = response.user.scheduleEnabled
            preferences[UPDATE_POLICY] = response.user.updatePolicy
            preferences[WECHAT_WORK_ORDER_ACCESS] = response.user.wechatWorkOrderAccessEnabled
        }
        runtimeCoordinator.apply(response.user.toSession(response.accessToken, response.refreshToken), response.runtimePolicy)
    }

    override suspend fun logout() {
        session.first()?.userId?.let { workOrderRepository.clear(it) }
        context.authDataStore.edit { it.clear() }
    }

    suspend fun updateAvatarVersion(version: Long) {
        context.authDataStore.edit { preferences -> preferences[AVATAR_VERSION] = version }
    }

    suspend fun updateProfile(accessToken: String, request: ProfileUpdateRequest) {
        api.updateProfile("Bearer $accessToken", request)
    }

    suspend fun validateSession(session: AuthSession) {
        val profile = api.profile("Bearer ${session.accessToken}")
        profile.runtimePolicy?.let { runtimeCoordinator.apply(session, it) }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
        val USERNAME = stringPreferencesKey("username")
        val ROLE = stringPreferencesKey("role")
        val USER_ID = longPreferencesKey("user_id")
        val AVATAR_VERSION = longPreferencesKey("avatar_version")
        val SCHEDULE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("schedule_enabled")
        val UPDATE_POLICY = stringPreferencesKey("update_policy")
        val WECHAT_WORK_ORDER_ACCESS = androidx.datastore.preferences.core.booleanPreferencesKey("wechat_work_order_access")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideClientPolicyApi(retrofit: Retrofit): ClientPolicyApi = retrofit.create(ClientPolicyApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingModule {
    @Binds
    @Singleton
    abstract fun bindAuthSessionProvider(repository: AuthRepository): AuthSessionProvider
}

private fun UserDto.toSession(accessToken: String, refreshToken: String) = AuthSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    username = username,
    role = role,
    userId = id,
    avatarVersion = avatarVersion,
    scheduleEnabled = scheduleEnabled,
    updatePolicy = updatePolicy,
    wechatWorkOrderAccessEnabled = wechatWorkOrderAccessEnabled,
)
