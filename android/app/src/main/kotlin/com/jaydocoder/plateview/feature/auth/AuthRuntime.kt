package com.jaydocoder.plateview.feature.auth

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.jaydocoder.plateview.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore("auth_session")

data class AuthSession(val accessToken: String, val refreshToken: String, val username: String, val role: String)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val accessToken: String, val refreshToken: String, val user: UserDto)
data class UserDto(val username: String, val role: String)

interface AuthApi { @POST("auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse }

@Singleton class AuthRepository @Inject constructor(@ApplicationContext private val context: Context, private val api: AuthApi) {
    val session: Flow<AuthSession?> = context.authDataStore.data.map { p ->
        val access = p[ACCESS] ?: return@map null; val refresh = p[REFRESH] ?: return@map null
        AuthSession(access, refresh, p[USERNAME].orEmpty(), p[ROLE].orEmpty())
    }
    suspend fun login(username: String, password: String) { val r = api.login(LoginRequest(username, password)); context.authDataStore.edit { p -> p[ACCESS]=r.accessToken; p[REFRESH]=r.refreshToken; p[USERNAME]=r.user.username; p[ROLE]=r.user.role } }
    suspend fun logout() { context.authDataStore.edit { it.clear() } }
    private companion object { val ACCESS=stringPreferencesKey("access"); val REFRESH=stringPreferencesKey("refresh"); val USERNAME=stringPreferencesKey("username"); val ROLE=stringPreferencesKey("role") }
}

@Module @InstallIn(SingletonComponent::class) object AuthModule {
    @Provides @Singleton fun api(): AuthApi = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(AuthApi::class.java)
}
